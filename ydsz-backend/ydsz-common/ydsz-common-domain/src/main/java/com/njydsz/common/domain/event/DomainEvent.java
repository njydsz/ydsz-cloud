package com.njydsz.common.domain.event;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.context.ApplicationEvent;

import com.njydsz.common.core.context.RequestContext;

/**
 * 领域事件基类 — 模块间事件契约的基础。
 *
 * <p>继承 Spring {@link ApplicationEvent}，可直接通过 {@code ApplicationEventPublisher}
 * 发布并由 {@code @EventListener} 消费。在领域驱动设计（DDD）中，领域事件表示
 * 领域中已经发生、具有业务含义的重要事情，用于实现聚合之间的解耦通信。
 *
 * <p><b>核心语义：</b>
 * <ul>
 *   <li><b>已发生的事实：</b>领域事件描述的是"已经发生的事"，命名应使用过去时</li>
 *   <li><b>不可变性：</b>领域事件一旦创建，其状态不可改变</li>
 *   <li><b>业务含义：</b>领域事件应表达明确的业务语义，而非技术细节</li>
 *   <li><b>上下文感知：</b>自动携带租户、用户、追踪等上下文元数据</li>
 *   <li><b>跨模块契约：</b>所有跨模块事件均应继承本类，确保统一的元数据字段</li>
 * </ul>
 *
 * <p><b>P2-1</b>：本类现在继承 {@link ApplicationEvent}，使所有领域事件可直接被
 * Spring 事件系统消费。跨模块事件类型常量定义在 {@link ModuleEventTypes}。
 *
 * <p><b>创建方式：</b>
 * 推荐使用 Builder 模式创建领域事件，自动填充 eventId、occurredAt 和上下文元数据：
 * <pre>{@code
 * DomainEvent event = DomainEvent.builder()
 *     .eventType("OrderCreated")
 *     .aggregateId("order-123")
 *     .aggregateType("Order")
 *     .version(1)
 *     .metadata("source", "API")
 *     .build();
 * }</pre>
 *
 * <p>子类继承时，只需在构造器中调用 {@link #DomainEvent(String)} 即可：
 * <pre>{@code
 * public class OrderCreatedEvent extends DomainEvent {
 *     private final Long orderId;
 *     private final BigDecimal totalAmount;
 *
 *     public OrderCreatedEvent(Long orderId, BigDecimal totalAmount) {
 *         super("OrderCreated");
 *         this.orderId = orderId;
 *         this.totalAmount = totalAmount;
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 */
public class DomainEvent extends ApplicationEvent implements Serializable {

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
     * 事件版本（用于事件溯源）
     */
    private final int version;

    /**
     * 租户ID（自动从 RequestContext 填充）
     */
    private final String tenantId;

    /**
     * 操作人ID（自动从 RequestContext 填充）
     */
    private final String userId;

    /**
     * 链路追踪ID（自动从 RequestContext 填充）
     */
    private final String traceId;

    /**
     * 扩展元数据
     */
    private final Map<String, Object> metadata;

    /**
     * 构造领域事件（子类推荐使用）
     *
     * <p>自动生成 eventId 和 occurredAt，并从 {@link RequestContext} 填充上下文元数据。
     *
     * @param eventType 事件类型
     */
    public DomainEvent(String eventType) {
        this(UUID.randomUUID().toString(), LocalDateTime.now(), eventType,
             null, null, 1,
             RequestContext.getTenantId(), RequestContext.getUserId(), RequestContext.getTraceId(),
             Collections.emptyMap());
    }

    /**
     * 构造领域事件（包含聚合根关联信息，子类可使用）
     *
     * <p>自动生成 eventId 和 occurredAt，并从 {@link RequestContext} 填充上下文元数据。
     *
     * @param eventType     事件类型
     * @param aggregateId   聚合根ID
     * @param aggregateType 聚合根类型
     */
    public DomainEvent(String eventType, String aggregateId, String aggregateType) {
        this(UUID.randomUUID().toString(), LocalDateTime.now(), eventType,
             aggregateId, aggregateType, 1,
             RequestContext.getTenantId(), RequestContext.getUserId(), RequestContext.getTraceId(),
             Collections.emptyMap());
    }

