package com.njydsz.common.safe.resilience;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 自研熔断器（三态 + FORCED_OPEN 状态机，平台唯一标准实现）。
 *
 * <p>状态流转：
 *
 * <ul>
 *   <li>CLOSED → OPEN：滑动窗口内（满足最小调用数）失败率或慢调用率超阈值
 *   <li>OPEN → HALF_OPEN：{@code waitDurationInOpenState} 到期后由许可获取线程惰性触发
 *       （配置 {@code automaticTransitionFromOpenToHalfOpenEnabled} 控制是否由
 *       getState()/canExecute() 等读操作触发）
 *   <li>HALF_OPEN → CLOSED：探测成功（成功数达到半开探测数）
 *   <li>HALF_OPEN → OPEN：任一探测失败
 *   <li>FORCED_OPEN：运维强制熔断，直至 {@link #transitionToClosedState()} 或 {@link #reset()}
 * </ul>
 *
 * <p><b>线程安全：</b>状态由 {@link AtomicReference} + CAS 保护；滑动窗口统计在内部锁内
 * 完成（临界区仅为整数累加）；半开许可由 CAS 计数器控制，仅放行
 * {@code permittedNumberOfCallsInHalfOpenState} 个探测调用。
 *
 * <p><b>典型用法：</b>
 *
 * <pre>{@code
 * CircuitBreaker breaker = registry.circuitBreaker("user-service", config);
 * String result = breaker.executeSupplier(() -> callRemote(), () -> "fallback");
 * }</pre>
 *
 * <p>响应式场景（WebFlux/Gateway）请用 {@link #acquirePermission()} / {@link #onSuccess(long)}
 * / {@link #onError(long, Throwable)} 三段式手动记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public final class CircuitBreaker {

  /**
   * 熔断器状态。
   *
   * <ul>
   *   <li>{@link #CLOSED}：正常放行，统计失败率/慢调用率
   *   <li>{@link #OPEN}：熔断打开，拒绝调用，等待半开
   *   <li>{@link #HALF_OPEN}：放行少量探测调用
   *   <li>{@link #FORCED_OPEN}：强制熔断（运维态），拒绝一切调用
   * </ul>
   */
  public enum State {
    /** 正常放行 */
    CLOSED,
    /** 熔断打开 */
    OPEN,
    /** 半开探测 */
    HALF_OPEN,
    /** 强制熔断（仅人工触发/解除） */
    FORCED_OPEN
  }

  private final String name;
  private final CircuitBreakerConfig config;
  private final CircuitBreakerEvents eventPublisher = new CircuitBreakerEvents();
  private final SlidingWindowMetrics metrics;
  private final AtomicReference<State> stateRef;
  private final ReentrantLock halfOpenLock = new ReentrantLock();
  private final AtomicInteger halfOpenPermits;
  private final AtomicInteger halfOpenSuccess;
  private volatile long openedAtMillis;

  CircuitBreaker(String name, CircuitBreakerConfig config) {
    this.name = name;
    this.config = config;
    this.metrics =
        new SlidingWindowMetrics(config.getSlidingWindowType(), config.getSlidingWindowSize());
    this.stateRef = new AtomicReference<>(State.CLOSED);
    this.halfOpenPermits = new AtomicInteger(0);
    this.halfOpenSuccess = new AtomicInteger(0);
  }

  /**
   * 获取熔断器名称。
   *
   * @return 名称
   */
  public String getName() {
    return name;
  }

  /**
   * 获取熔断器配置（不可变）。
   *
   * @return 配置
   */
  public CircuitBreakerConfig getConfig() {
    return config;
  }

  /**
   * 获取事件发布器（订阅状态变更/成功/失败事件）。
   *
   * @return 事件发布器
   */
  public CircuitBreakerEvents getEventPublisher() {
    return eventPublisher;
  }

  /**
   * 尝试获取调用许可。
   *
   * <p>CLOSED 直接放行；OPEN 到期后由首个调用线程触发 OPEN → HALF_OPEN 并消耗探测许可；
   * HALF_OPEN 仅在许可剩余时放行；FORCED_OPEN 一律拒绝。
   *
   * @return true=允许调用
   */
  public boolean tryAcquirePermission() {
    State current = stateRef.get();
    if (current == State.CLOSED) {
      return true;
    }
    if (current == State.FORCED_OPEN) {
      return false;
    }
    if (current == State.HALF_OPEN) {
      return tryAcquireHalfOpenPermit();
    }
    // OPEN：判断是否到期进入 HALF_OPEN
    if (isWaitDurationElapsed()) {
      if (stateRef.compareAndSet(State.OPEN, State.HALF_OPEN)) {
        transitionTo(State.OPEN, State.HALF_OPEN);
        resetHalfOpenPermits();
      }
      return tryAcquireHalfOpenPermit();
    }
    return false;
  }

  /**
   * 获取调用许可，熔断中抛出 {@link CallNotPermittedException}。
   *
   * <p>响应式场景的标准入口：调用前先 acquirePermission，成功后 defer 执行真实调用，
   * 完成后回调 {@link #onSuccess(long)} / {@link #onError(long, Throwable)}。
   *
   * @throws CallNotPermittedException 熔断中拒绝调用
   */
  public void acquirePermission() {
    if (!tryAcquirePermission()) {
      throw new CallNotPermittedException(this);
    }
  }

  /**
   * 判断当前是否允许调用（不触发状态转换副作用，用于快速检查）。
   *
   * <p>当配置启用自动半开时，读操作也会惰性触发 OPEN → HALF_OPEN 转换（对齐既有
   * Resilience4j 语义）；未启用时仅返回纯检查结果。
   *
   * @return true=允许调用
   */
  public boolean canExecute() {
    if (config.isAutomaticTransitionFromOpenToHalfOpenEnabled()) {
      return tryAcquirePermission();
    }
    State current = stateRef.get();
    if (current == State.CLOSED) {
      return true;
    }
    if (current == State.FORCED_OPEN) {
      return false;
    }
    if (current == State.HALF_OPEN) {
      return halfOpenPermits.get() > 0;
    }
    return isWaitDurationElapsed();
  }

  /**
   * 记录一次成功调用。
   *
   * <p>HALF_OPEN 状态下累计探测成功数，达到 {@code permittedNumberOfCallsInHalfOpenState}
   * 后恢复 CLOSED；CLOSED 状态下写入滑动窗口。
   *
   * @param durationMs 调用耗时（毫秒）
   * @param unit 耗时单位
   */
  public void onSuccess(long durationMs, TimeUnit unit) {
    long duration = unit.toMillis(durationMs);
    boolean slow = isSlowCall(duration);
    State current = stateRef.get();
    if (current == State.HALF_OPEN) {
      halfOpenLock.lock();
      try {
        if (stateRef.get() == State.HALF_OPEN) {
          int success = halfOpenSuccess.incrementAndGet();
          if (success >= config.getPermittedNumberOfCallsInHalfOpenState()
              && stateRef.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
            transitionTo(State.HALF_OPEN, State.CLOSED);
            metrics.reset();
          }
        }
      } finally {
        halfOpenLock.unlock();
      }
      eventPublisher.publishSuccess(name, duration);
      return;
    }
    if (current == State.CLOSED) {
      metrics.record(duration, false, slow);
      evaluateClosedWindow();
      eventPublisher.publishSuccess(name, duration);
    }
  }

  /**
   * 记录一次成功调用（毫秒）。
   *
   * @param durationMs 调用耗时（毫秒）
   */
  public void onSuccess(long durationMs) {
    onSuccess(durationMs, TimeUnit.MILLISECONDS);
  }

  /**
   * 记录一次失败调用。
   *
   * <p>HALF_OPEN 状态下任一失败立即重回 OPEN；CLOSED 状态下按失败判定谓词过滤后写入
   * 滑动窗口（谓词返回 false 的异常不计失败，仍记录成功）。
   *
   * @param durationMs 调用耗时（毫秒）
   * @param unit 耗时单位
   * @param throwable 触发失败的异常
   */
  public void onError(long durationMs, TimeUnit unit, Throwable throwable) {
    long duration = unit.toMillis(durationMs);
    boolean slow = isSlowCall(duration);
    State current = stateRef.get();
    if (current == State.HALF_OPEN) {
      halfOpenLock.lock();
      try {
        if (stateRef.compareAndSet(State.HALF_OPEN, State.OPEN)) {
          transitionTo(State.HALF_OPEN, State.OPEN);
          metrics.reset();
        }
      } finally {
        halfOpenLock.unlock();
      }
      eventPublisher.publishError(name, duration, throwable);
      return;
    }
    if (current == State.CLOSED) {
      if (config.getRecordExceptionPredicate().test(throwable)) {
        metrics.record(duration, true, slow);
        eventPublisher.publishError(name, duration, throwable);
      } else {
        metrics.record(duration, false, slow);
        eventPublisher.publishSuccess(name, duration);
      }
      evaluateClosedWindow();
    }
  }

  /**
   * 记录一次失败调用（毫秒）。
   *
   * @param durationMs 调用耗时（毫秒）
   * @param throwable 触发失败的异常
   */
  public void onError(long durationMs, Throwable throwable) {
    onError(durationMs, TimeUnit.MILLISECONDS, throwable);
  }

  /**
   * 执行受保护的操作（带降级）。
   *
   * @param operation 受保护操作
   * @param fallback 降级操作
   * @param <T> 结果类型
   * @return 操作结果或降级结果
   */
  public <T> T execute(Supplier<T> operation, Supplier<T> fallback) {
    if (!tryAcquirePermission()) {
      log.debug("[CircuitBreaker] '{}' 熔断中, 执行降级", name);
      return fallback.get();
    }
    long start = System.nanoTime();
    try {
      T result = operation.get();
      onSuccess(elapsedMs(start));
      return result;
    } catch (Exception e) {
      onError(elapsedMs(start), e);
      return fallback.get();
    }
  }

  /**
   * 执行受保护的操作（熔断中抛 {@link CallNotPermittedException}）。
   *
   * @param operation 受保护操作
   * @param <T> 结果类型
   * @return 操作结果
   * @throws CallNotPermittedException 熔断中拒绝调用
   */
  public <T> T executeSupplier(Supplier<T> operation) {
    acquirePermission();
    long start = System.nanoTime();
    try {
      T result = operation.get();
      onSuccess(elapsedMs(start));
      return result;
    } catch (Exception e) {
      onError(elapsedMs(start), e);
      throw e;
    }
  }

  /**
   * 执行无返回值操作（带降级）。
   *
   * @param operation 受保护操作
   * @param fallback 降级操作
   */
  public void execute(Runnable operation, Runnable fallback) {
    if (!tryAcquirePermission()) {
      fallback.run();
      return;
    }
    long start = System.nanoTime();
    try {
      operation.run();
      onSuccess(elapsedMs(start));
    } catch (Exception e) {
      onError(elapsedMs(start), e);
      fallback.run();
    }
  }

  /**
   * 获取当前状态快照。
   *
   * <p>自动半开启用时，读操作也会惰性触发 OPEN → HALF_OPEN（对齐既有语义）； 未启用则为纯读。
   *
   * @return 当前状态
   */
  public State getState() {
    if (config.isAutomaticTransitionFromOpenToHalfOpenEnabled()) {
      State current = stateRef.get();
      if (current == State.OPEN && isWaitDurationElapsed()
          && stateRef.compareAndSet(State.OPEN, State.HALF_OPEN)) {
        transitionTo(State.OPEN, State.HALF_OPEN);
        resetHalfOpenPermits();
      }
    }
    return stateRef.get();
  }

  /**
   * 获取滑动窗口指标快照。
   *
   * @return 指标快照
   */
  public Metrics getMetrics() {
    SlidingWindowMetrics.Snapshot snapshot = metrics.snapshot();
    return new Metrics(
        snapshot.getFailureRate(),
        snapshot.getSlowCallRate(),
        snapshot.getTotal(),
        snapshot.getFailure(),
        snapshot.getSuccess(),
        snapshot.getSlow(),
        snapshot.getSlowFailure(),
        snapshot.getSlowSuccess(),
        snapshot.getTotalDurationMs(),
        snapshot.getAverageDurationMs());
  }

  /**
   * 强制转换为 OPEN 状态（运维熔断，需手动解除）。
   *
   * <p>状态置为 FORCED_OPEN，拒绝一切调用，直至 {@link #transitionToClosedState()} 或
   * {@link #reset()}。
   */
  public void transitionToForcedOpenState() {
    State prev = stateRef.getAndSet(State.FORCED_OPEN);
    if (prev != State.FORCED_OPEN) {
      transitionTo(prev, State.FORCED_OPEN);
      metrics.reset();
    }
  }

  /**
   * 强制恢复 CLOSED 状态（解除运维熔断 / 强制闭合）。
   */
  public void transitionToClosedState() {
    State prev = stateRef.getAndSet(State.CLOSED);
    if (prev != State.CLOSED) {
      transitionTo(prev, State.CLOSED);
      metrics.reset();
    }
  }

  /** 重置统计并恢复 CLOSED（等价于强制闭合）。 */
  public void reset() {
    transitionToClosedState();
  }

  /** OPEN 等待是否已到期。 */
  private boolean isWaitDurationElapsed() {
    return System.currentTimeMillis() - openedAtMillis
        >= config.getWaitDurationInOpenState().toMillis();
  }

  /** 竞争获取半开探测许可。 */
  private boolean tryAcquireHalfOpenPermit() {
    for (; ; ) {
      int remaining = halfOpenPermits.get();
      if (remaining <= 0) {
        return false;
      }
      if (halfOpenPermits.compareAndSet(remaining, remaining - 1)) {
        return true;
      }
    }
  }

  /** 重置半开许可与探测成功计数。 */
  private void resetHalfOpenPermits() {
    halfOpenPermits.set(config.getPermittedNumberOfCallsInHalfOpenState());
    halfOpenSuccess.set(0);
  }

  /** CLOSED 状态下评估滑动窗口是否触发熔断。 */
  private void evaluateClosedWindow() {
    if (stateRef.get() != State.CLOSED) {
      return;
    }
    SlidingWindowMetrics.Snapshot snapshot = metrics.snapshot();
    if (snapshot.getTotal() < config.getMinimumNumberOfCalls()) {
      return;
    }
    float failureRate = snapshot.getFailureRate();
    float slowRate = snapshot.getSlowCallRate();
    boolean thresholdExceeded =
        failureRate >= config.getFailureRateThreshold()
            || slowRate >= config.getSlowCallRateThreshold();
    if (thresholdExceeded && stateRef.compareAndSet(State.CLOSED, State.OPEN)) {
      transitionTo(State.CLOSED, State.OPEN);
    }
  }

  /** 发布状态转换事件并记录打开时间。 */
  private void transitionTo(State from, State to) {
    if (to == State.OPEN || to == State.FORCED_OPEN) {
      openedAtMillis = System.currentTimeMillis();
      log.warn("[CircuitBreaker] '{}' 状态转换: {} -> {}", name, from, to);
    } else {
      log.info("[CircuitBreaker] '{}' 状态转换: {} -> {}", name, from, to);
    }
    eventPublisher.publishStateTransition(name, from, to);
  }

  /** 判断是否慢调用。 */
  private boolean isSlowCall(long durationMs) {
    return durationMs >= config.getSlowCallDurationThreshold().toMillis();
  }

  /** 计算已流逝毫秒。 */
  private static long elapsedMs(long startNanos) {
    return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
  }

  /** 滑动窗口指标快照（不可变）。 */
  @Getter
  public static final class Metrics {

    private final float failureRate;
    private final float slowCallRate;
    private final int numberOfBufferedCalls;
    private final int numberOfFailedCalls;
    private final int numberOfSuccessfulCalls;
    private final int numberOfSlowCalls;
    private final int numberOfSlowFailedCalls;
    private final int numberOfSlowSuccessfulCalls;
    private final long totalDurationMs;
    private final long averageDurationMs;

    private Metrics(
        float failureRate,
        float slowCallRate,
        int numberOfBufferedCalls,
        int numberOfFailedCalls,
        int numberOfSuccessfulCalls,
        int numberOfSlowCalls,
        int numberOfSlowFailedCalls,
        int numberOfSlowSuccessfulCalls,
        long totalDurationMs,
        long averageDurationMs) {
      this.failureRate = failureRate;
      this.slowCallRate = slowCallRate;
      this.numberOfBufferedCalls = numberOfBufferedCalls;
      this.numberOfFailedCalls = numberOfFailedCalls;
      this.numberOfSuccessfulCalls = numberOfSuccessfulCalls;
      this.numberOfSlowCalls = numberOfSlowCalls;
      this.numberOfSlowFailedCalls = numberOfSlowFailedCalls;
      this.numberOfSlowSuccessfulCalls = numberOfSlowSuccessfulCalls;
      this.totalDurationMs = totalDurationMs;
      this.averageDurationMs = averageDurationMs;
    }

    /**
     * 获取失败率（百分比；无调用时返回 -1）。
     *
     * @return 失败率
     */
    public float getFailureRate() {
      return failureRate;
    }

    /**
     * 获取慢调用率（百分比；无调用时返回 -1）。
     *
     * @return 慢调用率
     */
    public float getSlowCallRate() {
      return slowCallRate;
    }
  }
}
