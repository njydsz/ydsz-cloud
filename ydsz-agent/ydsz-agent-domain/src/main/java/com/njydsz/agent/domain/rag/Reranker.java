package com.njydsz.agent.domain.rag;

import java.util.List;

/**
 * Reranker 重排序接口
 *
 * <p>对召回阶段的候选文档进行精排，弥补单向量相似度在语义匹配精度上的不足。 实现可选择：
 *
 * <ul>
 *   <li>Cross-Encoder 模型重打分（如 bge-reranker-v2-m3）
 *   <li>LLM 相关性评分
 *   <li>规则融合评分（时间衰减 + 来源权重）
 * </ul>
 *
 * <p><b>线程安全</b>：实现通常为无状态或远程服务客户端，可安全并发调用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface Reranker {

  /**
   * 对候选文本块进行重排序
   *
   * <p>输入为召回阶段截断前的候选列表（通常 topK * multiplier）， 输出为按相关性降序排列的精排结果（最多 topK 条）。
   *
   * @param query 原始用户查询
   * @param chunks 候选文本块
   * @param topK 返回条数上限
   * @return 精排后的文本块列表（按相关性降序）
   */
  List<TextChunk> rerank(String query, List<TextChunk> chunks, int topK);

  /**
   * 获取 Reranker 类型标识
   *
   * @return 如 "bge-reranker"、"llm-scorer"、"identity"
   */
  String getType();
}
