package com.njydsz.common.auth.audit;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * 安全审计事件不可变对象。
 *
 * <p>记录一次安全相关的操作（如权限拒绝、认证失败等），
 * 包含参与者、动作、资源、结果等结构化信息。
 *
 * <p>所有字段通过 Builder 模式构造，构造后不可变。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class SecurityAuditEvent {

    /** 事件唯一标识 */
    private final String eventId;

    /** 事件时间戳（毫秒） */
    private final long timestamp;

    /** 操作者标识（userId） */
    private final String actor;

    /** 操作类型（如 PERMISSION_DENIED、AUTH_FAILURE） */
    private final String action;

    /** 被访问的资源 */
    private final String resource;

    /** 操作结果（SUCCESS / FAILURE） */
    private final String result;

    /** 客户端 IP 地址 */
    private final String ipAddress;

    /** 客户端 User-Agent */
    private final String userAgent;

    /** 链路追踪 ID */
    private final String traceId;

    /** 附加详情 */
    private final Map<String, Object> details;

    private SecurityAuditEvent(Builder builder) {
        this.eventId = builder.eventId != null ? builder.eventId : UUID.randomUUID().toString();
        this.timestamp = builder.timestamp > 0 ? builder.timestamp : System.currentTimeMillis();
        this.actor = builder.actor;
        this.action = builder.action;
        this.resource = builder.resource;
        this.result = builder.result;
        this.ipAddress = builder.ipAddress;
        this.userAgent = builder.userAgent;
        this.traceId = builder.traceId;
        this.details = builder.details != null
                ? Collections.unmodifiableMap(builder.details)
                : Collections.emptyMap();
    }

    public String getEventId() {
        return eventId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getResource() {
        return resource;
    }

    public String getResult() {
        return result;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getTraceId() {
        return traceId;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    /**
     * 将事件格式化为结构化日志字符串。
     *
     * @return 结构化字符串表示
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder(256);
        sb.append("[AUDIT] eventId=").append(eventId)
                .append(", timestamp=").append(timestamp)
                .append(", actor=").append(actor)
                .append(", action=").append(action)
                .append(", resource=").append(resource)
                .append(", result=").append(result);
        if (ipAddress != null) {
            sb.append(", ipAddress=").append(ipAddress);
        }
        if (userAgent != null) {
            sb.append(", userAgent=").append(userAgent);
        }
        if (traceId != null) {
            sb.append(", traceId=").append(traceId);
        }
        if (!details.isEmpty()) {
            sb.append(", details=").append(details);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return toJson();
    }

    /**
     * 构造 {@link SecurityAuditEvent} 的 Builder。
     *
     * @return Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * SecurityAuditEvent 构建器。
     */
    public static final class Builder {

        private String eventId;
        private long timestamp;
        private String actor;
        private String action;
        private String resource;
        private String result;
        private String ipAddress;
        private String userAgent;
        private String traceId;
        private Map<String, Object> details;

        private Builder() {
        }

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder actor(String actor) {
            this.actor = actor;
            return this;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder resource(String resource) {
            this.resource = resource;
            return this;
        }

        public Builder result(String result) {
            this.result = result;
            return this;
        }

        public Builder ipAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder details(Map<String, Object> details) {
            this.details = details;
            return this;
        }

        /**
         * 添加单个详情键值对。
         *
         * @param key   键
         * @param value 值
         * @return this
         */
        public Builder detail(String key, Object value) {
            if (this.details == null) {
                this.details = new java.util.HashMap<>();
            }
            this.details.put(key, value);
            return this;
        }

        public SecurityAuditEvent build() {
            return new SecurityAuditEvent(this);
        }
    }
}
