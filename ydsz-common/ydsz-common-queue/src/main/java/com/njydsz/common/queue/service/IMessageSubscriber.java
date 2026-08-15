package com.njydsz.common.queue.service;

import com.njydsz.common.queue.domain.QueueMessage;

/**
 * 消息订阅者接口
 *
 * <p>定义消息订阅的核心操作，支持同步消费、异步消费、结构化消费和一次性消费。
 *
 * <p><b>同步消费：</b>
 * <ul>
 *   <li>{@link #subscribe()} - 阻塞式获取一条消息</li>
 *   <li>{@link #subscribeMessage()} - 阻塞式获取一条 QueueMessage</li>
 * </ul>
 *
 * <p><b>异步消费：</b>
 * <ul>
 *   <li>{@link #subscribeAsync(IMessageHandler)} - 启动后台线程持续监听消息</li>
 *   <li>返回消费者 ID，可用于停止消费（见 {@link #stop()}）</li>
 * </ul>
 *
 * <p><b>一次性消费：</b>
 * <ul>
 *   <li>{@link #subscribeOnce(IMessageHandler)} - 消费一条消息后自动停止</li>
 * </ul>
 *
 * <p><b>线程安全性：</b>
 * 实现类应该是线程安全的，可以被多个线程共享使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface IMessageSubscriber {

    /**
     * 同步消费字符串消息（阻塞式）
     *
     * <p>此方法为阻塞调用，将等待直到有一条消息可用。
     * 适合单条消息处理或简单的拉取场景。
     *
     * @return 消费到的消息 payload，无消息时返回 null
     */
    String subscribe();

    /**
     * 同步消费结构化消息（阻塞式）
     *
     * <p>消费一条消息并解析为 {@link QueueMessage}，携带 headers、traceId 等元数据。
     *
     * @return 消费到的消息对象，无消息时返回 null
     */
    QueueMessage subscribeMessage();

    /**
     * 异步订阅消息
     *
     * <p>启动后台线程（或线程池）持续监听消息并回调 handler。
     * 此方法非阻塞，返回后消费者仍在后台运行。
     *
     * @param handler 消息处理回调，不能为 null
     * @return 消费者 ID，可用于 {@link #stop()} 停止消费
     */
    String subscribeAsync(IMessageHandler handler);

    /**
     * 一次性消费消息
     *
     * <p>消费一条消息后自动停止，适合定时拉取场景。
     *
     * @param handler 消息处理回调
     * @return 消费者 ID
     */
    String subscribeOnce(IMessageHandler handler);

    /**
     * 获取底层传输通道
     *
     * @return 底层通道对象
     */
    default Object getChannel() {
        return null;
    }

    /**
     * 获取消费者 ID
     *
     * @return 消费者 ID
     */
    default String getConsumerId() {
        return null;
    }

    /**
     * 获取已消费消息数量
     *
     * @return 已消费成功消息数
     */
    default int getConsumedCount() {
        return 0;
    }

    /**
     * 获取最近一次消费失败的原因
     *
     * @return 最后一次异常，无异常时返回 null
     */
    default Throwable getLastError() {
        return null;
    }

    /**
     * 判断消费者是否正在运行
     *
     * @return 是否正在运行
     */
    default boolean isRunning() {
        return false;
    }

    /**
     * 优雅停机
     *
     * <p>停止后台消费线程，等待正在处理的消息完成。
     */
    default void stop() {
    }
}
