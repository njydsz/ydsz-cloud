package com.njydsz.common.socket.enums;

/**
 * WebSocket 消息优先级枚举（P1-4）。
 *
 * <p>用于消息优先级排序，高优先级消息优先推送和补偿。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public enum MessagePriority {

  /** 紧急：系统告警、故障通知 */
  URGENT(1),

  /** 高：审批通知、任务指派 */
  HIGH(2),

  /** 普通：日常通知、消息提醒 */
  NORMAL(3),

  /** 低：数据刷新、仪表盘更新 */
  LOW(4);

  private final int weight;

  MessagePriority(int weight) {
    this.weight = weight;
  }

  /**
   * 获取优先级权重（值越小优先级越高）。
   *
   * @return 权重值
   */
  public int getWeight() {
    return weight;
  }

  /**
   * 从字符串解析优先级，默认返回 NORMAL。
   *
   * @param value 字符串值
   * @return 优先级枚举
   */
  public static MessagePriority fromString(String value) {
    if (value == null || value.isEmpty()) {
      return NORMAL;
    }
    try {
      return MessagePriority.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      return NORMAL;
    }
  }
}
