package com.njydsz.agent.domain.config.properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
