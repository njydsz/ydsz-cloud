package com.njydsz.agent.web.controller;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.agent.domain.knowledge.Entity;
import com.njydsz.agent.domain.knowledge.KnowledgeGraphStore;
import com.njydsz.agent.domain.knowledge.Relation;
import com.njydsz.agent.server.knowledge.KnowledgeGraphService;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.safe.idempotent.annotation.Idempotent;

/**
 * 知识图谱 REST API Controller。
 *
 * <p>提供知识图谱的文档摄入、实体搜索、子图查询等能力，与 RAG 向量检索互补：
 *
 * <ul>
 *   <li>{@code POST /agent/knowledge/ingest} — 摄入文档到知识图谱（抽取实体关系）
 *   <li>{@code GET /agent/knowledge/entity/search} — 按名称搜索实体（含邻域扩展）
 *   <li>{@code GET /agent/knowledge/entity/{entityId}/subgraph} — 查询实体 N 跳子图
 *   <li>{@code GET /agent/knowledge/entity/{entityId}/relations} — 查询实体的关系边
 *   <li>{@code GET /agent/knowledge/stats} — 获取图谱统计信息
 * </ul>
 *
 * <h3>与 RAG 互补</h3>
 *
 * <p>知识图谱擅长精确匹配（人名、产品名、技术栈等实体级别的查找），
 * RAG 向量检索擅长语义模糊匹配。建议在前端或 Agent 层将两者并行融合使用。
 *
 * <h3>权限控制</h3>
 *
 * <p>所有接口要求对应的 Agent 模块权限码：
 *
 * <ul>
 *   <li>摄入：{@code agent:knowledge:ingest}
 *   <li>搜索/查询：{@code agent:knowledge:search}
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
@Validated
@ApiVersion("26.09.17")
@RestController
@RequestMapping("/agent/knowledge")
public class KnowledgeGraphController {

  /** 默认子图查询深度 */
  private static final int DEFAULT_DEPTH = 3;

  /** 图谱服务 */
  private final KnowledgeGraphService knowledgeGraphService;

  /**
   * 构造知识图谱 Controller。
   *
   * @param knowledgeGraphService 知识图谱服务
   */
  public KnowledgeGraphController(KnowledgeGraphService knowledgeGraphService) {
    this.knowledgeGraphService = knowledgeGraphService;
  }

  /**
   * 摄入文档到知识图谱。
   *
   * <p>处理流程：
   *
   * <ol>
   *   <li>对 {@code content} 进行段落切分（按语义分段）</li>
   *   <li>调用 LLM（配置默认模型）逐段抽取实体（人名、产品名、技术栈、事件等）和关系（属于、使用、依赖等）</li>
   *   <li>实体去重合并（同名同类型实体合并，置信度取最大值），同名异义实体通过上下文消歧</li>
   *   <li>将去重后的实体和关系写入图存储（Neo4j / 关系型图存储）</li>
   * </ol>
   *
   * <p>Token 消耗：与文档长度正相关，估算公式 {@code ≈ content.length / 4 * 2}（输入+输出 Token）。
   * 抽取模型优先级：配置的实体抽取模型 > 系统默认 LLM。Token 配额消耗计入发起租户当日配额。
   *
   * <p>幂等性：同一 docId 重复摄入会触发全量覆盖（先删除旧实体关系，再写入新结果），不会产生重复数据。
   *
   * @param request 摄入请求 Map（必填：docId / content；可选：metadata 等扩展字段）
   * @return 统一响应结果，data 为 {@code {docId, entityCount, relationCount, status}} Map；status 为 "ingested" 表示成功
   */
  @AuthApiPermission(apiCodes = {"agent:knowledge:ingest"})
  @Audit(
      module = "知识图谱",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'ingest'")
  @Idempotent(key = "ydsz:agent:KnowledgeGraphController:ingest:lock", ttlSeconds = 10)
  @PostMapping("/ingest")
  public YdszResponse<Map<String, Object>> ingest(@RequestBody Map<String, String> request) {
    String docId = request.get("docId");
    String content = request.get("content");
    if (docId == null || docId.isBlank()) {
      return YdszResponse.error("docId 不能为空");
    }
    if (content == null || content.isBlank()) {
      return YdszResponse.error("content 不能为空");
    }
    log.info("[KG-API] 摄入文档: docId={}, contentLength={}", docId, content.length());

    KnowledgeGraphService.IngestResult result = knowledgeGraphService.ingestDocument(docId, content);
    return YdszResponse.success(Map.of(
        "docId", docId,
        "entityCount", result.entityCount(),
        "relationCount", result.relationCount(),
        "status", "ingested"));
  }

  /**
   * 按名称搜索实体（含邻域扩展）。
   *
   * <p>处理流程：
   *
   * <ol>
   *   <li>在图存储中按实体名称精确匹配（case-insensitive）</li>
   *   <li>对匹配实体执行 {@code depth} 跳邻域扩展（沿关系边 BFS 遍历）</li>
   *   <li>去重后返回实体集合（含核心实体 + 邻域实体）</li>
   * </ol>
   *
   * <p>本接口为纯图检索，不消耗 LLM Token。如需语义模糊匹配请结合 RAG 向量检索使用。
   *
   * @param query 搜索关键词（必填，匹配实体名称），支持中英文
   * @param depth 邻域扩展深度（默认 2，范围 1-5），0 表示仅返回核心实体不扩展
   * @return 统一响应结果，data 为 {@link EntityVO} 列表（含 id / name / type / description / sourceDocId）；无匹配时返回空列表
   */
  @AuthApiPermission(apiCodes = {"agent:knowledge:search"})
  @Audit(
      module = "知识图谱",
      type = AuditType.OPERATION,
      action = AuditAction.QUERY,
      content = "'searchEntities'")
  @GetMapping("/entity/search")
  public YdszResponse<List<EntityVO>> searchEntities(
      @RequestParam("query") String query,
      @RequestParam(value = "depth", defaultValue = "2") @Min(1) @Max(5) int depth) {
    log.info("[KG-API] 搜索实体: query={}, depth={}", query, depth);
    List<Entity> entities = knowledgeGraphService.searchRelatedEntities(query, depth);
    List<EntityVO> vos = new ArrayList<>(entities.size());
    for (Entity entity : entities) {
      vos.add(toEntityVO(entity));
    }
    return YdszResponse.success(vos);
  }

  /**
   * 查询实体子图（N 跳邻域内的实体和关系）。
   *
   * <p>以指定实体为起点，执行 {@code depth} 跳 BFS 遍历获取子图（实体集合 + 关系边集合）。
   * 用于前端知识图谱可视化渲染或 Agent 关联推理。depth=0 仅返回实体本身。
   *
   * <p>本接口为纯图检索，不消耗 LLM Token。
   *
   * @param entityId 实体 ID（路径参数，由摄入接口返回）
   * @param depth 跳数（默认 3，范围 0-5），0 表示仅返回实体本身
   * @return 统一响应结果，data 为 {@link SubGraphVO}（含 entities / relations 两个列表）；实体 ID 不存在时返回空子图（非 null）
   */
  @AuthApiPermission(apiCodes = {"agent:knowledge:search"})
  @Audit(
      module = "知识图谱",
      type = AuditType.OPERATION,
      action = AuditAction.QUERY,
      content = "'querySubgraph'")
  @GetMapping("/entity/{entityId}/subgraph")
  public YdszResponse<SubGraphVO> querySubgraph(
      @PathVariable String entityId,
      @RequestParam(value = "depth", defaultValue = "3") @Min(0) @Max(5) int depth) {
    log.info("[KG-API] 查询子图: entityId={}, depth={}", entityId, depth);
    KnowledgeGraphStore.SubGraph subGraph = knowledgeGraphService.querySubgraph(entityId, depth);

    List<EntityVO> entityVOs = new ArrayList<>(subGraph.entities().size());
    for (Entity entity : subGraph.entities()) {
      entityVOs.add(toEntityVO(entity));
    }
    List<RelationVO> relationVOs = new ArrayList<>(subGraph.relations().size());
    for (Relation relation : subGraph.relations()) {
      relationVOs.add(toRelationVO(relation));
    }
    return YdszResponse.success(new SubGraphVO(entityVOs, relationVOs));
  }

  /**
   * 查询实体的所有关系边。
   *
   * <p>返回指定实体作为起始节点或目标节点的全部关系边（包括入边和出边）。
   * 本接口为纯图检索，不消耗 LLM Token。
   *
   * @param entityId 实体 ID（路径参数）
   * @return 统一响应结果，data 为 {@link RelationVO} 列表（含 id / fromEntityId / toEntityId / type / confidence）；无关系时返回空列表
   */
  @AuthApiPermission(apiCodes = {"agent:knowledge:search"})
  @Audit(
      module = "知识图谱",
      type = AuditType.OPERATION,
      action = AuditAction.QUERY,
      content = "'queryRelations'")
  @GetMapping("/entity/{entityId}/relations")
  public YdszResponse<List<RelationVO>> queryRelations(@PathVariable String entityId) {
    log.info("[KG-API] 查询关系: entityId={}", entityId);
    List<Relation> relations = knowledgeGraphService.queryRelations(entityId);
    List<RelationVO> vos = new ArrayList<>(relations.size());
    for (Relation relation : relations) {
      vos.add(toRelationVO(relation));
    }
    return YdszResponse.success(vos);
  }

  /**
   * 获取图谱统计信息。
   *
   * <p>返回知识图谱存储的全局统计数据。数据来源：图存储层执行 count 查询（如 Neo4j 的 MATCH COUNT），
   * 不消耗 LLM Token。统计值为实时快照，不同请求间可能因并发摄入而略有差异。
   *
   * @return 统一响应结果，data 为 {@code {entityCount: Long, relationCount: Long}} Map（具体字段名由 {@link KnowledgeGraphService#getStats()} 返回）
   */
  @AuthApiPermission(apiCodes = {"agent:knowledge:search"})
  @Audit(
      module = "知识图谱",
      type = AuditType.OPERATION,
      action = AuditAction.QUERY,
      content = "'stats'")
  @GetMapping("/stats")
  public YdszResponse<Map<String, Long>> stats() {
    return YdszResponse.success(knowledgeGraphService.getStats());
  }

  // ===== VO 转换 =====

  private EntityVO toEntityVO(Entity entity) {
    return new EntityVO(
        entity.id(),
        entity.name(),
        entity.type().name(),
        entity.description(),
        entity.sourceDocId());
  }

  private RelationVO toRelationVO(Relation relation) {
    return new RelationVO(
        relation.id(),
        relation.fromEntityId(),
        relation.toEntityId(),
        relation.type().name(),
        relation.confidence());
  }

  /**
   * 实体视图对象（API 输出）。
   *
   * @param id 实体 ID
   * @param name 实体名称
   * @param type 实体类型
   * @param description 实体描述
   * @param sourceDocId 来源文档 ID
   */
  public record EntityVO(
      String id, String name, String type, String description, String sourceDocId) {}

  /**
   * 关系视图对象（API 输出）。
   *
   * @param id 关系 ID
   * @param fromEntityId 起始实体 ID
   * @param toEntityId 目标实体 ID
   * @param type 关系类型
   * @param confidence 置信度
   */
  public record RelationVO(
      String id,
      String fromEntityId,
      String toEntityId,
      String type,
      BigDecimal confidence) {}

  /**
   * 子图视图对象（API 输出）。
   *
   * @param entities 实体列表
   * @param relations 关系列表
   */
  public record SubGraphVO(List<EntityVO> entities, List<RelationVO> relations) {}
}
