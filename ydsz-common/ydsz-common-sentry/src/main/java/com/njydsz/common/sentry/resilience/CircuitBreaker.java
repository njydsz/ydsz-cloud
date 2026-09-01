package com.njydsz.common.sentry.resilience;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;

/**
 * 熔断降级保护器（基于平台自研弹性引擎）。
 *
 * <p>底层委托自研引擎 {@link com.njydsz.common.safe.resilience.CircuitBreaker}，
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
 * <p>历史说明：1.0.0 曾底层替换为 Resilience4j；因内网项目不允许引入第三方弹性库竞品，
 * 现回归平台自研引擎（决策见 docs/ADR-0004-resilience-self-hosted.md），对外 API 保持兼容。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class CircuitBreaker {

  /**
   * 熔断状态枚举（与引擎状态一一对应，FORCED_OPEN 归并为 OPEN）。
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
  private final com.njydsz.common.safe.resilience.CircuitBreaker delegate;

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
    this.delegate =
        new com.njydsz.common.safe.resilience.CircuitBreaker(
            name,
            com.njydsz.common.safe.resilience.CircuitBreakerConfig.custom()
                .failureRateThreshold((float) (failureRateThreshold * 100))
                .slidingWindowType(
                    com.njydsz.common.safe.resilience.CircuitBreakerConfig.SlidingWindowType
                        .TIME_BASED)
                .slidingWindowSize(slidingWindowSizeSeconds)
                .waitDurationInOpenState(java.time.Duration.ofMillis(halfOpenAfterMillis))
                .minimumNumberOfCalls(10)
                .permittedNumberOfCallsInHalfOpenState(1)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build());

    log.info(
        "[Sentry] CircuitBreaker '{}' 初始化完成: threshold={}, window={}s, halfOpenAfter={}ms",
        name,
        failureRateThreshold,
        slidingWindowSizeSeconds,
        halfOpenAfterMillis);
  }

  /**
   * 使用自定义引擎注册中心创建（供 Spring 容器管理的共享 Registry 场景）。
   *
   * @param name 熔断器名称
   * @param config 自研引擎配置
   * @param registry 共享注册表
   */
  public CircuitBreaker(
      String name,
      com.njydsz.common.safe.resilience.CircuitBreakerConfig config,
      com.njydsz.common.safe.resilience.CircuitBreakerRegistry registry) {
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
    return delegate.execute(operation, fallback);
  }

  /**
   * 执行无返回值操作。
   *
   * @param operation 业务操作
   * @param fallback 降级操作
   */
  public void execute(Runnable operation, Runnable fallback) {
    delegate.execute(operation, fallback);
  }

  /**
   * 判断当前是否允许执行操作。
   *
   * @return {@code true} 允许执行；{@code false} 应走降级
   */
  public boolean canExecute() {
    com.njydsz.common.safe.resilience.CircuitBreaker.State state = delegate.getState();
    return state != com.njydsz.common.safe.resilience.CircuitBreaker.State.OPEN
        && state != com.njydsz.common.safe.resilience.CircuitBreaker.State.FORCED_OPEN;
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
      case OPEN, FORCED_OPEN -> State.OPEN;
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
   * 获取底层自研引擎熔断器实例（用于高级场景：事件订阅、指标导出）。
   *
   * @return 自研引擎 CircuitBreaker 实例
   */
  public com.njydsz.common.safe.resilience.CircuitBreaker getDelegate() {
    return delegate;
  }
}
