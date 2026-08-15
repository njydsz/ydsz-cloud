package com.njydsz.common.queue.service;

import java.util.List;

import com.njydsz.common.queue.domain.QueueMessage;
import com.njydsz.common.queue.service.impl.RedisStreamPublisher;

/**
 * 消息发布者接口（扁平化设计）
 *
 * <p>定义消息发布的核心操作。组合能力（批量发布、延迟发布、顺序消息发布等）
 * 已提取到 {@link MessagePublisherHelper} 工具类，实现接口职责单一。
 *
 * <p>实现类仅需覆盖核心方法 {@link #publish(String)}，
 * 即可获得 {@link MessagePublisherHelper} 提供的批量/顺序/延迟等组合能力。
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
 * // 批量发布（使用工具类）
 * MessagePublisherHelper.publishBatch(publisher, messages);
 *
 * // 顺序消息发布（使用工具类）
 * MessagePublisherHelper.publishSequential(publisher, sequentialMessage);
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
     * 发布字符串消息（核心方法）
     *
     * <p>将字符串消息发布到队列通道。如果消息格式符合 QueueMessage JSON 结构，
     * 会被正确解析；否则会作为普通文本处理。
     *
     * @param message 待发布的字符串消息
     */
    void publish(String message);

    /**
     * 批量发布 QueueMessage 消息
     *
     * <p>将多条 QueueMessage 批量发布到队列通道。
     * 支持批量发送的队列实现（如 Redis Stream pipeline、Kafka batch、RocketMQ batch）
     * 可以覆盖此方法以利用原生批量 API 提升吞吐量。
     *
     * <p>默认实现委托 {@link MessagePublisherHelper#publishBatch(IMessagePublisher, List)}。
     *
     * @param messages 待发布的消息列表
     */
    default void publishBatch(List<QueueMessage> messages) {
        MessagePublisherHelper.publishBatch(this, messages);
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
