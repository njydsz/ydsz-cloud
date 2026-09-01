package com.njydsz.agent.domain.channel;

import java.util.Map;

/**
 * 渠道请求值对象。
 *
 * <p>封装来自不同渠道的请求数据。</p>
 *
 * @author ydsz-agent
 * @since 26.09.01
 */
public final class ChannelRequest {

    private final String requestId;
    private final ChannelType channelType;
    private final String tenantId;
    private final String userId;
    private final String conversationId;
    private final String message;
    private final Map<String, Object> metadata;

    private ChannelRequest(Builder builder) {
        this.requestId = builder.requestId;
        this.channelType = builder.channelType;
        this.tenantId = builder.tenantId;
        this.userId = builder.userId;
        this.conversationId = builder.conversationId;
        this.message = builder.message;
        this.metadata = builder.metadata != null ? Map.copyOf(builder.metadata) : Map.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getRequestId() {
        return requestId;
    }

    public ChannelType getChannelType() {
        return channelType;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getUserId() {
        return userId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public static final class Builder {
        private String requestId;
        private ChannelType channelType;
        private String tenantId;
        private String userId;
        private String conversationId;
        private String message;
        private Map<String, Object> metadata;

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder channelType(ChannelType channelType) {
            this.channelType = channelType;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder conversationId(String conversationId) {
            this.conversationId = conversationId;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public ChannelRequest build() {
            return new ChannelRequest(this);
        }
    }
}
