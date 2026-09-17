package com.njydsz.agent.domain.rag;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * RAG 全链路调试信息（domain 层值对象）
 *
 * <p>记录一次混合检索请求的各阶段耗时与结果数量，用于可观测性面板展示。 数据结构遵循 Snail AI 的"全链路调试面板"设计模式。
 *
 * <p>各阶段：
 *
 * <ul>
 *   <li>向量检索（Embedding + 向量相似度）
 *   <li>全文检索（BM25 / PostgreSQL ts_rank）
 *   <li>融合排序（RRF 算法合并两路结果）
 *   <li>精排（Reranker 模型二次重排序）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagDebugInfo {

  /** 截断后的查询文本（避免日志过长） */
  private String query = "";

  /** 请求的 topK 值 */
  private int topK = 5;

  /** 向量检索最小相似度阈值 */
  private double minScore = 0.7;

  /** 向量检索耗时（毫秒） */
  private long vectorLatencyMs = 0;

  /** 向量检索结果数量 */
  private int vectorResultCount = 0;

  /** 全文检索耗时（毫秒） */
  private long fullTextLatencyMs = 0;

  /** 全文检索结果数量 */
  private int fullTextResultCount = 0;

  /** 全文检索是否可用（表是否存在、数据库是否可达） */
  private boolean isFullTextAvailable = false;

  /** RRF 融合耗时（毫秒） */
  private long fuseLatencyMs = 0;

  /** RRF 融合后结果数量 */
  private int mergedResultCount = 0;

  /** 精排耗时（毫秒） */
  private long rerankLatencyMs = 0;

  /** 精排后结果数量 */
  private int rerankedResultCount = 0;

  /** 当前使用的 Reranker 类型（identity / http-reranker） */
  private String rerankerType = "identity";

  /** 全链路总耗时（毫秒） */
  private long totalLatencyMs = 0;
}
