package com.njydsz.workflow.domain.enums;

import lombok.Getter;

/**
 * 超时处理策略枚举
 *
 * <p>当审批任务超时未处理时，系统根据此枚举执行对应的自动处理策略。
 *
 * <ul>
 *   <li>{@link #AUTO_PASS} — 自动通过（标记超时自动审批）
 *   <li>{@link #TRANSFER_ADMIN} — 转交管理员
 *   <li>{@link #TRANSFER_SUPERIOR} — 转交上级
 *   <li>{@link #REMIND} — 仅催办（发送提醒通知）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
public enum FlowTimeoutStrategy {

  /** 自动通过（标记超时自动审批） */
  AUTO_PASS("AUTO_PASS", "自动通过"),

  /** 转交管理员 */
  TRANSFER_ADMIN("TRANSFER_ADMIN", "转交管理员"),

  /** 转交上级 */
  TRANSFER_SUPERIOR("TRANSFER_SUPERIOR", "转交上级"),

  /** 仅催办（发送提醒） */
  REMIND("REMIND", "仅提醒");

  /** 策略编码 */
  private final String code;

  /** 策略描述 */
  private final String desc;

  FlowTimeoutStrategy(String code, String desc) {
    this.code = code;
    this.desc = desc;
  }

  /**
   * 根据编码解析超时策略。
   *
   * @param code 策略编码，可为 {@code null}
   * @return 匹配的策略；无匹配或入参为 {@code null} 时返回 {@link #REMIND}
   */
  public static FlowTimeoutStrategy of(String code) {
    if (code == null) {
      return REMIND;
    }
    for (FlowTimeoutStrategy s : values()) {
      if (s.code.equals(code)) {
        return s;
      }
    }
    return REMIND;
  }
}
