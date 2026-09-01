package com.njydsz.common.safe.resilience;

/**
 * 熔断器事件（状态变更 / 成功 / 失败）。
 *
 * <p>统一事件载体，通过 {@link CircuitBreakerEvents} 订阅。调用方可依据 {@link #getType()}
 * 或非空访问器（{@link #getStateTransition()} / {@link #getThrowable()}）区分事件类别。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class CircuitBreakerEvent {

  /** 状态变更事件 */
  public static final int TYPE_STATE_TRANSITION = 0;

  /** 成功调用事件 */
  public static final int TYPE_SUCCESS = 1;

  /** 失败调用事件 */
  public static final int TYPE_ERROR = 2;

  private final String circuitBreakerName;
  private final int type;
  private final long timestamp;
  private final StateTransition stateTransition;
  private final Throwable throwable;
  private final long durationMs;

  private CircuitBreakerEvent(
      String circuitBreakerName,
      int type,
      long timestamp,
      StateTransition stateTransition,
      Throwable throwable,
      long durationMs) {
    this.circuitBreakerName = circuitBreakerName;
    this.type = type;
    this.timestamp = timestamp;
    this.stateTransition = stateTransition;
    this.throwable = throwable;
    this.durationMs = durationMs;
  }

  /**
   * 创建状态变更事件。
   *
   * @param circuitBreakerName 熔断器名称
   * @param from 转换前状态
   * @param to 转换后状态
   * @return 事件实例
   */
  static CircuitBreakerEvent stateTransition(
      String circuitBreakerName, CircuitBreaker.State from, CircuitBreaker.State to) {
    return new CircuitBreakerEvent(
        circuitBreakerName,
        TYPE_STATE_TRANSITION,
        System.currentTimeMillis(),
        new StateTransition(from, to),
        null,
        0L);
  }

  /**
   * 创建成功调用事件。
   *
   * @param circuitBreakerName 熔断器名称
   * @param durationMs 调用耗时（毫秒）
   * @return 事件实例
   */
  static CircuitBreakerEvent success(String circuitBreakerName, long durationMs) {
    return new CircuitBreakerEvent(
        circuitBreakerName, TYPE_SUCCESS, System.currentTimeMillis(), null, null, durationMs);
  }

  /**
   * 创建失败调用事件。
   *
   * @param circuitBreakerName 熔断器名称
   * @param durationMs 调用耗时（毫秒）
   * @param throwable 触发失败的异常
   * @return 事件实例
   */
  static CircuitBreakerEvent error(String circuitBreakerName, long durationMs, Throwable throwable) {
    return new CircuitBreakerEvent(
        circuitBreakerName,
        TYPE_ERROR,
        System.currentTimeMillis(),
        null,
        throwable,
        durationMs);
  }

  /**
   * 获取熔断器名称。
   *
   * @return 熔断器名称
   */
  public String getCircuitBreakerName() {
    return circuitBreakerName;
  }

  /**
   * 获取事件类型（{@link #TYPE_STATE_TRANSITION} / {@link #TYPE_SUCCESS} / {@link #TYPE_ERROR}）。
   *
   * @return 事件类型
   */
  public int getType() {
    return type;
  }

  /**
   * 获取事件创建时间戳（毫秒）。
   *
   * @return 时间戳
   */
  public long getTimestamp() {
    return timestamp;
  }

  /**
   * 获取状态转换信息（仅状态变更事件非空）。
   *
   * @return 状态转换；非状态变更事件返回 null
   */
  public StateTransition getStateTransition() {
    return stateTransition;
  }

  /**
   * 获取触发失败的异常（仅失败事件非空）。
   *
   * @return 异常；非失败事件返回 null
   */
  public Throwable getThrowable() {
    return throwable;
  }

  /**
   * 获取调用耗时（毫秒，仅成功/失败事件有意义）。
   *
   * @return 耗时
   */
  public long getDurationMs() {
    return durationMs;
  }

  /** 状态转换（from → to）。 */
  public static final class StateTransition {

    private final CircuitBreaker.State fromState;
    private final CircuitBreaker.State toState;

    private StateTransition(CircuitBreaker.State fromState, CircuitBreaker.State toState) {
      this.fromState = fromState;
      this.toState = toState;
    }

    /**
     * 获取转换前状态。
     *
     * @return 转换前状态
     */
    public CircuitBreaker.State getFromState() {
      return fromState;
    }

    /**
     * 获取转换后状态。
     *
     * @return 转换后状态
     */
    public CircuitBreaker.State getToState() {
      return toState;
    }

    @Override
    public String toString() {
      return fromState + " -> " + toState;
    }
  }
}
