package com.njydsz.pmis.common.queue.mq.active;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.jms.*;

import org.apache.activemq.ActiveMQConnectionFactory;

import com.njydsz.pmis.common.exception.custom.InfrastructureException;
import com.njydsz.pmis.common.queue.domain.QueueMessage;
import com.njydsz.pmis.common.queue.rate.ConsumerRateLimiter;
import com.njydsz.pmis.common.queue.recovery.ConsumerThreadGuard;
import com.njydsz.pmis.common.queue.service.IMessageHandler;
import com.njydsz.pmis.common.queue.service.IMessageSubscriber;

import lombok.extern.slf4j.Slf4j;

/**
 * ActiveMQ 消息订阅者
 *
 * <p>使用 ActiveMQ Artemis JMS API 实现消息消费功能。
 * 支持同步拉取和异步持续消费两种模式。
 *
 * <p><b>技术特点：</b>
 * <ul>
 *   <li>JMS 标准：遵循 Java Message Service 规范</li>
 *   <li>同步/异步：支持同步拉取和异步监听</li>
 *   <li>消息过滤：支持消息选择器过滤</li>
 *   <li>消费限流：支持 prefetch 限流</li>
 * </ul>
 *
 * <p><b>消费模式：</b>
 * <ul>
 *   <li>同步拉取：调用 subscribe() 阻塞等待消息</li>
 *   <li>异步监听：实现 MessageListener 持续接收消息</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @since 1.0.0
 */
@Slf4j
public class ActiveMQSubscriber implements IMessageSubscriber {

    private final ActiveMQConnectionFactory connectionFactory;
    private final Connection connection;
    private final Session session;
    private final MessageConsumer consumer;
    private final String queueName;

    private final AtomicBoolean running;
    private final AtomicLong consumedCount;
    private final AtomicReference<Throwable> lastError;
    private final ConsumerRateLimiter rateLimiter;
    private final ExecutorService consumerExecutor;

    private volatile Thread consumerThread;
    private volatile ConsumerThreadGuard threadGuard;

    public ActiveMQSubscriber(ActiveMQProperties properties, String queueName, ExecutorService consumerExecutor) {
        if (properties == null) {
            throw new IllegalArgumentException("ActiveMQ 配置不能为空");
        }
        if (queueName == null || queueName.isEmpty()) {
            throw new IllegalArgumentException("队列名称不能为空");
        }
        this.queueName = queueName;
        this.running = new AtomicBoolean(false);
        this.consumedCount = new AtomicLong(0);
        this.lastError = new AtomicReference<>();
        this.rateLimiter = new ConsumerRateLimiter(properties.resolvedConsumerRateLimitPerSecond());
        this.consumerExecutor = consumerExecutor;

        try {
            this.connectionFactory = createConnectionFactory(properties);
            this.connection = connectionFactory.createConnection();
            this.session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            Destination destination = session.createQueue(queueName);
            this.consumer = session.createConsumer(destination);
            connection.start();
            log.info("[ActiveMQ] 订阅者初始化完成，queue={}, broker={}",
                    queueName, properties.resolvedBrokerUrl());
        } catch (Exception e) {
            log.error("[ActiveMQ] 初始化订阅者失败，queue={}", queueName, e);
            throw new InfrastructureException("ActiveMQ 订阅者初始化失败：" + e.getMessage(), e);
        }
    }

    @Override
    public String subscribe() {
        try {
            Message message = consumer.receive(1000);
            if (message == null) {
                return null;
            }
            String body = extractMessageBody(message);
            if (body != null) {
                message.acknowledge();
                consumedCount.incrementAndGet();
            }
            return body;
        } catch (Exception e) {
            lastError.set(e);
            log.error("[ActiveMQ] 拉取消息异常，queue={}", queueName, e);
            return null;
        }
    }

