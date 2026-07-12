package com.njydsz.pmis.common.queue.mq.rabbit;

import com.njydsz.pmis.common.exception.custom.InfrastructureException;
import com.njydsz.pmis.common.queue.domain.QueueMessage;
import com.njydsz.pmis.common.queue.rate.ConsumerRateLimiter;
import com.njydsz.pmis.common.queue.service.IMessageHandler;
import com.njydsz.pmis.common.queue.service.IMessageSubscriber;
import com.njydsz.pmis.common.queue.trace.MessageTracer;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * RabbitMQ 消息订阅者
 *
 * <p>使用原生 amqp-client 实现 RabbitMQ 消息消费功能。
 * 支持手动 ACK、消息确认等高级特性。
 *
 * <p><b>技术特点：</b>
 * <ul>
 *   <li>多种交换机：支持 Direct、Fanout、Topic 等</li>
 *   <li>手动 ACK：支持手动消息确认</li>
 *   <li>消费限流：支持 prefetchCount 限流</li>
 *   <li>并发消费：支持多线程并发消费</li>
 * </ul>
 *
 * <p><b>消息确认机制：</b>
 * <ul>
 *   <li>ACK：确认消息已成功处理</li>
 *   <li>NACK：拒绝消息，可选择是否重新入队</li>
 *   <li>REJECT：拒绝消息，不重新入队</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Slf4j
public class RabbitMQSubscriber implements IMessageSubscriber {

    private static final String HEADER_RETRY_COUNT = "x-retry-count";
    private static final int DEFAULT_MAX_RETRY_COUNT = 3;

    private final Connection connection;
    private final Channel channel;
    private final String queueName;
    private final int maxRetryCount;

    private final AtomicBoolean running;
    private final AtomicLong consumedCount;
    private final AtomicReference<Throwable> lastError;
    private final ConsumerRateLimiter rateLimiter;

    public RabbitMQSubscriber(RabbitMQProperties properties, String queueName) {
        if (properties == null) {
            throw new IllegalArgumentException("RabbitMQ 配置不能为空");
        }
        if (queueName == null || queueName.isEmpty()) {
            throw new IllegalArgumentException("队列名称不能为空");
        }
        this.queueName = queueName;
        this.maxRetryCount = DEFAULT_MAX_RETRY_COUNT;
        this.running = new AtomicBoolean(false);
        this.consumedCount = new AtomicLong(0);
        this.lastError = new AtomicReference<>();
        this.rateLimiter = new ConsumerRateLimiter(properties.resolvedConsumerRateLimitPerSecond());

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(properties.resolvedHost());
        factory.setPort(properties.resolvedPort());
        factory.setUsername(properties.resolvedUsername());
        factory.setPassword(properties.resolvedPassword());
        factory.setVirtualHost(properties.resolvedVirtualHost());

        try {
            this.connection = factory.newConnection();
            this.channel = connection.createChannel();
            channel.basicQos(properties.getPrefetchCount());
            log.info("[RabbitMQ] 订阅者初始化完成，queue={}", queueName);
        } catch (IOException | TimeoutException e) {
            log.error("[RabbitMQ] 初始化订阅者失败，queue={}", queueName, e);
            throw new InfrastructureException("RabbitMQ 订阅者初始化失败：" + e.getMessage(), e);
        }
    }

    @Override
    public String subscribe() {
        try {
            AMQP.Queue.DeclareOk result = channel.queueDeclarePassive(queueName);
            if (result == null || result.getMessageCount() == 0) {
                return null;
            }
            GetResponse response = channel.basicGet(queueName, true);
            if (response == null) {
                return null;
            }
            return new String(response.getBody(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            lastError.set(e);
            log.error("[RabbitMQ] 拉取消息异常，queue={}", queueName, e);
            return null;
        }
    }

    @Override
    public String subscribeAsync(IMessageHandler handler) {
        if (!running.compareAndSet(false, true)) {
            log.warn("[RabbitMQ] 订阅者已在运行中，queue={}", queueName);
            return queueName;
        }
        try {
            channel.basicConsume(queueName, false, new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope,
                                          AMQP.BasicProperties properties, byte[] body) {
                    rateLimiter.acquire();
                    processMessage(body, handler, envelope, properties);
                }
            });
            log.info("[RabbitMQ] 异步消费已启动，queue={}", queueName);
        } catch (Exception e) {
            running.set(false);
            lastError.set(e);
            log.error("[RabbitMQ] 启动消费者失败，queue={}", queueName, e);
            throw new InfrastructureException("RabbitMQ 消费者启动失败：" + e.getMessage(), e);
        }
        return queueName;
    }

