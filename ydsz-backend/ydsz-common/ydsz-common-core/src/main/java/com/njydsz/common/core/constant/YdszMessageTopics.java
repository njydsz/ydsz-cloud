package com.njydsz.common.core.constant;

/**
 * 消息中心 RocketMQ Topic / ConsumerGroup 常量定义。
 *
 * <p>统一管理消息中心使用的所有 RocketMQ 资源名称，避免生产者与消费者
 * 因字符串硬编码不一致导致消息丢失或消费失败。
 *
 * <p><b>Topic 列表：</b>
 * <ul>
 *   <li>{@link #TOPIC_MESSAGE} — 单条消息投递 Topic（主链路）</li>
 *   <li>{@link #TOPIC_MESSAGE_BATCH} — 批量消息投递 Topic（P1-11）</li>
 *   <li>{@link #DLQ_MESSAGE} — 死信队列 Topic（RocketMQ DLQ 命名规范 {@code %DLQ%<consumerGroup>}）</li>
 * </ul>
 *
 * <p><b>ConsumerGroup 列表：</b>
 * <ul>
 *   <li>{@link #GROUP_MESSAGE} — 单条消息消费组</li>
 *   <li>{@link #GROUP_MESSAGE_BATCH} — 批量消息消费组</li>
 *   <li>{@link #GROUP_DLQ_MESSAGE} — 死信队列消费组</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class YdszMessageTopics {

    private YdszMessageTopics() {
        throw new UnsupportedOperationException("YdszMessageTopics is a constant class and cannot be instantiated");
    }

    // ==================== ConsumerGroup ====================

    /** 单条消息消费组 */
    public static final String GROUP_MESSAGE = "ydsz_message_consumer_group";

    /** 批量消息消费组 */
    public static final String GROUP_MESSAGE_BATCH = "ydsz_message_batch_consumer_group";

    /** 死信队列消费组 */
    public static final String GROUP_DLQ_MESSAGE = "ydsz_message_dlq_consumer_group";

    // ==================== Topic ====================

    /** 单条消息投递 Topic（主链路） */
    public static final String TOPIC_MESSAGE = "ydsz_message";

    /** 批量消息投递 Topic（P1-11） */
    public static final String TOPIC_MESSAGE_BATCH = "ydsz_message_batch";

    /** 死信队列 Topic（RocketMQ DLQ 命名规范：{@code %DLQ%<consumerGroup>}） */
    public static final String DLQ_MESSAGE = "%DLQ%" + GROUP_MESSAGE;
}
