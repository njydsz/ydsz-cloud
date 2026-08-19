package com.njydsz.agent.domain.model;

import java.io.Serializable;

/**
 * 租户 LLM 配额配置值对象
 *
 * <p>定义单个租户在 LLM 调用层面的用量上限，包含每日 Token 限额和月度预算限额。 配额为 0 表示不限制该维度。
 *
 * <p><b>线程安全</b>：全字段 final 不可变值对象，可安全跨线程共享。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class TenantQuota implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 默认每日 Token 限额（100 万 Token/天） */
  public static final long DEFAULT_DAILY_TOKEN_LIMIT = 1_000_000L;

  /** 默认月度预算（1000 USD/月） */
  public static final double DEFAULT_MONTHLY_BUDGET_USD = 1000.0;

  /** 默认告警阈值（80%） */
  public static final double DEFAULT_ALERT_THRESHOLD = 0.8;

  /** 租户 ID */
  private final String tenantId;

  /** 每日 Token 限额（0 = 不限制） */
  private final long dailyTokenLimit;

  /** 月度预算（USD，0 = 不限制） */
  private final double monthlyBudgetUsd;

  /** 告警阈值（0.0-1.0，默认 0.8 即 80%） */
  private final double alertThreshold;

  public TenantQuota(
      String tenantId, long dailyTokenLimit, double monthlyBudgetUsd, double alertThreshold) {
    this.tenantId = tenantId != null ? tenantId : "default";
    this.dailyTokenLimit = Math.max(dailyTokenLimit, 0);
    this.monthlyBudgetUsd = Math.max(monthlyBudgetUsd, 0);
    this.alertThreshold = alertThreshold > 0 ? alertThreshold : DEFAULT_ALERT_THRESHOLD;
  }

  /** 创建默认配额（使用系统默认值） */
  public static TenantQuota defaultQuota(String tenantId) {
    return new TenantQuota(tenantId, DEFAULT_DAILY_TOKEN_LIMIT, DEFAULT_MONTHLY_BUDGET_USD, DEFAULT_ALERT_THRESHOLD);
  }

  /** 创建无限制配额 */
  public static TenantQuota unlimited(String tenantId) {
    return new TenantQuota(tenantId, 0, 0, DEFAULT_ALERT_THRESHOLD);
  }

  public String getTenantId() {
    return tenantId;
  }

  public long getDailyTokenLimit() {
    return dailyTokenLimit;
  }

  public double getMonthlyBudgetUsd() {
    return monthlyBudgetUsd;
  }

  public double getAlertThreshold() {
    return alertThreshold;
  }

  public boolean isDailyTokenLimited() {
    return dailyTokenLimit > 0;
  }

  public boolean isMonthlyBudgetLimited() {
    return monthlyBudgetUsd > 0;
  }

  @Override
  public String toString() {
    return "TenantQuota{tenantId='"
        + tenantId
        + "', dailyTokenLimit="
        + dailyTokenLimit
        + ", monthlyBudgetUsd="
        + monthlyBudgetUsd
        + '}';
  }
}
