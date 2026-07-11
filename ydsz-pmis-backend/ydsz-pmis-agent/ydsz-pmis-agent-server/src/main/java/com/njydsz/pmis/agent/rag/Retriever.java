package com.njydsz.pmis.agent.server.rag;

import com.njydsz.pmis.agent.server.config.RAGProperties;
import com.njydsz.pmis.agent.server.engine.embedding.EmbeddingProvider;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 检索器（P3-1 落地）。
 *
 * <p>封装「query → embedding → vector search → filter」完整检索链路。
 * 对标 LangChain VectorStoreRetriever / Dify RetrieverService。
 *
 * <p>检索流程：
 * <ol>
 *   <li>将 query 文本通过 {@link EmbeddingProvider} 转为向量</li>
 *   <li>调用 {@link VectorStore#search} 检索 top-k 分块</li>
 *   <li>按 {@link RAGProperties#getMinScore()} 过滤低相似度结果</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-1)
 */
public class Retriever {

    private final EmbeddingProvider embeddingProvider;
    private final VectorStore vectorStore;
    private final RAGProperties properties;

    public Retriever(EmbeddingProvider embeddingProvider,
                     VectorStore vectorStore,
                     RAGProperties properties) {
        this.embeddingProvider = embeddingProvider;
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    /**
     * 检索与 query 最相关的分块。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param query           查询文本
     * @return 检索结果列表（按相似度降序，已过滤低分）
     */
    public List<RetrievedChunk> retrieve(String knowledgeBaseId, String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        int topK = properties.getTopK();
        float[] queryVector = embeddingProvider.embed(query);
        List<RetrievedChunk> chunks = vectorStore.search(knowledgeBaseId, queryVector, topK);

        // 过滤低相似度结果
        double minScore = properties.getMinScore();
        if (minScore > 0) {
            chunks = chunks.stream()
                    .filter(c -> c.getScore() == null || c.getScore() >= minScore)
                    .collect(Collectors.toList());
        }

        return chunks;
    }

    /**
     * 检索并拼接为上下文文本。
     *
     * <p>将检索到的分块按顺序拼接，供 LLM prompt 使用。
     * 格式：
     * <pre>
     * [1] 内容1
     * [2] 内容2
     * </pre>
     *
     * @param knowledgeBaseId 知识库 ID
     * @param query           查询文本
     * @return 拼接后的上下文文本（可能为空）
     */
    public String retrieveAsContext(String knowledgeBaseId, String query) {
        List<RetrievedChunk> chunks = retrieve(knowledgeBaseId, query);
        if (chunks.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            sb.append("[").append(i + 1).append("] ")
                    .append(chunks.get(i).getContent())
                    .append("\n");
        }
        return sb.toString().trim();
    }
}
