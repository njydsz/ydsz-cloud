package com.njydsz.pmis.common.sentry.resilience;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;

/**
 * 熔断降级保护器
 *
 * <p>当 Resilience4j 不可用时使用此简化版熔断器。
 * 基于滑动窗口失败率统计，达到阈值后触发熔断。
 *
 * <p>状态流转：
 * <ul>
 *   <li>CLOSED → 失败率超过阈值 → OPEN</li>
 *   <li>OPEN → 等待半开时间 → HALF_OPEN</li>
 *   <li>HALF_OPEN → 探测成功 → CLOSED</li>
 *   <li>HALF_OPEN → 探测失败 → OPEN</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
public class CircuitBreaker {

    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    private final String name;
    private final double failureRateThreshold;
    private final int slidingWindowSize;
    private final long halfOpenAfterMillis;

    private volatile State state = State.CLOSED;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger totalCount = new AtomicInteger(0);
    private volatile long lastFailureTime = 0;

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
        if (state == State.CLOSED) {
            return true;
        }
        if (state == State.OPEN) {
            long elapsed = System.currentTimeMillis() - lastFailureTime;
            if (elapsed >= halfOpenAfterMillis) {
                state = State.HALF_OPEN;
                log.info("[Sentry] CircuitBreaker '{}' 进入半开状态", name);
                return true;
            }
            return false;
        }
        // HALF_OPEN: 允许一次探测
        return true;
    }

    private void onSuccess() {
        if (state == State.HALF_OPEN) {
            state = State.CLOSED;
            failureCount.set(0);
            totalCount.set(0);
            log.info("[Sentry] CircuitBreaker '{}' 半开探测成功, 恢复 CLOSED", name);
        } else {
            totalCount.incrementAndGet();
            checkThreshold();
        }
    }

    private void onFailure() {
        lastFailureTime = System.currentTimeMillis();
        if (state == State.HALF_OPEN) {
            state = State.OPEN;
            log.warn("[Sentry] CircuitBreaker '{}' 半开探测失败, 恢复 OPEN", name);
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
                state = State.OPEN;
                log.warn("[Sentry] CircuitBreaker '{}' 失败率 {}/{}={:.2%} 超过阈值 {}, 触发熔断",
                        name, failures, total, rate, failureRateThreshold);
            }
            // 重置滑动窗口
            failureCount.set(0);
            totalCount.set(0);
        }
    }

    public State getState() {
        return state;
    }

    public String getName() {
        return name;
    }

    public int getFailureCount() {
        return failureCount.get();
    }

    public int getTotalCount() {
        return totalCount.get();
    }
}
