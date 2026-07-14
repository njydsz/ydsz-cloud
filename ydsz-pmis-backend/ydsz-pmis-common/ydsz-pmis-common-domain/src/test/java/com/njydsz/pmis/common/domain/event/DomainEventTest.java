package com.njydsz.pmis.common.domain.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DomainEvent 单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("DomainEvent 领域事件测试")
class DomainEventTest {

    @Test
    @DisplayName("Builder 创建事件应自动填充 eventId 和 occurredAt")
    void shouldAutoFillEventIdAndOccurredAt() {
        DomainEvent event = DomainEvent.builder()
                .eventType("OrderCreated")
                .build();
        assertNotNull(event.getEventId());
        assertNotNull(event.getOccurredAt());
        assertEquals("OrderCreated", event.getEventType());
    }

    @Test
    @DisplayName("Builder 应支持设置聚合根信息")
    void shouldSupportAggregateInfo() {
        DomainEvent event = DomainEvent.builder()
                .eventType("OrderCreated")
                .aggregateId("order-123")
                .aggregateType("Order")
                .version(1)
                .build();
        assertEquals("order-123", event.getAggregateId());
        assertEquals("Order", event.getAggregateType());
        assertEquals(1, event.getVersion());
    }

    @Test
    @DisplayName("Builder 应支持添加元数据")
    void shouldSupportMetadata() {
        DomainEvent event = DomainEvent.builder()
                .eventType("OrderCreated")
                .metadata("source", "API")
                .metadata("ip", "192.168.1.1")
                .build();
        assertEquals("API", event.getMetadata("source"));
        assertEquals("192.168.1.1", event.getMetadata("ip"));
    }

    @Test
    @DisplayName("metadata 应为不可变 Map")
    void shouldReturnImmutableMetadata() {
        DomainEvent event = DomainEvent.builder()
                .eventType("Test")
                .metadata("key", "value")
                .build();
        Map<String, Object> metadata = event.getMetadata();
        assertEquals(1, metadata.size());
    }

    @Test
    @DisplayName("eventType 为空时应抛出 IllegalArgumentException")
    void shouldThrowWhenEventTypeIsNull() {
        try {
            DomainEvent.builder().build();
            assertTrue(false, "Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("eventType"));
        }
    }

    @Test
    @DisplayName("equals 应基于 eventId 判断")
    void shouldEqualsBasedOnEventId() {
        DomainEvent event1 = DomainEvent.builder()
                .eventId("evt-001")
                .eventType("Test")
                .build();
        DomainEvent event2 = DomainEvent.builder()
                .eventId("evt-001")
                .eventType("DifferentType")
                .build();
        assertEquals(event1, event2);
        assertEquals(event1.hashCode(), event2.hashCode());
    }

    @Test
    @DisplayName("构造器应正确创建带聚合根信息的事件")
    void shouldCreateEventWithAggregateInfo() {
        DomainEvent event = new DomainEvent("OrderCreated", "order-1", "Order");
        assertEquals("OrderCreated", event.getEventType());
        assertEquals("order-1", event.getAggregateId());
        assertEquals("Order", event.getAggregateType());
    }

    @Test
    @DisplayName("新事件的 metadata 应为空 Map 而非 null")
    void shouldHaveEmptyMetadataForNewEvent() {
        DomainEvent event = new DomainEvent("Test");
        assertNotNull(event.getMetadata());
        assertTrue(event.getMetadata().isEmpty());
    }

    @Test
    @DisplayName("新事件的 tenantId/userId/traceId 应为 null（无 RequestContext 时）")
    void shouldHaveNullContextFieldsWithoutRequestContext() {
        DomainEvent event = new DomainEvent("Test");
        assertNull(event.getTenantId());
        assertNull(event.getUserId());
        assertNull(event.getTraceId());
    }
}
