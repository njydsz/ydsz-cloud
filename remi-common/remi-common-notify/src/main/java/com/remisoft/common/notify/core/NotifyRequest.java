package com.remisoft.common.notify.core;

import com.remisoft.common.notify.enums.NotifyChannel;
import com.remisoft.common.notify.enums.NotifyPriority;

/**
 * 通知发送请求模型
 *
 * <p>封装一次通知发送所需的全部上下文信息，支持事务后发送、优先级路由等高级特性。
 *
 * @author remi-team
 * @since 1.0.0
 */
public class NotifyRequest {

    /** 通知渠道 */
    private final NotifyChannel channel;

    /** 接收者标识（邮箱地址、手机号、用户ID等） */
    private final String receiver;

    /** 消息标题 */
    private final String title;

    /** 消息内容 */
    private final String content;

    /** 模板编码（模板发送时使用，可为 null） */
    private final String templateCode;

    /** 模板参数（模板发送时使用，可为 null） */
    private final Object templateParams;

    /** 消息优先级 */
    private final NotifyPriority priority;

    /** 用户ID（用于偏好检查和审计，可为 null） */
    private final String userId;

    /** traceId（链路追踪用，可为 null） */
    private final String traceId;

    private NotifyRequest(Builder builder) {
        this.channel = builder.channel;
        this.receiver = builder.receiver;
        this.title = builder.title;
        this.content = builder.content;
        this.templateCode = builder.templateCode;
        this.templateParams = builder.templateParams;
        this.priority = builder.priority;
        this.userId = builder.userId;
        this.traceId = builder.traceId;
    }

    /**
     * 获取通知渠道
     *
     * @return 通知渠道
     */
    public NotifyChannel getChannel() {
        return channel;
    }

    /**
     * 获取接收者标识
     *
     * @return 接收者
     */
    public String getReceiver() {
        return receiver;
    }

    /**
     * 获取消息标题
     *
     * @return 标题
     */
    public String getTitle() {
        return title;
    }

    /**
     * 获取消息内容
     *
     * @return 内容
     */
    public String getContent() {
        return content;
    }

    /**
     * 获取模板编码
     *
     * @return 模板编码，非模板请求时返回 null
     */
    public String getTemplateCode() {
        return templateCode;
    }

    /**
     * 获取模板参数
     *
     * @return 模板参数对象
     */
    public Object getTemplateParams() {
        return templateParams;
    }

    /**
     * 获取消息优先级
     *
     * @return 优先级
     */
    public NotifyPriority getPriority() {
        return priority;
    }

    /**
     * 获取用户ID
     *
     * @return 用户ID，未设置时返回 null
     */
    public String getUserId() {
        return userId;
    }

    /**
     * 获取链路追踪ID
     *
     * @return traceId，未设置时返回 null
     */
    public String getTraceId() {
        return traceId;
    }

    /**
     * 判断是否为模板发送请求
     *
     * @return true 表示使用模板发送
     */
    public boolean isTemplateRequest() {
        return templateCode != null && !templateCode.isEmpty();
    }

    /**
     * 创建 Builder
     *
     * @param channel  通知渠道
     * @param receiver 接收者
     * @param title    标题
     * @param content  内容
     * @return Builder 实例
     */
    public static Builder of(NotifyChannel channel, String receiver, String title, String content) {
        return new Builder(channel, receiver, title, content);
    }

    /**
     * NotifyRequest 构建器
     *
     * @author remi-team
     * @since 1.0.0
     */
    public static class Builder {

        private final NotifyChannel channel;
        private final String receiver;
        private final String title;
        private final String content;
        private String templateCode;
        private Object templateParams;
        private NotifyPriority priority = NotifyPriority.P2_NORMAL;
        private String userId;
        private String traceId;

        Builder(NotifyChannel channel, String receiver, String title, String content) {
            this.channel = channel;
            this.receiver = receiver;
            this.title = title;
            this.content = content;
        }

        /**
         * 设置模板编码和参数
         *
         * @param templateCode   模板编码
         * @param templateParams 模板参数
         * @return this
         */
        public Builder template(String templateCode, Object templateParams) {
            this.templateCode = templateCode;
            this.templateParams = templateParams;
            return this;
        }

        /**
         * 设置优先级
         *
         * @param priority 优先级
         * @return this
         */
        public Builder priority(NotifyPriority priority) {
            this.priority = priority;
            return this;
        }

        /**
         * 设置用户ID
         *
         * @param userId 用户ID
         * @return this
         */
        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        /**
         * 设置 traceId
         *
         * @param traceId 链路追踪ID
         * @return this
         */
        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        /**
         * 构建 NotifyRequest
         *
         * @return NotifyRequest 实例
         */
        public NotifyRequest build() {
            return new NotifyRequest(this);
        }
    }
}
