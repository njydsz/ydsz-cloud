package com.njydsz.pmis.common.queue.queue;

import com.njydsz.pmis.common.queue.service.IMessagePublisher;
import com.njydsz.pmis.common.queue.service.IMessageSubscriber;

/**
 * 消息队列接口
 *
 * <p>定义消息队列的标准操作，负责创建消息发布者和订阅者。
 * 该接口是队列实现的抽象层，支持多种消息队列模式（如 Redis List、PubSub、Stream）。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 创建队列实例
 * IMessageQueue queue = messageQueueProvider.createMessageQueue(QueueType.stream);
 *
 * // 创建发布者和订阅者
 * IMessagePublisher publisher = queue.createPublisher("my-channel");
 * IMessageSubscriber subscriber = queue.createSubscriber("my-channel");
 *
 * // 发布消息
 * publisher.publish("Hello World");
 *
 * // 消费消息
 * subscriber.subscribe(message -> {
 *     log.info("Message: {}", message.getBody());
 * });
 *
 * // 关闭队列释放资源
 * queue.close();
 * }</pre>
 *
 * <p><b>实现说明：</b>
 * <ul>
 *   <li>createPublisher: 创建消息发布者，用于发送消息到队列</li>
 *   <li>createSubscriber: 创建消息订阅者，用于从队列接收消息</li>
 *   <li>close: 关闭队列连接，释放相关资源</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
public interface IMessageQueue extends AutoCloseable {

    /**
     * 创建消息发布者
     *
     * <p>发布者负责将消息发送到指定的队列通道。
     * 同一队列可以创建多个发布者实例。
     *
     * @param channel 队列通道名称（如主题名、队列名）
     * @return 消息发布者实例
     */
    IMessagePublisher createPublisher(String channel);

    /**
     * 创建消息订阅者
     *
     * <p>订阅者负责从指定的队列通道接收消息。
     * 订阅者支持同步拉取和异步监听两种消费模式。
     *
     * @param channel 队列通道名称
     * @return 消息订阅者实例
     */
    IMessageSubscriber createSubscriber(String channel);

    /**
     * 获取队列通道列表
     *
     * <p>返回当前队列实例管理的所有通道名称。
     * 该方法为可选实现，某些队列模式可能不支持。
     *
     * @return 通道名称数组，如果不支持返回空数组
     */
    default String[] getChannels() {
        return new String[0];
    }

    /**
     * 检查队列是否已关闭
     *
     * @return true 如果队列已关闭，false 如果队列处于活跃状态
     */
    default boolean isClosed() {
        return false;
    }

    /**
     * 获取队列类型描述
     *
     * @return 队列类型的描述字符串
     */
    default String getType() {
        return this.getClass().getSimpleName();
    }

    /**
     * 关闭队列连接
     *
     * <p>释放所有与队列相关的资源，包括连接池、通道等。
     * 关闭后不应再使用该队列实例进行任何操作。
     */
    default void close() {
    }

    /**
     * 检查队列是否已关闭，如果已关闭则抛出异常
     *
     * <p>在执行发布、订阅等操作前调用此方法，
     * 防止在队列关闭后继续进行操作。
     *
     * @throws IllegalStateException 如果队列已关闭
     */
    default void checkNotClosed() {
        if (isClosed()) {
            throw new IllegalStateException("队列已关闭，无法继续操作");
        }
    }
}