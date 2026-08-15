package com.njydsz.common.event.model;

import java.util.Map;

/**
 * Outbox 消息草稿（业务输入）
 *
 * <p>表示业务代码提供的消息数据，仅包含业务方需要设置的字段。
 * 通过 {@link OutboxMessage#fromDraft} 方法添加系统字段后转为完整的 {@link OutboxMessage} 实体。
 *
 * <p><b>设计意图：</b>
 * <ul>
 *   <li>明确区分"业务输入"和"系统管理"字段，防止业务代码误设 id、status 等字段</li>
 *   <li>提供类型安全的消息构建方式</li>
 *   <li>作为 REST API / RPC 接口的 DTO 使用，无需暴露内部实体</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * OutboxMessageDraft draft = OutboxMessageDraft.builder()
 *     .aggregateType("Order")
 *     .aggregateId("order-001")
 *     .eventType("OrderCreated")
 *     .payload("{\"id\":\"order-001\"}")
 *     .build();
 *
 * OutboxMessage entity = OutboxMessage.fromDraft(draft, systemFields);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.6.0
 * @see OutboxMessage
 */
public class OutboxMessageDraft {

    /** 聚合根 ID（如订单号） */
    private final String aggregateId;

    /** 聚合根类型（如 "Order"） */
    private final String aggregateType;

    /** 事件类型（如 "OrderCreated"） */
    private final String eventType;

    /** 事件负载（JSON 字符串） */
    private final String payload;

    /** 扩展头（可选，用于路由/追踪） */
    private final Map<String, String> headers;

    /** 幂等去重 ID（幂等消费端去重，可选） */
    private final String deduplicationId;

    /** 事件 Schema 版本号（如 "v1.0.0"） */
    private final String schemaVersion;

    /** 内容类型（如 "application/vnd.ydsz.order.v1+json"） */
    private final String contentType;

    /**
     * 优先级（0-9，9 最高）
     *
     * <p>{@code null} 表示使用系统默认优先级（由 OutboxService 填充）。
     */
    private final Integer priority;

    /**
     * 私有构造函数，使用 {@link Builder} 创建
     */
    private OutboxMessageDraft(String aggregateId, String aggregateType,
                                String eventType, String payload,
                                Map<String, String> headers, String deduplicationId,
                                String schemaVersion, String contentType,
                                Integer priority) {
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.eventType = eventType;
        this.payload = payload;
        this.headers = headers;
        this.deduplicationId = deduplicationId;
        this.schemaVersion = schemaVersion;
        this.contentType = contentType;
        this.priority = priority;
    }

    /**
     * 创建 Builder
     *
     * @return Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getDeduplicationId() {
        return deduplicationId;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public String getContentType() {
        return contentType;
    }

    public Integer getPriority() {
        return priority;
    }

    @Override
    public String toString() {
        return "OutboxMessageDraft{" +
                "aggregateType='" + aggregateType + '\'' +
                ", aggregateId='" + aggregateId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", priority=" + priority +
                '}';
    }

    /**
     * OutboxMessageDraft 构建器
     */
    public static class Builder {

        private String aggregateId;
        private String aggregateType;
        private String eventType;
        private String payload;
        private Map<String, String> headers;
        private String deduplicationId;
        private String schemaVersion;
        private String contentType;
        private Integer priority;

        Builder() {
        }

        public Builder aggregateId(String aggregateId) {
            this.aggregateId = aggregateId;
            return this;
        }

        public Builder aggregateType(String aggregateType) {
            this.aggregateType = aggregateType;
            return this;
        }

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder payload(String payload) {
            this.payload = payload;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public Builder deduplicationId(String deduplicationId) {
            this.deduplicationId = deduplicationId;
            return this;
        }

        public Builder schemaVersion(String schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        public Builder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder priority(Integer priority) {
            this.priority = priority;
            return this;
        }

        /**
         * 构建 OutboxMessageDraft
         *
         * @return Draft 实例
         * @throws IllegalArgumentException 必填字段为空时抛出
         */
        public OutboxMessageDraft build() {
            if (aggregateType == null || aggregateType.isBlank()) {
                throw new IllegalArgumentException("aggregateType must not be null or blank");
            }
            if (aggregateId == null || aggregateId.isBlank()) {
                throw new IllegalArgumentException("aggregateId must not be null or blank");
            }
            if (eventType == null || eventType.isBlank()) {
                throw new IllegalArgumentException("eventType must not be null or blank");
            }
            if (payload == null) {
                throw new IllegalArgumentException("payload must not be null");
            }
            return new OutboxMessageDraft(aggregateId, aggregateType, eventType,
                    payload, headers, deduplicationId, schemaVersion, contentType, priority);
        }
    }
}
