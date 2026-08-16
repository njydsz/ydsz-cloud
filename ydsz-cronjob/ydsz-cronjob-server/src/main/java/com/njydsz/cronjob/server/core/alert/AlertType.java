package com.njydsz.cronjob.server.core.alert;

/**
 * 告警类型枚举（P5 告警 + 监控）。
 *
 * <p>定义告警触发条件，对应 {@code ydsz_job_alert_rule.alert_type} 字段。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum AlertType {

  /** 任务执行失败即告警（每次失败都触发，受冷却窗口去重）。 */
  FAIL,

  /** 任务执行超时即告警（status=TIMEOUT 时触发）。 */
  TIMEOUT,

  /** 任务执行慢（单次耗时 &gt;= threshold 毫秒）。 */
  SLOW,

  /** 时间窗口内失败率 &gt;= threshold（百分比 0-100）。 */
  FAIL_RATE,

  /** 时间窗口内 P95 耗时 &gt;= threshold（毫秒）。 */
  DURATION_P95,

  /**
   * P2-F2: SLA 预警（任务执行耗时达到 SLA 承诺值的 80%，尚未超时）。
   *
   * <p>软预警：通知运维关注即将超 SLA 的任务，不中断执行。 由 {@link
   * com.njydsz.cronjob.server.core.dispatch.TimeoutMonitor} 周期性扫描触发。
   */
  SLA_WARNING;

  /**
   * 解析告警类型字符串，大小写不敏感。
   *
   * @param value 告警类型字符串
   * @return 解析后的枚举值；null 或无法识别时返回 null
   */
  public static AlertType parse(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return AlertType.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /**
   * 判断该告警类型是否需要阈值。
   *
   * @return FAIL / TIMEOUT / SLA_WARNING 无需阈值；SLOW / FAIL_RATE / DURATION_P95 需要阈值
   */
  public boolean requiresThreshold() {
    return this != FAIL && this != TIMEOUT && this != SLA_WARNING;
  }

  /**
   * 判断该告警类型是否需要时间窗口。
   *
   * @return FAIL_RATE / DURATION_P95 需要时间窗口；其他不需要
   */
  public boolean requiresTimeWindow() {
    return this == FAIL_RATE || this == DURATION_P95;
  }
}
