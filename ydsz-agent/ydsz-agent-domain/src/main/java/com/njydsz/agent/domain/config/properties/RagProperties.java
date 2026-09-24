package com.njydsz.agent.domain.config.properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识检索（RAG）配置属性。
 *
 * <p>绑定配置前缀 {@code ydzs.agent.rag}，控制向量检索的核心参数：TopK 召回数量、
 * 最低相似度分数、上下文 Token 预算、向量存储后端、Embedding 模型与维度、
 * 文档分块策略（大小/重叠/策略类型/分隔符）以及租户隔离开关。
 * 默认启用（isEnabled=true），TopK=5，最低分数 0.7，向量存储为 in-memory。
 *
 * @author ydsz
 * @since 26.09.24
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagProperties {
  private static final int DEFAULT_TOP_K = 5;
  private static final double DEFAULT_MIN_SCORE = 0.7;
  private static final int DEFAULT_CONTEXT_TOKEN_BUDGET = 4096;
  private static final int DEFAULT_EMBEDDING_DIMENSION = 1536;
  private static final int DEFAULT_CHUNK_OVERLAP = 200;

  private boolean isEnabled = true;
  private int defaultTopK = DEFAULT_TOP_K;
  private double defaultMinScore = DEFAULT_MIN_SCORE;
  private int contextTokenBudget = DEFAULT_CONTEXT_TOKEN_BUDGET;
  private String vectorStore = "in-memory";
  private String embeddingModel;
  private String embeddingApiKey = "";
  private String embeddingBaseUrl = "";
  private int dimension = DEFAULT_EMBEDDING_DIMENSION;
  private int chunkSize = 1000;
  private int chunkOverlap = DEFAULT_CHUNK_OVERLAP;
  private String chunkStrategy = "simple";
  private String chunkSeparator = "\n#{1,3}\s";
  private boolean isTenantIsolation = false;
}
