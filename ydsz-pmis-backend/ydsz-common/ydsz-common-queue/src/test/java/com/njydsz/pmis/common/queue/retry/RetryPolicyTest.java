package com.njydsz.common.queue.retry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 重试策略测试
 *
 * @author ydsz-team
 * @since 1.0.0
 */
class RetryPolicyTest {

    @Test
    void testExponentialBackoff() {
        RetryPolicy policy = RetryPolicy.exponentialBackoff(3, 1000, 30000);
        assertTrue(policy.canRetry(0));
        assertTrue(policy.canRetry(2));
        assertFalse(policy.canRetry(3));
        assertEquals(1000, policy.getDelayMillis(0));
        assertEquals(2000, policy.getDelayMillis(1));
        assertEquals(4000, policy.getDelayMillis(2));
        assertEquals(3, policy.getMaxAttempts());
    }

    @Test
    void testExponentialBackoffMaxDelay() {
        RetryPolicy policy = RetryPolicy.exponentialBackoff(10, 1000, 5000);
        assertEquals(1000, policy.getDelayMillis(0));
        assertEquals(2000, policy.getDelayMillis(1));
        assertEquals(4000, policy.getDelayMillis(2));
        assertEquals(5000, policy.getDelayMillis(3));
        assertEquals(5000, policy.getDelayMillis(9));
    }

    @Test
    void testFixedInterval() {
        RetryPolicy policy = RetryPolicy.fixedInterval(5, 2000);
        assertTrue(policy.canRetry(0));
        assertTrue(policy.canRetry(4));
        assertFalse(policy.canRetry(5));
        assertEquals(2000, policy.getDelayMillis(0));
        assertEquals(2000, policy.getDelayMillis(4));
        assertEquals(5, policy.getMaxAttempts());
    }

    @Test
    void testRetryStateTracking() {
        RetryPolicy policy = RetryPolicy.exponentialBackoff(3, 1000, 30000);
        RetryPolicy.RetryState state = policy.createState();

        assertTrue(state.canRetry());
        assertEquals(0, state.getAttemptCount());

        assertTrue(state.tryIncrement());
        assertEquals(1, state.getAttemptCount());
        assertTrue(state.canRetry());

        assertTrue(state.tryIncrement());
        assertEquals(2, state.getAttemptCount());
        assertTrue(state.canRetry());

        assertTrue(state.tryIncrement());
        assertEquals(3, state.getAttemptCount());
        assertFalse(state.canRetry());
        assertFalse(state.tryIncrement());
        assertTrue(state.isExhausted());
    }

    @Test
    void testRetryStateSuccess() {
        RetryPolicy policy = RetryPolicy.fixedInterval(3, 1000);
        RetryPolicy.RetryState state = policy.createState();

        state.tryIncrement();
        state.markSuccess();

        assertTrue(state.isSuccess());
        assertFalse(state.canRetry());
        assertFalse(state.isExhausted());
    }

    @Test
    void testInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> RetryPolicy.exponentialBackoff(-1, 1000, 30000));
        assertThrows(IllegalArgumentException.class, () -> RetryPolicy.exponentialBackoff(3, 0, 30000));
        assertThrows(IllegalArgumentException.class, () -> RetryPolicy.exponentialBackoff(3, 1000, 500));
        assertThrows(IllegalArgumentException.class, () -> RetryPolicy.fixedInterval(-1, 1000));
        assertThrows(IllegalArgumentException.class, () -> RetryPolicy.fixedInterval(3, 0));
    }
}
