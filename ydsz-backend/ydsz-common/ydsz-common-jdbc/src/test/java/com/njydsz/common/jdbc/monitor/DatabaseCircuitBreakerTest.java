package com.njydsz.common.jdbc.monitor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据库操作熔断器测试
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("DatabaseCircuitBreaker - 数据库熔断器测试")
class DatabaseCircuitBreakerTest {

    @Test
    @DisplayName("初始状态应为 CLOSED，允许请求通过")
    void shouldStartInClosedState() {
        DatabaseCircuitBreaker breaker = new DatabaseCircuitBreaker(5, 1000, 2);
        assertEquals(DatabaseCircuitBreaker.State.CLOSED, breaker.getState());
        assertTrue(breaker.tryAcquire());
    }

    @Test
    @DisplayName("连续失败达到阈值后应进入 OPEN 状态")
    void shouldOpenAfterFailureThreshold() {
        DatabaseCircuitBreaker breaker = new DatabaseCircuitBreaker(3, 10000, 2);
        breaker.recordFailure();
        breaker.recordFailure();
        assertEquals(DatabaseCircuitBreaker.State.CLOSED, breaker.getState());
        breaker.recordFailure();
        assertEquals(DatabaseCircuitBreaker.State.OPEN, breaker.getState());
        assertFalse(breaker.tryAcquire());
    }

    @Test
    @DisplayName("CLOSED 状态下成功应重置失败计数")
    void shouldResetFailuresOnSuccess() {
        DatabaseCircuitBreaker breaker = new DatabaseCircuitBreaker(3, 10000, 2);
        breaker.recordFailure();
        breaker.recordFailure();
        breaker.recordSuccess();
        assertEquals(0, breaker.getConsecutiveFailures());
        assertEquals(DatabaseCircuitBreaker.State.CLOSED, breaker.getState());
    }

    @Test
    @DisplayName("OPEN 状态超时后应进入 HALF_OPEN 状态")
    void shouldTransitionToHalfOpenAfterTimeout() throws InterruptedException {
        DatabaseCircuitBreaker breaker = new DatabaseCircuitBreaker(2, 100, 2);
        breaker.recordFailure();
        breaker.recordFailure();
        assertEquals(DatabaseCircuitBreaker.State.OPEN, breaker.getState());
        assertFalse(breaker.tryAcquire());

        // 等待超时
        Thread.sleep(150);

        // 应该进入 HALF_OPEN 并允许探测请求
        assertTrue(breaker.tryAcquire());
        assertEquals(DatabaseCircuitBreaker.State.HALF_OPEN, breaker.getState());
    }

    @Test
    @DisplayName("HALF_OPEN 状态下成功应恢复为 CLOSED")
    void shouldRecoverFromHalfOpenOnSuccess() throws InterruptedException {
        DatabaseCircuitBreaker breaker = new DatabaseCircuitBreaker(2, 50, 2);
        breaker.recordFailure();
        breaker.recordFailure();
        Thread.sleep(80);
        breaker.tryAcquire(); // 触发 HALF_OPEN
        breaker.recordSuccess();
        assertEquals(DatabaseCircuitBreaker.State.CLOSED, breaker.getState());
    }

    @Test
    @DisplayName("HALF_OPEN 状态下失败应重新进入 OPEN")
    void shouldReopenFromHalfOpenOnFailure() throws InterruptedException {
        DatabaseCircuitBreaker breaker = new DatabaseCircuitBreaker(2, 50, 2);
        breaker.recordFailure();
        breaker.recordFailure();
        Thread.sleep(80);
        breaker.tryAcquire(); // 触发 HALF_OPEN
        breaker.recordFailure();
        assertEquals(DatabaseCircuitBreaker.State.OPEN, breaker.getState());
    }

    @Test
    @DisplayName("HALF_OPEN 状态应限制探测请求数量")
    void shouldLimitProbeRequestsInHalfOpen() throws InterruptedException {
        DatabaseCircuitBreaker breaker = new DatabaseCircuitBreaker(1, 50, 2);
        breaker.recordFailure();
        assertEquals(DatabaseCircuitBreaker.State.OPEN, breaker.getState());
        Thread.sleep(80);

        // 半开状态下只允许 2 个探测请求
        assertTrue(breaker.tryAcquire());
        assertTrue(breaker.tryAcquire());
        assertFalse(breaker.tryAcquire());
    }
}
