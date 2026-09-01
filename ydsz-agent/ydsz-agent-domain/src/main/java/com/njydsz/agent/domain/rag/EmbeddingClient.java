package com.njydsz.agent.domain.rag;

import java.util.List;

import com.njydsz.agent.domain.gateway.LlmException;

/**
 * Embedding 客户端接口
 *
 * <p>将文本转换为向量嵌入，用于向量相似度检索。
 *
 * <p><b>线程安全</b>：Embedding 客户端一般为单例且被并发调用，实现须线程安全，且 embed/embedBatch 不应缓存可变中间结果。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface EmbeddingClient {

  /**
   * 生成单条文本的嵌入向量
   *
   * @param text 文本
   * @return 嵌入向量（维度取决于模型）
   * @throws LlmException 嵌入调用异常
   */
  List<Float> embed(String text);

  /**
   * 批量生成嵌入向量
   *
   * @param texts 文本列表
   * @return 嵌入向量列表（与输入一一对应）
   * @throws LlmException 嵌入调用异常
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
