package com.njydsz.common.safe.ratelimit.circuitbreaker;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.safe.ratelimit.enums.RateLimitResult;
import com.njydsz.common.safe.ratelimit.model.RateLimitDecision;
import com.njydsz.common.safe.resilience.CallNotPermittedException;

/**
 * 熔断器（基于平台自研弹性引擎 {@link com.njydsz.common.safe.resilience.CircuitBreaker}）
 *
 * <p><b>三态机：</b>
 *
 * <ul>
 *   <li>CLOSED（关闭）：正常调用，统计失败率
 *   <li>OPEN（开启）：直接拒绝，不调用下游
 *   <li>HALF_OPEN（半开）：放行少量探测请求，成功则关闭，失败则继续开启
 * </ul>
 *
 * <p><b>触发条件（可配置）：</b>
 *
 * <ul>
 *   <li>失败率阈值（failureRateThreshold，默认 0.5）
 *   <li>慢调用率阈值（slowCallRateThreshold，默认 1.0）
 *   <li>最小调用数（minimumNumberOfCalls，默认 10）
 *   <li>滑动窗口大小（slidingWindowSize，默认 100）
 *   <li>开启后等待时间（waitDurationInOpenState，默认 10s）
 * </ul>
 *
 * <p>每个资源标识对应一个独立的底层熔断器实例，由自研引擎提供滑动窗口统计、
 * 状态自动流转、半开探测与事件总线能力（内网项目不引入第三方弹性库）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class CircuitBreaker {

  /** 资源 → 底层熔断器实例 */
  private final Map<String, com.njydsz.common.safe.resilience.CircuitBreaker> breakers =
      new ConcurrentHashMap<>();

  private final CircuitBreakerConfig config;

  public CircuitBreaker(CircuitBreakerConfig config) {
    this.config = config;
  }

  public CircuitBreaker() {
    this(CircuitBreakerConfig.defaults());
  }

  /**
   * 尝试执行（同步）。
   *
   * @param resource 资源标识
   * @param callback 受保护的调用回调
   * @param <T> 调用结果类型
   * @return 限流决策（含执行结果或拒绝原因）
   */
  public <T> RateLimitDecision tryAcquire(String resource, CircuitBreakerCallback<T> callback) {
    com.njydsz.common.safe.resilience.CircuitBreaker cb = getOrCreate(resource);
    try {
      T result =
          cb.executeSupplier(
              () -> {
                try {
                  return callback.call();
                } catch (RuntimeException e) {
                  throw e;
                } catch (Throwable e) {
                  // 受检异常/错误包装为业务运行时异常以适配 Supplier 契约（云顶规范 11 章：禁止裸抛 RuntimeException/Exception）
                  throw new CircuitBreakerExecutionException(e);
                }
              });
      return RateLimitDecision.builder()
          .resource(resource)
          .result(RateLimitResult.PASS)
          .remaining(1)
          .threshold(1)
          .timestamp(Instant.now())
          .reason("circuit breaker pass")
          .build();
    } catch (CallNotPermittedException ex) {
      return blockedDecision(resource, "circuit breaker open: " + ex.getMessage());
    } catch (Exception ex) {
      return blockedDecision(resource, "circuit breaker failure: " + ex.getMessage());
    }
  }

  private RateLimitDecision blockedDecision(String resource, String reason) {
    return RateLimitDecision.builder()
        .resource(resource)
        .result(RateLimitResult.BLOCKED)
        .remaining(0)
        .threshold(1)
        .timestamp(Instant.now())
        .reason(reason)
        .build();
  }

  /**
   * 强制开启
   *
   * @param resource 资源标识
   */
  public void forceOpen(String resource) {
    getOrCreate(resource).transitionToForcedOpenState();
  }

  /**
   * 强制关闭
   *
   * @param resource 资源标识
   */
  public void forceClose(String resource) {
    getOrCreate(resource).transitionToClosedState();
  }

  /**
   * 获取当前状态
   *
   * @param resource 资源标识
   * @return 熔断器状态（未创建时返回 CLOSED）
   */
  public State getState(String resource) {
    com.njydsz.common.safe.resilience.CircuitBreaker cb = breakers.get(resource);
    if (cb == null) {
      return State.CLOSED;
    }
    return toLocalState(cb.getState());
  }

  /** 获取或创建指定资源的熔断器实例。 */
  private com.njydsz.common.safe.resilience.CircuitBreaker getOrCreate(String resource) {
    return breakers.computeIfAbsent(resource, key -> newEngineBreaker(config, key));
  }

  /** 由本类配置构建底层引擎熔断器（名称 = 前缀-资源名，便于日志定位）。 */
  private static com.njydsz.common.safe.resilience.CircuitBreaker newEngineBreaker(
      CircuitBreakerConfig config, String resource) {
    String prefix = config.getName() == null ? "ratelimit" : config.getName();
    return com.njydsz.common.safe.resilience.CircuitBreaker.of(
        prefix + "-" + resource, config.toEngineConfig());
  }

  /** 引擎状态 → 本地三态映射（FORCED_OPEN 视为 OPEN）。 */
  private static State toLocalState(
      com.njydsz.common.safe.resilience.CircuitBreaker.State engineState) {
    return switch (engineState) {
      case CLOSED -> State.CLOSED;
      case OPEN, FORCED_OPEN -> State.OPEN;
      case HALF_OPEN -> State.HALF_OPEN;
    };
  }

  /**
   * 熔断器三态。
   *
   * <p>CLOSED 正常调用并统计失败率；OPEN 直接拒绝；HALF_OPEN 放行少量探测请求， 成功则回退 CLOSED，失败则重回 OPEN。
   */
  public enum State {
    /** 关闭态：正常调用，统计失败率。 */
    CLOSED,
    /** 开启态：直接拒绝调用，等待配置时长后进入半开。 */
    OPEN,
    /** 半开态：放行少量探测请求，用于试探下游是否已恢复。 */
    HALF_OPEN
  }

  /**
   * 熔断器保护的调用回调。
   *
   * <p>由调用方实现，承载实际的下游调用逻辑；回调抛出异常将被熔断器计为一次失败， 并据此触发熔断状态流转。
   *
   * @param <T> 调用返回类型
   */
  @FunctionalInterface
  public interface CircuitBreakerCallback<T> {
    /**
     * 执行受熔断保护的实际调用。
     *
     * @return 调用结果
     * @throws Throwable 调用过程抛出的任意异常，都会被熔断器记录为一次失败
     */
    T call() throws Throwable;
  }

  /** 熔断器配置（阈值语义为 0-1 比例，映射到引擎百分比配置）。 */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CircuitBreakerConfig {
    /** 失败率阈值（0-1） */
    @Builder.Default private double failureRateThreshold = 0.5;

    /** 慢调用率阈值（0-1） */
    @Builder.Default private double slowCallRateThreshold = 1.0;

    /** 慢调用阈值（毫秒） */
    @Builder.Default private long slowCallDurationThresholdMillis = 1000;

    /** 最小调用数 */
    @Builder.Default private int minimumNumberOfCalls = 10;

    /** 滑动窗口大小 */
    @Builder.Default private int slidingWindowSize = 100;

    /** OPEN 状态等待时间 */
    @Builder.Default private Duration waitDurationInOpenState = Duration.ofSeconds(10);

    /** HALF_OPEN 状态允许的探测数 */
    @Builder.Default private int permittedNumberOfCallsInHalfOpenState = 10;

    /** 滑动窗口类型 */
    @Builder.Default private SlidingWindowType slidingWindowType = SlidingWindowType.COUNT_BASED;

    /** 熔断器名称（用于日志与事件） */
    @Builder.Default private String name = "ratelimit";

    /**
     * 创建采用默认参数的熔断器配置。
     *
     * <p>默认策略：失败率阈值 50%、慢调用率阈值 100%、最小调用数 10、滑动窗口 100（COUNT_BASED）、 OPEN 等待 10s、半开探测数 10。等价于无参构造
     * {@link CircuitBreaker#CircuitBreaker()} 所引用配置。
     *
     * @return 默认配置实例
     */
    public static CircuitBreakerConfig defaults() {
      return new CircuitBreakerConfig();
    }

    /** 转换为自研引擎配置（阈值 0-1 → 百分比）。 */
    com.njydsz.common.safe.resilience.CircuitBreakerConfig toEngineConfig() {
      return com.njydsz.common.safe.resilience.CircuitBreakerConfig.custom()
          .failureRateThreshold((float) (this.failureRateThreshold * 100))
          .slowCallRateThreshold((float) (this.slowCallRateThreshold * 100))
          .slowCallDurationThreshold(Duration.ofMillis(this.slowCallDurationThresholdMillis))
          .minimumNumberOfCalls(this.minimumNumberOfCalls)
          .waitDurationInOpenState(this.waitDurationInOpenState)
          .permittedNumberOfCallsInHalfOpenState(this.permittedNumberOfCallsInHalfOpenState)
          .slidingWindowSize(this.slidingWindowSize)
          .slidingWindowType(
              SlidingWindowType.COUNT_BASED.equals(this.slidingWindowType)
                  ? com.njydsz.common.safe.resilience.CircuitBreakerConfig.SlidingWindowType
                      .COUNT_BASED
                  : com.njydsz.common.safe.resilience.CircuitBreakerConfig.SlidingWindowType
                      .TIME_BASED)
          .build();
    }
  }

  /**
   * 滑动窗口统计类型。
   *
   * <p>决定失败率/慢调用率统计窗口的划分方式： TIME_BASED 按时间窗口统计，COUNT_BASED 按调用次数窗口统计。
   */
  public enum SlidingWindowType {
    /** 基于时间的滑动窗口。 */
    TIME_BASED,
    /** 基于调用次数的滑动窗口。 */
    COUNT_BASED
  }
}
