package com.njydsz.pmis.common.event.service;

import java.util.List;
import java.util.Optional;

import com.njydsz.pmis.common.domain.event.DomainEvent;
import com.njydsz.pmis.common.domain.event.EventStore;

/**
 * 基于 Outbox 的领域事件存储适配器
 *
 * <p>实现 {@link EventStore} SPI 接口，将 {@code append} 操作委托给 {@link OutboxService}，
 * 实现领域事件与 Outbox 表的无缝集成。
 *
 * <p><b>设计说明：</b>
 * <ul>
 *   <li>Outbox 模式专注于可靠投递（forward-only），不提供事件回放能力</li>
 *   <li>{@code append} / {@code appendAll} 委托给 {@link OutboxService#appendToOutbox(DomainEvent)}</li>
 *   <li>查询方法（findByAggregate / findByType / findById / getLatestVersion）抛出
 *       {@link UnsupportedOperationException}，如需事件回放请实现独立的 EventStore</li>
 * </ul>
 *
 * <p>当容器中不存在其他 {@link EventStore} 实现时，由 {@code EventAutoConfiguration}
 * 自动注册此适配器作为默认实现。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class OutboxEventStore implements EventStore {

    private final OutboxService outboxService;

    /**
     * @param outboxService Outbox 写入服务
     */
    public OutboxEventStore(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @Override
    public void append(DomainEvent event) {
        outboxService.appendToOutbox(event);
    }

    @Override
    public void appendAll(List<DomainEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        for (DomainEvent event : events) {
            outboxService.appendToOutbox(event);
        }
    }

    @Override
    public List<DomainEvent> findByAggregate(String aggregateId, String aggregateType) {
        throw new UnsupportedOperationException(
                "OutboxEventStore is a forward-only delivery store, "
                        + "event replay is not supported. "
                        + "Implement a dedicated EventStore for event sourcing.");
    }

    @Override
    public List<DomainEvent> findByType(String eventType) {
        throw new UnsupportedOperationException(
                "OutboxEventStore is a forward-only delivery store, "
                        + "event replay is not supported. "
                        + "Implement a dedicated EventStore for event sourcing.");
    }

    @Override
    public Optional<DomainEvent> findById(String eventId) {
        throw new UnsupportedOperationException(
                "OutboxEventStore is a forward-only delivery store, "
                        + "event replay is not supported. "
                        + "Implement a dedicated EventStore for event sourcing.");
    }

    @Override
    public int getLatestVersion(String aggregateId, String aggregateType) {
        throw new UnsupportedOperationException(
                "OutboxEventStore is a forward-only delivery store, "
                        + "event replay is not supported. "
                        + "Implement a dedicated EventStore for event sourcing.");
    }
}
