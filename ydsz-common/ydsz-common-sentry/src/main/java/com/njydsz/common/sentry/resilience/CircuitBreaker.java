package com.njydsz.common.sentry.resilience;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;

/**
 * 熔断降级保护器
 *
 * <p>基于时间桶滑动窗口统计失败率，达到阈值后触发熔断。
 * 使用 AtomicReference + CAS 确保 HALF_OPEN 状态下仅单个探测请求通过。
 *
 * <p>滑动窗口实现：将窗口时长平均划分为若干桶，每个桶记录一个时间片内的成功/失败次数。
 * 统计时累加所有有效桶的数据，桶过期后会被清零复用，
 * 避免固定窗口边界处失败率被忽略的问题（如窗口末尾的连续失败
 * 与下一窗口开头的连续失败不会因分母重置而被稀释）。
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
        /** 正常放行 */
        CLOSED,
        /** 熔断打开 */
        OPEN,
        /** 半开探测 */
        HALF_OPEN
    }

    /** 桶数量（固定 10 桶，每桶覆盖窗口时长的 1/10） */
    private static final int BUCKET_COUNT = 10;
    /** 触发熔断所需最少样本数 */
    private static final int MINIMUM_CALLS = BUCKET_COUNT;

    /**
     * 滑动窗口的单个时间桶。
     *
     * <p>每个桶覆盖 {@code windowDurationMillis / BUCKET_COUNT} 毫秒的时间片，
     * 记录该时间片内的成功与失败次数。
     */
    private static class Bucket {

        /** 桶的起始时间戳（毫秒），0 表示从未使用 */
        private final AtomicLong startTimeMillis = new AtomicLong(0);
        /** 成功次数 */
        private final AtomicInteger successes = new AtomicInteger(0);
        /** 失败次数 */
        private final AtomicInteger failures = new AtomicInteger(0);

        /**
         * 在当前时间记录一次调用结果。
         *
         * <p>如果桶已过期（当前时间超出桶覆盖的时间片范围），自动清零后从头累计。
         *
         * @param now           当前时间戳（毫秒）
         * @param bucketDuration 每个桶的时间跨度（毫秒）
         * @param success       调用是否成功
         */
        void record(long now, long bucketDuration, boolean success) {
            long start = startTimeMillis.get();
            if (start == 0 || now - start >= bucketDuration) {
                // 桶未初始化或已过期，尝试重置
                if (startTimeMillis.compareAndSet(start, now)) {
                    successes.set(success ? 1 : 0);
                    failures.set(success ? 0 : 1);
                    return;
                }
                // 其他线程已重置，继续往下累加
                start = startTimeMillis.get();
            }
            if (now - start < bucketDuration) {
                if (success) {
                    successes.incrementAndGet();
                } else {
                    failures.incrementAndGet();
                }
            }
        }

        /**
         * 判断桶是否在指定窗口时间内（未过期）。
         *
         * @param now           当前时间戳（毫秒）
         * @param bucketDuration 每个桶的时间跨度（毫秒）
         * @return {@code true} 表示桶在窗口内
         */
        boolean isValid(long now, long bucketDuration) {
            long start = startTimeMillis.get();
            return start > 0 && now - start < bucketDuration;
        }

        int getSuccesses() {
            return successes.get();
        }

        int getFailures() {
            return failures.get();
        }
    }

    /** 滑动窗口计数器 */
    private static class SlidingWindowCounter {

        /** 桶数组 */
        private final Bucket[] buckets;
        /** 滑动窗口总时长（毫秒） */
        private final long windowDurationMillis;
        /** 单个桶的时间跨度（毫秒） */
        private final long bucketDurationMillis;

        SlidingWindowCounter(int bucketCount, long windowDurationMillis) {
            this.buckets = new Bucket[bucketCount];
            for (int i = 0; i < bucketCount; i++) {
                buckets[i] = new Bucket();
            }
            this.windowDurationMillis = windowDurationMillis;
            this.bucketDurationMillis = windowDurationMillis / bucketCount;
        }

        /**
         * 记录一次调用结果
         *
         * @param success 是否成功
         */
        void record(boolean success) {
            long now = System.currentTimeMillis();
            // 通过时间取模选择桶索引，实现循环复用
            int index = (int) ((now / bucketDurationMillis) % buckets.length);
            buckets[index].record(now, bucketDurationMillis, success);
        }

        /**
         * 获取窗口内总请求数
         *
         * @return 总请求数
         */
        int getTotalCount() {
            long now = System.currentTimeMillis();
            int total = 0;
            for (Bucket bucket : buckets) {
                if (bucket.isValid(now, bucketDurationMillis)) {
                    total += bucket.getSuccesses() + bucket.getFailures();
                }
            }
            return total;
        }

        /**
         * 获取窗口内失败请求数
         *
         * @return 失败请求数
         */
        int getFailureCount() {
            long now = System.currentTimeMillis();
            int failures = 0;
            for (Bucket bucket : buckets) {
                if (bucket.isValid(now, bucketDurationMillis)) {
                    failures += bucket.getFailures();
                }
            }
            return failures;
        }

        /**
         * 获取窗口内失败率
         *
         * @return 失败率（0.0~1.0），无请求时返回 0.0
         */
        double getFailureRate() {
            int total = getTotalCount();
            if (total == 0) {
                return 0.0;
            }
            return (double) getFailureCount() / total;
        }
    }

    private final String name;
    private final double failureRateThreshold;
    private final SlidingWindowCounter windowCounter;
    private final long halfOpenAfterMillis;

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private volatile long lastFailureTime = 0;
    private final AtomicInteger halfOpenProbeInProgress = new AtomicInteger(0);

    public CircuitBreaker(String name, double failureRateThreshold,
                          int slidingWindowSize, long halfOpenAfterMillis) {
        this.name = name;
        this.failureRateThreshold = failureRateThreshold;
        // slidingWindowSize 作为窗口秒数，转换为毫秒作为窗口总时长
        this.windowCounter = new SlidingWindowCounter(BUCKET_COUNT, slidingWindowSize * 1000L);
        this.halfOpenAfterMillis = halfOpenAfterMillis;
        log.info("[Sentry] CircuitBreaker '{}' 初始化: threshold={}, window={}s, halfOpenAfter={}ms",
                name, failureRateThreshold, slidingWindowSize, halfOpenAfterMillis);
    }

    /**
     * 执行受保护的操作。
     *
     * @param operation 业务操作
     * @param fallback  降级操作
     * @return 操作结果（业务成功时返回业务结果，失败或熔断时返回降级结果）
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
     * 执行无返回值操作。
     *
     * @param operation 业务操作
     * @param fallback  降级操作
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

    /**
     * 判断当前是否允许执行操作。
     *
     * @return {@code true} 允许执行；{@code false} 应走降级
     */
    boolean canExecute() {
        State currentState = state.get();
        if (currentState == State.CLOSED) {
            return true;
        }
        if (currentState == State.OPEN) {
            long elapsed = System.currentTimeMillis() - lastFailureTime;
            if (elapsed >= halfOpenAfterMillis) {
                // CAS 确保仅有一个线程转换到 HALF_OPEN
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
        return halfOpenProbeInProgress.compareAndSet(0, 1);
    }

    /**
     * 记录一次成功调用。
     */
    private void onSuccess() {
        State currentState = state.get();
        if (currentState == State.HALF_OPEN) {
            halfOpenProbeInProgress.set(0);
            state.set(State.CLOSED);
            log.info("[Sentry] CircuitBreaker '{}' 半开探测成功, 恢复 CLOSED", name);
        } else if (currentState == State.CLOSED) {
            windowCounter.record(true);
            checkThreshold();
        }
    }

    /**
     * 记录一次失败调用。
     */
    private void onFailure() {
        lastFailureTime = System.currentTimeMillis();
        State currentState = state.get();
        if (currentState == State.HALF_OPEN) {
            halfOpenProbeInProgress.set(0);
            state.set(State.OPEN);
            log.warn("[Sentry] CircuitBreaker '{}' 半开探测失败, 恢复 OPEN", name);
        } else if (currentState == State.CLOSED) {
            windowCounter.record(false);
            checkThreshold();
        }
    }

    /**
     * 检查失败率是否超过阈值。
     *
     * <p>样本数不足 {@link #MINIMUM_CALLS} 时不触发熔断，避免冷启动误触发。
     */
    private void checkThreshold() {
        int total = windowCounter.getTotalCount();
        if (total < MINIMUM_CALLS) {
            return;
        }
        double rate = windowCounter.getFailureRate();
        if (rate >= failureRateThreshold) {
            if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                log.warn("[Sentry] CircuitBreaker '{}' 失败率 {:.2f}% 超过阈值 {}, 触发熔断",
                        name, rate * 100, failureRateThreshold);
            }
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
        return windowCounter.getFailureCount();
    }

    /**
     * 获取滑动窗口内的总请求次数。
     *
     * @return 当前总计数
     */
    public int getTotalCount() {
        return windowCounter.getTotalCount();
    }
}
