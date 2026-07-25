package com.njydsz.common.safe.ratelimit.circuitbreaker;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.njydsz.common.safe.ratelimit.enums.RateLimitResult;
import com.njydsz.common.safe.ratelimit.model.RateLimitDecision;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 熔断器（Resilience4j 风格）
 *
 * <p><b>三态机：</b>
 * <ul>
 *   <li>CLOSED（关闭）：正常调用，统计失败率</li>
 *   <li>OPEN（开启）：直接拒绝，不调用下游</li>
 *   <li>HALF_OPEN（半开）：放行少量探测请求，成功则关闭，失败则继续开启</li>
 * </ul>
 *
 * <p><b>触发条件（可配置）：</b>
 * <ul>
 *   <li>失败率阈值（failureRateThreshold，默认 50%）</li>
 *   <li>慢调用率阈值（slowCallRateThreshold，默认 100%）</li>
 *   <li>最小调用数（minimumNumberOfCalls，默认 10）</li>
 *   <li>滑动窗口大小（slidingWindowSize，默认 100）</li>
 *   <li>开启后等待时间（waitDurationInOpenState，默认 10s）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class CircuitBreaker {

    /** 资源 → 熔断器实例 */
    private final ConcurrentHashMap<String, CircuitBreakerInstance> breakers = new ConcurrentHashMap<>();

    private final CircuitBreakerConfig config;

    public CircuitBreaker(CircuitBreakerConfig config) {
        this.config = config;
    }

    public CircuitBreaker() {
        this(CircuitBreakerConfig.defaults());
    }

    /**
     * 尝试执行（同步）
     */
    public <T> RateLimitDecision tryAcquire(String resource, CircuitBreakerCallback<T> callback) {
        CircuitBreakerInstance instance = breakers.computeIfAbsent(resource,
                k -> new CircuitBreakerInstance(config));
        State state = instance.getState();
        if (state == State.OPEN) {
            return blockedDecision(resource, "circuit breaker open");
        }
        long start = System.nanoTime();
        try {
            T result = callback.call();
            long durationNs = System.nanoTime() - start;
            instance.recordSuccess(durationNs, config);
            return RateLimitDecision.builder()
                    .resource(resource)
                    .result(RateLimitResult.PASS)
                    .remaining(1)
                    .threshold(1)
                    .timestamp(Instant.now())
                    .reason("circuit breaker pass")
                    .build();
        } catch (Exception ex) {
            long durationNs = System.nanoTime() - start;
            instance.recordFailure(durationNs, config, ex);
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
     */
    public void forceOpen(String resource) {
        CircuitBreakerInstance instance = breakers.computeIfAbsent(resource,
                k -> new CircuitBreakerInstance(config));
        instance.forceOpen();
    }

    /**
     * 强制关闭
     */
    public void forceClose(String resource) {
        CircuitBreakerInstance instance = breakers.computeIfAbsent(resource,
                k -> new CircuitBreakerInstance(config));
        instance.forceClose();
    }

    /**
     * 获取当前状态
     */
    public State getState(String resource) {
        CircuitBreakerInstance instance = breakers.get(resource);
        return instance == null ? State.CLOSED : instance.getState();
    }

    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    @FunctionalInterface
    public interface CircuitBreakerCallback<T> {
        T call() throws Exception;
    }

    /**
     * 熔断器配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CircuitBreakerConfig {
        /** 失败率阈值（0-1） */
        @Builder.Default
        private double failureRateThreshold = 0.5;
        /** 慢调用率阈值（0-1） */
        @Builder.Default
        private double slowCallRateThreshold = 1.0;
        /** 慢调用阈值（毫秒） */
        @Builder.Default
        private long slowCallDurationThresholdMillis = 1000;
        /** 最小调用数 */
        @Builder.Default
        private int minimumNumberOfCalls = 10;
        /** 滑动窗口大小 */
        @Builder.Default
        private int slidingWindowSize = 100;
        /** OPEN 状态等待时间 */
        @Builder.Default
        private Duration waitDurationInOpenState = Duration.ofSeconds(10);
        /** HALF_OPEN 状态允许的探测数 */
        @Builder.Default
        private int permittedNumberOfCallsInHalfOpenState = 10;
        /** 滑动窗口类型 */
        @Builder.Default
        private SlidingWindowType slidingWindowType = SlidingWindowType.COUNT_BASED;

        public static CircuitBreakerConfig defaults() {
            return new CircuitBreakerConfig();
        }
    }

    public enum SlidingWindowType {
        TIME_BASED, COUNT_BASED
    }

    /**
     * 单资源熔断器实例
     */
    private static class CircuitBreakerInstance {
        private final CircuitBreakerConfig config;
        private volatile State state = State.CLOSED;
        private volatile Instant openedAt;
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private final AtomicInteger slowCallCount = new AtomicInteger(0);
        private final AtomicInteger totalCount = new AtomicInteger(0);
        private final AtomicLong totalDurationNanos = new AtomicLong(0);
        private final AtomicInteger halfOpenPermits = new AtomicInteger(0);

        CircuitBreakerInstance(CircuitBreakerConfig config) {
            this.config = config;
        }

        State getState() {
            if (state == State.OPEN && openedAt != null) {
                if (Instant.now().isAfter(openedAt.plus(config.getWaitDurationInOpenState()))) {
                    // 自动转换到 HALF_OPEN
                    state = State.HALF_OPEN;
                    halfOpenPermits.set(config.getPermittedNumberOfCallsInHalfOpenState());
                }
            }
            return state;
        }

        synchronized void recordSuccess(long durationNanos, CircuitBreakerConfig config) {
            totalCount.incrementAndGet();
            totalDurationNanos.addAndGet(durationNanos);
            if (durationNanos > config.getSlowCallDurationThresholdMillis() * 1_000_000L) {
                slowCallCount.incrementAndGet();
            } else {
                successCount.incrementAndGet();
            }
            if (state == State.HALF_OPEN) {
                int permits = halfOpenPermits.decrementAndGet();
                if (permits <= 0) {
                    // 探测成功，关闭熔断器
                    state = State.CLOSED;
                    reset();
                }
            }
            checkThresholds();
        }

        synchronized void recordFailure(long durationNanos, CircuitBreakerConfig config, Throwable ex) {
            totalCount.incrementAndGet();
            totalDurationNanos.addAndGet(durationNanos);
            failureCount.incrementAndGet();
            if (state == State.HALF_OPEN) {
                // 探测失败，重新打开
                state = State.OPEN;
                openedAt = Instant.now();
                return;
            }
            checkThresholds();
        }

        private void checkThresholds() {
            int total = totalCount.get();
            if (total < config.getMinimumNumberOfCalls()) {
                return;
            }
            double failureRate = (double) failureCount.get() / total;
            double slowCallRate = (double) slowCallCount.get() / total;
            if (failureRate >= config.getFailureRateThreshold()
                    || slowCallRate >= config.getSlowCallRateThreshold()) {
                if (state != State.OPEN) {
                    log.info("Circuit breaker open: failureRate={}, slowCallRate={}", failureRate, slowCallRate);
                    state = State.OPEN;
                    openedAt = Instant.now();
                }
            }
        }

        void forceOpen() {
            state = State.OPEN;
            openedAt = Instant.now();
        }

        void forceClose() {
            state = State.CLOSED;
            openedAt = null;
            reset();
        }

        private void reset() {
            successCount.set(0);
            failureCount.set(0);
            slowCallCount.set(0);
            totalCount.set(0);
            totalDurationNanos.set(0);
        }
    }
}