    private void processMessage(byte[] body, IMessageHandler handler, Envelope envelope,
                                AMQP.BasicProperties properties) {
        if (body == null || body.length == 0) {
            return;
        }
        try {
            String bodyStr = new String(body, StandardCharsets.UTF_8);
            QueueMessage message = QueueMessage.fromPayload(bodyStr);
            if (message == null) {
                message = QueueMessage.of(bodyStr);
            }
            MessageTracer.injectTraceId(message.getTraceId());
            if (handler != null) {
                handler.onMessage(message);
            }
            channel.basicAck(envelope.getDeliveryTag(), false);
            consumedCount.incrementAndGet();
            lastError.set(null);
            log.debug("[RabbitMQ] 消息处理成功，queue={}, deliveryTag={}, traceId={}",
                    queueName, envelope.getDeliveryTag(), message.getTraceId());
        } catch (Exception e) {
            lastError.set(e);
            int retryCount = getRetryCount(properties);
            log.error("[RabbitMQ] 消息处理异常，queue={}, deliveryTag={}, retryCount={}/{}",
                    queueName, envelope.getDeliveryTag(), retryCount, maxRetryCount, e);
            handleFailedMessage(body, properties, envelope, retryCount);
        } finally {
            MessageTracer.clearTraceId();
        }
    }

    private void handleFailedMessage(byte[] body, AMQP.BasicProperties properties,
                                     Envelope envelope, int retryCount) {
        if (retryCount >= maxRetryCount) {
            safeNack(envelope.getDeliveryTag());
            log.error("[RabbitMQ] 消息已达最大重试次数，拒绝并不重新入队，queue={}, deliveryTag={}, retryCount={}",
                    queueName, envelope.getDeliveryTag(), retryCount);
            return;
        }
        try {
            republishWithRetryCount(body, properties, retryCount + 1);
            channel.basicAck(envelope.getDeliveryTag(), false);
            log.warn("[RabbitMQ] 消息将重试，queue={}, deliveryTag={}, retryCount={}/{}",
                    queueName, envelope.getDeliveryTag(), retryCount + 1, maxRetryCount);
        } catch (IOException ex) {
            log.error("[RabbitMQ] 重发消息失败，拒绝并不重新入队，queue={}, deliveryTag={}",
                    queueName, envelope.getDeliveryTag(), ex);
            safeNack(envelope.getDeliveryTag());
        }
    }

    private void safeNack(long deliveryTag) {
        try {
            channel.basicNack(deliveryTag, false, false);
        } catch (IOException ex) {
            log.error("[RabbitMQ] NACK失败，queue={}, deliveryTag={}", queueName, deliveryTag, ex);
        }
    }

    private int getRetryCount(AMQP.BasicProperties properties) {
        if (properties == null || properties.getHeaders() == null) {
            return 0;
        }
        Object value = properties.getHeaders().get(HEADER_RETRY_COUNT);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    private void republishWithRetryCount(byte[] body, AMQP.BasicProperties originalProperties,
                                         int newRetryCount) throws IOException {
        Map<String, Object> headers = originalProperties != null && originalProperties.getHeaders() != null
                ? new HashMap<>(originalProperties.getHeaders())
                : new HashMap<>(4);
        headers.put(HEADER_RETRY_COUNT, newRetryCount);

        AMQP.BasicProperties newProperties = new AMQP.BasicProperties.Builder()
                .headers(headers)
                .deliveryMode(originalProperties != null ? originalProperties.getDeliveryMode() : 2)
                .build();

        channel.basicPublish("", queueName, newProperties, body);
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
            log.info("[RabbitMQ] 订阅者已停止，queue={}", queueName);
        } catch (Exception e) {
            log.warn("[RabbitMQ] 停止订阅者时发生异常", e);
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
}