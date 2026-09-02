package com.njydsz.cronjob.server.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * P1-1: 节点健康检查配置。
 *
 * <p>控制 {@code NodeHealthChecker} 的健康检查行为，包括连续失败阈值、响应时长阈值等。
 *
 * <h3>配置示例</h3>
 *
 * <pre>{@code
 * ydsz:
 *   cronjob:
 *     node:
 *       node-health:
 *         consecutive-failure-threshold: 3    # 连续失败 3 次后隔离
 *         response-time-threshold-ms: 5000    # 响应时长告警阈值 5s
 * }</pre>
 *
 * <h3>P1-12: 配置校验</h3>
 *
 * <p>通过 JSR-380 注解声明约束，启动时自动校验。
 *
 * @author ydsz-team
 * @since 1.0.4
 */
@Data
public class NodeHealthConfig {

  /** 默认连续失败阈值：3 次 */
  private static final int DEFAULT_CONSECUTIVE_FAILURE_THRESHOLD = 3;

  /** 默认响应时长阈值：5000ms */
  private static final long DEFAULT_RESPONSE_TIME_THRESHOLD_MS = 5000L;

  /**
   * 连续失败次数阈值：节点心跳/健康检查连续失败次数超过此值后自动隔离（标记 DRAINING + 加入黑名单）。
   *
   * <p>P1-12: 必须在 1~20 之间。
   */
  @Min(value = 1, message = "连续失败阈值 consecutiveFailureThreshold 不能小于 1")
  @Max(value = 20, message = "连续失败阈值 consecutiveFailureThreshold 不能大于 20")
  private int consecutiveFailureThreshold = DEFAULT_CONSECUTIVE_FAILURE_THRESHOLD;

  /**
   * 响应时长告警阈值（毫秒）：节点加权平均响应时长超过此值时输出告警日志，但不自动隔离。
   *
   * <p>P1-12: 必须在 100ms~60000ms 之间。
   */
  @Min(value = 100, message = "响应时长阈值 responseTimeThresholdMs 不能小于 100ms")
  @Max(value = 60000, message = "响应时长阈值 responseTimeThresholdMs 不能大于 60000ms")
  private long responseTimeThresholdMs = DEFAULT_RESPONSE_TIME_THRESHOLD_MS;
}
