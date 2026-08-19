package com.njydsz.agent.web.controller;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.agent.api.dto.DocumentIngestDTO;
import com.njydsz.agent.api.dto.RagQueryDTO;
import com.njydsz.agent.domain.rag.TextChunk;
import com.njydsz.agent.server.rag.DocumentIngestionService;
import com.njydsz.agent.server.rag.RagService;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import lombok.extern.slf4j.Slf4j;

/**
 * RAG 知识库管理 REST API Controller。
 *
 * <p>提供 RAG（Retrieval-Augmented Generation）知识库管理和检索能力，是 Agent 增强回答准确性的关键组件：
 *
 * <ul>
 *   <li>{@code POST /agent/rag/ingest} - 摄入文档到知识库（自动分块 + 向量化）
 *   <li>{@code POST /agent/rag/search} - 向量相似度检索（返回 TopK 匹配 + 引用 + 上下文）
 *   <li>{@code DELETE /agent/rag/documents/{documentId}} - 删除指定文档的所有索引
 *   <li>{@code GET /agent/rag/stats} - 获取向量存储统计（文档数 / chunk 数 / 容量等）
 * </ul>
 *
 * <h3>核心流程</h3>
 *
 * <pre>
 *   文档摄入：原文 → 分块（chunking）→ Embedding → 向量存储
 *   检索：Query → Embedding → 向量相似度（cosine/euclidean）→ TopK chunk + 引用
 * </pre>
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>文档摄入：支持任意长度文档，自动按 token 长度切分 chunk
 *   <li>相似度检索：支持自定义 topK / minScore / includeContext
 *   <li>引用追溯：返回 {@code Citation} 列表（含 documentId / chunkId / score / 文本片段）
 *   <li>上下文构建：将 TopK chunk 拼装为 LLM 可消费的 context 字符串
 * </ul>
 *
 * <h3>安全与稳定性</h3>
 *
 * <ul>
 *   <li>所有写操作均加 {@link Idempotent} 防重（5s TTL）
 *   <li>所有写操作均加 {@link Audit} 异步落库审计日志
 *   <li>删除接口加 {@link RateLimit} 限流（50 QPS）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent/rag")
public class RagController {

  /** RAG 检索服务（封装向量相似度检索 + 引用构建 + 上下文拼装） */
  private final RagService ragService;

  /** 文档摄入服务（封装分块 + Embedding + 写入向量库） */
  private final DocumentIngestionService ingestionService;

  public RagController(RagService ragService, DocumentIngestionService ingestionService) {
    this.ragService = ragService;
    this.ingestionService = ingestionService;
  }

  /**
   * 摄入文档到知识库。
   *
   * <p>处理流程：
   *
   * <ol>
   *   <li>对 documentId 对应的原文做分块（按 token 长度切分）
   *   <li>对每个 chunk 调 Embedding 模型生成向量
   *   <li>将 chunk + vector 写入向量存储（pgvector / Milvus 等）
   *   <li>返回 chunk 数量，便于前端展示"已建立 X 条索引"
   * </ol>
   *
   * @param request 文档摄入请求（documentId / documentTitle / content / source）
   * @return 统一响应结果，data 为 {@code {documentId, chunkCount, status}} Map
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_RAG_INGEST)
  @Audit(
      module = "RAG管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'ingest'")
  @Idempotent(key = "ydsz:agent:RagController:ingest:lock", ttlSeconds = 5)
  @PostMapping("/ingest")
  public YdszResponse<Map<String, Object>> ingest(@Valid @RequestBody DocumentIngestDTO request) {
    log.info(
        "[RAG-API] 摄入文档: docId={}, title={}", request.getDocumentId(), request.getDocumentTitle());
    int chunkCount =
        ingestionService.ingest(
            request.getDocumentId(),
            request.getContent(),
            request.getDocumentTitle(),
            request.getSource());
    return YdszResponse.success(
        Map.of(
            "documentId", request.getDocumentId(), "chunkCount", chunkCount, "status", "ingested"));
  }

  /**
   * 向量相似度检索。
   *
   * <p>处理流程：
   *
   * <ol>
   *   <li>对 query 做 Embedding 得到查询向量
   *   <li>在向量库中按 cosine 相似度检索 TopK chunk
   *   <li>过滤 score &lt; minScore 的 chunk（默认 0.7）
   *   <li>构建引用列表（{@code Citation}）和上下文（{@code context}）
   * </ol>
   *
   * @param request RAG 检索请求（query / topK / minScore / includeContext）
   * @return 统一响应结果，data 为 {@code {query, resultCount, citations, context}} Map
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_RAG_SEARCH)
  @Audit(
      module = "RAG管理",
      type = AuditType.OPERATION,
      action = AuditAction.QUERY,
      content = "'search'")
  @PostMapping("/search")
  public YdszResponse<Map<String, Object>> search(@Valid @RequestBody RagQueryDTO request) {
    // 参数默认值兜底：topK=5 / minScore=0.7 / includeContext=true
    int topK = request.getTopK() != null ? request.getTopK() : 5;
    double minScore = request.getMinScore() != null ? request.getMinScore() : 0.7;
    boolean includeContext = request.getIncludeContext() == null || request.getIncludeContext();

    // 1. 检索 TopK chunk
    List<TextChunk> chunks = ragService.retrieve(request.getQuery(), topK, minScore);
    // 2. 构建引用列表
    List<RagService.Citation> citations = ragService.getCitations(chunks);
    // 3. 拼装上下文（可选）
    String context = includeContext ? ragService.buildContext(chunks) : null;

    return YdszResponse.success(
        Map.of(
            "query",
            request.getQuery(),
            "resultCount",
            chunks.size(),
            "citations",
            citations,
            "context",
            context != null ? context : ""));
  }

  /**
   * 删除指定文档的所有索引。
   *
   * <p>从向量库中删除 documentId 对应的全部 chunk（按 document_id 字段过滤）， 通常用于文档下线、内容修订后的索引重建。注意：此操作不可逆，删除后无法恢复。
   *
   * @param documentId 文档 ID
   * @return 统一响应结果
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_RAG_DELETE)
  @Audit(
      module = "RAG管理",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'deleteDocument'")
  @Idempotent(key = "ydsz:agent:RagController:deleteDocument:lock", ttlSeconds = 5)
  @RateLimit(resource = "agent.rag.deleteDocument", threshold = 50)
  @DeleteMapping("/documents/{documentId}")
  public YdszResponse<Void> deleteDocument(@PathVariable String documentId) {
    log.info("[RAG-API] 删除文档索引: documentId={}", documentId);
    ingestionService.delete(documentId);
    return YdszResponse.success();
  }

  /**
   * 获取向量存储统计。
   *
   * <p>返回向量库的容量/使用情况（文档总数 / chunk 总数 / 存储占用等）， 供运维监控和容量规划使用。
   *
   * @return 统一响应结果，data 为 {@link DocumentIngestionService.VectorStoreStats}
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_RAG_SEARCH)
  @Audit(
      module = "RAG管理",
      type = AuditType.OPERATION,
      action = AuditAction.QUERY,
      content = "'stats'")
  @GetMapping("/stats")
  public YdszResponse<DocumentIngestionService.VectorStoreStats> stats() {
    return YdszResponse.success(ingestionService.getStats());
  }
}
