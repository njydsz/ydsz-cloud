package com.remisoft.common.socket.resilience;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket 模块轻量级熔断器（P0-2）。
 *
 * <p>基于滑动窗口失败率统计，达到阈值后触发熔断。
 * 使用 {@link AtomicReference} + CAS 确保线程安全，
 * HALF_OPEN 状态仅允许单个探测请求通过。
 *
 * <p>状态流转：
 * <ul>
 *   <li>CLOSED → 失败率超过阈值 → OPEN</li>
 *   <li>OPEN → 等待半开时间 → HALF_OPEN</li>
 *   <li>HALF_OPEN → 探测成功 → CLOSED</li>
 *   <li>HALF_OPEN → 探测失败 → OPEN</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
public class WebSocketCircuitBreaker {

    /**
     * 熔断器状态。
     *
     * <p>状态流转为 CLOSED → OPEN → HALF_OPEN → CLOSED/OPEN：
     * CLOSED 表示放行全部请求；OPEN 表示熔断、直接走降级；HALF_OPEN 表示
     * 熔断期满后仅放行单个探测请求验证恢复情况。
     */
    public enum State {
        /** 关闭状态：正常放行，统计失败率 */
        CLOSED,
        /** 开启状态：熔断中，所有请求直接降级 */
        OPEN,
        /** 半开状态：放行单个探测请求验证恢复 */
        HALF_OPEN
    }

    private final String name;
    private final double failureRateThreshold;
    private final int slidingWindowSize;
    private final long halfOpenAfterMillis;

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger totalCount = new AtomicInteger(0);
    private volatile long lastFailureTime = 0;

    public WebSocketCircuitBreaker(String name, double failureRateThreshold,
                                  int slidingWindowSize, long halfOpenAfterMillis) {
        this.name = name;
        this.failureRateThreshold = failureRateThreshold;
        this.slidingWindowSize = slidingWindowSize;
        this.halfOpenAfterMillis = halfOpenAfterMillis;
        log.info("[WS-CircuitBreaker] '{}' 初始化: threshold={}, window={}, halfOpenAfter={}ms",
                name, failureRateThreshold, slidingWindowSize, halfOpenAfterMillis);
    }

    /**
     * 执行受保护的操作，失败时返回降级值。
     *
     * @param operation 受保护操作
     * @param fallback  降级操作
     * @return 结果
     */
    public <T> T execute(Supplier<T> operation, Supplier<T> fallback) {
        if (!tryAcquire()) {
            log.debug("[WS-CircuitBreaker] '{}' 熔断中, 执行降级", name);
            return fallback.get();
        }
        try {
            T result = operation.get();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure();
            log.debug("[WS-CircuitBreaker] '{}' 操作失败, 执行降级: {}", name, e.getMessage());
            return fallback.get();
        }
    }

    /**
     * 执行无返回值操作。
     */
    public void execute(Runnable operation, Runnable fallback) {
        if (!tryAcquire()) {
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
     * 尝试获取执行许可。
     *
     * @return true 表示允许执行
     */
    private boolean tryAcquire() {
        State currentState = state.get();
        if (currentState == State.CLOSED) {
            return true;
        }
        if (currentState == State.OPEN) {
            long elapsed = System.currentTimeMillis() - lastFailureTime;
            if (elapsed >= halfOpenAfterMillis) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    log.info("[WS-CircuitBreaker] '{}' 进入半开状态", name);
                    return true;
                }
                return false;
            }
            return false;
        }
        return true;
    }

    private void onSuccess() {
        if (state.get() == State.HALF_OPEN) {
            state.set(State.CLOSED);
            failureCount.set(0);
            totalCount.set(0);
            log.info("[WS-CircuitBreaker] '{}' 半开探测成功, 恢复 CLOSED", name);
        } else {
            totalCount.incrementAndGet();
            checkThreshold();
        }
    }

    private void onFailure() {
        lastFailureTime = System.currentTimeMillis();
        if (state.get() == State.HALF_OPEN) {
            state.set(State.OPEN);
            log.warn("[WS-CircuitBreaker] '{}' 半开探测失败, 恢复 OPEN", name);
        } else {
            failureCount.incrementAndGet();
            totalCount.incrementAndGet();
            checkThreshold();
        }
    }

    private void checkThreshold() {
        int total = totalCount.get();
        if (total >= slidingWindowSize) {
            int failures = failureCount.get();
            double rate = (double) failures / total;
            if (rate >= failureRateThreshold) {
                state.set(State.OPEN);
                log.warn("[WS-CircuitBreaker] '{}' 失败率 {}/{}={} 超过阈值 {}, 触发熔断",
                        name, failures, total, rate, failureRateThreshold);
            }
            failureCount.set(0);
            totalCount.set(0);
        }
    }

    /**
     * 获取当前熔断器状态。
     *
     * <p>由 {@link AtomicReference} 无锁读取，供监控面板、健康检查或日志
     * 展示熔断情况；结果是最新一次状态快照，非实时强一致。
     *
     * @return 当前状态（{@link State#CLOSED}/{@link State#OPEN}/{@link State#HALF_OPEN}）
     */
    public State getState() {
        return state.get();
    }

    public String getName() {
        return name;
    }
}