    /**
     * 构造领域事件（全参数，包含上下文元数据）
     *
     * @param eventId       事件唯一标识
     * @param occurredAt    事件发生时间
     * @param eventType     事件类型
     * @param aggregateId   聚合根ID
     * @param aggregateType 聚合根类型
     * @param version       事件版本
     * @param tenantId      租户ID
     * @param userId        操作人ID
     * @param traceId       链路追踪ID
     * @param metadata      扩展元数据
     */
    public DomainEvent(String eventId, LocalDateTime occurredAt, String eventType,
                       String aggregateId, String aggregateType, int version,
                       String tenantId, String userId, String traceId,
                       Map<String, Object> metadata) {
        super(eventType);
        this.eventId = eventId;
        this.occurredAt = occurredAt;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.version = version;
        this.tenantId = tenantId;
        this.userId = userId;
        this.traceId = traceId;
        this.metadata = metadata != null ? Collections.unmodifiableMap(new HashMap<>(metadata)) : Collections.emptyMap();
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

    public String getTenantId() {
        return tenantId;
    }

    public String getUserId() {
        return userId;
    }

    public String getTraceId() {
        return traceId;
    }

    /**
     * 获取扩展元数据（不可变）
     *
     * @return 元数据 Map
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * 获取指定元数据项
     *
     * @param key 元数据键
     * @return 元数据值，不存在返回 null
     */
    public Object getMetadata(String key) {
        return metadata.get(key);
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
                ", tenantId='" + tenantId + '\'' +
                ", userId='" + userId + '\'' +
                ", traceId='" + traceId + '\'' +
                ", metadata=" + metadata +
                '}';
    }

    /**
     * DomainEvent 构建器
     *
     * <p>提供链式调用方式创建不可变的领域事件。
     * 默认自动填充 eventId、occurredAt 和上下文元数据。
     */
    public static class Builder {
        private String eventId;
        private LocalDateTime occurredAt;
        private String eventType;
        private String aggregateId;
        private String aggregateType;
        private int version = 1;
        private String tenantId;
        private String userId;
        private String traceId;
        private final Map<String, Object> metadata = new HashMap<>();

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

        /**
         * 设置租户ID（覆盖自动填充值）
         *
         * @param tenantId 租户ID
         * @return 当前 Builder
         */
        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        /**
         * 设置操作人ID（覆盖自动填充值）
         *
         * @param userId 操作人ID
         * @return 当前 Builder
         */
        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        /**
         * 设置链路追踪ID（覆盖自动填充值）
         *
         * @param traceId 链路追踪ID
         * @return 当前 Builder
         */
        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        /**
         * 添加元数据项
         *
         * @param key   元数据键
         * @param value 元数据值
         * @return 当前 Builder
         */
        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        /**
         * 设置元数据（覆盖已有元数据）
         *
         * @param metadata 元数据 Map
         * @return 当前 Builder
         */
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata.clear();
            if (metadata != null) {
                this.metadata.putAll(metadata);
            }
            return this;
        }

        public DomainEvent build() {
            if (eventType == null || eventType.isEmpty()) {
                throw new IllegalArgumentException("eventType must not be null or empty");
            }
            String eid = eventId != null ? eventId : UUID.randomUUID().toString();
            LocalDateTime occurred = occurredAt != null ? occurredAt : LocalDateTime.now();
            String tid = tenantId != null ? tenantId : RequestContext.getTenantId();
            String uid = userId != null ? userId : RequestContext.getUserId();
            String trace = traceId != null ? traceId : RequestContext.getTraceId();
            return new DomainEvent(eid, occurred, eventType, aggregateId, aggregateType,
                                   version, tid, uid, trace, metadata);
        }
    }
}
