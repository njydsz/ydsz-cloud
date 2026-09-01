package com.njydsz.common.feign.circuitbreaker;

/**
 * Feign 熔断器策略接口。
 *
 * <p>封装平台自研熔断器与 Feign 调用的集成点，提供请求许可判断和结果反馈。 实现类由 {@link CircuitBreakerFeignConfiguration}
 * 注册（需启用 {@code ydsz.feign.circuit-breaker.enabled=true}）。
 *
 * <p>当未注册实现时，{@code FeignResponseInterceptor} 跳过熔断逻辑（降级为无保护模式）。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see CircuitBreakerFeignConfiguration
 */
public interface FeignCircuitBreakerStrategy {

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
     * @return 失败率（0-100）
     */
    float getFailureRate();

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
   * 判断指定服务的熔断器是否允许当前请求通过。
   *
   * @param serviceName Feign 服务名称（来自 @FeignClient name）
   * @return true=允许通过；false=熔断器开启，应快速失败
   */
  boolean allowRequest(String serviceName);

  /**
   * 记录一次成功的调用。
   *
   * @param serviceName 服务名称
   * @param durationMs 调用耗时（毫秒）
   */
  void recordSuccess(String serviceName, long durationMs);

  /**
   * 记录一次失败的调用。
   *
   * @param serviceName 服务名称
   * @param durationMs 调用耗时（毫秒）
   * @param throwable 异常对象
   */
  void recordFailure(String serviceName, long durationMs, Throwable throwable);

  /**
   * 获取指定服务的熔断器状态。
   *
   * @param serviceName 服务名称
   * @return 熔断器状态
   */
  CircuitBreakerState getState(String serviceName);

  /**
   * 获取指定服务的熔断器指标。
   *
   * @param serviceName 服务名称
   * @return 熔断器指标
   */
  CircuitBreakerMetrics getMetrics(String serviceName);
}
