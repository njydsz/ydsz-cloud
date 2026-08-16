package com.njydsz.common.safe.ratelimit.circuitbreaker;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 熔断器抽象基类（三态状态机）。
 *
 * <p>封装熔断器核心状态流转逻辑，提供 CLOSED → OPEN → HALF_OPEN → CLOSED/OPEN 的标准状态机。 子类通过实现 {@link
 * #evaluateThreshold()} 定义触发熔断的具体策略（如连续失败次数、滑动窗口失败率等）。
 *
 * <p><b>状态流转：</b>
 *
 * <ul>
 *   <li>CLOSED → OPEN：调用 {@link #evaluateThreshold()} 返回 true 时自动触发
 *   <li>OPEN → HALF_OPEN：{@link #halfOpenAfterMillis} 到达后首个请求 CAS 转换
 *   <li>HALF_OPEN → CLOSED：探测成功（{@link #recordSuccess()}）
 *   <li>HALF_OPEN → OPEN：探测失败（{@link #recordFailure()}）
 * </ul>
 *
 * <p><b>线程安全：</b>状态使用 {@link AtomicReference} + CAS，确保并发场景下原子转换。
 *
 * <p><b>使用方式：</b>
 *
 * <pre>{@code
 * // 1. 继承并实现 evaluateThreshold()
 * public class MyCircuitBreaker extends AbstractCircuitBreaker {
 *     private final AtomicInteger failures = new AtomicInteger(0);
 *
 *     @Override
 *     protected boolean evaluateThreshold() {
 *         return failures.get() >= config.getFailureThreshold();
 *     }
 *
 *     @Override
 *     protected void resetStats() { failures.set(0); }
 * }
 *
 * // 2. 执行受保护操作
 * String result = breaker.execute(() -> callRemote(), () -> "fallback");
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.3.0
 */
@Slf4j
public abstract class AbstractCircuitBreaker {

  /**
   * 熔断器三态。
   *
   * <ul>
   *   <li>{@link #CLOSED}：正常放行请求
   *   <li>{@link #OPEN}：熔断打开，直接拒绝请求
   *   <li>{@link #HALF_OPEN}：半开探测，放行少量试探请求
   * </ul>
   */
  public enum State {
    /** 正常放行，统计失败率 */
    CLOSED,
    /** 熔断打开，直接拒绝请求 */
    OPEN,
    /** 半开探测，放行少量试探请求 */
    HALF_OPEN
  }

  /** 熔断器配置 */
  @Getter protected final Config config;

  /** 当前状态（CAS 保护） */
  private final AtomicReference<State> stateRef;

  /** 进入 OPEN 状态的时间戳（毫秒），用于判断是否可进入 HALF_OPEN */
  private volatile long openedAt;

  /** HALF_OPEN 状态下的剩余探测许可数 */
  private final AtomicReference<Integer> halfOpenPermits;

  /**
   * 构造熔断器
   *
   * @param config 熔断器配置
   */
  protected AbstractCircuitBreaker(Config config) {
    this.config = config;
    this.stateRef = new AtomicReference<>(State.CLOSED);
    this.halfOpenPermits = new AtomicReference<>(0);
  }

  /**
   * 尝试获取执行许可。
   *
   * <p>线程安全：CAS 确保 OPEN→HALF_OPEN 转换仅由一个线程执行。
   *
   * @return true=允许执行；false=熔断中，应走降级
   */
  public boolean tryAcquire() {
    State current = stateRef.get();
    if (current == State.CLOSED) {
      return true;
    }
    if (current == State.HALF_OPEN) {
      // 仅当有剩余探测许可时放行
      return halfOpenPermits.getAndUpdate(p -> p > 0 ? p - 1 : 0) > 0;
    }
    // OPEN 状态：检查是否可进入 HALF_OPEN
    long now = System.currentTimeMillis();
    if (now - openedAt >= config.getHalfOpenAfterMillis()) {
      if (stateRef.compareAndSet(State.OPEN, State.HALF_OPEN)) {
        halfOpenPermits.set(config.getPermittedHalfOpenCalls());
        log.info("[CircuitBreaker] '{}' 进入半开状态", config.getName());
      }
      // CAS 失败后其他线程可能已在 HALF_OPEN，检查许可
      return stateRef.get() == State.HALF_OPEN
          && halfOpenPermits.getAndUpdate(p -> p > 0 ? p - 1 : 0) > 0;
    }
    return false;
  }

  /**
   * 记录一次成功调用。
   *
   * <p>HALF_OPEN 状态下成功则恢复到 CLOSED；CLOSED 状态下累加统计并评估阈值。
   */
  public void recordSuccess() {
    State current = stateRef.get();
    if (current == State.HALF_OPEN) {
      stateRef.set(State.CLOSED);
      halfOpenPermits.set(0);
      resetStats();
      log.info("[CircuitBreaker] '{}' 半开探测成功, 恢复 CLOSED", config.getName());
    } else if (current == State.CLOSED) {
      onSuccessRecord();
      evaluateAndTransition();
    }
    // OPEN 状态下 recordSuccess 不触发状态变更（仅 HALF_OPEN 探测有意义）
  }

  /**
   * 记录一次失败调用。
   *
   * <p>HALF_OPEN 状态下失败则重回 OPEN；CLOSED 状态下累加统计并评估阈值。
   */
  public void recordFailure() {
    State current = stateRef.get();
    if (current == State.HALF_OPEN) {
      if (stateRef.compareAndSet(State.HALF_OPEN, State.OPEN)) {
        openedAt = System.currentTimeMillis();
        log.warn("[CircuitBreaker] '{}' 半开探测失败, 重转 OPEN", config.getName());
      }
      return;
    }
    if (current == State.CLOSED) {
      onFailureRecord();
      evaluateAndTransition();
    }
  }

  /**
   * 执行受保护的操作，失败或熔断时走降级。
   *
   * @param operation 受保护操作
   * @param fallback 降级操作
   * @return 结果
   */
  public <T> T execute(Supplier<T> operation, Supplier<T> fallback) {
    if (!tryAcquire()) {
      log.debug("[CircuitBreaker] '{}' 熔断中, 执行降级", config.getName());
      return fallback.get();
    }
    try {
      T result = operation.get();
      recordSuccess();
      return result;
    } catch (Exception e) {
      recordFailure();
      log.debug("[CircuitBreaker] '{}' 操作失败, 执行降级: {}", config.getName(), e.getMessage());
      return fallback.get();
    }
  }

  /**
   * 执行无返回值操作。
   *
   * @param operation 受保护操作
   * @param fallback 降级操作
   */
  public void execute(Runnable operation, Runnable fallback) {
    if (!tryAcquire()) {
      fallback.run();
      return;
    }
    try {
      operation.run();
      recordSuccess();
    } catch (Exception e) {
      recordFailure();
      fallback.run();
    }
  }

  /**
   * 获取当前熔断状态。
   *
   * @return 当前状态快照
   */
  public State getState() {
    return stateRef.get();
  }

  /**
   * 判断是否处于熔断状态（OPEN 或 HALF_OPEN 无许可）。
   *
   * @return true=已熔断
   */
  public boolean isOpen() {
    return stateRef.get() != State.CLOSED;
  }

  /**
   * 获取熔断器名称。
   *
   * @return 名称
   */
  public String getName() {
    return config.getName();
  }

  /**
   * 获取底层 Resilience4j CircuitBreaker 实例（可选，供高级场景使用）。
   *
   * <p>默认返回 null，使用 Resilience4j 实现的子类可覆写。
   *
   * @return Resilience4j CircuitBreaker 实例；默认 null
   */
  public io.github.resilience4j.circuitbreaker.CircuitBreaker getDelegate() {
    return null;
  }

  /**
   * 评估当前统计是否达到熔断阈值。
   *
   * <p>子类实现此方法定义触发熔断的具体策略。
   *
   * @return true=达到阈值，应触发状态转换 CLOSED→OPEN
   */
  protected abstract boolean evaluateThreshold();

  /** 在 CLOSED 状态下记录一次成功后调用（子类可累加统计）。 */
  protected void onSuccessRecord() {
    // 默认无操作
  }

  /** 在 CLOSED 状态下记录一次失败后调用（子类可累加统计）。 */
  protected void onFailureRecord() {
    // 默认无操作
  }

  /** 重置失败统计计数（恢复到 CLOSED 时调用）。 */
  protected void resetStats() {
    // 默认无操作
  }

  /** 评估阈值并在满足条件时触发 CLOSED→OPEN 转换。 */
  private void evaluateAndTransition() {
    if (stateRef.get() == State.CLOSED && evaluateThreshold()) {
      if (stateRef.compareAndSet(State.CLOSED, State.OPEN)) {
        openedAt = System.currentTimeMillis();
        log.warn(
            "[CircuitBreaker] '{}' 触发熔断, threshold={}",
            config.getName(),
            config.getFailureThreshold());
      }
    }
  }

  /** 熔断器配置（不可变）。 */
  @Getter
  public static class Config {
    /** 熔断器名称 */
    private final String name;

    /** 失败阈值（含义由子类解释：连续失败次数或失败率阈值） */
    private final double failureThreshold;

    /** OPEN 状态持续时间（毫秒），到期后允许进入 HALF_OPEN */
    private final long halfOpenAfterMillis;

    /** HALF_OPEN 状态允许的探测请求数 */
    private final int permittedHalfOpenCalls;

    /**
     * 构造配置
     *
     * @param name 熔断器名称
     * @param failureThreshold 失败阈值
     * @param halfOpenAfterMillis OPEN 等待时间（毫秒）
     * @param permittedHalfOpenCalls 半开探测数
     */
    public Config(
        String name,
        double failureThreshold,
        long halfOpenAfterMillis,
        int permittedHalfOpenCalls) {
      this.name = name;
      this.failureThreshold = failureThreshold;
      this.halfOpenAfterMillis = halfOpenAfterMillis;
      this.permittedHalfOpenCalls = permittedHalfOpenCalls;
    }
  }
}
