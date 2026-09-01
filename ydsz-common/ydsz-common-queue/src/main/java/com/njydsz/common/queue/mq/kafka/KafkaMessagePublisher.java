package com.njydsz.common.queue.mq.kafka;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantLock;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;

import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.queue.domain.QueueMessage;
import com.njydsz.common.queue.service.IMessagePublisher;

/**
 * Kafka 消息发布者
 *
 * <p>使用 Kafka Producer API 实现消息发布功能。 支持同步和异步发送，自动处理消息序列化。
 *
 * <p><b>技术特点：</b>
 *
 * <ul>
 *   <li>高吞吐量：适合大规模消息传递场景
 *   <li>异步发送：支持异步发送提高性能
 *   <li>分区支持：可根据消息键实现消息分区
 *   <li>批量发送：支持批量发送减少网络开销
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * KafkaMessagePublisher publisher = new KafkaMessagePublisher(properties, "my-topic");
 * publisher.publish("Hello Kafka");
 * publisher.publish(QueueMessage.of("Hello"));
 * publisher.close();
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class KafkaMessagePublisher implements IMessagePublisher {

  private final KafkaProducer<String, String> producer;
  private final String topic;
  private volatile boolean closed = false;
  private final ReentrantLock closeLock = new ReentrantLock();

  public KafkaMessagePublisher(KafkaQueueProperties properties, String topic) {
    if (properties == null) {
      throw new IllegalArgumentException("Kafka 配置不能为空");
    }
    if (topic == null || topic.isEmpty()) {
      throw new IllegalArgumentException("主题名称不能为空");
    }
    this.topic = topic;
    this.producer = createProducer(properties);
    log.info(
        "[Kafka] 发布者初始化完成，topic={}, bootstrapServers={}",
        topic,
        properties.resolvedBootstrapServers());
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
      log.error("[Kafka] 消息发布失败，topic={}", topic, e);
      throw SysException.builder().message("Kafka 消息发布失败：" + e.getMessage()).cause(e).build();
    }
  }

  @Override
  public void publish(QueueMessage message) {
    if (message == null || closed) {
      return;
    }
    try {
      String payload = QueueMessage.toPayload(message);
      String key = message.getTraceId();
      ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);
      producer.send(
          record,
          new Callback() {
            @Override
            public void onCompletion(RecordMetadata metadata, Exception exception) {
              if (exception != null) {
                log.error(
                    "[Kafka] 消息发送失败回调，topic={}, traceId={}, partition={}, error={}",
                    topic,
                    key,
                    metadata != null ? metadata.partition() : -1,
                    exception.getMessage());
              } else if (log.isDebugEnabled()) {
                log.debug(
                    "[Kafka] 消息发送成功，topic={}, traceId={}, partition={}, offset={}",
                    topic,
                    key,
                    metadata.partition(),
                    metadata.offset());
              }
            }
          });
    } catch (Exception e) {
      log.error("[Kafka] 消息发布失败，topic={}, traceId={}", topic, message.getTraceId(), e);
      throw SysException.builder().message("Kafka 消息发布失败：" + e.getMessage()).cause(e).build();
    }
  }

  /**
   * 发布延迟消息。
   *
   * <p>Kafka 原生不支持任意延迟投递（仅部分版本提供固定层级的延迟 topic），因此本实现<b>不保证延迟语义
   * </b>：记录一条 warn 日志后立即按普通消息投递，即消息会被消费者立刻收到。
   *
   * <p>依赖延迟投递的业务请改用 RocketMQ 等支持定时消息的通道，或在上层自行实现延迟队列。
   *
   * @param message 待投递消息，不允许为 {@code null}
   * @param delayMillis 期望延迟毫秒数，本实现忽略该参数
   */
  public void publishDelayed(QueueMessage message, long delayMillis) {
    log.warn("[Kafka] 延迟消息暂不支持，topic={}", topic);
    publish(message);
  }

  /**
   * 发布顺序消息，以消息分组键作为 Kafka 分区键保证分区内有序。
   *
   * <p>前提条件：{@code message.isSequential()} 为 {@code true}，即已设置 {@code messageGroupKey}；
   * Kafka 只能保证<b>同一分区内</b>的顺序，跨分区仍可能乱序，因此同一业务流的消息必须使用相同的分组键。
   *
   * <p>发布器已关闭（{@code closed == true}）时静默返回，不投递也不抛异常。
   *
   * @param message 待投递的顺序消息，必须设置 {@code messageGroupKey}，否则视为非法参数
   * @throws IllegalArgumentException 消息为 {@code null} 或未设置 {@code messageGroupKey} 时抛出
   */
  public void publishSequential(QueueMessage message) {
    if (message == null || !message.isSequential()) {
      throw new IllegalArgumentException("顺序消息必须设置 messageGroupKey");
    }
    if (closed) {
      return;
    }
    try {
      String payload = QueueMessage.toPayload(message);
      String key = message.getMessageGroupKey();
      ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);
      producer.send(
          record,
          new Callback() {
            @Override
            public void onCompletion(RecordMetadata metadata, Exception exception) {
              if (exception != null) {
                log.error(
                    "[Kafka] 顺序消息发送失败回调，topic={}, groupKey={}, partition={}, error={}",
                    topic,
                    key,
                    metadata != null ? metadata.partition() : -1,
                    exception.getMessage());
              } else if (log.isDebugEnabled()) {
                log.debug(
                    "[Kafka] 顺序消息发送成功，topic={}, groupKey={}, partition={}, offset={}",
                    topic,
                    key,
                    metadata.partition(),
                    metadata.offset());
              }
            }
          });
    } catch (Exception e) {
      log.error("[Kafka] 顺序消息发布失败，topic={}, groupKey={}", topic, message.getMessageGroupKey(), e);
      throw SysException.builder().message("Kafka 顺序消息发布失败：" + e.getMessage()).cause(e).build();
    }
  }

  @Override
  public void publishBatch(List<QueueMessage> messages) {
    if (messages == null || messages.isEmpty()) {
      return;
    }
    if (closed) {
      return;
    }
    try {
      List<ProducerRecord<String, String>> records = new ArrayList<>(messages.size());
      for (QueueMessage message : messages) {
        if (message == null) {
          continue;
        }
        String payload = QueueMessage.toPayload(message);
        String key = message.getTraceId();
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);
        records.add(record);
      }
      for (ProducerRecord<String, String> record : records) {
        producer.send(
            record,
            new Callback() {
              @Override
              public void onCompletion(RecordMetadata metadata, Exception exception) {
                if (exception != null) {
                  log.error(
                      "[Kafka] 批量消息发送失败回调，topic={}, partition={}, error={}",
                      topic,
                      metadata != null ? metadata.partition() : -1,
                      exception.getMessage());
                } else if (log.isDebugEnabled()) {
                  log.debug(
                      "[Kafka] 批量消息发送成功，topic={}, partition={}, offset={}",
                      topic,
                      metadata.partition(),
                      metadata.offset());
                }
              }
            });
      }
      producer.flush();
    } catch (Exception e) {
      log.error("[Kafka] 批量消息发布失败，topic={}", topic, e);
      throw SysException.builder().message("Kafka 批量消息发布失败：" + e.getMessage()).cause(e).build();
    }
  }

  public String getChannel() {
    return topic;
  }

  public boolean isActive() {
    return !closed && producer != null;
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
          producer.flush();
          producer.close(Duration.ofSeconds(5));
          log.info("[Kafka] 发布者已关闭，topic={}", topic);
        }
      } catch (Exception e) {
        log.warn("[Kafka] 关闭发布者时发生异常", e);
      }
    } finally {
      closeLock.unlock();
    }
  }

  private KafkaProducer<String, String> createProducer(KafkaQueueProperties properties) {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.resolvedBootstrapServers());
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    props.put(ProducerConfig.RETRIES_CONFIG, 3);
    props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
    props.put(ProducerConfig.LINGER_MS_CONFIG, 5);
    props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
    props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000);
    return new KafkaProducer<>(props);
  }
}
