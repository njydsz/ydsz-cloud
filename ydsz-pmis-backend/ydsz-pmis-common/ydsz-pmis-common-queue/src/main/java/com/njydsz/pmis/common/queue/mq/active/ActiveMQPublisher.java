package com.njydsz.pmis.common.queue.mq.active;

import com.njydsz.pmis.common.exception.custom.InfrastructureException;
import com.njydsz.pmis.common.queue.domain.QueueMessage;
import com.njydsz.pmis.common.queue.service.IMessagePublisher;
import jakarta.jms.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.ActiveMQConnectionFactory;

import java.util.concurrent.locks.ReentrantLock;

/**
 * ActiveMQ 消息发布者
 *
 * <p>使用 ActiveMQ Artemis JMS API 实现消息发布功能。
 * 支持 ActiveMQ Classic 和 ActiveMQ Artemis 两种模式。
 *
 * <p><b>技术特点：</b>
 * <ul>
 *   <li>JMS 标准：遵循 Java Message Service 规范</li>
 *   <li>多种模式：支持点对点和发布/订阅模式</li>
 *   <li>消息持久化：支持消息持久化保证可靠性</li>
 *   <li>事务支持：支持 JMS 事务</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * ActiveMQPublisher publisher = new ActiveMQPublisher(properties, "my-queue");
 * publisher.publish("Hello ActiveMQ");
 * publisher.publish(QueueMessage.of("Hello"));
 * publisher.close();
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Slf4j
public class ActiveMQPublisher implements IMessagePublisher {

    private final ActiveMQConnectionFactory connectionFactory;
    private final Connection connection;
    private final Session session;
    private final MessageProducer producer;
    private final String queueName;
    private volatile boolean closed = false;
    private final ReentrantLock closeLock = new ReentrantLock();

    public ActiveMQPublisher(ActiveMQProperties properties, String queueName) {
        if (properties == null) {
            throw new IllegalArgumentException("ActiveMQ 配置不能为空");
        }
        if (queueName == null || queueName.isEmpty()) {
            throw new IllegalArgumentException("队列名称不能为空");
        }
        this.queueName = queueName;
        try {
            this.connectionFactory = createConnectionFactory(properties);
            this.connection = connectionFactory.createConnection();
            this.session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            Destination destination = session.createQueue(queueName);
            this.producer = session.createProducer(destination);
            if (properties.isPersistent()) {
                producer.setDeliveryMode(DeliveryMode.PERSISTENT);
            }
            connection.start();
            log.info("[ActiveMQ] 发布者初始化完成，queue={}, broker={}",
                    queueName, properties.resolvedBrokerUrl());
        } catch (Exception e) {
            log.error("[ActiveMQ] 初始化发布者失败，queue={}", queueName, e);
            throw new InfrastructureException("ActiveMQ 发布者初始化失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void publish(String message) {
        if (message == null || closed) {
            return;
        }
        try {
            QueueMessage queueMessage = QueueMessage.fromPayload(message);
            if (queueMessage == null) {
                queueMessage = QueueMessage.of(message);
            }
            publish(queueMessage);
        } catch (Exception e) {
            log.error("[ActiveMQ] 消息发布失败，queue={}", queueName, e);
            throw new InfrastructureException("ActiveMQ 消息发布失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void publish(QueueMessage message) {
        if (message == null || closed) {
            return;
        }
        try {
            String payload = QueueMessage.toPayload(message);
            TextMessage textMessage = session.createTextMessage(payload);
            textMessage.setJMSMessageID(message.getTraceId());
            producer.send(textMessage);
            if (log.isDebugEnabled()) {
                log.debug("[ActiveMQ] 消息已发送，queue={}, traceId={}", queueName, message.getTraceId());
            }
        } catch (Exception e) {
            log.error("[ActiveMQ] 消息发布失败，queue={}, traceId={}", queueName, message.getTraceId(), e);
            throw new InfrastructureException("ActiveMQ 消息发布失败：" + e.getMessage(), e);
        }
    }

    @Override
    public String getChannel() {
        return queueName;
    }

    @Override
    public boolean isActive() {
        return !closed && connection != null;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closeLock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            try {
                if (producer != null) {
                    producer.close();
                }
                if (session != null) {
                    session.close();
                }
                if (connection != null) {
                    connection.close();
                }
                log.info("[ActiveMQ] 发布者已关闭，queue={}", queueName);
            } catch (Exception e) {
                log.warn("[ActiveMQ] 关闭发布者时发生异常", e);
            }
        } finally {
            closeLock.unlock();
        }
    }

    private ActiveMQConnectionFactory createConnectionFactory(ActiveMQProperties properties) {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();
        factory.setBrokerURL(properties.resolvedBrokerUrl());
        factory.setUserName(properties.resolvedUsername());
        factory.setPassword(properties.resolvedPassword());
        return factory;
    }
}