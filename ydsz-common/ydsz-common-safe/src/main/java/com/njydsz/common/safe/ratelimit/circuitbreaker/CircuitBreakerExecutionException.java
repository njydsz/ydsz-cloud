package com.njydsz.common.safe.ratelimit.circuitbreaker;

/**
 * 熔断器执行异常
 *
 * <p>在熔断保护回调（{@link CircuitBreakerCallback}）执行期间，将底层受检异常包装为
 * 运行时异常以适配 {@link java.util.function.Supplier} 契约时抛出。
 *
 * <p>区别于调用方业务异常：本异常由熔断器框架内部产生，用于统计失败率并驱动
 * CLOSED → OPEN 状态迁移，调用方通常无需区分，交由 {@link CircuitBreaker#tryAcquire}
 * 统一转换为 {@code RateLimitDecision.BLOCKED}。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class CircuitBreakerExecutionException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * 构造熔断器执行异常。
   *
   * @param cause 底层执行异常（非 {@code null}）
   */
  public CircuitBreakerExecutionException(Throwable cause) {
    super(cause);
  }
}
