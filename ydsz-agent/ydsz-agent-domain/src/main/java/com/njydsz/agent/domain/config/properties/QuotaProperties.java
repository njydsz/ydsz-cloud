package com.njydsz.agent.domain.config.properties;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuotaProperties {
  private static final long DEFAULT_DAILY_TOKEN_LIMIT = 1000000L;
  private static final double DEFAULT_ALERT_THRESHOLD = 0.8;

  private boolean isEnabled = true;
  private long dailyTokenLimit = DEFAULT_DAILY_TOKEN_LIMIT;
  private double monthlyBudgetUsd = 100.0;
  private double alertThreshold = DEFAULT_ALERT_THRESHOLD;
}
