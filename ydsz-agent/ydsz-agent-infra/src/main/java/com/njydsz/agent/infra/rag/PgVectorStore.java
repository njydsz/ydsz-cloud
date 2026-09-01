package com.njydsz.agent.infra.rag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import com.njydsz.agent.domain.rag.EmbeddingClient;
import com.njydsz.agent.domain.rag.TextChunk;
import com.njydsz.agent.domain.rag.VectorStore;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.tenant.TenantContextHolder;

/**
 * PostgreSQL pgvector 向量存储实现
 *
 * <p>使用 PostgreSQL pgvector 扩展存储和检索向量数据。 依赖表 {@code ydsz_agt_document_chunk}（含 vector 类型列）。
 *
 * <p><b>DDL（多租户：需含 tenant_id 列）：</b>
 *
 * <pre>
 * CREATE TABLE ydsz_agt_document_chunk (
 *   id           VARCHAR(64) PRIMARY KEY,
 *   document_id  VARCHAR(64) NOT NULL,
 *   content      TEXT NOT NULL,
 *   embedding    vector(1536),
 *   chunk_index  INTEGER,
 *   token_count  INTEGER,
 *   document_title VARCHAR(256),
 *   source       VARCHAR(128),
 *   metadata     JSONB,
 *   tenant_id    VARCHAR(64),
 *   created_at   TIMESTAMPTZ DEFAULT NOW()
 * );
 * CREATE INDEX idx_chunk_embedding ON ydsz_agt_document_chunk USING ivfflat (embedding vector_cosine_ops);
 * CREATE INDEX idx_chunk_doc ON ydsz_agt_document_chunk(document_id);
 * CREATE INDEX idx_chunk_tenant ON ydsz_agt_document_chunk(tenant_id);
 * </pre>
 *
 * <p><b>多租户隔离（P0 修复）</b>：本实现走 {@link JdbcTemplate} 原生 SQL，不经过 MyBatis 租户拦截器，因此必须在 SQL 层显式追加 {@code
 * tenant_id} 条件，否则跨租户检索会泄露数据。 租户 ID 从 {@link TenantContextHolder} 解析；超级管理员/系统租户/跳过隔离场景不做过滤。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class PgVectorStore implements VectorStore {

  /** JDBC 模板 */
  private final JdbcTemplate jdbcTemplate;

  /** Embedding 客户端 */
  private final EmbeddingClient embeddingClient;

  /** 向量维度 */
  private final int dimension;

  /** 是否启用租户隔离（关闭时保持无 tenant_id 列的环境兼容） */
  private final boolean tenantIsolationEnabled;

  public PgVectorStore(
      JdbcTemplate jdbcTemplate, EmbeddingClient embeddingClient, boolean tenantIsolationEnabled) {
    this.jdbcTemplate = jdbcTemplate;
    this.embeddingClient = embeddingClient;
    this.dimension = embeddingClient.getDimension();
    this.tenantIsolationEnabled = tenantIsolationEnabled;
  }

  /**
   * 解析当前请求租户 ID；无需隔离时返回 null。
   *
   * @return 租户 ID；未启用隔离 / 无租户上下文 / 超管 / 跳过隔离时返回 null
   */
  private String resolveTenantId() {
    if (!tenantIsolationEnabled
        || !TenantContextHolder.isPresent()
        || TenantContextHolder.isSuperAdmin()
        || TenantContextHolder.isSkipIsolation()) {
      return null;
    }
    return TenantContextHolder.getTenantId();
  }

  /**
   * 存储文本块到 PostgreSQL 向量表。
   *
   * <p>写入 {@code ydsz_agt_document_chunk} 表，向量列使用 {@code ::vector} 类型转换，metadata 使用 {@code
   * ::jsonb}； 通过 {@code ON CONFLICT (id) DO UPDATE} 实现幂等 upsert， 重复入库同一文档块时更新内容、向量与元数据。启用租户隔离时写入
   * {@code tenant_id}。
   *
   * @param chunk 待存储的文本块；未生成嵌入时向量列写入 {@code null}
   */
  @Override
  public void store(TextChunk chunk) {
    String tenantId = resolveTenantId();
    String sql =
        """
                INSERT INTO ydsz_agt_document_chunk
                    (id, document_id, content, embedding, chunk_index, token_count,
                     document_title, source, metadata, tenant_id, created_at)
                VALUES (?, ?, ?, ?::vector, ?, ?, ?, ?, ?::jsonb, ?, NOW())
                ON CONFLICT (id) DO UPDATE SET
                    content = EXCLUDED.content,
                    embedding = EXCLUDED.embedding,
                    token_count = EXCLUDED.token_count,
                    metadata = EXCLUDED.metadata,
                    tenant_id = EXCLUDED.tenant_id
                """;
    String embeddingStr = chunk.hasEmbedding() ? vectorToString(chunk.getEmbedding()) : null;
    String metadataJson = YdszJson.toJson(chunk.getMetadata());
    jdbcTemplate.update(
        sql,
        chunk.getId(),
        chunk.getDocumentId(),
        chunk.getContent(),
        embeddingStr,
        chunk.getChunkIndex(),
        chunk.getTokenCount(),
        chunk.getDocumentTitle(),
        chunk.getSource(),
        metadataJson,
        tenantId);
    log.debug(
        "[VectorStore] 存储文本块: id={}, docId={}, tokens={}, tenantId={}",
        chunk.getId(),
        chunk.getDocumentId(),
        chunk.getTokenCount(),
        tenantId);
  }

  @Override
  public void storeBatch(List<TextChunk> chunks) {
    if (chunks == null || chunks.isEmpty()) {
      return;
    }
    // P1 优化：批量入库使用 JDBC batchUpdate，替代逐条 INSERT
    String tenantId = resolveTenantId();
    String sql =
        """
                INSERT INTO ydsz_agt_document_chunk
                    (id, document_id, content, embedding, chunk_index, token_count,
                     document_title, source, metadata, tenant_id, created_at)
                VALUES (?, ?, ?, ?::vector, ?, ?, ?, ?, ?::jsonb, ?, NOW())
                ON CONFLICT (id) DO UPDATE SET
                    content = EXCLUDED.content,
                    embedding = EXCLUDED.embedding,
                    token_count = EXCLUDED.token_count,
                    metadata = EXCLUDED.metadata,
                    tenant_id = EXCLUDED.tenant_id
                """;
    List<Object[]> batchArgs = new ArrayList<>(chunks.size());
    for (TextChunk chunk : chunks) {
      String embeddingStr = chunk.hasEmbedding() ? vectorToString(chunk.getEmbedding()) : null;
      String metadataJson = YdszJson.toJson(chunk.getMetadata());
      batchArgs.add(
          new Object[] {
            chunk.getId(),
            chunk.getDocumentId(),
            chunk.getContent(),
            embeddingStr,
            chunk.getChunkIndex(),
            chunk.getTokenCount(),
            chunk.getDocumentTitle(),
            chunk.getSource(),
            metadataJson,
            tenantId
          });
    }
    jdbcTemplate.batchUpdate(sql, batchArgs);
    log.info("[VectorStore] 批量存储完成: {} 个文本块 (tenantId={})", chunks.size(), tenantId);
  }

  @Override
  public List<TextChunk> search(String query, int topK, double minScore) {
    List<Float> queryVector = embeddingClient.embed(query);
    return searchByVector(queryVector, topK, minScore);
  }

  @Override
  public List<TextChunk> searchByVector(List<Float> embedding, int topK, double minScore) {
    if (embedding == null || embedding.isEmpty()) {
      return List.of();
    }
    String vectorStr = vectorToString(embedding);
    double minDistance = 1.0 - minScore;
    String tenantId = resolveTenantId();

    StringBuilder sql =
        new StringBuilder(
            """
                SELECT id, document_id, content, chunk_index, token_count,
                       document_title, source, metadata,
                       1 - (embedding <=> ?::vector) AS score
                FROM ydsz_agt_document_chunk
                WHERE embedding IS NOT NULL
                  AND (embedding <=> ?::vector) < ?
                """);
    List<Object> params = new ArrayList<>();
    params.add(vectorStr);
    params.add(vectorStr);
    params.add(minDistance);
    if (tenantId != null) {
      sql.append("  AND tenant_id = ?\n");
      params.add(tenantId);
    }
    sql.append("ORDER BY embedding <=> ?::vector\nLIMIT ?");
    params.add(vectorStr);
    params.add(topK);

    try {
      return jdbcTemplate.query(
          sql.toString(),
          (rs, rowNum) -> {
            Map<String, Object> metadata = new HashMap<>();
            String metadataJson = rs.getString("metadata");
            if (metadataJson != null && !metadataJson.isBlank()) {
              metadata = YdszJson.fromJson(metadataJson, Map.class);
            }
            return new TextChunk(
                rs.getString("id"),
                rs.getString("content"),
                rs.getString("document_id"),
                rs.getString("document_title"),
                rs.getString("source"),
                rs.getInt("chunk_index"),
                rs.getInt("token_count"),
                metadata,
                null);
          },
          params.toArray());
    } catch (Exception e) {
      log.warn("[VectorStore] 向量检索失败，降级返回空: {}", e.getMessage());
      return List.of();
    }
  }

  @Override
  public void deleteByDocument(String documentId) {
    String tenantId = resolveTenantId();
    if (tenantId != null) {
      jdbcTemplate.update(
          "DELETE FROM ydsz_agt_document_chunk WHERE document_id = ? AND tenant_id = ?",
          documentId,
          tenantId);
    } else {
      jdbcTemplate.update(
          "DELETE FROM ydsz_agt_document_chunk WHERE document_id = ?", documentId);
    }
    log.info("[VectorStore] 删除文档文本块: docId={}, tenantId={}", documentId, tenantId);
  }

  @Override
  public long count() {
    try {
      String tenantId = resolveTenantId();
      if (tenantId != null) {
        Long count =
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ydsz_agt_document_chunk WHERE tenant_id = ?",
                Long.class,
                tenantId);
        return count != null ? count : 0;
      }
      Long count =
          jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ydsz_agt_document_chunk", Long.class);
      return count != null ? count : 0;
    } catch (Exception e) {
      log.warn("[VectorStore] 统计文本块数量失败, err={}", e.getMessage());
      return 0;
    }
  }

  @Override
  public String getType() {
    return "pgvector";
  }

  @Override
  public boolean isAvailable() {
    try {
      jdbcTemplate.queryForObject("SELECT 1 FROM ydsz_agt_document_chunk LIMIT 1", Integer.class);
      return true;
    } catch (Exception e) {
      log.warn("[VectorStore] 可用性检查失败, err={}", e.getMessage());
      return false;
    }
  }

  private String vectorToString(List<Float> vector) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < vector.size(); i++) {
      if (i > 0) {
        sb.append(",");
      }
      sb.append(vector.get(i));
    }
    sb.append("]");
    return sb.toString();
  }
}
