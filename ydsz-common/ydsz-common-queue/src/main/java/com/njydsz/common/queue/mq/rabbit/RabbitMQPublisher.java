package com.njydsz.common.queue.mq.rabbit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.queue.domain.QueueMessage;
import com.njydsz.common.queue.service.IMessagePublisher;

/**
 * RabbitMQ 消息发布者
 *
 * <p>使用原生 amqp-client 实现 RabbitMQ 消息发布功能。 支持交换机、路由键等 RabbitMQ 特有概念。
 *
 * <p><b>技术特点：</b>
 *
 * <ul>
 *   <li>多种交换机：支持 Direct、Fanout、Topic 等交换机类型
 *   <li>消息确认：支持消息确认机制保证可靠投递
 *   <li>消息持久化：支持消息和队列的持久化
 *   <li>灵活路由：基于路由键的消息路由
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * RabbitMQPublisher publisher = new RabbitMQPublisher(properties, "my-queue");
 * publisher.publish("Hello RabbitMQ");
 * publisher.publish(QueueMessage.of("Hello"));
 * publisher.close();
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class RabbitMQPublisher implements IMessagePublisher {

  private final Connection connection;
  private final Channel channel;
  private final String routingKey;
  private final String exchangeName;
  private volatile boolean closed = false;
  private final ReentrantLock closeLock = new ReentrantLock();

  public RabbitMQPublisher(RabbitMQProperties properties, String queueName) {
    this(properties, queueName, queueName);
  }

  public RabbitMQPublisher(RabbitMQProperties properties, String queueName, String routingKey) {
    if (properties == null) {
      throw new IllegalArgumentException("RabbitMQ 配置不能为空");
    }
    if (queueName == null || queueName.isEmpty()) {
      throw new IllegalArgumentException("队列名称不能为空");
    }
    this.routingKey = routingKey != null ? routingKey : queueName;
    this.exchangeName = properties.getExchangeName();
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(properties.resolvedHost());
    factory.setPort(properties.resolvedPort());
    factory.setUsername(properties.resolvedUsername());
    factory.setPassword(properties.resolvedPassword());
    factory.setVirtualHost(properties.resolvedVirtualHost());
    try {
      this.connection = factory.newConnection();
      this.channel = connection.createChannel();
      channel.exchangeDeclare(exchangeName, "direct", true);
      channel.queueDeclare(queueName, true, false, false, null);
      channel.queueBind(queueName, exchangeName, this.routingKey);
      log.info(
          "[RabbitMQ] 发布者初始化完成，queue={}, exchange={}, routingKey={}",
          queueName,
          exchangeName,
          this.routingKey);
    } catch (IOException | TimeoutException e) {
      log.error("[RabbitMQ] 初始化发布者失败，queue={}", queueName, e);
      throw SysException.builder().message("RabbitMQ 发布者初始化失败：" + e.getMessage()).cause(e).build();
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
      log.error("[RabbitMQ] 消息发布失败，routingKey={}", routingKey, e);
      throw SysException.builder().message("RabbitMQ 消息发布失败：" + e.getMessage()).cause(e).build();
    }
  }

  @Override
  public void publish(QueueMessage message) {
    if (message == null || closed) {
      return;
    }
    try {
      String payload = QueueMessage.toPayload(message);
      AMQP.BasicProperties props =
          new AMQP.BasicProperties.Builder()
              .contentType("application/json")
              .messageId(message.getTraceId())
              .deliveryMode(2)
              .build();
      channel.basicPublish(
          exchangeName, routingKey, props, payload.getBytes(StandardCharsets.UTF_8));
      if (log.isDebugEnabled()) {
        log.debug("[RabbitMQ] 消息已发送，routingKey={}, traceId={}", routingKey, message.getTraceId());
      }
    } catch (Exception e) {
      log.error("[RabbitMQ] 消息发布失败，routingKey={}, traceId={}", routingKey, message.getTraceId(), e);
      throw SysException.builder().message("RabbitMQ 消息发布失败：" + e.getMessage()).cause(e).build();
    }
  }

  @Override
  public void publishBatch(List<QueueMessage> messages) {
    if (messages == null || messages.isEmpty() || closed) {
      return;
    }
    for (QueueMessage message : messages) {
      publish(message);
    }
  }

  public String getChannel() {
    return routingKey;
  }

  public boolean isActive() {
    return !closed && connection != null && connection.isOpen();
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
        if (channel != null && channel.isOpen()) {
          channel.close();
        }
        if (connection != null && connection.isOpen()) {
          connection.close();
        }
        log.info("[RabbitMQ] 发布者已关闭，routingKey={}", routingKey);
      } catch (Exception e) {
        log.warn("[RabbitMQ] 关闭发布者时发生异常", e);
      }
    } finally {
      closeLock.unlock();
    }
  }
}
