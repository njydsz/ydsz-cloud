package com.njydsz.cronjob.server.core.scheduler;

/**
 * 调度类型枚举（P0-3）。
 *
 * <p>支持以下四种调度类型：
 *
 * <ul>
 *   <li>{@link #CRON}: Cron 表达式调度（默认，向后兼容）
 *   <li>{@link #FIXED_RATE}: 固定频率调度（每 N 毫秒执行一次，不等上次完成）
 *   <li>{@link #FIXED_DELAY}: 固定延迟调度（上次完成后等 N 毫秒再执行下一次）
 *   <li>{@link #API}: 仅 API 手动触发（不进入任何调度队列）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum ScheduleType {
  /** Cron 表达式调度（默认，向后兼容） */
  CRON,
  /** 固定频率调度（每 N 毫秒执行一次，不等上次完成） */
  FIXED_RATE,
  /** 固定延迟调度（上次完成后等 N 毫秒再执行下一次） */
  FIXED_DELAY,
  /** 仅 API 手动触发（不进入任何调度队列） */
  API;

  /**
   * 解析调度类型字符串。
   *
   * <p>null / 空字符串 / 非法值均回退到 {@link #CRON}（向后兼容）。
   *
   * @param value 调度类型字符串（大小写不敏感）
   * @return 对应的 {@link ScheduleType} 枚举值
   */
  public static ScheduleType parse(String value) {
    if (value == null || value.isBlank()) {
      return CRON;
    }
    try {
      return ScheduleType.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      return CRON;
    }
  }
}
