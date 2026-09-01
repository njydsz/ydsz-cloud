package com.njydsz.common.safe.resilience;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 熔断器事件发布器。
 *
 * <p>调用方通过 {@code circuitBreaker.getEventPublisher().onStateTransition(...)} 订阅状态变更、
 * 成功与失败事件；订阅方法可链式调用。监听器在记录线程内同步执行，应保持轻量，
 * 重逻辑请自行转异步。
 *
 * <p><b>线程安全：</b>监听器列表为 {@link CopyOnWriteArrayList}，支持并发订阅与发布。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class CircuitBreakerEvents {

  private final List<Consumer<CircuitBreakerEvent>> stateTransitionListeners =
      new CopyOnWriteArrayList<>();
  private final List<Consumer<CircuitBreakerEvent>> successListeners = new CopyOnWriteArrayList<>();
  private final List<Consumer<CircuitBreakerEvent>> errorListeners = new CopyOnWriteArrayList<>();

  /**
   * 订阅状态变更事件（CLOSED/OPEN/HALF_OPEN/FORCED_OPEN 之间的转换）。
   *
   * @param listener 监听器
   * @return this（支持链式订阅）
   */
  public CircuitBreakerEvents onStateTransition(Consumer<CircuitBreakerEvent> listener) {
    stateTransitionListeners.add(listener);
    return this;
  }

  /**
   * 订阅成功调用事件。
   *
   * @param listener 监听器
   * @return this（支持链式订阅）
   */
  public CircuitBreakerEvents onSuccess(Consumer<CircuitBreakerEvent> listener) {
    successListeners.add(listener);
    return this;
  }

  /**
   * 订阅失败调用事件。
   *
   * @param listener 监听器
   * @return this（支持链式订阅）
   */
  public CircuitBreakerEvents onError(Consumer<CircuitBreakerEvent> listener) {
    errorListeners.add(listener);
    return this;
  }

  /**
   * 发布状态变更事件。
   *
   * @param circuitBreakerName 熔断器名称
   * @param from 转换前状态
   * @param to 转换后状态
   */
  void publishStateTransition(
      String circuitBreakerName, CircuitBreaker.State from, CircuitBreaker.State to) {
    if (stateTransitionListeners.isEmpty()) {
      return;
    }
    CircuitBreakerEvent event = CircuitBreakerEvent.stateTransition(circuitBreakerName, from, to);
    for (Consumer<CircuitBreakerEvent> listener : stateTransitionListeners) {
      listener.accept(event);
    }
  }

  /**
   * 发布成功调用事件。
   *
   * @param circuitBreakerName 熔断器名称
   * @param durationMs 调用耗时（毫秒）
   */
  void publishSuccess(String circuitBreakerName, long durationMs) {
    if (successListeners.isEmpty()) {
      return;
    }
    CircuitBreakerEvent event = CircuitBreakerEvent.success(circuitBreakerName, durationMs);
    for (Consumer<CircuitBreakerEvent> listener : successListeners) {
      listener.accept(event);
    }
  }

  /**
   * 发布失败调用事件。
   *
   * @param circuitBreakerName 熔断器名称
   * @param durationMs 调用耗时（毫秒）
   * @param throwable 触发失败的异常
   */
  void publishError(String circuitBreakerName, long durationMs, Throwable throwable) {
    if (errorListeners.isEmpty()) {
      return;
    }
    CircuitBreakerEvent event = CircuitBreakerEvent.error(circuitBreakerName, durationMs, throwable);
    for (Consumer<CircuitBreakerEvent> listener : errorListeners) {
      listener.accept(event);
    }
  }
}
