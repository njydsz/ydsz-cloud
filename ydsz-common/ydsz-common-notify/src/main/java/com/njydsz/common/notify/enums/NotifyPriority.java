package com.njydsz.common.notify.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通知消息优先级枚举
 *
 * <p>用于消息优先级路由，确保高优先级消息（如 P0 告警）优先发送， 不被低优先级消息（如营销通知）阻塞。
 *
 * <p><b>优先级说明：</b>
 *
 * <ul>
 *   <li>{@link #P0_CRITICAL} — 立即发送，跳过队列
 *   <li>{@link #P1_HIGH} — 高优先级队列，优先消费
 *   <li>{@link #P2_NORMAL} — 普通队列（默认）
 *   <li>{@link #P3_LOW} — 低优先级队列，闲时发送
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
@AllArgsConstructor
public enum NotifyPriority {

  /** 紧急：立即发送，跳过队列和限流 */
  P0_CRITICAL(0, "紧急"),

  /** 高优先级：优先消费，可跳过聚合 */
  P1_HIGH(1, "高"),

  /** 普通：默认优先级 */
  P2_NORMAL(2, "普通"),

  /** 低优先级：闲时发送，可被聚合 */
  P3_LOW(3, "低");

  private final int level;

  private final String name;
}
