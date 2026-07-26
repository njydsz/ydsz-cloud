package com.njydsz.agent.infra.rag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import com.njydsz.agent.domain.rag.TextChunk;
import com.njydsz.agent.domain.rag.VectorStore;

/**
 * 混合检索器（Hybrid Retrieval）
 *
 * <p>结合向量相似度检索和全文检索（BM25/ILIKE），通过 RRF（Reciprocal Rank Fusion）
 * 融合两路检索结果，提升召回率和精确度。
 *
 * <h3>RRF 算法</h3>
 * <pre>
 * score(d) = Σ 1 / (k + rank_i(d))
 * </pre>
 * <p>其中 k 为平滑常数（默认 60），rank_i(d) 为文档 d 在第 i 路检索中的排名（从 1 开始）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class HybridRetriever {

    private static final Logger log = LoggerFactory.getLogger(HybridRetriever.class);
    private static final int RRF_K = 60;
    private static final String TABLE_NAME = "ydsz_agent_document_chunk";

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final boolean fullTextAvailable;

    public HybridRetriever(VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.fullTextAvailable = checkFullTextAvailability();
    }

    public List<TextChunk> retrieve(String query, int topK, double minScore) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<TextChunk> vectorResults = vectorStore.search(query, topK * 2, minScore * 0.5);
        log.debug("[Hybrid-Retrieval] 向量检索: {} 条", vectorResults.size());

        List<TextChunk> fullTextResults = List.of();
        if (fullTextAvailable) {
            fullTextResults = fullTextSearch(query, topK * 2);
            log.debug("[Hybrid-Retrieval] 全文检索: {} 条", fullTextResults.size());
        }

        List<TextChunk> merged = rrfFuse(vectorResults, fullTextResults, topK);
        log.info("[Hybrid-Retrieval] 混合检索完成: query='{}', vector={}, fulltext={}, merged={}",
                truncate(query, 50), vectorResults.size(), fullTextResults.size(), merged.size());
        return merged;
    }

    private List<TextChunk> rrfFuse(List<TextChunk> vectorResults, List<TextChunk> fullTextResults, int topK) {
        Map<String, RrfEntry> entryMap = new HashMap<>();

        for (int i = 0; i < vectorResults.size(); i++) {
            TextChunk chunk = vectorResults.get(i);
            String key = chunk.getId() != null ? chunk.getId() : chunk.getContent();
            entryMap.computeIfAbsent(key, k -> new RrfEntry(chunk))
                    .addScore(1.0 / (RRF_K + i + 1));
        }

        for (int i = 0; i < fullTextResults.size(); i++) {
            TextChunk chunk = fullTextResults.get(i);
            String key = chunk.getId() != null ? chunk.getId() : chunk.getContent();
            entryMap.computeIfAbsent(key, k -> new RrfEntry(chunk))
                    .addScore(1.0 / (RRF_K + i + 1));
        }

        return entryMap.values().stream()
                .sorted((a, b) -> Double.compare(b.rrfScore, a.rrfScore))
                .limit(topK)
                .map(e -> e.chunk)
                .toList();
    }

    private List<TextChunk> fullTextSearch(String query, int topK) {
        try {
            String sql = "SELECT id, content, document_id, document_title, source, " +
                    "chunk_index, token_count FROM " + TABLE_NAME + " " +
                    "WHERE deleted = false AND content ILIKE ? " +
                    "ORDER BY ts_rank(to_tsvector('simple', content), " +
                    "plainto_tsquery('simple', ?)) DESC " +
                    "LIMIT ?";
            String pattern = "%" + query.replace("%", "\\%").replace("_", "\\_") + "%";
            return jdbcTemplate.query(sql,
                    (rs, rowNum) -> new TextChunk(
                            rs.getString("id"),
                            rs.getString("content"),
                            rs.getString("document_id"),
                            rs.getString("document_title"),
                            rs.getString("source"),
                            rs.getInt("chunk_index"),
                            rs.getInt("token_count"),
                            Map.of(),
                            null
                    ),
                    pattern, query, topK);
        } catch (Exception e) {
            log.warn("[Hybrid-Retrieval] 全文检索失败，降级到纯向量检索: {}", e.getMessage());
            return List.of();
        }
    }

    private boolean checkFullTextAvailability() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = '" + TABLE_NAME + "'",
                    Integer.class);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    private static class RrfEntry {
        final TextChunk chunk;
        double rrfScore = 0.0;

        RrfEntry(TextChunk chunk) {
            this.chunk = chunk;
        }

        void addScore(double score) {
            this.rrfScore += score;
        }
    }
}
