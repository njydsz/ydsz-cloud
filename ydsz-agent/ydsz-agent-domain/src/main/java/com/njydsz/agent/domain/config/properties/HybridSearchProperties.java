package com.njydsz.agent.domain.config.properties;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HybridSearchProperties {
  private static final double DEFAULT_WEB_RESULT_RATIO = 0.3;

  private boolean isWebEnabled = false;
  private double webResultRatio = DEFAULT_WEB_RESULT_RATIO;
}
