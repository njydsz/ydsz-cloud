package com.njydsz.message.domain.enums.core;

/**
 * 通知分类枚举。
 *
 * <p>对应 SQL {@code ydsz_msg_notification.category} 的 CHECK 约束取值，用于区分通知的业务类型。
 *
 * <ul>
 *   <li>{@link #SYSTEM} — 系统通知
 *   <li>{@link #WORKFLOW} — 流程通知
 *   <li>{@link #ALERT} — 告警通知
 *   <li>{@link #TO_DO} — 待办通知
 *   <li>{@link #ANNOUNCE} — 公告通知
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public enum NotificationCategoryEnum {

  /** 系统通知 */
  SYSTEM,
  /** 流程通知 */
  WORKFLOW,
  /** 告警通知 */
  ALERT,
  /** 待办通知 */
  TO_DO,
  /** 公告通知 */
  ANNOUNCE;

  /**
   * 从字符串安全解析通知分类，无效时返回 SYSTEM。
   *
   * @param value 分类字符串
   * @return 枚举值
   */
  public static NotificationCategoryEnum fromString(String value) {
    if (value == null || value.isBlank()) {
      return SYSTEM;
    }
    try {
      return NotificationCategoryEnum.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return SYSTEM;
    }
  }
}
