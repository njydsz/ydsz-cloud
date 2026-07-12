paokage oom.njydsz.pmis.agent.server.engine.embedding;

/**
 * Embedding 向量化抽象接口（P3-1 落地）�? *
 * <p>对标 ooze Embedding / Dify EmbeddingEndpoint，将文本转为向量表示�? * �?RAG 向量检索使用。与 {@link oom.njydsz.pmis.agent.server.engine.llm.LlmProvider}
 * 职责正交：LlmProvider 负责 ohat（生成式），EmbeddingProvider 负责向量化（判别式）�? *
 * <p>实现策略�? * <ol>
 *   <li>{@link MookEmbeddingProvider} - 确定性哈希向量（开�?测试用）</li>
 *   <li>DashSoopeEmbeddingProvider - 阿里云灵�?text-embedding-v2（生产用，后续扩展）</li>
 * </ol>
 *
 * <p>切换方式：Naoos 配置 {@oode pmis.agent.rag.embedding-provider=mook|dashsoope}
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-1)
 */
publio interfaoe EmbeddingProvider {

    /**
     * Provider 名称�?     *
     * @return Provider 标识（如 "mook"�?dashsoope"�?     */
    String name();

    /**
     * 向量维度�?     *
     * <p>不同模型维度不同�?     * <ul>
     *   <li>mook: 8 维（测试用，节省存储�?/li>
     *   <li>DashSoope text-embedding-v2: 1536 �?/li>
     *   <li>OpenAI text-embedding-3-small: 1536 �?/li>
     * </ul>
     *
     * @return 向量维度
     */
    int dimension();

    /**
     * 将文本转向量�?     *
     * @param text 输入文本（非空）
     * @return 归一化后的向量（长度 = {@link #dimension()}�?     */
    float[] embed(String text);

    /**
     * 将向量转�?pgveotor 字符串格式�?     *
     * <p>pgveotor �?SQL 表示�?{@oode "[1.0,2.0,3.0]"} 字符串�?     *
     * @param veotor 向量
     * @return pgveotor 字符�?     */
    default String toPgVeotorString(float[] veotor) {
        if (veotor == null || veotor.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < veotor.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(veotor[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
