package com.remisoft.agent.infra.rag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.remisoft.common.json.RemiJson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import com.remisoft.agent.domain.rag.TextChunk;

import com.remisoft.agent.domain.rag.EmbeddingClient;
import com.remisoft.agent.domain.rag.VectorStore;
/**
 * PostgreSQL pgvector 向量存储实现
 *
 * <p>使用 PostgreSQL pgvector 扩展存储和检索向量数据。
 * 依赖表 {@code remi_agent_document_chunk}（含 vector 类型列）。
 *
 * <p><b>DDL：</b>
 * <pre>
 * CREATE TABLE remi_agent_document_chunk (
 *   id           VARCHAR(64) PRIMARY KEY,
 *   document_id  VARCHAR(64) NOT NULL,
 *   content      TEXT NOT NULL,
 *   embedding    vector(1536),
 *   chunk_index  INTEGER,
 *   token_count  INTEGER,
 *   document_title VARCHAR(256),
 *   source       VARCHAR(128),
 *   metadata     JSONB,
 *   created_at   TIMESTAMPTZ DEFAULT NOW()
 * );
 * CREATE INDEX idx_chunk_embedding ON remi_agent_document_chunk USING ivfflat (embedding vector_cosine_ops);
 * CREATE INDEX idx_chunk_doc ON remi_agent_document_chunk(document_id);
 * </pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
public class PgVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(PgVectorStore.class);

    /** JDBC 模板 */
    private final JdbcTemplate jdbcTemplate;
    /** Embedding 客户端 */
    private final EmbeddingClient embeddingClient;
    /** 向量维度 */
    private final int dimension;

    public PgVectorStore(JdbcTemplate jdbcTemplate,
                         EmbeddingClient embeddingClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingClient = embeddingClient;
        this.dimension = embeddingClient.getDimension();
    }

    /**
     * 存储文本块到 PostgreSQL 向量表。
     *
     * <p>写入 {@code remi_agent_document_chunk} 表，向量列使用
     * {@code ::vector} 类型转换，metadata 使用 {@code ::jsonb}；
     * 通过 {@code ON CONFLICT (id) DO UPDATE} 实现幂等 upsert，
     * 重复入库同一文档块时更新内容、向量与元数据。</p>
     *
     * @param chunk 待存储的文本块；未生成嵌入时向量列写入 {@code null}
     */
    @Override
    public void store(TextChunk chunk) {
        String sql = """
                INSERT INTO remi_agent_document_chunk
                    (id, document_id, content, embedding, chunk_index, token_count,
                     document_title, source, metadata, created_at)
                VALUES (?, ?, ?, ?::vector, ?, ?, ?, ?, ?::jsonb, NOW())
                ON CONFLICT (id) DO UPDATE SET
                    content = EXCLUDED.content,
                    embedding = EXCLUDED.embedding,
                    token_count = EXCLUDED.token_count,
                    metadata = EXCLUDED.metadata
                """;
        String embeddingStr = chunk.hasEmbedding() ? vectorToString(chunk.getEmbedding()) : null;
        String metadataJson = RemiJson.toJson(chunk.getMetadata());
        jdbcTemplate.update(sql,
                chunk.getId(),
                chunk.getDocumentId(),
                chunk.getContent(),
                embeddingStr,
                chunk.getChunkIndex(),
                chunk.getTokenCount(),
                chunk.getDocumentTitle(),
                chunk.getSource(),
                metadataJson);
        log.debug("[VectorStore] 存储文本块: id={}, docId={}, tokens={}",
                chunk.getId(), chunk.getDocumentId(), chunk.getTokenCount());
    }

    @Override
    public void storeBatch(List<TextChunk> chunks) {
        for (TextChunk chunk : chunks) {
            store(chunk);
        }
        log.info("[VectorStore] 批量存储完成: {} 个文本块", chunks.size());
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

        String sql = """
                SELECT id, document_id, content, chunk_index, token_count,
                       document_title, source, metadata,
                       1 - (embedding <=> ?::vector) AS score
                FROM remi_agent_document_chunk
                WHERE embedding IS NOT NULL
                  AND (embedding <=> ?::vector) < ?
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """;
        try {
            return jdbcTemplate.query(sql,
                    (rs, rowNum) -> {
                        Map<String, Object> metadata = new HashMap<>();
                        String metadataJson = rs.getString("metadata");
                        if (metadataJson != null && !metadataJson.isBlank()) {
                            metadata = RemiJson.fromJson(metadataJson, Map.class);
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
                    vectorStr, vectorStr, minDistance, vectorStr, topK);
        } catch (Exception e) {
            log.warn("[VectorStore] 向量检索失败，降级返回空: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public void deleteByDocument(String documentId) {
        jdbcTemplate.update(
                "DELETE FROM remi_agent_document_chunk WHERE document_id = ?",
                documentId);
        log.info("[VectorStore] 删除文档文本块: docId={}", documentId);
    }

    @Override
    public long count() {
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM remi_agent_document_chunk", Long.class);
            return count != null ? count : 0;
        } catch (Exception e) {
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
            jdbcTemplate.queryForObject("SELECT 1 FROM remi_agent_document_chunk LIMIT 1", Integer.class);
            return true;
        } catch (Exception e) {
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
