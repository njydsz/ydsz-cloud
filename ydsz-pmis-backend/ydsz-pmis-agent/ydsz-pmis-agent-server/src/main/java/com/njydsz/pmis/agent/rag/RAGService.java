package com.njydsz.pmis.agent.server.rag;

import com.njydsz.pmis.agent.web.config.RAGProperties;
import com.njydsz.pmis.agent.server.engine.embedding.EmbeddingProvider;
import com.njydsz.pmis.agent.server.engine.memory.TokenCounter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RAG 文档入库服务（P3-1 落地，P1-2 线程安全修复）。
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
 * <p><b>P1-2 修复</b>：原 {@code batchRetrieve} 方法直接修改共享单例 {@link RAGProperties#setTopK}，
 * 多线程并发调用时会产生竞态条件，导致检索结果不可预测。
 * 现改为创建 RAGProperties 的副本（深拷贝），在副本上设置 topK，不影响共享实例。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-1), 1.3.1 (P1-2)
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
     * <p><b>P1-2 修复</b>：不再修改共享的 {@code properties} 单例 Bean，
     * 而是创建副本传入 {@link Retriever}，消除竞态条件。
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

        // P1-2: 创建 RAGProperties 副本，避免修改共享单例
        RAGProperties queryProps = copyProperties(properties);
        if (topKPerQuery > 0) {
            queryProps.setTopK(topKPerQuery);
        }

        List<RetrievedChunk> all = new ArrayList<>();
        Retriever retriever = new Retriever(embeddingProvider, vectorStore, queryProps);
        for (String query : queries) {
            all.addAll(retriever.retrieve(knowledgeBaseId, query));
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

    /**
     * 创建 RAGProperties 的副本（P1-2 线程安全修复）。
     *
     * <p>由于 {@link RAGProperties} 使用 {@code @Data} 注解（Lombok 生成 getter/setter），
     * 此处逐字段复制到新实例，确保原始单例不被修改。
     *
     * @param original 原始配置
     * @return 配置副本
     */
    private static RAGProperties copyProperties(RAGProperties original) {
        RAGProperties copy = new RAGProperties();
        copy.setEnabled(original.isEnabled());
        copy.setEmbeddingProvider(original.getEmbeddingProvider());
        copy.setVectorStore(original.getVectorStore());
        copy.setChunkSize(original.getChunkSize());
        copy.setChunkOverlap(original.getChunkOverlap());
        copy.setTopK(original.getTopK());
        copy.setMinScore(original.getMinScore());
        copy.setMaxContextTokens(original.getMaxContextTokens());
        return copy;
    }
}
