package com.njydsz.common.safe.resilience;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * 熔断器配置（不可变，自研引擎）。
 *
 * <p>通过 {@link #custom()} 构建，参数语义对齐业界熔断器通行约定：
 *
 * <ul>
 *   <li>{@code failureRateThreshold}：失败率阈值（百分比，如 50 表示 50%）
 *   <li>{@code slowCallRateThreshold}：慢调用率阈值（百分比）
 *   <li>{@code slidingWindowType}：滑动窗口类型（按次数 / 按时间）
 *   <li>{@code slidingWindowSize}：COUNT_BASED 表示调用次数；TIME_BASED 表示秒数
 *   <li>{@code minimumNumberOfCalls}：窗口内达到该调用数后才参与失败率判定
 *   <li>{@code permittedNumberOfCallsInHalfOpenState}：HALF_OPEN 允许的探测调用数
 *   <li>{@code automaticTransitionFromOpenToHalfOpenEnabled}：OPEN 到期后是否允许
 *       读操作（getState/canExecute）惰性触发 OPEN → HALF_OPEN 转换
 * </ul>
 *
 * <p><b>线程安全：</b>配置对象不可变，可跨线程共享。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class CircuitBreakerConfig {

  /** 默认失败率阈值（百分比） */
  public static final float DEFAULT_FAILURE_RATE_THRESHOLD = 50.0f;

  /** 默认慢调用率阈值（百分比） */
  public static final float DEFAULT_SLOW_CALL_RATE_THRESHOLD = 100.0f;

  /** 默认慢调用时长阈值 */
  public static final Duration DEFAULT_SLOW_CALL_DURATION_THRESHOLD = Duration.ofSeconds(60);

  /** 默认 OPEN 状态持续时间 */
  public static final Duration DEFAULT_WAIT_DURATION_IN_OPEN_STATE = Duration.ofSeconds(60);

  /** 默认滑动窗口大小 */
  public static final int DEFAULT_SLIDING_WINDOW_SIZE = 100;

  /** 默认最小调用数 */
  public static final int DEFAULT_MINIMUM_NUMBER_OF_CALLS = 100;

  /** 默认半开探测调用数 */
  public static final int DEFAULT_PERMITTED_NUMBER_OF_CALLS_IN_HALF_OPEN_STATE = 10;

  private final float failureRateThreshold;
  private final float slowCallRateThreshold;
  private final Duration slowCallDurationThreshold;
  private final Duration waitDurationInOpenState;
  private final SlidingWindowType slidingWindowType;
  private final int slidingWindowSize;
  private final int minimumNumberOfCalls;
  private final int permittedNumberOfCallsInHalfOpenState;
  private final boolean automaticTransitionFromOpenToHalfOpenEnabled;
  private final Predicate<Throwable> recordExceptionPredicate;

  private CircuitBreakerConfig(Builder builder) {
    this.failureRateThreshold = builder.failureRateThreshold;
    this.slowCallRateThreshold = builder.slowCallRateThreshold;
    this.slowCallDurationThreshold = builder.slowCallDurationThreshold;
    this.waitDurationInOpenState = builder.waitDurationInOpenState;
    this.slidingWindowType = builder.slidingWindowType;
    this.slidingWindowSize = builder.slidingWindowSize;
    this.minimumNumberOfCalls = builder.minimumNumberOfCalls;
    this.permittedNumberOfCallsInHalfOpenState = builder.permittedNumberOfCallsInHalfOpenState;
    this.automaticTransitionFromOpenToHalfOpenEnabled =
        builder.automaticTransitionFromOpenToHalfOpenEnabled;
    this.recordExceptionPredicate = builder.recordExceptionPredicate;
  }

  /**
   * 创建默认配置。
   *
   * <p>失败率阈值 50%、慢调用率阈值 100%、OPEN 等待 60s、COUNT_BASED 窗口 100 次、 最小调用数 100、半开探测 10 次。
   *
   * @return 默认配置实例
   */
  public static CircuitBreakerConfig ofDefaults() {
    return custom().build();
  }

  /**
   * 创建配置构建器。
   *
   * @return 构建器
   */
  public static Builder custom() {
    return new Builder();
  }

  /**
   * 获取失败率阈值（百分比）。
   *
   * @return 失败率阈值
   */
  public float getFailureRateThreshold() {
    return failureRateThreshold;
  }

  /**
   * 获取慢调用率阈值（百分比）。
   *
   * @return 慢调用率阈值
   */
  public float getSlowCallRateThreshold() {
    return slowCallRateThreshold;
  }

  /**
   * 获取慢调用时长阈值。
   *
   * @return 慢调用时长阈值
   */
  public Duration getSlowCallDurationThreshold() {
    return slowCallDurationThreshold;
  }

  /**
   * 获取 OPEN 状态持续时间。
   *
   * @return OPEN 状态持续时间
   */
  public Duration getWaitDurationInOpenState() {
    return waitDurationInOpenState;
  }

  /**
   * 获取滑动窗口类型。
   *
   * @return 滑动窗口类型
   */
  public SlidingWindowType getSlidingWindowType() {
    return slidingWindowType;
  }

  /**
   * 获取滑动窗口大小（COUNT_BASED 为次数，TIME_BASED 为秒数）。
   *
   * @return 滑动窗口大小
   */
  public int getSlidingWindowSize() {
    return slidingWindowSize;
  }

  /**
   * 获取最小调用数（窗口内达到该值后才参与判定）。
   *
   * @return 最小调用数
   */
  public int getMinimumNumberOfCalls() {
    return minimumNumberOfCalls;
  }

  /**
   * 获取半开探测调用数。
   *
   * @return 半开探测调用数
   */
  public int getPermittedNumberOfCallsInHalfOpenState() {
    return permittedNumberOfCallsInHalfOpenState;
  }

  /**
   * 是否启用读操作惰性触发 OPEN → HALF_OPEN（自动半开）。
   *
   * @return true=启用
   */
  public boolean isAutomaticTransitionFromOpenToHalfOpenEnabled() {
    return automaticTransitionFromOpenToHalfOpenEnabled;
  }

  /**
   * 获取失败判定谓词（返回 true 的异常计为失败）。
   *
   * @return 失败判定谓词
   */
  public Predicate<Throwable> getRecordExceptionPredicate() {
    return recordExceptionPredicate;
  }

  /** 滑动窗口类型。 */
  public enum SlidingWindowType {
    /** 基于调用次数的滑动窗口 */
    COUNT_BASED,
    /** 基于时间的滑动窗口（按秒分桶） */
    TIME_BASED
  }

  /** 配置构建器。 */
  public static final class Builder {

    private float failureRateThreshold = DEFAULT_FAILURE_RATE_THRESHOLD;
    private float slowCallRateThreshold = DEFAULT_SLOW_CALL_RATE_THRESHOLD;
    private Duration slowCallDurationThreshold = DEFAULT_SLOW_CALL_DURATION_THRESHOLD;
    private Duration waitDurationInOpenState = DEFAULT_WAIT_DURATION_IN_OPEN_STATE;
    private SlidingWindowType slidingWindowType = SlidingWindowType.COUNT_BASED;
    private int slidingWindowSize = DEFAULT_SLIDING_WINDOW_SIZE;
    private int minimumNumberOfCalls = DEFAULT_MINIMUM_NUMBER_OF_CALLS;
    private int permittedNumberOfCallsInHalfOpenState =
        DEFAULT_PERMITTED_NUMBER_OF_CALLS_IN_HALF_OPEN_STATE;
    private boolean automaticTransitionFromOpenToHalfOpenEnabled = false;
    private Predicate<Throwable> recordExceptionPredicate = throwable -> true;

    private Builder() {}

    /**
     * 设置失败率阈值（百分比）。
     *
     * @param failureRateThreshold 失败率阈值（0-100）
     * @return 构建器
     */
    public Builder failureRateThreshold(float failureRateThreshold) {
      this.failureRateThreshold = failureRateThreshold;
      return this;
    }

    /**
     * 设置慢调用率阈值（百分比）。
     *
     * @param slowCallRateThreshold 慢调用率阈值（0-100）
     * @return 构建器
     */
    public Builder slowCallRateThreshold(float slowCallRateThreshold) {
      this.slowCallRateThreshold = slowCallRateThreshold;
      return this;
    }

    /**
     * 设置慢调用时长阈值。
     *
     * @param slowCallDurationThreshold 慢调用时长阈值
     * @return 构建器
     */
    public Builder slowCallDurationThreshold(Duration slowCallDurationThreshold) {
      this.slowCallDurationThreshold = Objects.requireNonNull(slowCallDurationThreshold);
      return this;
    }

    /**
     * 设置 OPEN 状态持续时间。
     *
     * @param waitDurationInOpenState OPEN 状态持续时间
     * @return 构建器
     */
    public Builder waitDurationInOpenState(Duration waitDurationInOpenState) {
      this.waitDurationInOpenState = Objects.requireNonNull(waitDurationInOpenState);
      return this;
    }

    /**
     * 设置滑动窗口类型。
     *
     * @param slidingWindowType 滑动窗口类型
     * @return 构建器
     */
    public Builder slidingWindowType(SlidingWindowType slidingWindowType) {
      this.slidingWindowType = Objects.requireNonNull(slidingWindowType);
      return this;
    }

    /**
     * 设置滑动窗口大小。
     *
     * @param slidingWindowSize 滑动窗口大小（COUNT_BASED 为次数，TIME_BASED 为秒数，至少 1）
     * @return 构建器
     */
    public Builder slidingWindowSize(int slidingWindowSize) {
      this.slidingWindowSize = slidingWindowSize;
      return this;
    }

    /**
     * 设置最小调用数。
     *
     * @param minimumNumberOfCalls 最小调用数（至少 1）
     * @return 构建器
     */
    public Builder minimumNumberOfCalls(int minimumNumberOfCalls) {
      this.minimumNumberOfCalls = minimumNumberOfCalls;
      return this;
    }

    /**
     * 设置半开探测调用数。
     *
     * @param permittedNumberOfCallsInHalfOpenState 半开探测调用数（至少 1）
     * @return 构建器
     */
    public Builder permittedNumberOfCallsInHalfOpenState(
        int permittedNumberOfCallsInHalfOpenState) {
      this.permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState;
      return this;
    }

    /**
     * 设置是否启用读操作惰性触发自动半开。
     *
     * @param automaticTransitionFromOpenToHalfOpenEnabled true=启用
     * @return 构建器
     */
    public Builder automaticTransitionFromOpenToHalfOpenEnabled(
        boolean automaticTransitionFromOpenToHalfOpenEnabled) {
      this.automaticTransitionFromOpenToHalfOpenEnabled =
          automaticTransitionFromOpenToHalfOpenEnabled;
      return this;
    }

    /**
     * 设置失败判定谓词。
     *
     * @param recordExceptionPredicate 谓词（返回 true 的异常计为失败）
     * @return 构建器
     */
    public Builder recordException(Predicate<Throwable> recordExceptionPredicate) {
      this.recordExceptionPredicate = Objects.requireNonNull(recordExceptionPredicate);
      return this;
    }

    /**
     * 构建配置（含参数校验）。
     *
     * @return 不可变配置实例
     * @throws IllegalArgumentException 参数非法时抛出
     */
    public CircuitBreakerConfig build() {
      if (failureRateThreshold <= 0 || failureRateThreshold > 100) {
        throw new IllegalArgumentException("failureRateThreshold 必须在 (0, 100] 区间");
      }
      if (slowCallRateThreshold <= 0 || slowCallRateThreshold > 100) {
        throw new IllegalArgumentException("slowCallRateThreshold 必须在 (0, 100] 区间");
      }
      if (slidingWindowSize < 1) {
        throw new IllegalArgumentException("slidingWindowSize 至少为 1");
      }
      if (minimumNumberOfCalls < 1) {
        throw new IllegalArgumentException("minimumNumberOfCalls 至少为 1");
      }
      if (permittedNumberOfCallsInHalfOpenState < 1) {
        throw new IllegalArgumentException("permittedNumberOfCallsInHalfOpenState 至少为 1");
      }
      if (waitDurationInOpenState.isNegative() || waitDurationInOpenState.isZero()) {
        throw new IllegalArgumentException("waitDurationInOpenState 必须为正");
      }
      return new CircuitBreakerConfig(this);
    }
  }
}
