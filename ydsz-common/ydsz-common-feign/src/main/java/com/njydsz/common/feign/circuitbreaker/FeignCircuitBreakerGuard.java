package com.njydsz.common.feign.circuitbreaker;

/**
 * Feign 熔断器核心调用接口（请求级拦截）。
 *
 * <p>定义每次 Feign 调用时的熔断判断和结果记录方法，属于严格热路径（调用频率极高）。
 * 实现类应确保所有方法均为非阻塞、无锁（或低锁争用）的轻量级操作。
 *
 * <p>设计要点：
 *
 * <ul>
 *   <li>{@link #allowRequest}：基于当前熔断器状态判断是否允许放行（不建议阻塞式等待）
 *   <li>{@link #recordSuccess}/{@link #recordFailure}：记录调用结果供熔断器计算状态转换
 *   <li>不包含 getState / getMetrics 等运维查询，避免热路径与服务端监控查询耦合
 * </ul>
 *
 * <p>当未注册实现时，{@code FeignResponseInterceptor} 跳过熔断逻辑（降级为无保护模式）。
 *
 * @author ydsz-team
 * @since 26.09.19
 * @see FeignCircuitBreakerStrategy
 */
public interface FeignCircuitBreakerGuard {

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
}
