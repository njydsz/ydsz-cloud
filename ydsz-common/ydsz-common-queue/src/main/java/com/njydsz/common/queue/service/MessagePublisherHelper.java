package com.njydsz.common.queue.service;

import java.util.List;

import com.njydsz.common.queue.domain.QueueMessage;

/**
 * 发布者辅助工具类
 *
 * <p>提供发布者的组合操作（批量发布、消息模型发布、顺序消息发布等），
 * 解耦自 {@link IMessagePublisher} 接口默认方法，实现接口扁平化。
 *
 * <p>使用此类可在不侵入接口契约的前提下，为任意发布者实例附加组合能力。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * IMessagePublisher basePublisher = queue.createPublisher("topic");
 * MessagePublisherHelper.publishBatch(basePublisher, messages);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class MessagePublisherHelper {

    private MessagePublisherHelper() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * 将 QueueMessage 序列化后发布（单条）
     *
     * @param publisher 基础发布者
     * @param message   消息对象
     */
    public static void publishMessage(IMessagePublisher publisher, QueueMessage message) {
        if (publisher == null || message == null) {
            return;
        }
        publisher.publish(QueueMessage.toPayload(message));
    }

    /**
     * 批量发布消息（循环调用单条发布）。
     *
     * <p>对于支持原生批量的队列实现（如 Redis Stream Pipeline、Kafka batch），
     * 建议覆盖 {@link IMessagePublisher#publishBatch(List)} 以利用原生批量 API 提升吞吐量。
     *
     * @param publisher 基础发布者
     * @param messages  消息列表
     */
    public static void publishBatch(IMessagePublisher publisher, List<QueueMessage> messages) {
        if (publisher == null || messages == null || messages.isEmpty()) {
            return;
        }
        for (QueueMessage message : messages) {
            publishMessage(publisher, message);
        }
    }

    /**
     * 批量发布字符串消息
     *
     * @param publisher 基础发布者
     * @param messages  字符串消息数组
     */
    public static void publishBatch(IMessagePublisher publisher, String... messages) {
        if (publisher == null || messages == null || messages.length == 0) {
            return;
        }
        for (String message : messages) {
            publisher.publish(message);
        }
    }

    /**
     * 发布顺序消息
     *
     * @param publisher 基础发布者
     * @param message   顺序消息（必须设置 messageGroupKey）
     * @throws IllegalArgumentException 如果消息未设置 messageGroupKey
     */
    public static void publishSequential(IMessagePublisher publisher, QueueMessage message) {
        if (message == null || !message.isSequential()) {
            throw new IllegalArgumentException("顺序消息必须设置 messageGroupKey");
        }
        publishMessage(publisher, message);
    }

    /**
     * 发布带延迟的消息
     *
     * <p>默认实现直接发布（不延迟），支持延迟的队列实现可覆盖。
     *
     * @param publisher   基础发布者
     * @param message     消息对象
     * @param delayMillis 延迟时间（毫秒）
     */
    public static void publishDelayed(IMessagePublisher publisher, QueueMessage message, long delayMillis) {
        if (publisher == null || message == null) {
            return;
        }
        // 默认忽略延迟参数，直接发布
        publishMessage(publisher, message);
    }
}
