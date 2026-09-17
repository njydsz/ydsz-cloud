package com.njydsz.agent.domain.config.properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolProperties {
  private static final int DEFAULT_TIMEOUT_MS = 30000;
  private static final int DEFAULT_TIMEOUT_SECONDS = 30;
  private static final int DEFAULT_MAX_DEPTH = 5;
  private static final int DEFAULT_EVICTION_MAX_RESULT_CHARS = 2_000;
  private static final int DEFAULT_EVICTION_MAX_TOTAL_CHARS = 10_000;

  private boolean isEnabled = true;
  private int timeoutMs = DEFAULT_TIMEOUT_MS;
  private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
  private int maxDepth = DEFAULT_MAX_DEPTH;
  private boolean isParallelEnabled = false;
  private boolean isFailFast = true;
  private boolean isEvictionEnabled = false;
  private int evictionMaxResultChars = DEFAULT_EVICTION_MAX_RESULT_CHARS;
  private int evictionMaxTotalChars = DEFAULT_EVICTION_MAX_TOTAL_CHARS;
  private int evictionMaxResultTokens = -1;
}
