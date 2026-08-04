package com.njydsz.common.sentry.resilience;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;

/**
 * 熔断降级保护器
 *
 * <p>基于滑动窗口失败率统计，达到阈值后触发熔断。
 * 使用 AtomicReference + CAS 确保 HALF_OPEN 状态下仅单个探测请求通过。
 *
 * <p>状态流转：
 * <ul>
 *   <li>CLOSED → 失败率超过阈值 → OPEN</li>
 *   <li>OPEN → 等待半开时间 → HALF_OPEN</li>
 *   <li>HALF_OPEN → 探测成功 → CLOSED</li>
 *   <li>HALF_OPEN → 探测失败 → OPEN</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class CircuitBreaker {

    /**
     * 熔断状态枚举。
     *
     * <ul>
     *   <li>{@link #CLOSED}：正常放行请求</li>
     *   <li>{@link #OPEN}：熔断打开，直接拒绝请求</li>
     *   <li>{@link #HALF_OPEN}：半开探测，放行少量试探请求</li>
     * </ul>
     */
    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    private final String name;
    private final double failureRateThreshold;
    private final int slidingWindowSize;
    private final long halfOpenAfterMillis;

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger totalCount = new AtomicInteger(0);
    private volatile long lastFailureTime = 0;
    private final AtomicInteger halfOpenProbeInProgress = new AtomicInteger(0);

    public CircuitBreaker(String name, double failureRateThreshold,
                          int slidingWindowSize, long halfOpenAfterMillis) {
        this.name = name;
        this.failureRateThreshold = failureRateThreshold;
        this.slidingWindowSize = slidingWindowSize;
        this.halfOpenAfterMillis = halfOpenAfterMillis;
        log.info("[Sentry] CircuitBreaker '{}' 初始化: threshold={}, window={}, halfOpenAfter={}ms",
                name, failureRateThreshold, slidingWindowSize, halfOpenAfterMillis);
    }

    /**
     * 执行受保护的操作
     *
     * @param operation 操作
     * @param fallback  降级操作
     * @return 结果
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
     * 执行无返回值操作
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

    private boolean canExecute() {
        State currentState = state.get();
        if (currentState == State.CLOSED) {
            return true;
        }
        if (currentState == State.OPEN) {
            long elapsed = System.currentTimeMillis() - lastFailureTime;
            if (elapsed >= halfOpenAfterMillis) {
                // CAS 确保 only one thread transitions to HALF_OPEN
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    log.info("[Sentry] CircuitBreaker '{}' 进入半开状态", name);
                    return true;
                }
                // CAS 失败说明其他线程已先转换，当前线程走降级
                return false;
            }
            return false;
        }
        // HALF_OPEN: 仅允许一个探测请求
        if (currentState == State.HALF_OPEN) {
            return halfOpenProbeInProgress.compareAndSet(0, 1);
        }
        return false;
    }

    private void onSuccess() {
        State currentState = state.get();
        if (currentState == State.HALF_OPEN) {
            halfOpenProbeInProgress.set(0);
            state.set(State.CLOSED);
            failureCount.set(0);
            totalCount.set(0);
            log.info("[Sentry] CircuitBreaker '{}' 半开探测成功, 恢复 CLOSED", name);
        } else if (currentState == State.CLOSED) {
            totalCount.incrementAndGet();
            checkThreshold();
        }
    }

    private void onFailure() {
        lastFailureTime = System.currentTimeMillis();
        State currentState = state.get();
        if (currentState == State.HALF_OPEN) {
            halfOpenProbeInProgress.set(0);
            state.set(State.OPEN);
            log.warn("[Sentry] CircuitBreaker '{}' 半开探测失败, 恢复 OPEN", name);
        } else if (currentState == State.CLOSED) {
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
                if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                    log.warn("[Sentry] CircuitBreaker '{}' 失败率 {}/{}={} 超过阈值 {}, 触发熔断",
                            name, failures, total, String.format("%.2f%%", rate * 100), failureRateThreshold);
                }
            }
            // 重置滑动窗口
            failureCount.set(0);
            totalCount.set(0);
        }
    }

    /**
     * 获取当前熔断状态。
     *
     * @return 当前状态（CLOSED / OPEN / HALF_OPEN）
     */
    public State getState() {
        return state.get();
    }

    public String getName() {
        return name;
    }

    /**
     * 获取滑动窗口内的失败次数。
     *
     * @return 当前失败计数
     */
    public int getFailureCount() {
        return failureCount.get();
    }

    /**
     * 获取滑动窗口内的总请求次数。
     *
     * @return 当前总计数
     */
    public int getTotalCount() {
        return totalCount.get();
    }
}
