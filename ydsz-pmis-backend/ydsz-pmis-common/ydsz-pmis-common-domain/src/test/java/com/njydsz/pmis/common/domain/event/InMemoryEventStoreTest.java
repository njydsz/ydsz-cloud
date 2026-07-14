package com.njydsz.pmis.common.domain.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * InMemoryEventStore 单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("InMemoryEventStore 内存事件存储测试")
class InMemoryEventStoreTest {

    private InMemoryEventStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryEventStore();
    }

    @Test
    @DisplayName("append 后应能通过 findById 查到")
    void shouldFindByIdAfterAppend() {
        DomainEvent event = DomainEvent.builder()
                .eventId("evt-001")
                .eventType("OrderCreated")
                .aggregateId("order-1")
                .aggregateType("Order")
                .build();
        store.append(event);
        Optional<DomainEvent> found = store.findById("evt-001");
        assertTrue(found.isPresent());
        assertEquals("OrderCreated", found.get().getEventType());
    }

    @Test
    @DisplayName("findByAggregate 应返回该聚合根的所有事件")
    void shouldFindByAggregate() {
        store.append(DomainEvent.builder()
                .eventId("e1").eventType("Created")
                .aggregateId("agg-1").aggregateType("Order").version(1).build());
        store.append(DomainEvent.builder()
                .eventId("e2").eventType("Updated")
                .aggregateId("agg-1").aggregateType("Order").version(2).build());
        store.append(DomainEvent.builder()
                .eventId("e3").eventType("Created")
                .aggregateId("agg-2").aggregateType("Order").version(1).build());

        List<DomainEvent> events = store.findByAggregate("agg-1", "Order");
        assertEquals(2, events.size());
    }

    @Test
    @DisplayName("findByType 应返回指定类型的所有事件")
    void shouldFindByType() {
        store.append(DomainEvent.builder()
                .eventId("e1").eventType("Created").build());
        store.append(DomainEvent.builder()
                .eventId("e2").eventType("Updated").build());
        store.append(DomainEvent.builder()
                .eventId("e3").eventType("Created").build());

        List<DomainEvent> events = store.findByType("Created");
        assertEquals(2, events.size());
    }

    @Test
    @DisplayName("getLatestVersion 应返回最大版本号")
    void shouldReturnLatestVersion() {
        store.append(DomainEvent.builder()
                .eventId("e1").eventType("Created")
                .aggregateId("agg-1").aggregateType("Order").version(1).build());
        store.append(DomainEvent.builder()
                .eventId("e2").eventType("Updated")
                .aggregateId("agg-1").aggregateType("Order").version(5).build());
        store.append(DomainEvent.builder()
                .eventId("e3").eventType("Updated")
                .aggregateId("agg-1").aggregateType("Order").version(3).build());

        assertEquals(5, store.getLatestVersion("agg-1", "Order"));
    }

    @Test
    @DisplayName("无事件时 getLatestVersion 应返回 0")
    void shouldReturnZeroWhenNoEvents() {
        assertEquals(0, store.getLatestVersion("nonexistent", "Unknown"));
    }

    @Test
    @DisplayName("appendAll 应批量存储事件")
    void shouldAppendAll() {
        List<DomainEvent> events = List.of(
                DomainEvent.builder().eventId("e1").eventType("A").build(),
                DomainEvent.builder().eventId("e2").eventType("B").build(),
                DomainEvent.builder().eventId("e3").eventType("C").build());
        store.appendAll(events);
        assertEquals(3, store.size());
    }

    @Test
    @DisplayName("clear 应清空所有事件")
    void shouldClearAllEvents() {
        store.append(DomainEvent.builder().eventId("e1").eventType("A").build());
        store.append(DomainEvent.builder().eventId("e2").eventType("B").build());
        assertEquals(2, store.size());
        store.clear();
        assertEquals(0, store.size());
    }
}
