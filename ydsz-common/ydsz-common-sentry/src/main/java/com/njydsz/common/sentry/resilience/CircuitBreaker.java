package com.njydsz.common.sentry.resilience;

import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 熔断降级保护器（基于 Resilience4j）。
 *
 * <p>底层委托 Resilience4j {@link io.github.resilience4j.circuitbreaker.CircuitBreaker}，
 * 提供滑动窗口失败率统计、状态自动流转、半开探测等标准熔断能力。 与自实现版本（v1.x）相比：
 *
 * <ul>
 *   <li>经过 10+ 年生产验证，无 CAS 竞态 / 桶取模临界点问题
 *   <li>原生支持 Micrometer 指标导出，无需手动绑定 Gauge
 *   <li>支持事件总线（状态变更 / 错误 / 成功事件）
 * </ul>
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
 * @since 1.0.0
 */
@Slf4j
public class CircuitBreaker {

  /**
   * 熔断状态枚举（与 Resilience4j CircuitBreaker.State 一一对应）。
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
  private final CircuitBreaker delegate;

  public CircuitBreaker(
      String name,
      double failureRateThreshold,
      int slidingWindowSizeSeconds,
      long halfOpenAfterMillis) {
    this.name = name;

    CircuitBreakerConfig config =
        CircuitBreakerConfig.custom()
            .failureRateThreshold((float) failureRateThreshold)
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
            .slidingWindowSize(slidingWindowSizeSeconds)
            .waitDurationInOpenState(java.time.Duration.ofMillis(halfOpenAfterMillis))
            .minimumNumberOfCalls(10)
            .permittedNumberOfCallsInHalfOpenState(1)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .recordException((Predicate<Throwable>) e -> true)
            .build();

    CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
    this.delegate = registry.circuitBreaker(name);

    log.info(
        "[Sentry] CircuitBreaker '{}' 初始化完成: threshold={}, window={}s, halfOpenAfter={}ms",
        name,
        failureRateThreshold,
        slidingWindowSizeSeconds,
        halfOpenAfterMillis);
  }

  /**
   * 使用自定义 Registry 创建（供 Spring 容器管理的共享 Registry 场景）。
   *
   * @param name 熔断器名称
   * @param config Resilience4j 配置
   * @param registry 共享注册表
   */
  public CircuitBreaker(String name, CircuitBreakerConfig config, CircuitBreakerRegistry registry) {
    this.name = name;
    this.delegate = registry.circuitBreaker(name, config);
    log.info("[Sentry] CircuitBreaker '{}' 初始化完成（共享 Registry）", name);
  }

  /**
   * 执行受保护的操作。
   *
   * @param operation 业务操作
   * @param fallback 降级操作
   * @param <T> 操作结果类型
   * @return 操作结果（业务成功时返回业务结果，失败或熔断时返回降级结果）
   */
  public <T> T execute(Supplier<T> operation, Supplier<T> fallback) {
    if (!canExecute()) {
      log.debug("[Sentry] CircuitBreaker '{}' 熔断中, 执行降级", name);
      return fallback.get();
    }
    try {
      T result = operation.get();
      onSuccess();
      return result;
    } catch (Exception e) {
      onFailure();
      log.debug("[Sentry] CircuitBreaker '{}' 操作失败, 执行降级: {}", name, e.getMessage());
      return fallback.get();
    }
  }

  /**
   * 执行无返回值操作。
   *
   * @param operation 业务操作
   * @param fallback 降级操作
   */
  public void execute(Runnable operation, Runnable fallback) {
    if (!canExecute()) {
      fallback.run();
      return;
    }
    try {
      operation.run();
      onSuccess();
    } catch (Exception e) {
      onFailure();
      fallback.run();
    }
  }

  /**
   * 判断当前是否允许执行操作。
   *
   * @return {@code true} 允许执行；{@code false} 应走降级
   */
  public boolean canExecute() {
    return delegate.getState() != CircuitBreaker.State.OPEN
        && delegate.getState()
            != CircuitBreaker.State.FORCED_OPEN;
  }

  /** 记录一次成功调用。 */
  private void onSuccess() {
    delegate.onSuccess(0, TimeUnit.MILLISECONDS);
  }

  /** 记录一次失败调用。 */
  private void onFailure() {
    delegate.onError(
        0, TimeUnit.MILLISECONDS, new RuntimeException("CircuitBreaker recorded failure"));
  }

  /**
   * 记录一次成功调用（带耗时）。
   *
   * <p>供业务模块在外部受控场景下精细记录耗时。
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
   * <p>供业务模块在外部受控场景下精细记录耗时与异常。
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
    switch (delegate.getState()) {
      case CLOSED:
        return State.CLOSED;
      case OPEN:
      case FORCED_OPEN:
        return State.OPEN;
      case HALF_OPEN:
        return State.HALF_OPEN;
      default:
        return State.CLOSED;
    }
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
   * 获取底层 Resilience4j CircuitBreaker 实例（用于高级场景：事件订阅、指标导出）。
   *
   * @return Resilience4j CircuitBreaker 实例
   */
  public CircuitBreaker getDelegate() {
    return delegate;
  }
}
