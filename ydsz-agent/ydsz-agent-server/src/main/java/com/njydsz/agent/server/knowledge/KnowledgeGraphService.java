package com.njydsz.agent.server.knowledge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.LinkedHashSet;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.agent.domain.knowledge.Entity;
import com.njydsz.agent.domain.knowledge.EntityExtractionService;
import com.njydsz.agent.domain.knowledge.KnowledgeGraphStore;
import com.njydsz.agent.domain.knowledge.Relation;

/**
 * 知识图谱服务（编排层）。
 *
 * <p>桥接 {@link EntityExtractionService} 与 {@link KnowledgeGraphStore}，
 * 提供文档摄入、实体搜索、子图查询等编排能力。
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>{@link #ingestDocument} — 从文档原文抽取实体关系并入库到知识图谱
 *   <li>{@link #searchRelatedEntities} — 按关键词搜索相关实体（含邻域扩展）
 *   <li>{@link #querySubgraph} — 查询指定实体的 N 跳子图
 * </ul>
 *
 * <p><b>RAG 互补设计：</b>实体匹配更适合精确查找（如人名、产品名、技术栈），
 * 向量语义检索适合模糊语义匹配，两者可并行融合用于构建更完整的上下文。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
@Service
public class KnowledgeGraphService {

  /** 默认子图查询深度 */
  private static final int DEFAULT_SUBGRAPH_DEPTH = 3;

  /** 默认实体搜索邻域扩展深度 */
  private static final int DEFAULT_SEARCH_DEPTH = 2;

  /** 实体抽取服务 */
  private final EntityExtractionService extractionService;

  /** 图谱存储 */
  private final KnowledgeGraphStore graphStore;

  /**
   * 构造知识图谱服务。
   *
   * @param extractionService 实体抽取服务
   * @param graphStore 图谱存储
   */
  public KnowledgeGraphService(
      EntityExtractionService extractionService,
      KnowledgeGraphStore graphStore) {
    this.extractionService = Objects.requireNonNull(extractionService, "extractionService 不能为 null");
    this.graphStore = Objects.requireNonNull(graphStore, "graphStore 不能为 null");
  }

  /**
   * 摄入文档到知识图谱。
   *
   * <p>从文档原文中抽取实体和关系，写入图谱存储。
   * 已存在的实体自动合并（更新描述和属性），关系去重。
   *
   * @param docId 文档 ID
   * @param text 文档原文
   * @return 入库的实体数和关系数
   */
  public IngestResult ingestDocument(String docId, String text) {
    Objects.requireNonNull(docId, "docId 不能为 null");
    if (text == null || text.isBlank()) {
      return new IngestResult(0, 0);
    }
    log.info("[KG-Service] 开始摄入文档: docId={}, textLength={}", docId, text.length());

    // 抽取实体和关系
    EntityExtractionService.ExtractionResult extractionResult =
        extractionService.extract(text, docId);

    // 写入图谱
    int entityCount = 0;
    for (Entity entity : extractionResult.entities()) {
      graphStore.addEntity(entity);
      entityCount++;
    }
    int relationCount = 0;
    for (Relation relation : extractionResult.relations()) {
      graphStore.addRelation(relation);
      relationCount++;
    }

    log.info("[KG-Service] 文档摄入完成: docId={}, entities={}, relations={}",
        docId, entityCount, relationCount);
    return new IngestResult(entityCount, relationCount);
  }

  /**
   * 按关键词搜索相关实体。
   *
   * <p>先按名称匹配实体，然后扩展邻域（BFS N 跳）获得相关实体集合。
   *
   * @param query 搜索关键词
   * @param depth 邻域扩展深度（>= 1）
   * @return 匹配的实体列表（去重）
   */
  public List<Entity> searchRelatedEntities(String query, int depth) {
    if (query == null || query.isBlank()) {
      return Collections.emptyList();
    }
    int safeDepth = depth > 0 ? depth : DEFAULT_SEARCH_DEPTH;

    // 按名称匹配候选实体
    List<Entity> candidates = graphStore.findEntitiesByName(query);
    if (candidates.isEmpty()) {
      // 尝试从所有实体的描述中搜索全部匹配
      return searchAllEntities(query);
    }

    // 对每个候选实体做邻域扩展
    Set<Entity> expanded = new LinkedHashSet<>(candidates);
    for (Entity candidate : candidates) {
      List<Entity> neighbors = graphStore.traverse(candidate.id(), safeDepth);
      expanded.addAll(neighbors);
    }
    return new ArrayList<>(expanded);
  }

  /**
   * 查询实体子图。
   *
   * <p>返回包含指定实体及其 N 跳邻域的子图（实体 + 关系）。
   *
   * @param entityId 实体 ID
   * @param depth 跳数（>= 0）
   * @return 子图（实体列表 + 关系列表），实体不存在时返回空子图
   */
  public KnowledgeGraphStore.SubGraph querySubgraph(String entityId, int depth) {
    if (entityId == null || entityId.isBlank()) {
      return new KnowledgeGraphStore.SubGraph(Collections.emptyList(), Collections.emptyList());
    }
    int safeDepth = depth >= 0 ? depth : DEFAULT_SUBGRAPH_DEPTH;

    Optional<Entity> entityOpt = graphStore.findEntityById(entityId);
    if (entityOpt.isEmpty()) {
      log.warn("[KG-Service] 实体不存在: {}", entityId);
      return new KnowledgeGraphStore.SubGraph(Collections.emptyList(), Collections.emptyList());
    }
    return graphStore.subgraph(entityId, safeDepth);
  }

  /**
   * 查询实体的所有关系（出边 + 入边）。
   *
   * @param entityId 实体 ID
   * @return 关系列表
   */
  public List<Relation> queryRelations(String entityId) {
    if (entityId == null || entityId.isBlank()) {
      return Collections.emptyList();
    }
    return graphStore.findRelations(entityId);
  }

  /**
   * 按名称搜索实体（精确匹配优先，模糊匹配兜底）。
   *
   * @param name 实体名称
   * @return 匹配的实体列表
   */
  public List<Entity> searchEntitiesByName(String name) {
    if (name == null || name.isBlank()) {
      return Collections.emptyList();
    }
    return graphStore.findEntitiesByName(name);
  }

  /**
   * 获取图谱统计信息。
   *
   * @return 统计数据（实体数、关系数）
   */
  public Map<String, Long> getStats() {
    Map<String, Long> stats = new HashMap<>();
    stats.put("entityCount", graphStore.entityCount());
    stats.put("relationCount", graphStore.relationCount());
    return stats;
  }

  // ===== 私有方法 =====

  /**
   * 从所有实体中搜索（名称包含查询词模糊匹配）。
   *
   * <p>当初步 {@link #searchRelatedEntities} 名称匹配无结果时，作为兜底全量搜索。
   *
   * @param query 查询词
   * @return 匹配的实体列表
   */
  private List<Entity> searchAllEntities(String query) {
    // findEntitiesByName 内部做 contains 模糊匹配，此处直接复用
    return graphStore.findEntitiesByName(query);
  }

  /**
   * 文档摄入结果。
   *
   * @param entityCount 入库实体数
   * @param relationCount 入库关系数
   */
  public record IngestResult(int entityCount, int relationCount) {}
}
