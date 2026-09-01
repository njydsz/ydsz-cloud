package com.njydsz.common.safe.resilience;

/**
 * 熔断开启时拒绝调用的异常（自研引擎）。
 *
 * <p>当熔断器处于 OPEN / FORCED_OPEN 状态，或 HALF_OPEN 探测许可耗尽时，
 * {@link CircuitBreaker#acquirePermission()} 将抛出本异常。
 *
 * <p>调用方可捕获本异常执行降级逻辑（如返回兜底响应、快速失败 503 等）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class CallNotPermittedException extends RuntimeException {

  private final transient CircuitBreaker circuitBreaker;

  /**
   * 构造异常。
   *
   * @param circuitBreaker 拒绝调用的熔断器
   */
  CallNotPermittedException(CircuitBreaker circuitBreaker) {
    super(
        "CircuitBreaker '"
            + circuitBreaker.getName()
            + "' is OPEN and does not permit further calls");
    this.circuitBreaker = circuitBreaker;
  }

  /**
   * 获取拒绝调用的熔断器。
   *
   * @return 熔断器实例
   */
  public CircuitBreaker getCircuitBreaker() {
    return circuitBreaker;
  }
}
