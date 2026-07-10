package com.njydsz.pmis.agent.rag;

import com.njydsz.pmis.agent.config.RAGProperties;
import com.njydsz.pmis.agent.engine.embedding.EmbeddingProvider;
import com.njydsz.pmis.agent.engine.memory.TokenCounter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RAG 文档入库服务（P3-1 落地）。
 *
 * <p>封装「文档 → 分块 → 向量化 → 存储」完整入库链路。
 * 对标 LangChain DocumentLoader + TextSplitter + Embeddings / Coze 知识库入库。
 *
 * <p>入库流程：
 * <ol>
 *   <li>通过 {@link DocumentSplitter} 将文档切分为分块</li>
 *   <li>对每个分块调用 {@link EmbeddingProvider#embed} 生成向量</li>
 *   <li>通过 {@link VectorStore#store} 持久化</li>
 *   <li>统计 token 数并返回</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-1)
 */
@Slf4j
public class RAGService {

    private final EmbeddingProvider embeddingProvider;
    private final VectorStore vectorStore;
    private final RAGProperties properties;

    public RAGService(EmbeddingProvider embeddingProvider,
                     VectorStore vectorStore,
                     RAGProperties properties) {
        this.embeddingProvider = embeddingProvider;
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    /**
     * 文档入库：分块 → 向量化 → 存储。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param documentId       文档 ID
     * @param content          文档内容
     * @return 入库的分块数量（0 表示空文档或入库失败）
     */
    public int ingest(String knowledgeBaseId, String documentId, String content) {
        if (content == null || content.isBlank()) {
            log.warn("[RAG] 文档内容为空，跳过入库: kb={} doc={}", knowledgeBaseId, documentId);
            return 0;
        }

        DocumentSplitter splitter = new DocumentSplitter(
                properties.getChunkSize(), properties.getChunkOverlap());
        List<String> chunks = splitter.split(content);
        if (chunks.isEmpty()) {
            log.warn("[RAG] 分块后为空: kb={} doc={}", knowledgeBaseId, documentId);
            return 0;
        }

        int totalTokens = 0;
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            try {
                float[] embedding = embeddingProvider.embed(chunk);
                int tokenCount = TokenCounter.estimate(chunk);
                totalTokens += tokenCount;
                vectorStore.store(knowledgeBaseId, documentId, i, chunk, embedding, tokenCount);
            } catch (Exception e) {
                log.error("[RAG] 分块入库失败: kb={} doc={} chunk={}",
                        knowledgeBaseId, documentId, i, e);
            }
        }

        log.info("[RAG] 文档入库完成: kb={} doc={} chunks={} tokens={}",
                knowledgeBaseId, documentId, chunks.size(), totalTokens);
        return chunks.size();
    }

    /**
     * 批量检索：对多个 query 同时检索，返回合并后的去重结果。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param queries         查询列表
     * @param topKPerQuery    每个 query 的 top-k
     * @return 合并后的检索结果
     */
    public List<RetrievedChunk> batchRetrieve(String knowledgeBaseId,
                                              List<String> queries,
                                              int topKPerQuery) {
        if (queries == null || queries.isEmpty()) {
            return List.of();
        }
        List<RetrievedChunk> all = new ArrayList<>();
        int originalTopK = properties.getTopK();
        properties.setTopK(topKPerQuery);
        try {
            Retriever retriever = new Retriever(embeddingProvider, vectorStore, properties);
            for (String query : queries) {
                all.addAll(retriever.retrieve(knowledgeBaseId, query));
            }
        } finally {
            properties.setTopK(originalTopK);
        }
        // 去重（按 id）
        Map<String, RetrievedChunk> dedup = new LinkedHashMap<>();
        for (RetrievedChunk chunk : all) {
            if (chunk.getId() != null) {
                dedup.merge(chunk.getId(), chunk,
                        (a, b) -> a.getScore() != null && b.getScore() != null
                                && a.getScore() >= b.getScore() ? a : b);
            } else {
                dedup.put(UUID.randomUUID().toString(), chunk);
            }
        }
        return new ArrayList<>(dedup.values());
    }
}
