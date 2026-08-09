package com.njydsz.common.queue.constant;

/**
 * 消息中心 RocketMQ 主题与消费组常量。
 *
 * <p>集中管理消息模块使用的 RocketMQ Topic / ConsumerGroup 命名，
 * 供 {@code ydsz-message-server} 的消费者与生产者统一引用，避免散落魔法值。
 *
 * <p><b>命名约定：</b>{@code ydsz-msg-<type>[-batch|-dlq]}（Topic），
 * {@code ydsz-msg-group-<type>[-batch|-dlq]}（ConsumerGroup）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class YdszMessageTopics {

    private YdszMessageTopics() {
    }

    /** 普通消息主题（按 Tag 区分优先级：URGENT/HIGH/NORMAL/LOW） */
    public static final String TOPIC_MESSAGE = "ydsz-msg-message";

    /** 批量消息主题 */
    public static final String TOPIC_MESSAGE_BATCH = "ydsz-msg-message-batch";

    /** 死信消息主题（重试耗尽后落库 status=DEAD） */
    public static final String DLQ_MESSAGE = "ydsz-msg-message-dlq";

    /** 普通消息消费组 */
    public static final String GROUP_MESSAGE = "ydsz-msg-group-message";

    /** 批量消息消费组 */
    public static final String GROUP_MESSAGE_BATCH = "ydsz-msg-group-message-batch";

    /** 死信消息消费组 */
    public static final String GROUP_DLQ_MESSAGE = "ydsz-msg-group-message-dlq";
}
