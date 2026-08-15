package com.njydsz.common.queue.service;

import java.util.List;

import com.njydsz.common.queue.domain.QueueMessage;
import com.njydsz.common.queue.service.impl.RedisStreamPublisher;

/**
 * 消息发布者接口
 *
 * <p>定义消息发布的核心操作，支持单条发布、批量发布、延迟发布和顺序消息发布。
 *
 * <p><b>批量优化：</b>
 * 对于支持原生批量的队列实现（如 {@link RedisStreamPublisher}），
 * 可覆盖 {@link #publishBatch(List)} 以利用原生批量 API 提升吞吐量。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 单条发布
 * publisher.publish("Hello World");
 *
 * // 发布 QueueMessage
 * publisher.publish(queueMessage);
 *
 * // 批量发布
 * publisher.publishBatch(messages);
 *
 * // 顺序消息发布
 * publisher.publishSequential(sequentialMessage);
 *
 * // 延迟发布
 * publisher.publishDelayed(message, 60000);
 * }</pre>
 *
 * <p><b>线程安全性：</b>
 * 实现类应该是线程安全的，可以被多个线程共享使用。
 *
 * @author ydsz-team
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
     * <p>将 {@link QueueMessage} 发布到队列通道，携带 headers、traceId 等元数据。
     *
     * @param message 待发布的消息，为 null 时静默忽略
     */
    void publish(QueueMessage message);

    /**
     * 批量发布 QueueMessage 消息
     *
     * <p>将多条 QueueMessage 批量发布到队列通道。
     * 支持批量发送的队列实现（如 Redis Stream pipeline、Kafka batch、RocketMQ batch）
     * 可以覆盖此方法以利用原生批量 API 提升吞吐量。
     *
     * @param messages 待发布的消息列表，为空或 null 时静默忽略
     */
    void publishBatch(List<QueueMessage> messages);

    /**
     * 发布顺序消息
     *
     * <p>保证相同 {@code groupKey} 的消息被路由到同一分区/队列，顺序消费。
     *
     * @param message 待发布的顺序消息，需携带 {@code groupKey} 和 {@code sequence}
     */
    void publishSequential(QueueMessage message);

    /**
     * 发布延迟消息
     *
     * <p>消息在指定延迟时间后才被消费者可见。仅 RocketMQ 原生支持 18 级延迟；
     * 其他引擎调用此方法等同于立即发送。
     *
     * @param message     待发布的消息
     * @param delayMillis 延迟时间（毫秒）
     */
    void publishDelayed(QueueMessage message, long delayMillis);

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
