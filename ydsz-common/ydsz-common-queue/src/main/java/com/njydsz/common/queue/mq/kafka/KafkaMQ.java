package com.njydsz.common.queue.mq.kafka;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.queue.queue.AbstractMessageQueue;
import com.njydsz.common.queue.service.IMessagePublisher;
import com.njydsz.common.queue.service.IMessageSubscriber;

/**
 * Kafka 消息队列
 *
 * <p>Kafka 是分布式事件流平台，具有高吞吐量、持久化、消息回溯等特点。 适合大规模消息传递、日志收集、流处理等场景。
 *
 * <p><b>资源管理：</b>
 *
 * <ul>
 *   <li>每个 Publisher 持有独立的 KafkaProducer
 *   <li>每个 Subscriber 持有独立的 KafkaConsumer
 *   <li>{@link #doClose()} 关闭所有已创建的 Producer 和 Consumer，防止资源泄漏
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class KafkaMQ extends AbstractMessageQueue {

  private final KafkaQueueProperties properties;
  private final ExecutorService consumerExecutor;
  private final List<KafkaMessagePublisher> publishers = new CopyOnWriteArrayList<>();
  private final List<KafkaMessageSubscriber> subscribers = new CopyOnWriteArrayList<>();

  public KafkaMQ(KafkaQueueProperties properties, ExecutorService consumerExecutor) {
    super("Kafka");
    if (properties == null) {
      throw BusinessException.builder().key("Kafka 配置不能为空").build();
    }
    this.properties = properties;
    this.consumerExecutor = consumerExecutor;
    // 连接校验延迟到首次 publish/subscribe（lazy init），避免 broker 启动顺序导致应用启动失败。
    log.info("[KafkaMQ] 初始化成功（连接延迟校验），bootstrapServers={}",
        properties.resolvedBootstrapServers());
  }

  @Override
  public IMessagePublisher createPublisher(String channel) {
    checkNotClosed();
    if (channel == null || channel.isEmpty()) {
      throw BusinessException.builder().key("主题名称不能为空").build();
    }
    KafkaMessagePublisher publisher = new KafkaMessagePublisher(properties, channel);
    publishers.add(publisher);
    return publisher;
  }

  @Override
  public IMessageSubscriber createSubscriber(String channel) {
    checkNotClosed();
    if (channel == null || channel.isEmpty()) {
      throw BusinessException.builder().key("主题名称不能为空").build();
    }
    KafkaMessageSubscriber subscriber =
        new KafkaMessageSubscriber(properties, channel, consumerExecutor);
    subscribers.add(subscriber);
    return subscriber;
  }

  @Override
  protected void doClose() {
    for (KafkaMessagePublisher publisher : publishers) {
      try {
        publisher.close();
      } catch (Exception e) {
        log.warn("[KafkaMQ] 关闭 Publisher 时异常", e);
      }
    }
    publishers.clear();

    for (KafkaMessageSubscriber subscriber : subscribers) {
      try {
        subscriber.stop();
      } catch (Exception e) {
        log.warn("[KafkaMQ] 关闭 Subscriber 时异常", e);
      }
    }
    subscribers.clear();

    log.info("[KafkaMQ] 所有资源已释放");
  }
}
