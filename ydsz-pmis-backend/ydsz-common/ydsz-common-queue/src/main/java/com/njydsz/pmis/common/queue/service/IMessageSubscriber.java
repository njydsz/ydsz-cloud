package com.njydsz.common.queue.service;

import com.njydsz.common.exception.custom.InfrastructureException;
import com.njydsz.common.queue.domain.QueueMessage;
/**
 * 消息订阅者接口
 *
 * <p>该接口定义了消息队列的消费者行为，包括同步消费和异步消费两种模式。
 * 实现类需要根据具体的消息中间件特性，实现相应的消费逻辑。
 *
 * <p><b>同步消费：</b>
 * <ul>
 *   <li>{@link #subscribe()} - 阻塞式获取一条消息</li>
 *   <li>{@link #subscribeMessage()} - 获取一条消息（可自定义超时）</li>
 * </ul>
 *
 * <p><b>异步消费：</b>
 * <ul>
 *   <li>{@link #subscribeAsync(IMessageHandler)} - 启动后台线程持续监听消息</li>
 *   <li>返回消费者 ID，可用于停止消费（见 {@link #stop()}）</li>
 * </ul>
 *
 * <p><b>生命周期：</b>
 * <pre>{@code
 * IMessageSubscriber subscriber = queue.createSubscriber("channel");
 *
 * // 异步消费模式
 * String consumerId = subscriber.subscribeAsync(message -> {
 *     log.info("Received: {}", message.getBody());
 * });
 *
 * // 停止消费
 * subscriber.stop();
 * }</pre>
 *
 * <p><b>注意事项：</b>
 * <ul>
 *   <li>异步消费时，handler 中应处理好异常，避免消息重复消费</li>
 *   <li>stop() 方法应优雅停机，等待正在处理的消息完成</li>
 *   <li>subscribeMessage() 的同步消费应设置合理的超时时间</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface IMessageSubscriber {

    /**
     * 同步消费消息（阻塞式）
     *
     * <p>此方法为阻塞调用，将等待直到有一条消息可用。
     * 适合单条消息处理或简单的拉取场景。
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>简单的单条消息处理</li>
     *   <li>需要精确控制消费节奏</li>
     * </ul>
     *
     * @return 消费到的消息，无消息时返回 null
     */
    String subscribe();

    /**
     * 同步消费消息（返回结构化消息）
     *
     * <p>此方法为阻塞调用，将等待直到有一条消息可用。
     * 返回 {@link QueueMessage} 对象，包含消息体及元数据。
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>需要访问消息头、优先级等元数据</li>
     *   <li>需要重试计数、时间戳等信息</li>
     * </ul>
     *
     * <p><b>示例：</b>
     * <pre>{@code
     * QueueMessage msg = subscriber.subscribeMessage();
     * if (msg != null) {
     *     log.info("Body: {}", msg.getBody());
     *     log.info("Headers: {}", msg.getHeaders());
     * }
     * }</pre>
     *
     * @return 消费到的消息对象，无消息时返回 null
     */
    default QueueMessage subscribeMessage() {
        String message = subscribe();
        return message != null ? QueueMessage.fromPayload(message) : null;
    }

    /**
     * 同步消费并处理单条消息（一次性消费）
     *
     * <p>此方法消费一条消息并立即调用 handler 处理。
     * 如果 handler 处理失败，异常会向上抛出，消息可能被重新投递。
     *
     * <p><b>注意：</b>此方法只消费一条消息，不适合持续监听场景。
     * 如需持续消费，请使用 {@link #subscribeAsync(IMessageHandler)}。
     *
     * @param handler 消息处理器，不能为 null
     * @return 消息 traceId，消费失败或无消息时返回 null
     * @throws RuntimeException 当 handler 处理失败时抛出
     */
    default String subscribeOnce(IMessageHandler handler) {
        QueueMessage message = subscribeMessage();
        if (message == null || handler == null) {
            return null;
        }
        try {
            handler.onMessage(message);
            return message.getTraceId();
        } catch (Exception e) {
            throw new InfrastructureException("消息处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 异步订阅消息
     *
     * <p>启动后台线程（或线程池）持续监听消息并回调 handler。
     * 此方法非阻塞，返回后消费者仍在后台运行。
     *
     * <p><b>特性：</b>
     * <ul>
     *   <li>非阻塞，立即返回消费者 ID</li>
     *   <li>后台线程持续监听消息</li>
     *   <li>handler 中可抛出异常控制重试</li>
     * </ul>
     *
     * <p><b>异常处理建议：</b>
     * <pre>{@code
     * subscriber.subscribeAsync(message -> {
     *     try {
     *         processMessage(message);
     *     } catch (Exception e) {
     *         log.error("消息处理失败", e);
     *         throw e; // 抛出异常可触发重试
     *     }
     * });
     * }</pre>
     *
     * @param handler 消息处理回调，不能为 null
     * @return 消费者 ID，可用于 {@link #stop()} 停止消费
     */
    String subscribeAsync(IMessageHandler handler);

    /**
     * 获取底层传输通道
     *
     * <p>返回消息中间件的底层传输通道对象，可用于高级操作。
     * 不同实现的返回值类型不同：
     * <ul>
     *   <li>Redis：Jedis 或 JedisPubSub</li>
     *   <li>Kafka：KafkaConsumer</li>
     *   <li>RabbitMQ：Channel</li>
     *   <li>ActiveMQ：Session</li>
     * </ul>
     *
     * @return 底层通道对象
     */
    default Object getChannel() {
        return null;
    }

    /**
     * 获取消费者 ID
     *
     * <p>返回当前消费者的唯一标识。不同中间件的标识格式不同。
     *
     * @return 消费者 ID
     */
    default String getConsumerId() {
        return null;
    }

    /**
     * 获取已消费消息数量
     *
     * <p>返回从消费者启动以来成功处理的消息总数。
     * 此计数器仅统计成功处理的消息，失败的消息不计入。
     *
     * @return 已消费成功消息数
     */
    default int getConsumedCount() {
        return 0;
    }

    /**
     * 获取最近一次消费失败的原因
     *
     * <p>返回最近一次处理消息时抛出的异常对象。
     * 如果从未发生过异常，返回 null。
     *
     * @return 最后一次异常，无异常时返回 null
     */
    default Throwable getLastError() {
        return null;
    }

    /**
     * 判断消费者是否正在运行
     *
     * <p>返回 true 表示消费者已启动并正在监听消息。
     * 返回 false 表示消费者已停止或未启动。
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
     * 此方法为阻塞调用，会等待所有资源释放完成。
     *
     * <p><b>停机流程：</b>
     * <ol>
     *   <li>停止接收新消息</li>
     *   <li>等待正在处理的消息完成</li>
     *   <li>提交/ACK 最后一条消息</li>
     *   <li>关闭底层连接</li>
     *   <li>释放所有资源</li>
     * </ol>
     */
    default void stop() {
    }
}