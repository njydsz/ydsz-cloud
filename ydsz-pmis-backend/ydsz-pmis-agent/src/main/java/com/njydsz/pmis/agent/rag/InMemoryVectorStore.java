package com.njydsz.pmis.agent.rag;

import com.njydsz.pmis.agent.engine.embedding.MockEmbeddingProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存向量存储实现（P3-1 落地）。
 *
 * <p>用于单元测试与无 DB 环境降级。使用 {@link ConcurrentHashMap} 存储，
 * 检索时遍历计算余弦相似度。
 *
 * <p><b>注意</b>：非线程安全的批量检索场景需自行加锁，单测场景无需考虑。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-1)
 */
public class InMemoryVectorStore implements VectorStore {

    /** 内存分块条目 */
    private record ChunkEntry(String id, String knowledgeBaseId, String documentId,
                              int chunkIndex, String content, float[] embedding,
                              int tokenCount) {}

    /** 存储：id → chunk */
    private final ConcurrentMap<String, ChunkEntry> store = new ConcurrentHashMap<>();
    /** ID 生成器 */
    private final AtomicLong idSeq = new AtomicLong(0);

    @Override
    public String store(String knowledgeBaseId, String documentId, int chunkIndex,
                       String content, float[] embedding, int tokenCount) {
        String id = "chunk-" + idSeq.incrementAndGet();
        ChunkEntry entry = new ChunkEntry(id, knowledgeBaseId, documentId,
                chunkIndex, content, embedding.clone(), tokenCount);
        store.put(id, entry);
        return id;
    }

    @Override
    public List<RetrievedChunk> search(String knowledgeBaseId, float[] queryVector, int topK) {
        if (queryVector == null || topK <= 0) {
            return List.of();
        }
        List<RetrievedChunk> results = new ArrayList<>();
        for (ChunkEntry entry : store.values()) {
            if (!entry.knowledgeBaseId().equals(knowledgeBaseId)) {
                continue;
            }
            double score = MockEmbeddingProvider.cosineSimilarity(queryVector, entry.embedding());
            results.add(toRetrievedChunk(entry, score));
        }
        results.sort(Comparator.comparingDouble(RetrievedChunk::getScore).reversed());
        return results.size() <= topK ? results : results.subList(0, topK);
    }

    private static RetrievedChunk toRetrievedChunk(ChunkEntry entry, double score) {
        return RetrievedChunk.builder()
                .id(entry.id())
                .documentId(entry.documentId())
                .knowledgeBaseId(entry.knowledgeBaseId())
                .chunkIndex(entry.chunkIndex())
                .content(entry.content())
                .tokenCount(entry.tokenCount())
                .score(score)
                .build();
    }

    @Override
    public int deleteByDocument(String documentId) {
        int count = 0;
        for (ChunkEntry entry : new ArrayList<>(store.values())) {
            if (entry.documentId().equals(documentId)) {
                store.remove(entry.id());
                count++;
            }
        }
        return count;
    }

    @Override
    public int deleteByKnowledgeBase(String knowledgeBaseId) {
        int count = 0;
        for (ChunkEntry entry : new ArrayList<>(store.values())) {
            if (entry.knowledgeBaseId().equals(knowledgeBaseId)) {
                store.remove(entry.id());
                count++;
            }
        }
        return count;
    }

    @Override
    public int countByKnowledgeBase(String knowledgeBaseId) {
        int count = 0;
        for (ChunkEntry entry : store.values()) {
            if (entry.knowledgeBaseId().equals(knowledgeBaseId)) {
                count++;
            }
        }
        return count;
    }

    /** 清空存储（测试辅助方法） */
    public void clear() {
        store.clear();
        idSeq.set(0);
    }

    /** 总条数（测试辅助方法） */
    public int size() {
        return store.size();
    }
}
