package com.njydsz.agent.domain.config.properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RerankerProperties {
  private static final int DEFAULT_TIMEOUT_MILLIS = 5000;

  private boolean isEnabled = false;
  private String baseUrl = "";
  private String apiKey = "";
  private String model = "bge-reranker-v2-m3";
  private int timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
}
