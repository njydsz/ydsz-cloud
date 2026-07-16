package com.njydsz.pmis.common.queue.resilience;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 队列熔断器测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
class QueueCircuitBreakerTest {

    @Test
    void testInitialStateIsClosed() {
        QueueCircuitBreaker cb = new QueueCircuitBreaker("test", 3, 1000);
        assertEquals(QueueCircuitBreaker.State.CLOSED, cb.getState());
        assertTrue(cb.allowRequest());
        assertEquals(0, cb.getConsecutiveFailures());
    }

    @Test
    void testOpenAfterThresholdFailures() {
        QueueCircuitBreaker cb = new QueueCircuitBreaker("test", 3, 10000);
        cb.recordFailure();
        cb.recordFailure();
        assertTrue(cb.allowRequest());
        assertEquals(QueueCircuitBreaker.State.CLOSED, cb.getState());
        cb.recordFailure();
        assertEquals(QueueCircuitBreaker.State.OPEN, cb.getState());
        assertFalse(cb.allowRequest());
    }

    @Test
    void testRecoveryToHalfOpen() throws InterruptedException {
        QueueCircuitBreaker cb = new QueueCircuitBreaker("test", 1, 100);
        cb.recordFailure();
        assertEquals(QueueCircuitBreaker.State.OPEN, cb.getState());
        assertFalse(cb.allowRequest());
        Thread.sleep(120);
        assertTrue(cb.allowRequest());
        assertEquals(QueueCircuitBreaker.State.HALF_OPEN, cb.getState());
    }

    @Test
    void testHalfOpenSuccessClosesCircuit() throws InterruptedException {
        QueueCircuitBreaker cb = new QueueCircuitBreaker("test", 1, 100);
        cb.recordFailure();
        Thread.sleep(120);
        cb.allowRequest();
        assertEquals(QueueCircuitBreaker.State.HALF_OPEN, cb.getState());
        cb.recordSuccess();
        assertEquals(QueueCircuitBreaker.State.CLOSED, cb.getState());
        assertEquals(0, cb.getConsecutiveFailures());
    }

    @Test
    void testHalfOpenFailureReopensCircuit() throws InterruptedException {
        QueueCircuitBreaker cb = new QueueCircuitBreaker("test", 1, 100);
        cb.recordFailure();
        Thread.sleep(120);
        cb.allowRequest();
        assertEquals(QueueCircuitBreaker.State.HALF_OPEN, cb.getState());
        cb.recordFailure();
        assertEquals(QueueCircuitBreaker.State.OPEN, cb.getState());
    }

    @Test
    void testSuccessResetsFailureCount() {
        QueueCircuitBreaker cb = new QueueCircuitBreaker("test", 5, 10000);
        cb.recordFailure();
        cb.recordFailure();
        cb.recordFailure();
        assertEquals(3, cb.getConsecutiveFailures());
        cb.recordSuccess();
        assertEquals(0, cb.getConsecutiveFailures());
        assertEquals(QueueCircuitBreaker.State.CLOSED, cb.getState());
    }

    @Test
    void testManualReset() {
        QueueCircuitBreaker cb = new QueueCircuitBreaker("test", 1, 10000);
        cb.recordFailure();
        assertEquals(QueueCircuitBreaker.State.OPEN, cb.getState());
        cb.reset();
        assertEquals(QueueCircuitBreaker.State.CLOSED, cb.getState());
        assertEquals(0, cb.getConsecutiveFailures());
    }
}
