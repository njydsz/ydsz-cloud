package com.njydsz.agent.domain.config.properties;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CacheProperties {
  private static final int DEFAULT_TTL_MINUTES = 60;
  private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.95;

  private boolean isEnabled = false;
  private int ttlMinutes = DEFAULT_TTL_MINUTES;
  private int maxSize = 1000;
  private double similarityThreshold = DEFAULT_SIMILARITY_THRESHOLD;
  private String type = "caffeine";
  private int l1MaxSize = 200;
  private int l1ExpireMinutes = 5;
}
