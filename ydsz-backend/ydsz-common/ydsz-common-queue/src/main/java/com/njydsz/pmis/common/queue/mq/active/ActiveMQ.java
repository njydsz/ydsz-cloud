package com.njydsz.common.queue.mq.active;

import java.util.concurrent.ExecutorService;

import jakarta.jms.Connection;

import org.apache.activemq.ActiveMQConnectionFactory;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.queue.queue.AbstractMessageQueue;
import com.njydsz.common.queue.service.IMessagePublisher;
import com.njydsz.common.queue.service.IMessageSubscriber;

import lombok.extern.slf4j.Slf4j;

/**
 * ActiveMQ 消息队列
 *
 * <p>ActiveMQ 是 Apache 旗下的开源消息中间件，支持 JMS 规范。
 * 提供高可靠性、多种消息类型的支持。
 *
 * <p><b>技术特点：</b>
 * <ul>
 *   <li>JMS 标准：完全遵循 Java Message Service 规范</li>
 *   <li>多种协议：支持 OpenWire、AMQP、MQTT、STOMP 等协议</li>
 *   <li>高可靠：支持消息持久化和事务</li>
 *   <li>灵活路由：支持多种目的地类型</li>
 * </ul>
 *
 * <p><b>两种模式：</b>
 * <ul>
 *   <li>ActiveMQ Classic：传统的消息代理</li>
 *   <li>ActiveMQ Artemis：新一代高性能消息代理（推荐）</li>
 * </ul>
 *
 * <p><b>适用场景：</b>
 * <ul>
 *   <li>企业应用集成</li>
 *   <li>异步消息处理</li>
 *   <li>事件驱动架构</li>
 *   <li>微服务通信</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * IMessageQueue queue = messageQueueProvider.createMessageQueue(QueueType.active);
 * IMessagePublisher publisher = queue.createPublisher("my-queue");
 * IMessageSubscriber subscriber = queue.createSubscriber("my-queue");
 *
 * publisher.publish("Hello ActiveMQ");
 *
 * subscriber.subscribeAsync(message -> {
 *     log.info("Received: {}", message.getBody());
 * });
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class ActiveMQ extends AbstractMessageQueue {

    private final ActiveMQProperties properties;
    private final ExecutorService consumerExecutor;

    public ActiveMQ(ActiveMQProperties properties, ExecutorService consumerExecutor) {
        super("ActiveMQ");
        if (properties == null) {
            throw BusinessException.builder().key("ActiveMQ 配置不能为空").build();
        }
        this.properties = properties;
        this.consumerExecutor = consumerExecutor;
        validateConnection();
        log.info("[ActiveMQ] 初始化成功，brokerUrl={}", properties.resolvedBrokerUrl());
    }

    @Override
    public IMessagePublisher createPublisher(String channel) {
        checkNotClosed();
        if (channel == null || channel.isEmpty()) {
            throw BusinessException.builder().key("队列名称不能为空").build();
        }
        return new ActiveMQPublisher(properties, channel);
    }

    @Override
    public IMessageSubscriber createSubscriber(String channel) {
        checkNotClosed();
        if (channel == null || channel.isEmpty()) {
            throw BusinessException.builder().key("队列名称不能为空").build();
        }
        return new ActiveMQSubscriber(properties, channel, consumerExecutor);
    }

    @Override
    protected void doClose() {
    }

    private void validateConnection() {
        try {
            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(
                    properties.resolvedUsername(),
                    properties.resolvedPassword(),
                    properties.resolvedBrokerUrl()
            );
            try (Connection conn = factory.createConnection()) {
                conn.start();
                log.debug("[ActiveMQ] 连接验证成功");
            }
        } catch (Exception e) {
            log.error("[ActiveMQ] 连接验证失败，brokerUrl={}", properties.resolvedBrokerUrl(), e);
            throw BusinessException.builder().key("ActiveMQ 连接失败，请检查配置：" + e.getMessage()).build();
        }
    }
}
