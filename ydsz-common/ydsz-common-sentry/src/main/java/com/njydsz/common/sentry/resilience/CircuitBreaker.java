package com.njydsz.common.sentry.resilience;

import java.util.concurrent.TimeUnit;
import java.time.Duration;
import java.util.function.Supplier;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 熔断降级保护器（基于 Resilience4j）。
 *
 * <p>底层委托 Resilience4j {@link io.github.resilience4j.circuitbreaker.CircuitBreaker}，
 * 提供滑动窗口失败率统计、状态自动流转、半开探测等标准熔断能力。
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
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class CircuitBreaker {

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
   * 构造熔断器（按秒时间窗）。
   *
   * @param name 熔断器名称
   * @param failureRateThreshold 失败率阈值（0-1 比例，如 0.3 表示 30%）
   * @param slidingWindowSizeSeconds 滑动窗口大小（秒，TIME_BASED）
   * @param halfOpenAfterMillis OPEN 等待时长（毫秒）
   */
  public CircuitBreaker(
      String name,
      double failureRateThreshold,
      int slidingWindowSizeSeconds,
      long halfOpenAfterMillis) {
    this.name = name;
    CircuitBreakerConfig config =
        CircuitBreakerConfig.custom()
            .failureRateThreshold((float) (failureRateThreshold * 100))
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
            .slidingWindowSize(slidingWindowSizeSeconds)
            .waitDurationInOpenState(Duration.ofMillis(halfOpenAfterMillis))
            .minimumNumberOfCalls(10)
            .permittedNumberOfCallsInHalfOpenState(1)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build();
    this.delegate = io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry.of(config)
        .circuitBreaker(name);

    log.info(
        "[Sentry] CircuitBreaker '{}' 初始化完成: threshold={}, window={}s, halfOpenAfter={}ms",
        name,
        failureRateThreshold,
        slidingWindowSizeSeconds,
        halfOpenAfterMillis);
  }

  /**
   * 使用 Resilience4j 注册中心与配置创建。
   *
   * @param name 熔断器名称
   * @param config Resilience4j 熔断器配置
   * @param registry Resilience4j 注册中心
   */
  public CircuitBreaker(
      String name,
      CircuitBreakerConfig config,
      CircuitBreakerRegistry registry) {
    this.name = name;
    this.delegate = registry.circuitBreaker(name, config);
    log.info("[Sentry] CircuitBreaker '{}' 初始化完成（共享 Registry）", name);
  }

  private CircuitBreaker() {
    this.name = null;
    this.delegate = null;
  }

  /**
   * 使用 Resilience4j 注册中心与配置创建（静态工厂方法）。
   *
   * @param name 熔断器名称
   * @param config Resilience4j 熔断器配置
   * @param registry Resilience4j 注册中心
   * @return CircuitBreaker 实例
   */
  public static CircuitBreaker fromRegistry(
      String name,
      CircuitBreakerConfig config,
      CircuitBreakerRegistry registry) {
    return new CircuitBreaker(name, config, registry);
  }

  /**
   * 执行受保护的操作（熔断中走 fallback）。
   *
   * @param operation 业务操作
   * @param fallback 降级操作
   * @param <T> 操作结果类型
   * @return 操作结果（业务成功时返回业务结果，失败或熔断时返回降级结果）
   */
  public <T> T execute(Supplier<T> operation, Supplier<T> fallback) {
    // Try.ofSupplier 处理 CallNotPermittedException（熔断中）走 fallback
    return io.github.resilience4j.circuitbreaker.CircuitBreaker.decorateSupplier(delegate, operation)
        .get();
  }

  /**
   * 执行无返回值操作（熔断中走 fallback）。
   *
   * @param operation 业务操作
   * @param fallback 降级操作
   */
  public void execute(Runnable operation, Runnable fallback) {
    try {
      io.github.resilience4j.circuitbreaker.CircuitBreaker.decorateRunnable(delegate, operation)
          .run();
    } catch (io.github.resilience4j.circuitbreaker.CallNotPermittedException e) {
      fallback.run();
    }
  }

  /**
   * 判断当前是否允许执行操作。
   *
   * @return {@code true} 允许执行；{@code false} 应走降级
   */
  public boolean canExecute() {
    io.github.resilience4j.circuitbreaker.CircuitBreaker.State state = delegate.getState();
    return state != io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN;
  }

  /**
   * 记录一次成功调用（带耗时）。
   *
   * @param duration 耗时
   * @param unit 耗时单位
   */
  public void recordSuccess(long duration, TimeUnit unit) {
    delegate.onSuccess(duration, unit);
  }

  /**
   * 记录一次失败调用（带耗时和异常）。
   *
   * @param duration 耗时
   * @param unit 耗时单位
   * @param throwable 触发失败的异常（可为 null）
   */
  public void recordFailure(long duration, TimeUnit unit, Throwable throwable) {
    delegate.onError(
        duration,
        unit,
        throwable != null ? throwable : new RuntimeException("CircuitBreaker recorded failure"));
  }

  /**
   * 获取当前熔断状态。
   *
   * @return 当前状态（CLOSED / OPEN / HALF_OPEN）
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
   * @return 熔断器名称
   */
  public String getName() {
    return name;
  }

  /**
   * 获取滑动窗口内的失败次数。
   *
   * @return 当前失败计数
   */
  public int getFailureCount() {
    return delegate.getMetrics().getNumberOfFailedCalls();
  }

  /**
   * 获取滑动窗口内的总请求次数。
   *
   * @return 当前总计数
   */
  public int getTotalCount() {
    return delegate.getMetrics().getNumberOfBufferedCalls();
  }

  /**
   * 获取底层 Resilience4j 熔断器实例（用于高级场景：事件订阅、指标导出）。
   *
   * @return Resilience4j CircuitBreaker 实例
   */
  public io.github.resilience4j.circuitbreaker.CircuitBreaker getDelegate() {
    return delegate;
  }
}
