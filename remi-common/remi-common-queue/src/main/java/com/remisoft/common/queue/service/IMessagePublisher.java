package com.remisoft.common.queue.service;

import java.util.List;

import com.remisoft.common.queue.domain.QueueMessage;

/**
 * 消息发布者接口
 *
 * <p>定义消息发布的标准操作，支持发布字符串消息和统一消息模型。
 * 发布者负责将消息发送到指定的队列通道，是消息生产者的抽象。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 发布字符串消息
 * publisher.publish("Hello World");
 *
 * // 发布 QueueMessage 消息
 * QueueMessage message = QueueMessage.of("Hello World");
 * message.addHeader("type", "greeting");
 * publisher.publish(message);
 * }</pre>
 *
 * <p><b>线程安全性：</b>
 * 实现类应该是线程安全的，可以被多个线程共享使用。
 *
 * @author remi-team
 * @since 1.0.0
 */
public interface IMessagePublisher {

    /**
     * 发布字符串消息
     *
     * <p>将字符串消息发布到队列通道。如果消息格式符合 QueueMessage JSON 结构，
     * 会被正确解析；否则会作为普通文本处理。
     *
     * @param message 待发布的字符串消息
     */
    void publish(String message);

    /**
     * 发布 QueueMessage 消息
     *
     * <p>发布统一消息模型，支持消息头、追踪ID等元数据。
     * 默认实现会将消息序列化为 JSON 后调用 publish(String) 方法。
     *
     * @param message 待发布的消息对象
     */
    default void publish(QueueMessage message) {
        publish(QueueMessage.toPayload(message));
    }

    /**
     * 发布带延迟的消息
     *
     * <p>某些队列实现支持延迟消息，该方法用于发布需要延迟处理的消息。
     * 默认实现直接调用 publish(message)，不支持延迟的队列会忽略此参数。
     *
     * @param message   待发布的消息
     * @param delayMillis 延迟时间（毫秒）
     */
    default void publishDelayed(QueueMessage message, long delayMillis) {
        publish(message);
    }

    /**
     * 发布顺序消息
     *
     * <p>顺序消息保证同一消息组内的消息按顺序被消费。
     * 相同 messageGroupKey 的消息会被路由到同一队列分区，保证顺序性。
     *
     * <p><b>使用示例：</b>
     * <pre>{@code
     * // 发布顺序消息
     * QueueMessage message = QueueMessage.of("order data");
     * message.setSequential("order-123", 1L);
     * publisher.publishSequential(message);
     * }</pre>
     *
     * @param message 待发布的顺序消息（必须设置 messageGroupKey）
     */
    default void publishSequential(QueueMessage message) {
        if (message == null || !message.isSequential()) {
            throw new IllegalArgumentException("顺序消息必须设置 messageGroupKey");
        }
        publish(message);
    }

    /**
     * 批量发布消息
     *
     * <p>将多条消息批量发布到队列通道，提高批量操作的效率。
     * 默认实现是循环调用 publish() 方法，子类可以覆盖以优化性能。
     *
     * @param messages 待发布的字符串消息数组
     */
    default void publishBatch(String... messages) {
        if (messages == null || messages.length == 0) {
            return;
        }
        for (String message : messages) {
            publish(message);
        }
    }

    /**
     * 批量发布 QueueMessage 消息
     *
     * <p>将多条 QueueMessage 批量发布到队列通道。
     * 支持批量发送的队列实现（如 Redis Stream pipeline、Kafka batch、RocketMQ batch）
     * 可以覆盖此方法以利用原生批量 API 提升吞吐量。
     *
     * @param messages 待发布的消息列表
     */
    default void publishBatch(List<QueueMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (QueueMessage message : messages) {
            publish(message);
        }
    }

    /**
     * 获取发布者关联的通道名称
     *
     * @return 通道名称
     */
    default String getChannel() {
        return null;
    }

    /**
     * 检查发布者是否处于活跃状态
     *
     * @return true 如果可以正常发布消息，false 否则
     */
    default boolean isActive() {
        return true;
    }

    /**
     * 关闭发布者
     *
     * <p>释放发布者占用的资源，如关闭连接等。
     * 关闭后不应再使用该发布者进行任何操作。
     */
    default void close() {
    }
}