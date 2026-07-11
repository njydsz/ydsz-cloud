package com.njydsz.pmis.agent.server.engine.embedding;

/**
 * Embedding 向量化抽象接口（P3-1 落地）。
 *
 * <p>对标 Coze Embedding / Dify EmbeddingEndpoint，将文本转为向量表示，
 * 供 RAG 向量检索使用。与 {@link com.njydsz.pmis.agent.server.engine.llm.LlmProvider}
 * 职责正交：LlmProvider 负责 chat（生成式），EmbeddingProvider 负责向量化（判别式）。
 *
 * <p>实现策略：
 * <ol>
 *   <li>{@link MockEmbeddingProvider} - 确定性哈希向量（开发/测试用）</li>
 *   <li>DashScopeEmbeddingProvider - 阿里云灵积 text-embedding-v2（生产用，后续扩展）</li>
 * </ol>
 *
 * <p>切换方式：Nacos 配置 {@code pmis.agent.rag.embedding-provider=mock|dashscope}
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-1)
 */
public interface EmbeddingProvider {

    /**
     * Provider 名称。
     *
     * @return Provider 标识（如 "mock"、"dashscope"）
     */
    String name();

    /**
     * 向量维度。
     *
     * <p>不同模型维度不同：
     * <ul>
     *   <li>mock: 8 维（测试用，节省存储）</li>
     *   <li>DashScope text-embedding-v2: 1536 维</li>
     *   <li>OpenAI text-embedding-3-small: 1536 维</li>
     * </ul>
     *
     * @return 向量维度
     */
    int dimension();

    /**
     * 将文本转向量。
     *
     * @param text 输入文本（非空）
     * @return 归一化后的向量（长度 = {@link #dimension()}）
     */
    float[] embed(String text);

    /**
     * 将向量转为 pgvector 字符串格式。
     *
     * <p>pgvector 的 SQL 表示为 {@code "[1.0,2.0,3.0]"} 字符串。
     *
     * @param vector 向量
     * @return pgvector 字符串
     */
    default String toPgVectorString(float[] vector) {
        if (vector == null || vector.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
