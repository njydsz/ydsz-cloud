package com.njydsz.common.socket.resilience;

import java.time.Duration;
import java.util.function.Supplier;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket 模块轻量级熔断器（基于 Resilience4j）。
 *
 * <p>底层委托 Resilience4j CircuitBreaker，提供滑动窗口失败率统计、状态自动流转、半开探测等标准熔断能力。
 *
 * <p>状态流转：
 *
 * <ul>
 *   <li>CLOSED → 失败率超过阈值 → OPEN
 *   <li>OPEN → 等待半开时间 → HALF_OPEN
 *   <li>HALF_OPEN → 探测成功 → CLOSED
 *   <li>HALF_OPEN → 探测失败 → OPEN
 * </ul>
 *
 * <h3>26.09.01 变更</h3>
 *
 * <p>自 26.09.01 起，委托 Resilience4j CircuitBreaker，移除自研 AbstractCircuitBreaker 继承体系，
 * 复用经过生产验证的 Resilience4j 滑动窗口、状态机、指标等核心能力。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class WebSocketCircuitBreaker {

  /**
   * 熔断状态枚举。
   *
   * <ul>
   *   <li>{@link #CLOSED}：正常放行请求
   *   <li>{@link #OPEN}：熔断打开，直接拒绝请求
   *   <li>{@link #HALF_OPEN}：半开探测，放行少量试探请求
   * </ul>
   */
  public enum State {
    /** 正常放行 */
    CLOSED,
    /** 熔断打开 */
    OPEN,
    /** 半开探测 */
    HALF_OPEN
  }

  private final String name;
  private final io.github.resilience4j.circuitbreaker.CircuitBreaker delegate;

  /**
   * 构造 WebSocket 熔断器。
   *
   * @param name 熔断器名称
   * @param failureRateThreshold 失败率阈值（0~1.0）
   * @param slidingWindowSize 滑动窗口大小（调用次数）
   * @param halfOpenAfterMillis OPEN 状态等待时间（毫秒）
   */
  public WebSocketCircuitBreaker(
      String name, double failureRateThreshold, int slidingWindowSize, long halfOpenAfterMillis) {
    this.name = name;
    CircuitBreakerConfig config =
        CircuitBreakerConfig.custom()
            .failureRateThreshold((float) (failureRateThreshold * 100))
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(slidingWindowSize)
            .waitDurationInOpenState(Duration.ofMillis(halfOpenAfterMillis))
            .minimumNumberOfCalls(slidingWindowSize)
            .permittedNumberOfCallsInHalfOpenState(1)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build();
    this.delegate = CircuitBreakerRegistry.of(config).circuitBreaker(name);

    log.info(
        "[WS-CircuitBreaker] '{}' 初始化: threshold={}, window={}, halfOpenAfter={}ms",
        name,
        failureRateThreshold,
        slidingWindowSize,
        halfOpenAfterMillis);
  }

  /**
   * 执行受保护的操作，失败或熔断时走降级。
   *
   * @param operation 受保护操作
   * @param fallback 降级操作
   * @param <T> 操作结果类型
   * @return 操作结果或降级结果
   */
  public <T> T execute(Supplier<T> operation, Supplier<T> fallback) {
    try {
      return io.github.resilience4j.circuitbreaker.CircuitBreaker
          .decorateSupplier(delegate, operation)
          .get();
    } catch (CallNotPermittedException e) {
      log.debug("[WS-CircuitBreaker] '{}' 熔断中, 执行降级", name);
      return fallback.get();
    } catch (Exception e) {
      log.debug("[WS-CircuitBreaker] '{}' 操作失败, 执行降级: {}", name, e.getMessage());
      return fallback.get();
    }
  }

  /**
   * 获取当前熔断状态。
   *
   * @return 当前状态快照
   */
  public State getState() {
    return switch (delegate.getState()) {
      case OPEN -> State.OPEN;
      case HALF_OPEN -> State.HALF_OPEN;
      default -> State.CLOSED;
    };
  }

  /**
   * 获取熔断器名称。
   *
   * @return 名称
   */
  public String getName() {
    return name;
  }
}
