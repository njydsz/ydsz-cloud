package com.njydsz.common.safe.ratelimit.properties;

import lombok.Data;

/**
 * 熔断器配置属性
 *
 * <p>用于配置 Redis 集群限流调用的熔断保护参数。
 *
 * <p><b>配置示例：</b>
 *
 * <pre>{@code
 * ydsz:
 *   ratelimit:
 *     circuit-breaker:
 *       enabled: true
 *       failure-rate-threshold: 50
 *       minimum-number-of-calls: 5
 *       wait-duration-seconds: 10
 *       permitted-half-open-calls: 3
 *       sliding-window-size: 10
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class CircuitBreakerProperties {

  /**
   * 是否启用熔断器
   *
   * <p>默认 true。设为 false 可禁用熔断保护。
   */
  private boolean enabled = true;

  /**
   * 失败率阈值（百分比）
   *
   * <p>当失败率达到此百分比时触发熔断。 默认 50（即 50%）。
   */
  private double failureRateThreshold = 50.0;

  /**
   * 最小调用数
   *
   * <p>在计算失败率前需要的最小调用次数，避免少量调用导致误触发。 默认 5。
   */
  private int minimumNumberOfCalls = 5;

  /**
   * OPEN 状态等待时间（秒）
   *
   * <p>熔断器开启后等待多少秒进入 HALF_OPEN 状态。 默认 10 秒。
   */
  private long waitDurationSeconds = 10;

  /**
   * HALF_OPEN 状态允许的探测数
   *
   * <p>半开状态下允许通过的请求数，全部成功则关闭熔断器。 默认 3。
   */
  private int permittedHalfOpenCalls = 3;

  /**
   * 滑动窗口大小
   *
   * <p>统计失败率的窗口大小（调用次数）。 默认 10。
   */
  private int slidingWindowSize = 10;
}
