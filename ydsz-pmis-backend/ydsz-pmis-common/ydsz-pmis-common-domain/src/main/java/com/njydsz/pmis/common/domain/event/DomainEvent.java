package com.njydsz.pmis.common.domain.event;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 领域事件基类
 *
 * <p>在领域驱动设计（DDD）中，领域事件（Domain Event）表示领域中已经发生的。
 * 具有业务含义的重要事情。领域事件用于实现聚合之间的解耦通信。
 * 以及将副作用从核心业务逻辑中分离出来。
 *
 * <p><b>核心语义：</b>
 * <ul>
 *   <li><b>已发生的事实：</b>领域事件描述的是"已经发生的事。，命名应使用过去时。</li>
 *   <li><b>不可变性：</b>领域事件一旦创建，其状态不可改。</li>
 *   <li><b>业务含义：</b>领域事件应表达明确的业务语义，而非技术细。</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * public class OrderCreatedEvent extends DomainEvent {
 *     private final Long orderId;
 *     private final String orderNo;
 *     private final BigDecimal totalAmount;
 *
 *     public OrderCreatedEvent(Long orderId, String orderNo, BigDecimal totalAmount) {
 *         super("OrderCreated");
 *         this.orderId = orderId;
 *         this.orderNo = orderNo;
 *         this.totalAmount = totalAmount;
 *     }
 * }
 *
 * // 使用 Builder 创建
 * DomainEvent event = DomainEvent.builder()
 *     .eventType("OrderCreated")
 *     .aggregateId("order-123")
 *     .aggregateType("Order")
 *     .version(1)
 *     .build();
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
public class DomainEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 事件唯一标识
     */
    private final String eventId;

    /**
     * 事件发生时间
     */
    private final LocalDateTime occurredAt;

    /**
     * 事件类型
     */
    private final String eventType;

    /**
     * 聚合根ID
     */
    private final String aggregateId;

    /**
     * 聚合根类型
     */
    private final String aggregateType;

    /**
     * 事件版本号（用于事件溯源。
     */
    private final int version;

    /**
     * 构造领域事。
     *
     * @param eventType 事件类型
     */
    public DomainEvent(String eventType) {
        this.eventId = UUID.randomUUID().toString();
        this.occurredAt = LocalDateTime.now();
        this.eventType = eventType;
        this.aggregateId = null;
        this.aggregateType = null;
        this.version = 1;
    }

    /**
     * 构造领域事件（指定事件ID和发生时间）
     *
     * @param eventId    事件唯一标识
     * @param occurredAt 事件发生时间
     * @param eventType  事件类型
     */
    public DomainEvent(String eventId, LocalDateTime occurredAt, String eventType) {
        this.eventId = eventId;
        this.occurredAt = occurredAt;
        this.eventType = eventType;
        this.aggregateId = null;
        this.aggregateType = null;
        this.version = 1;
    }

    /**
     * 构造领域事件（包含聚合根关联信息）
     *
     * @param eventType     事件类型
     * @param aggregateId   聚合根ID
     * @param aggregateType 聚合根类型
     */
    public DomainEvent(String eventType, String aggregateId, String aggregateType) {
        this.eventId = UUID.randomUUID().toString();
        this.occurredAt = LocalDateTime.now();
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.version = 1;
    }

    /**
     * 构造领域事件（完整参数。
     *
     * @param eventId       事件唯一标识
     * @param occurredAt    事件发生时间
     * @param eventType     事件类型
     * @param aggregateId   聚合根ID
     * @param aggregateType 聚合根类型
     * @param version       事件版本。
     */
    public DomainEvent(String eventId, LocalDateTime occurredAt, String eventType,
                       String aggregateId, String aggregateType, int version) {
        this.eventId = eventId;
        this.occurredAt = occurredAt;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.version = version;
    }

    /**
     * 工厂方法：创建领域事。
     *
     * @param eventType 事件类型
     * @return 领域事件实例
     */
    public static DomainEvent of(String eventType) {
        return new DomainEvent(eventType);
    }

    /**
     * 工厂方法：创建领域事件（指定事件ID和发生时间）
     *
     * @param eventId    事件唯一标识
     * @param occurredAt 事件发生时间
     * @param eventType  事件类型
     * @return 领域事件实例
     */
    public static DomainEvent of(String eventId, LocalDateTime occurredAt, String eventType) {
        return new DomainEvent(eventId, occurredAt, eventType);
    }

    /**
     * 工厂方法：创建领域事件（包含聚合根关联信息）
     *
     * @param eventType     事件类型
     * @param aggregateId   聚合根ID
     * @param aggregateType 聚合根类型
     * @return 领域事件实例
     */
    public static DomainEvent of(String eventType, String aggregateId, String aggregateType) {
        return new DomainEvent(eventType, aggregateId, aggregateType);
    }

    /**
     * 获取 Builder 实例
     *
     * @return Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }

    public String getEventId() {
        return eventId;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public String getEventType() {
        return eventType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public int getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DomainEvent that = (DomainEvent) o;
        return Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }

    @Override
    public String toString() {
        return "DomainEvent{" +
                "eventId='" + eventId + '\'' +
                ", occurredAt=" + occurredAt +
                ", eventType='" + eventType + '\'' +
                ", aggregateId='" + aggregateId + '\'' +
                ", aggregateType='" + aggregateType + '\'' +
                ", version=" + version +
                '}';
    }

    /**
     * DomainEvent 构建。
     *
     * <p>提供链式调用方式创建不可变的领域事件。
     */
    public static class Builder {
        private String eventId;
        private LocalDateTime occurredAt;
        private String eventType;
        private String aggregateId;
        private String aggregateType;
        private int version = 1;

        private Builder() {
        }

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder occurredAt(LocalDateTime occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        /**
         * 设置事件类型
         *
         * @param eventType 事件类型
         * @return 当前 Builder
         */
        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        /**
         * 设置聚合根ID
         *
         * @param aggregateId 聚合根ID
         * @return 当前 Builder
         */
        public Builder aggregateId(String aggregateId) {
            this.aggregateId = aggregateId;
            return this;
        }

        /**
         * 设置聚合根类型
         *
         * @param aggregateType 聚合根类型
         * @return 当前 Builder
         */
        public Builder aggregateType(String aggregateType) {
            this.aggregateType = aggregateType;
            return this;
        }

        public Builder version(int version) {
            this.version = version;
            return this;
        }

        public DomainEvent build() {
            if (eventType == null || eventType.isEmpty()) {
                throw new IllegalArgumentException("eventType must not be null or empty");
            }
            String eid = eventId != null ? eventId : UUID.randomUUID().toString();
            LocalDateTime occurred = occurredAt != null ? occurredAt : LocalDateTime.now();
            return new DomainEvent(eid, occurred, eventType, aggregateId, aggregateType, version);
        }
    }
}
