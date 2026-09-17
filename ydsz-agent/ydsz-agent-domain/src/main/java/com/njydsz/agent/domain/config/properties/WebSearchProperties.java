package com.njydsz.agent.domain.config.properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebSearchProperties {
  private static final int DEFAULT_TIMEOUT_SECONDS = 10;
  private static final int DEFAULT_TOP_K = 5;

  private boolean isEnabled = false;
  private String provider = "duckduckgo";
  private String endpoint = "";
  private String apiKey = "";
  private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
  private int topK = DEFAULT_TOP_K;
  private boolean isDegradedOnFailure = true;
}
