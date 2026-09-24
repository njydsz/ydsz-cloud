package com.njydsz.agent.domain.config.properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 配额与预算限制配置属性。
 *
 * <p>绑定配置前缀 {@code ydzs.agent.quota}，控制 Agent Token 使用量、月度 USD 预算、
 * 以及告警触发阈值。超过配额时拒绝新请求并告警，保障成本可控。
 * 默认启用（isEnabled=true），日配额 100 万 Token，月度预算 100 USD，告警阈值 80%。
 *
 * @author ydsz
 * @since 26.09.24
 */
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
