package com.njydsz.pmis.common.constant;

/**
 * 消息模块 RocketMQ Topic 与 ConsumerGroup 常量。
 *
 * <p>统一管理消息中心使用的所有 RocketMQ topic 和消费组名称，
 * 避免散落在各处的硬编码字符串。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class PmisMessageTopics {

    private PmisMessageTopics() {
        throw new UnsupportedOperationException("Constants class");
    }

    /** 消息发送主 Topic */
    public static final String TOPIC_MESSAGE = "pmis-message";

    /** 消息发送 ConsumerGroup */
    public static final String GROUP_MESSAGE = "pmis-message-consumer-group";

    /** 死信队列 Topic */
    public static final String DLQ_MESSAGE = "pmis-message-dlq";

    /** 死信队列 ConsumerGroup */
    public static final String GROUP_DLQ_MESSAGE = "pmis-message-dlq-consumer-group";

    /** P1-11: 批量消息 Topic */
    public static final String TOPIC_MESSAGE_BATCH = "pmis-message-batch";

    /** P1-11: 批量消息 ConsumerGroup */
    public static final String GROUP_MESSAGE_BATCH = "pmis-message-batch-consumer-group";
}
