package com.njydsz.agent.domain.rag;

import java.util.List;

/**
 * Embedding 客户端接口
 *
 * <p>将文本转换为向量嵌入，用于向量相似度检索。
 * 实现可选择 OpenAI Embeddings、Cohere、BGE、m3e 等。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
public interface EmbeddingClient {

    /**
     * 生成单条文本的嵌入向量
     *
     * @param text 文本
     * @return 嵌入向量（维度取决于模型）
     * @throws com.njydsz.agent.domain.gateway.LlmException 嵌入调用异常
     */
    List<Float> embed(String text);

    /**
     * 批量生成嵌入向量
     *
     * @param texts 文本列表
     * @return 嵌入向量列表（与输入一一对应）
     * @throws com.njydsz.agent.domain.gateway.LlmException 嵌入调用异常
     */
    List<List<Float>> embedBatch(List<String> texts);

    /**
     * 向量维度
     *
     * @return 维度数（如 1536、1024）
     */
    int getDimension();

    /**
     * 模型标识
     *
     * @return 模型名称（如 "text-embedding-3-small"）
     */
    String getModel();
}
