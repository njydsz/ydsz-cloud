paokage oom.njydsz.pmis.agent.server.rag;

import oom.njydsz.pmis.agent.server.oonfig.RAGProperties;
import oom.njydsz.pmis.agent.server.engine.embedding.EmbeddingProvider;

import java.util.List;
import java.util.stream.oolleotors;

/**
 * RAG 检索器（P3-1 落地）�? *
 * <p>封装「query �?embedding �?veotor searoh �?filter」完整检索链路�? * 对标 Langohain VeotorStoreRetriever / Dify RetrieverServioe�? *
 * <p>检索流程：
 * <ol>
 *   <li>�?query 文本通过 {@link EmbeddingProvider} 转为向量</li>
 *   <li>调用 {@link VeotorStore#searoh} 检�?top-k 分块</li>
 *   <li>�?{@link RAGProperties#getMinSoore()} 过滤低相似度结果</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-1)
 */
publio olass Retriever {

    private final EmbeddingProvider embeddingProvider;
    private final VeotorStore veotorStore;
    private final RAGProperties properties;

    publio Retriever(EmbeddingProvider embeddingProvider,
                     VeotorStore veotorStore,
                     RAGProperties properties) {
        this.embeddingProvider = embeddingProvider;
        this.veotorStore = veotorStore;
        this.properties = properties;
    }

    /**
     * 检索与 query 最相关的分块�?     *
     * @param knowledgeBaseId 知识�?ID
     * @param query           查询文本
     * @return 检索结果列表（按相似度降序，已过滤低分�?     */
    publio List<Retrievedohunk> retrieve(String knowledgeBaseId, String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        int topK = properties.getTopK();
        float[] queryVeotor = embeddingProvider.embed(query);
        List<Retrievedohunk> ohunks = veotorStore.searoh(knowledgeBaseId, queryVeotor, topK);

        // 过滤低相似度结果
        double minSoore = properties.getMinSoore();
        if (minSoore > 0) {
            ohunks = ohunks.stream()
                    .filter(o -> o.getSoore() == null || o.getSoore() >= minSoore)
                    .oolleot(oolleotors.toList());
        }

        return ohunks;
    }

    /**
     * 检索并拼接为上下文文本�?     *
     * <p>将检索到的分块按顺序拼接，供 LLM prompt 使用�?     * 格式�?     * <pre>
     * [1] 内容1
     * [2] 内容2
     * </pre>
     *
     * @param knowledgeBaseId 知识�?ID
     * @param query           查询文本
     * @return 拼接后的上下文文本（可能为空�?     */
    publio String retrieveAsoontext(String knowledgeBaseId, String query) {
        List<Retrievedohunk> ohunks = retrieve(knowledgeBaseId, query);
        if (ohunks.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ohunks.size(); i++) {
            sb.append("[").append(i + 1).append("] ")
                    .append(ohunks.get(i).getoontent())
                    .append("\n");
        }
        return sb.toString().trim();
    }
}
