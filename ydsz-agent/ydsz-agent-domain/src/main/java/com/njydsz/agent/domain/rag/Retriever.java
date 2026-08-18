package com.njydsz.agent.domain.rag;

import java.util.List;

/**
 * 检索器接口（RAG 检索抽象层）
 *
 * <p>统一抽象向量检索、全文检索、混合检索等检索能力。实现类位于 infra 层，通过 {@code @Component} 注册。
 *
 * <p>设计要点：
 *
 * <ul>
 *   <li>入参为查询文本 + 检索参数，返回 {@link TextChunk} 列表
 *   <li>实现必须是线程安全的
 *   <li>检索失败应抛出 {@link RuntimeException}，不返回 null
 * </ul>
 *
 * @author ydsz-team
 * @since 2.18.0
 */
public interface Retriever {

  /**
   * 检索相关文本块。
   *
   * @param query 查询文本
   * @param topK 返回前 K 条
   * @param minScore 最小相似度阈值
   * @return 检索到的文本块列表（按相关度降序）
   */
  List<TextChunk> retrieve(String query, int topK, double minScore);

  /**
   * 检索相关文本块（使用默认参数）。
   *
   * @param query 查询文本
   * @return 检索到的文本块列表
   */
  default List<TextChunk> retrieve(String query) {
    return retrieve(query, 5, 0.7);
  }
}
