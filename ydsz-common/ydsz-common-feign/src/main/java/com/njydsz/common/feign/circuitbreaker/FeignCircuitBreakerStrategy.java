package com.njydsz.common.feign.circuitbreaker;

import java.math.BigDecimal;

/**
 * Feign 熔断器策略接口（完整能力）。
 *
 * <p>继承 {@link FeignCircuitBreakerGuard}（核心热路径），扩展运维查询能力（{@link #getState} / {@link #getMetrics}）。
 * 实现类由 {@link CircuitBreakerFeignConfiguration} 注册（需启用 {@code ydsz.feign.circuit-breaker.enabled=true}）。
 *
 * <p>当未注册实现时，{@code FeignResponseInterceptor} 跳过熔断逻辑（降级为无保护模式）。
 *
 * <p><b>接口分层（自 26.09.19）：</b>
 *
 * <ul>
 *   <li>{@link FeignCircuitBreakerGuard}：核心热路径（allowRequest / recordSuccess / recordFailure）
 *   <li>本接口：运维查询（getState / getMetrics），频率低，可包含 IO / 锁操作
 * </ul>
 *
 * <p>针对热路径编程时，可仅注入 {@link FeignCircuitBreakerGuard} 避免依赖运维查询方法。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see FeignCircuitBreakerGuard
 * @see CircuitBreakerFeignConfiguration
 */
public interface FeignCircuitBreakerStrategy extends FeignCircuitBreakerGuard {

  /** 熔断器状态枚举。 */
  enum CircuitBreakerState {
    /** 关闭状态（正常通行） */
    CLOSED,
    /** 打开状态（快速失败） */
    OPEN,
    /** 半开状态（尝试恢复） */
    HALF_OPEN,
    /** 强制打开状态 */
    FORCED_OPEN
  }

  /** 熔断器指标数据。 */
  interface CircuitBreakerMetrics {
    /**
     * 获取失败率（百分比）。
     *
     * @return 失败率（0-100），使用 BigDecimal 避免浮点精度丢失
     */
    BigDecimal getFailureRate();

    /**
     * 获取总调用次数。
     *
     * @return 总调用次数
     */
    int getTotalCalls();

    /**
     * 获取成功调用次数。
     *
     * @return 成功调用次数
     */
    int getSuccessfulCalls();

    /**
     * 获取失败调用次数。
     *
     * @return 失败调用次数
     */
    int getFailedCalls();

    /**
     * 获取慢调用次数。
     *
     * @return 慢调用次数
     */
    int getSlowCalls();

    /**
     * 获取平均耗时（毫秒）。
     *
     * @return 平均耗时（毫秒）
     */
    long getAverageDuration();
  }

  /**
   * 获取指定服务的熔断器状态。
   *
   * <p>仅在运维查询场景使用（热路径不允许直接调用）。
   *
   * @param serviceName 服务名称
   * @return 熔断器状态
   */
  CircuitBreakerState getState(String serviceName);

  /**
   * 获取指定服务的熔断器指标。
   *
   * <p>仅在运维查询场景使用（热路径不允许直接调用）。
   *
   * @param serviceName 服务名称
   * @return 熔断器指标
   */
  CircuitBreakerMetrics getMetrics(String serviceName);
}
