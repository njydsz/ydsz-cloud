package com.njydsz.message.server.producer;

import com.njydsz.common.feign.MessageRequest;

/**
 * 消息队列操作抽象接口。
 *
 * <p>将消息服务的 MQ 发送能力抽象化，底层可切换 RocketMQ / Kafka / RabbitMQ 等实现。
 * 默认实现为 {@link RocketMQMessageProducer}（基于 Spring RocketMQTemplate），
 * 也可通过 {@code CommonQueueMessageOperations} 适配 common-queue 的 {@code IMessagePublisher}。
 *
 * <p>配置项 {@code ydsz.message.mq-type} 控制使用哪种实现：
 * <ul>
 *   <li>{@code rocketmq}（默认）：直接使用 RocketMQTemplate</li>
 *   <li>{@code common-queue}：通过 common-queue 的 IMessageQueue 抽象发送</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MessageQueueOperations {

    /**
     * 同步发送消息到消息队列。
     *
     * @param req 消息请求
     * @return MQ 消息 ID
     */
    String syncSend(MessageRequest req);

    /**
     * 异步发送消息（不阻塞，结果通过回调通知）。
     *
     * @param req 消息请求
     */
    void asyncSend(MessageRequest req);

    /**
     * 发送事务消息（半消息）。
     *
     * <p>发送半消息后，由事务监听器执行本地事务校验，COMMIT 后消费端异步处理。
     * 不支持事务消息的 MQ 实现可降级为同步发送。
     *
     * @param req 消息请求
     * @return MQ 消息 ID
     */
    String sendTransactionMessage(MessageRequest req);
}
