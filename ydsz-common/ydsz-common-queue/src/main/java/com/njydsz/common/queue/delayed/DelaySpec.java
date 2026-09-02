package com.njydsz.common.queue.delayed;

import java.util.concurrent.TimeUnit;

/**
 * 延时消息参数规范
 *
 * <p>定义延时消息的延迟参数，支持固定延迟和指定投递时间两种模式。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 固定延迟 30 秒
 * DelaySpec.fixed(30, TimeUnit.SECONDS);
 *
 * // 指定投递时间
 * DelaySpec.at(LocalDateTime.now().plusMinutes(5));
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class DelaySpec {

  private final long delay;
  private final TimeUnit timeUnit;
  private final long deliverAtMillis;

  private DelaySpec(long delay, TimeUnit timeUnit, long deliverAtMillis) {
    this.delay = delay;
    this.timeUnit = timeUnit;
    this.deliverAtMillis = deliverAtMillis;
  }

  /**
   * 创建固定延迟的参数规范
   *
   * @param delay 延迟时间数值
   * @param timeUnit 时间单位
   * @return DelaySpec 实例
   */
  public static DelaySpec fixed(long delay, TimeUnit timeUnit) {
    if (delay < 0) {
      throw new IllegalArgumentException("延迟时间不能为负数: " + delay);
    }
    if (timeUnit == null) {
      throw new IllegalArgumentException("时间单位不能为空");
    }
    return new DelaySpec(delay, timeUnit, 0);
  }

  /**
   * 创建指定投递时间的参数规范
   *
   * @param deliverAtMillis 目标投递时间戳（毫秒）
   * @return DelaySpec 实例
   */
  public static DelaySpec at(long deliverAtMillis) {
    if (deliverAtMillis <= 0) {
      throw new IllegalArgumentException("投递时间必须为正数: " + deliverAtMillis);
    }
    return new DelaySpec(0, TimeUnit.MILLISECONDS, deliverAtMillis);
  }

  /**
   * 获取延迟时间（毫秒）
   *
   * <p>如果是固定延迟模式，返回 delay * timeUnit.toMillis(1)； 如果是指定时间模式，返回 deliverAtMillis - 当前时间。
   *
   * @return 延迟时间（毫秒）
   */
  public long toMillis() {
    if (timeUnit != null && delay > 0) {
      return timeUnit.toMillis(delay);
    }
    long remaining = deliverAtMillis - System.currentTimeMillis();
    return Math.max(0, remaining);
  }

  /**
   * 获取原始延迟数值。
   *
   * @return 延迟数值（配合时间单位使用）
   */
  public long getDelay() {
    return delay;
  }

  /**
   * 获取时间单位。
   *
   * @return 时间单位
   */
  public TimeUnit getTimeUnit() {
    return timeUnit;
  }

  /**
   * 获取指定投递时间戳（毫秒）。
   *
   * @return 投递时间戳（毫秒）
   */
  public long getDeliverAtMillis() {
    return deliverAtMillis;
  }

  /**
   * 是否为指定时间投递模式。
   *
   * @return {@code true} 表示按指定时间投递
   */
  public boolean isAtSpecifiedTime() {
    return deliverAtMillis > 0;
  }

  @Override
  public String toString() {
    if (isAtSpecifiedTime()) {
      return "DelaySpec{at=" + deliverAtMillis + "}";
    }
    return "DelaySpec{delay=" + delay + " " + timeUnit + "}";
  }
}
