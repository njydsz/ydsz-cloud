package com.njydsz.message.domain.enums.core;

/**
 * 通知级别枚举。
 *
 * <p>对应 SQL {@code ydsz_msg_notification.level} 的 CHECK 约束取值，用于区分通知的严重程度。
 *
 * <ul>
 *   <li>{@link #INFO} — 提示信息（默认）
 *   <li>{@link #WARN} — 警告，需要关注
 *   <li>{@link #ERROR} — 错误，需要处理
 *   <li>{@link #URGENT} — 紧急，需立即处理
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public enum NotificationLevelEnum {

  /** 提示 */
  INFO,
  /** 警告 */
  WARN,
  /** 错误 */
  ERROR,
  /** 紧急 */
  URGENT;

  /**
   * 从字符串安全解析通知级别，无效时返回 INFO。
   *
   * @param value 级别字符串
   * @return 枚举值
   */
  public static NotificationLevelEnum fromString(String value) {
    if (value == null || value.isBlank()) {
      return INFO;
    }
    try {
      return NotificationLevelEnum.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return INFO;
    }
  }
}
