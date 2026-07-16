package com.njydsz.common.sentry.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CircuitBreaker 单元测试
 *
 * @author ydsz-team
 * @since 1.5.0
 */
@DisplayName("CircuitBreaker 状态流转测试")
class CircuitBreakerTest {

    @Test
    @DisplayName("CLOSED 状态下正常执行操作")
    void shouldExecuteInClosedState() {
        CircuitBreaker cb = new CircuitBreaker("test", 0.5, 10, 1000);
        String result = cb.execute(() -> "success", () -> "fallback");
        assertThat(result).isEqualTo("success");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("操作失败时执行降级")
    void shouldFallbackOnFailure() {
        CircuitBreaker cb = new CircuitBreaker("test", 0.5, 10, 1000);
        String result = cb.execute(() -> {
            throw new RuntimeException("boom");
        }, () -> "fallback");
        assertThat(result).isEqualTo("fallback");
        assertThat(cb.getFailureCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("失败率超过阈值时触发熔断")
    void shouldOpenOnHighFailureRate() {
        CircuitBreaker cb = new CircuitBreaker("test", 0.3, 5, 1000);
        for (int i = 0; i < 4; i++) {
            cb.execute(() -> {
                throw new RuntimeException("fail");
            }, () -> "fallback");
        }
        cb.execute(() -> "success", () -> "fallback");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("OPEN 状态下执行降级")
    void shouldFallbackWhenOpen() {
        CircuitBreaker cb = new CircuitBreaker("test", 0.3, 2, 10000);
        cb.execute(() -> {
            throw new RuntimeException("fail");
        }, () -> "fallback");
        cb.execute(() -> {
            throw new RuntimeException("fail");
        }, () -> "fallback");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        String result = cb.execute(() -> "should-not-reach", () -> "fallback");
        assertThat(result).isEqualTo("fallback");
    }

    @Test
    @DisplayName("OPEN 超过半开时间后转为 HALF_OPEN")
    void shouldTransitionToHalfOpen() throws InterruptedException {
        CircuitBreaker cb = new CircuitBreaker("test", 0.5, 2, 100);
        cb.execute(() -> {
            throw new RuntimeException("fail");
        }, () -> "fallback");
        cb.execute(() -> {
            throw new RuntimeException("fail");
        }, () -> "fallback");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        Thread.sleep(150);
        String result = cb.execute(() -> "recovered", () -> "fallback");
        assertThat(result).isEqualTo("recovered");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("HALF_OPEN 探测失败恢复 OPEN")
    void shouldReopenOnHalfOpenFailure() throws InterruptedException {
        CircuitBreaker cb = new CircuitBreaker("test", 0.5, 2, 100);
        cb.execute(() -> {
            throw new RuntimeException("fail");
        }, () -> "fallback");
        cb.execute(() -> {
            throw new RuntimeException("fail");
        }, () -> "fallback");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        Thread.sleep(150);
        String result = cb.execute(() -> {
            throw new RuntimeException("still failing");
        }, () -> "fallback");
        assertThat(result).isEqualTo("fallback");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("并发场景下 HALF_OPEN 仅允许单个探测")
    void shouldAllowSingleProbeInHalfOpen() throws InterruptedException {
        CircuitBreaker cb = new CircuitBreaker("test", 0.5, 2, 50);
        cb.execute(() -> {
            throw new RuntimeException("fail");
        }, () -> "fallback");
        cb.execute(() -> {
            throw new RuntimeException("fail");
        }, () -> "fallback");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        Thread.sleep(100);
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger fallbackCount = new AtomicInteger(0);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    String result = cb.execute(() -> {
                        try { Thread.sleep(50); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        return "success";
                    }, () -> "fallback");
                    if ("success".equals(result)) {
                        successCount.incrementAndGet();
                    } else {
                        fallbackCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    fallbackCount.incrementAndGet();
                }
            });
        }
        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        assertThat(successCount.get()).isLessThanOrEqualTo(1);
        assertThat(fallbackCount.get()).isGreaterThanOrEqualTo(threadCount - 1);
    }
}
