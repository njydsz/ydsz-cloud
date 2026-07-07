package com.njydsz.pmis.message.metric;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * {@link MessageMetrics} 单元测试。
 *
 * <p>使用 {@link SimpleMeterRegistry} 验证各记录方法不抛异常。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("MessageMetrics 监控指标测试")
class MessageMetricsTest {

    @Test
    @DisplayName("recordSend 不抛异常并记录计数与耗时")
    void recordSendShouldNotThrow() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MessageMetrics metrics = new MessageMetrics(registry);

        assertDoesNotThrow(() -> metrics.recordSend("SMS", "SUCCESS", 50L));
        assertDoesNotThrow(() -> metrics.recordSend("EMAIL", "FAILED", 10L));
    }

    @Test
    @DisplayName("recordRetry 不抛异常")
    void recordRetryShouldNotThrow() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MessageMetrics metrics = new MessageMetrics(registry);
        assertDoesNotThrow(() -> metrics.recordRetry("SMS"));
    }

    @Test
    @DisplayName("recordDead 不抛异常")
    void recordDeadShouldNotThrow() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MessageMetrics metrics = new MessageMetrics(registry);
        assertDoesNotThrow(() -> metrics.recordDead("SMS"));
    }

    @Test
    @DisplayName("recordReceipt 不抛异常")
    void recordReceiptShouldNotThrow() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MessageMetrics metrics = new MessageMetrics(registry);
        assertDoesNotThrow(() -> metrics.recordReceipt("SMS", "DELIVERED"));
    }
}
