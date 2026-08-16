package com.njydsz.common.queue.mq.rabbit;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.queue.queue.AbstractMessageQueue;
import com.njydsz.common.queue.service.IMessagePublisher;
import com.njydsz.common.queue.service.IMessageSubscriber;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import lombok.extern.slf4j.Slf4j;
/**
 * RabbitMQ 消息队列
 *
 * <p>RabbitMQ 是基于 AMQP 协议的消息中间件，具有高可靠性、灵活路由等特点。
 * 支持多种交换机类型，适合企业级的消息传递场景。
 *
 * <p><b>技术特点：</b>
 * <ul>
 *   <li>多种交换机：Direct、Fanout、Topic、Headers</li>
 *   <li>消息确认：支持 Publisher Confirms 和 Consumer ACK</li>
 *   <li>灵活路由：基于绑定和路由键的灵活消息路由</li>
 *   <li>死信队列：支持消息拒绝后的死信处理</li>
 *   <li>优先级队列：支持消息优先级</li>
 * </ul>
 *
 * <p><b>交换机类型说明：</b>
 * <ul>
 *   <li>Direct：完全匹配路由键 routing-key</li>
 *   <li>Fanout：广播到所有绑定的队列</li>
 *   <li>Topic：支持通配符匹配（* 和 #）</li>
 *   <li>Headers：基于消息头的匹配</li>
 * </ul>
 *
 * <p><b>适用场景：</b>
 * <ul>
 *   <li>异步任务处理</li>
 *   <li>微服务间通信</li>
 *   <li>事件驱动架构</li>
 *   <li>消息路由和过滤</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * IMessageQueue queue = messageQueueProvider.createMessageQueue(QueueType.rabbit);
 * IMessagePublisher publisher = queue.createPublisher("my-queue");
 * IMessageSubscriber subscriber = queue.createSubscriber("my-queue");
 *
 * publisher.publish("Hello RabbitMQ");
 *
 * subscriber.subscribeAsync(message -> {
 *     log.info("Received: {}", message.getBody());
 * });
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated 建议使用 Kafka 或 RocketMQ 替代
 */
@Deprecated
@Slf4j
public class RabbitMQ extends AbstractMessageQueue {

    private final RabbitMQProperties properties;

    public RabbitMQ(RabbitMQProperties properties) {
        super("RabbitMQ");
        if (properties == null) {
            throw BusinessException.builder().key("RabbitMQ 配置不能为空").build();
        }
        this.properties = properties;
        validateConnection();
        log.info("[RabbitMQ] 初始化成功，host={}:{}", properties.resolvedHost(), properties.resolvedPort());
    }

    @Override
    public IMessagePublisher createPublisher(String channel) {
        checkNotClosed();
        if (channel == null || channel.isEmpty()) {
            throw BusinessException.builder().key("队列名称不能为空").build();
        }
        return new RabbitMQPublisher(properties, channel);
    }

    @Override
    public IMessageSubscriber createSubscriber(String channel) {
        checkNotClosed();
        if (channel == null || channel.isEmpty()) {
            throw BusinessException.builder().key("队列名称不能为空").build();
        }
        return new RabbitMQSubscriber(properties, channel);
    }

    @Override
    protected void doClose() {
    }

    private void validateConnection() {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(properties.resolvedHost());
            factory.setPort(properties.resolvedPort());
            factory.setUsername(properties.resolvedUsername());
            factory.setPassword(properties.resolvedPassword());
            factory.setVirtualHost(properties.resolvedVirtualHost());
            try (Connection conn = factory.newConnection()) {
                if (conn.isOpen()) {
                    log.debug("[RabbitMQ] 连接验证成功");
                }
            }
        } catch (IOException | TimeoutException e) {
            log.error("[RabbitMQ] 连接验证失败，host={}:{}", properties.resolvedHost(), properties.resolvedPort(), e);
            throw BusinessException.builder().key("RabbitMQ 连接失败，请检查配置：" + e.getMessage()).build();
        }
    }
}