    @Override
    public String subscribeAsync(IMessageHandler handler) {
        if (!running.compareAndSet(false, true)) {
            log.warn("[ActiveMQ] 订阅者已在运行中，queue={}", queueName);
            return queueName;
        }
        if (consumerExecutor != null) {
            threadGuard = new ConsumerThreadGuard("activemq-" + queueName, 10, consumerExecutor);
            threadGuard.start(() -> consumeLoop(handler));
            log.info("[ActiveMQ] 异步消费已启动（线程池托管），queue={}", queueName);
        } else {
            consumerThread = new Thread(() -> consumeLoop(handler), "ydsz-queue-activemq-" + queueName);
            consumerThread.setDaemon(true);
            consumerThread.start();
            log.warn("[ActiveMQ] 异步消费已启动（裸线程，不推荐），queue={}", queueName);
        }
        return queueName;
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        log.info("[ActiveMQ] 收到停止信号，queue={}", queueName);
        if (threadGuard != null) {
            threadGuard.stop();
        }
        if (consumerThread != null) {
            consumerThread.interrupt();
        }
        try {
            if (consumer != null) {
                consumer.close();
            }
            if (session != null) {
                session.close();
            }
            if (connection != null) {
                connection.close();
            }
            log.info("[ActiveMQ] 订阅者已停止，queue={}", queueName);
        } catch (Exception e) {
            log.warn("[ActiveMQ] 停止订阅者时发生异常", e);
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public Object getChannel() {
        return queueName;
    }

    @Override
    public String getConsumerId() {
        return queueName;
    }

    @Override
    public int getConsumedCount() {
        return (int) consumedCount.get();
    }

    @Override
    public Throwable getLastError() {
        return lastError.get();
    }

    private void consumeLoop(IMessageHandler handler) {
        try {
            while (running.get()) {
                try {
                    Message message = consumer.receive(1000);
                    if (message == null) {
                        continue;
                    }
                    rateLimiter.acquire();
                    processMessage(message, handler);
                } catch (Exception e) {
                    lastError.set(e);
                    log.error("[ActiveMQ] 消费循环异常，queue={}", queueName, e);
                    sleepQuietly(1000);
                }
            }
        } finally {
            running.set(false);
            log.info("[ActiveMQ] 消费循环已退出，queue={}", queueName);
        }
    }

    private String processMessageBody(Message message) {
        try {
            return message.getJMSMessageID();
        } catch (JMSException e) {
            return "unknown";
        }
    }

    private void processMessage(Message message, IMessageHandler handler) {
        if (message == null) {
            return;
        }
        String messageId = processMessageBody(message);
        try {
            String body = extractMessageBody(message);
            if (body == null) {
                return;
            }
            QueueMessage queueMessage = QueueMessage.fromPayload(body);
            if (queueMessage == null) {
                queueMessage = QueueMessage.of(body);
            }
            queueMessage.setTraceId(messageId);

            if (handler != null) {
                handler.onMessage(queueMessage);
            }
            message.acknowledge();
            consumedCount.incrementAndGet();
            lastError.set(null);
            log.debug("[ActiveMQ] 消息处理成功，queue={}, messageId={}", queueName, messageId);
        } catch (Exception e) {
            lastError.set(e);
            log.error("[ActiveMQ] 消息处理异常，queue={}, messageId={}", queueName, messageId, e);
        }
    }

    private String extractMessageBody(Message message) {
        try {
            if (message instanceof TextMessage) {
                return ((TextMessage) message).getText();
            }
            return message.toString();
        } catch (JMSException e) {
            log.warn("[ActiveMQ] 提取消息体失败", e);
            return null;
        }
    }

    private ActiveMQConnectionFactory createConnectionFactory(ActiveMQProperties properties) {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();
        factory.setBrokerURL(properties.resolvedBrokerUrl());
        factory.setUserName(properties.resolvedUsername());
        factory.setPassword(properties.resolvedPassword());
        return factory;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}