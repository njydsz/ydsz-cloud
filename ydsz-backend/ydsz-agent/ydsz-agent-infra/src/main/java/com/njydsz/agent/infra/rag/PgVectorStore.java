package com.njydsz.agent.infra.rag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.njydsz.common.json.YdszJson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import com.njydsz.agent.domain.rag.TextChunk;

/**
 * PostgreSQL pgvector 向量存储实现
 *
 * <p>使用 PostgreSQL pgvector 扩展存储和检索向量数据。
 * 依赖表 {@code ydsz_agent_document_chunk}（含 vector 类型列）。
 *
 * <p><b>DDL：</b>
 * <pre>
 * CREATE TABLE ydsz_agent_document_chunk (
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
 * CREATE INDEX idx_chunk_embedding ON ydsz_agent_document_chunk USING ivfflat (embedding vector_cosine_ops);
 * CREATE INDEX idx_chunk_doc ON ydsz_agent_document_chunk(document_id);
 * </pre>
 *
 * @author ydsz-team
 * @since 1.2.0
 */
public class PgVectorStore implements com.njydsz.agent.domain.rag.VectorStore {

    private static final Logger log = LoggerFactory.getLogger(PgVectorStore.class);

    private final JdbcTemplate jdbcTemplate;
    private final com.njydsz.agent.domain.rag.EmbeddingClient embeddingClient;
    private final int dimension;

    public PgVectorStore(JdbcTemplate jdbcTemplate,
                         com.njydsz.agent.domain.rag.EmbeddingClient embeddingClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingClient = embeddingClient;
        this.dimension = embeddingClient.getDimension();
    }

    @Override
    public void store(TextChunk chunk) {
        String sql = """
                INSERT INTO ydsz_agent_document_chunk
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
        String metadataJson = YdszJson.toJson(chunk.getMetadata());
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
                FROM ydsz_agent_document_chunk
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
                            metadata = YdszJson.toObject(metadataJson, Map.class);
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
                "DELETE FROM ydsz_agent_document_chunk WHERE document_id = ?",
                documentId);
        log.info("[VectorStore] 删除文档文本块: docId={}", documentId);
    }

    @Override
    public long count() {
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ydsz_agent_document_chunk", Long.class);
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
            jdbcTemplate.queryForObject("SELECT 1 FROM ydsz_agent_document_chunk LIMIT 1", Integer.class);
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
