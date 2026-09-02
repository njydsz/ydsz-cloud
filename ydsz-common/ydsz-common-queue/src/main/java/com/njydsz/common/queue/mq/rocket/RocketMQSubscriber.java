package com.njydsz.common.queue.mq.rocket;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;

import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.queue.domain.QueueMessage;
import com.njydsz.common.queue.rate.ConsumerRateLimiter;
import com.njydsz.common.queue.service.IMessageHandler;
import com.njydsz.common.queue.service.IMessageSubscriber;

/**
 * RocketMQ 消息订阅者
 *
 * <p>使用 RocketMQ Push Consumer API 实现消息消费功能。 支持并发消费和顺序消费，提供消息重试和死信队列支持。
 *
 * <p><b>技术特点：</b>
 *
 * <ul>
 *   <li>Push 模式：服务端推送消息，客户端被动接收
 *   <li>并发消费：多线程并发处理消息
 *   <li>顺序消费：按消息顺序依次处理
 *   <li>消息重试：消费失败自动重试
 *   <li>死信队列：重试超过阈值进入死信队列
 * </ul>
 *
 * <p><b>消息重试机制：</b>
 *
 * <ul>
 *   <li>消费失败后，消息会在一定时间间隔后重试
 *   <li>默认重试 16 次，超过后进入死信队列
 *   <li>可通过配置调整重试次数和间隔
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class RocketMQSubscriber implements IMessageSubscriber {

  private final DefaultMQPushConsumer consumer;
  private final String topic;
  private final String groupId;

  private final AtomicBoolean running;
  private final AtomicLong consumedCount;
  private final AtomicReference<Throwable> lastError;
  private final ConsumerRateLimiter rateLimiter;

  public RocketMQSubscriber(RocketMQProperties properties, String topic) {
    if (properties == null) {
      throw new IllegalArgumentException("RocketMQ 配置不能为空");
    }
    if (topic == null || topic.isEmpty()) {
      throw new IllegalArgumentException("主题名称不能为空");
    }
    this.topic = topic;
    this.groupId = properties.resolvedGroupId();
    this.consumer = createConsumer(properties, topic);
    this.running = new AtomicBoolean(false);
    this.consumedCount = new AtomicLong(0);
    this.lastError = new AtomicReference<>();
    this.rateLimiter = new ConsumerRateLimiter(properties.getConsumerRateLimitPerSecond());
    log.info("[RocketMQ] 订阅者初始化完成，topic={}, groupId={}", topic, groupId);
  }

  @Override
  public String subscribe() {
    log.warn("[RocketMQ] 同步拉取模式暂不支持，请使用 subscribeAsync()");
    return groupId;
  }

  @Override
  public String subscribeAsync(IMessageHandler handler) {
    if (!running.compareAndSet(false, true)) {
      log.warn("[RocketMQ] 订阅者已在运行中，topic={}, groupId={}", topic, groupId);
      return groupId;
    }
    try {
      consumer.registerMessageListener(
          (MessageListenerConcurrently)
              (msgs, context) -> {
                for (MessageExt msgExt : msgs) {
                  rateLimiter.acquire();
                  processMessage(msgExt, handler);
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
              });
      consumer.start();
      log.info("[RocketMQ] 异步消费已启动，topic={}, groupId={}", topic, groupId);
    } catch (Exception e) {
      running.set(false);
      lastError.set(e);
      log.error("[RocketMQ] 启动消费者失败，topic={}, groupId={}", topic, groupId, e);
      throw SysException.builder().message("RocketMQ 消费者启动失败：" + e.getMessage()).cause(e).build();
    }
    return groupId;
  }

  @Override
  public void stop() {
    if (!running.compareAndSet(true, false)) {
      return;
    }
    try {
      if (consumer != null) {
        consumer.shutdown();
        log.info("[RocketMQ] 订阅者已停止，topic={}, groupId={}", topic, groupId);
      }
    } catch (Exception e) {
      log.warn("[RocketMQ] 停止订阅者时发生异常", e);
    }
  }

  @Override
  public boolean isRunning() {
    return running.get();
  }

  public Object getChannel() {
    return topic;
  }

  public String getConsumerId() {
    return groupId;
  }

  public int getConsumedCount() {
    return (int) consumedCount.get();
  }

  public Throwable getLastError() {
    return lastError.get();
  }

  private void processMessage(MessageExt msgExt, IMessageHandler handler) {
    if (msgExt == null || msgExt.getBody() == null) {
      return;
    }
    try {
      String body = new String(msgExt.getBody());
      QueueMessage message = QueueMessage.fromPayload(body);
      if (message == null) {
        message = QueueMessage.of(body);
      }
      message.setTraceId(msgExt.getKeys() != null ? msgExt.getKeys() : message.getTraceId());

      if (handler != null) {
        try {
          handler.onMessage(message);
        } catch (Throwable t) {
          throw new RuntimeException(t);
        }
      }
      consumedCount.incrementAndGet();
      lastError.set(null);
      log.debug(
          "[RocketMQ] 消息处理成功，topic={}, msgId={}, traceId={}",
          msgExt.getTopic(),
          msgExt.getMsgId(),
          message.getTraceId());
    } catch (Exception e) {
      lastError.set(e);
      log.error("[RocketMQ] 消息处理异常，topic={}, msgId={}", msgExt.getTopic(), msgExt.getMsgId(), e);
      throw SysException.builder().message("消息处理失败").cause(e).build();
    }
  }

  private DefaultMQPushConsumer createConsumer(RocketMQProperties properties, String topic) {
    try {
      DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(properties.resolvedGroupId());
      consumer.setNamesrvAddr(properties.resolvedNamesrvAddr());
      consumer.setConsumeThreadMin(properties.getConsumeThreadMin());
      consumer.setConsumeThreadMax(properties.getConsumeThreadMax());
      consumer.setConsumeMessageBatchMaxSize(properties.getConsumeMessageBatchMaxSize());
      consumer.setMaxReconsumeTimes(properties.getMaxRetryCount());
      consumer.subscribe(topic, properties.getTag());
      return consumer;
    } catch (Exception e) {
      log.error(
          "[RocketMQ] 创建消费者失败，topic={}, namesrvAddr={}",
          topic,
          properties.resolvedNamesrvAddr(),
          e);
      throw SysException.builder().message("RocketMQ 消费者创建失败：" + e.getMessage()).cause(e).build();
    }
  }
}
