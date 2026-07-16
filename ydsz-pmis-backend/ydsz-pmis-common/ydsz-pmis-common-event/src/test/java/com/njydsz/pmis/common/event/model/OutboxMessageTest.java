package com.njydsz.pmis.common.event.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link OutboxMessage} 单元测试。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("OutboxMessage 测试")
class OutboxMessageTest {

    @Test
    @DisplayName("markAsSent 设置状态为 SENT")
    void markAsSent_setsSentStatus() {
        OutboxMessage msg = buildMessage();
        msg.markAsSent();

        assertEquals(OutboxStatus.SENT, msg.getStatus());
        assertNotNull(msg.getSentAt());
        assertNull(msg.getErrorMessage());
    }

    @Test
    @DisplayName("markAsFailed 未超重试次数时状态为 PENDING")
    void markAsFailed_underMaxRetries() {
        OutboxMessage msg = buildMessage();
        msg.markAsFailed("network error", 10);

        assertEquals(OutboxStatus.PENDING, msg.getStatus());
        assertEquals(1, msg.getRetryCount());
        assertEquals("network error", msg.getErrorMessage());
        assertNotNull(msg.getNextRetryAt());
    }

    @Test
    @DisplayName("markAsFailed 超过最大重试次数时状态为 DEAD_LETTER")
    void markAsFailed_exceedsMaxRetries() {
        OutboxMessage msg = buildMessage();
        msg.markAsFailed("error 1", 10);
        msg.markAsFailed("error 2", 20);
        msg.markAsFailed("error 3", 40);

        assertEquals(OutboxStatus.DEAD_LETTER, msg.getStatus());
        assertEquals(3, msg.getRetryCount());
    }

    @Test
    @DisplayName("markAsProcessing 设置状态为 PROCESSING")
    void markAsProcessing_setsProcessingStatus() {
        OutboxMessage msg = buildMessage();
        msg.markAsProcessing();

        assertEquals(OutboxStatus.PROCESSING, msg.getStatus());
        assertNotNull(msg.getUpdatedAt());
    }

    @Test
    @DisplayName("默认优先级为 5")
    void defaultPriority_is5() {
        OutboxMessage msg = OutboxMessage.builder()
                .id("test-id")
                .aggregateType("Order")
                .aggregateId("order-001")
                .eventType("OrderCreated")
                .payload("{}")
                .status(OutboxStatus.PENDING)
                .maxRetries(3)
                .nextRetryAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        assertEquals(OutboxMessage.DEFAULT_PRIORITY, msg.getPriority());
        assertEquals(5, msg.getPriority());
    }

    @Test
    @DisplayName("自定义优先级生效")
    void customPriority() {
        OutboxMessage msg = OutboxMessage.builder()
                .id("test-id")
                .aggregateType("Order")
                .aggregateId("order-001")
                .eventType("OrderCreated")
                .payload("{}")
                .status(OutboxStatus.PENDING)
                .maxRetries(3)
                .priority(9)
                .nextRetryAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        assertEquals(9, msg.getPriority());
    }

    private OutboxMessage buildMessage() {
        return OutboxMessage.builder()
                .id("test-id")
                .aggregateType("Order")
                .aggregateId("order-001")
                .eventType("OrderCreated")
                .payload("{\"id\":1}")
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .maxRetries(3)
                .nextRetryAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
