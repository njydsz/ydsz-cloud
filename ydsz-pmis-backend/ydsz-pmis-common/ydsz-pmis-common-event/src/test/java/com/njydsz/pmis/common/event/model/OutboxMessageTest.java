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
    @DisplayName("未设置优先级时为 null（由 OutboxService 填充默认值）")
    void priority_unset_isNull() {
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

        assertNull(msg.getPriority());
    }

    @Test
    @DisplayName("显式设置优先级 0 时保留 0（不覆盖为默认值）")
    void priority_zero_isPreserved() {
        OutboxMessage msg = OutboxMessage.builder()
                .id("test-id")
                .aggregateType("Order")
                .aggregateId("order-001")
                .eventType("OrderCreated")
                .payload("{}")
                .status(OutboxStatus.PENDING)
                .maxRetries(3)
                .priority(0)
                .nextRetryAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        assertEquals(0, msg.getPriority());
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

    @Test
    @DisplayName("DEFAULT_PRIORITY 常量值为 5")
    void defaultPriorityConstant() {
        assertEquals(5, OutboxMessage.DEFAULT_PRIORITY);
    }
}
