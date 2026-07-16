package com.njydsz.agent.infra.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.agent.domain.rag.EmbeddingClient;
import com.njydsz.agent.domain.rag.TextChunk;
import com.njydsz.agent.domain.rag.VectorStore;

/**
 * 内存向量存储（测试/降级用）
 *
 * <p>使用余弦相似度计算向量距离，数据不持久化。
 * 适用于开发测试、PG 不可用时的降级方案。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
public class InMemoryVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryVectorStore.class);
    private final List<TextChunk> store = new CopyOnWriteArrayList<>();
    private final EmbeddingClient embeddingClient;

    public InMemoryVectorStore(EmbeddingClient embeddingClient) {
        this.embeddingClient = embeddingClient;
    }

    @Override
    public void store(TextChunk chunk) {
        TextChunk stored = chunk.hasEmbedding() ? chunk : chunk.withEmbedding(embeddingClient.embed(chunk.getContent()));
        store.add(stored);
    }

    @Override
    public void storeBatch(List<TextChunk> chunks) {
        for (TextChunk chunk : chunks) {
            store(chunk);
        }
        log.info("[Memory-VectorStore] 批量存储: {} 块, 总计: {}", chunks.size(), store.size());
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
        List<ScoredChunk> scored = new ArrayList<>();
        for (TextChunk chunk : store) {
            if (!chunk.hasEmbedding()) {
                continue;
            }
            double score = cosineSimilarity(embedding, chunk.getEmbedding());
            if (score >= minScore) {
                scored.add(new ScoredChunk(chunk, score));
            }
        }
        scored.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        return scored.stream()
                .limit(topK)
                .map(s -> s.chunk)
                .toList();
    }

    @Override
    public void deleteByDocument(String documentId) {
        store.removeIf(chunk -> documentId.equals(chunk.getDocumentId()));
    }

    @Override
    public long count() {
        return store.size();
    }

    @Override
    public String getType() {
        return "memory";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    private double cosineSimilarity(List<Float> a, List<Float> b) {
        if (a.size() != b.size()) {
            return 0;
        }
        double dotProduct = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.size(); i++) {
            dotProduct += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private record ScoredChunk(TextChunk chunk, double score) {}
}
