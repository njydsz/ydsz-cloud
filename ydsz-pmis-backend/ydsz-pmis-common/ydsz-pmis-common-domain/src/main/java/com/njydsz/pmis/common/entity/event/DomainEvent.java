package com.njydsz.pmis.common.entity.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类 —— 所有领域事件的抽象基类。
 * <p>
 * 对标 remi-comm DomainEvent，携带事件标识、时间戳和聚合根 ID。
 * </p>
 *
 * @author njydsz
 * @since 1.0.0
 */
public abstract class DomainEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 事件唯一标识 */
    private final String eventId;

    /** 事件发生时间 */
    private final Instant occurredAt;

    /** 源聚合根 ID */
    private final Serializable aggregateId;

    /** 源聚合根类型名称 */
    private final String aggregateType;

    protected DomainEvent(Serializable aggregateId, String aggregateType) {
        this.eventId = UUID.randomUUID().toString();
        this.occurredAt = Instant.now();
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
    }

    public String getEventId() {
        return eventId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Serializable getAggregateId() {
        return aggregateId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    /**
     * 事件类型名称（默认使用类名，子类可覆盖）。
     */
    public String getEventType() {
        return this.getClass().getSimpleName();
    }
}
