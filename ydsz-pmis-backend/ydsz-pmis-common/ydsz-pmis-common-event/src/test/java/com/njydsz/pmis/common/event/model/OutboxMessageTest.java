package com.njydsz.pmis.common.event.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * OutboxMessage 单元测试
 *
 * @author Marvin Lee
 * @since 1.0.0
 */
@DisplayName("OutboxMessage 实体测试")
class OutboxMessageTest {

    @Test
    @DisplayName("markAsSent 应设置 SENT 状态和 sentAt 时间")
    void markAsSent_shouldSetSentStatus() {
        OutboxMessage message = createMessage(3);
        message.markAsSent();

        assertEquals(OutboxStatus.SENT, message.getStatus());
        assertNotNull(message.getSentAt());
        assertNull(message.getErrorMessage());
    }

    @Test
    @DisplayName("markAsFailed 在未超过最大重试次数时应保持 PENDING")
    void markAsFailed_withinMaxRetries_shouldStayPending() {
        OutboxMessage message = createMessage(3);
        message.markAsFailed("connection error", 30);

        assertEquals(OutboxStatus.PENDING, message.getStatus());
        assertEquals(1, message.getRetryCount());
        assertEquals("connection error", message.getErrorMessage());
        assertNotNull(message.getNextRetryAt());
    }

    @Test
    @DisplayName("markAsFailed 超过最大重试次数时应标记为 DEAD_LETTER")
    void markAsFailed_exceedMaxRetries_shouldBeDeadLetter() {
        OutboxMessage message = createMessage(2);
        message.markAsFailed("error1", 10);
        message.markAsFailed("error2", 20);

        assertEquals(OutboxStatus.DEAD_LETTER, message.getStatus());
        assertEquals(2, message.getRetryCount());
        assertEquals("error2", message.getErrorMessage());
    }

    @Test
    @DisplayName("Builder 应正确构建所有字段")
    void builder_shouldSetAllFields() {
        Instant now = Instant.now();
        OutboxMessage message = OutboxMessage.builder()
                .id("msg-001")
                .aggregateId("order-001")
                .aggregateType("Order")
                .eventType("OrderCreated")
                .payload("{\"id\":\"order-001\"}")
                .headers(Map.of("source", "web"))
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .maxRetries(5)
                .nextRetryAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertEquals("msg-001", message.getId());
        assertEquals("order-001", message.getAggregateId());
        assertEquals("Order", message.getAggregateType());
        assertEquals("OrderCreated", message.getEventType());
        assertEquals("{\"id\":\"order-001\"}", message.getPayload());
        assertEquals(1, message.getHeaders().size());
        assertEquals("web", message.getHeaders().get("source"));
        assertEquals(OutboxStatus.PENDING, message.getStatus());
        assertEquals(0, message.getRetryCount());
        assertEquals(5, message.getMaxRetries());
    }

    private OutboxMessage createMessage(int maxRetries) {
        return OutboxMessage.builder()
                .id("test-msg")
                .aggregateId("agg-1")
                .aggregateType("Test")
                .eventType("TestEvent")
                .payload("{}")
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .maxRetries(maxRetries)
                .nextRetryAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
