package com.njydsz.agent.domain.channel;

import java.util.Map;

/**
 * 渠道响应值对象。
 *
 * <p>封装返回给不同渠道的响应数据。</p>
 *
 * @author ydsz-agent
 * @since 26.09.01
 */
public final class ChannelResponse {

    private final String requestId;
    private final ChannelType channelType;
    private final boolean success;
    private final String content;
    private final String errorCode;
    private final String errorMessage;
    private final Map<String, Object> metadata;

    private ChannelResponse(Builder builder) {
        this.requestId = builder.requestId;
        this.channelType = builder.channelType;
        this.success = builder.success;
        this.content = builder.content;
        this.errorCode = builder.errorCode;
        this.errorMessage = builder.errorMessage;
        this.metadata = builder.metadata != null ? Map.copyOf(builder.metadata) : Map.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 创建成功响应。
     *
     * @param requestId 请求 ID
     * @param channelType 渠道类型
     * @param content 响应内容
     * @return 成功响应
     */
    public static ChannelResponse success(String requestId, ChannelType channelType, String content) {
        return new Builder()
                .requestId(requestId)
                .channelType(channelType)
                .success(true)
                .content(content)
                .build();
    }

    /**
     * 创建失败响应。
     *
     * @param requestId 请求 ID
     * @param channelType 渠道类型
     * @param errorCode 错误码
     * @param errorMessage 错误信息
     * @return 失败响应
     */
    public static ChannelResponse failure(String requestId, ChannelType channelType,
                                           String errorCode, String errorMessage) {
        return new Builder()
                .requestId(requestId)
                .channelType(channelType)
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }

    public String getRequestId() {
        return requestId;
    }

    public ChannelType getChannelType() {
        return channelType;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getContent() {
        return content;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public static final class Builder {
        private String requestId;
        private ChannelType channelType;
        private boolean success;
        private String content;
        private String errorCode;
        private String errorMessage;
        private Map<String, Object> metadata;

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder channelType(ChannelType channelType) {
            this.channelType = channelType;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public ChannelResponse build() {
            return new ChannelResponse(this);
        }
    }
}
