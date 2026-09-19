package com.njydsz.common.event.gateway;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.njydsz.common.event.model.OutboxMessage;

/**
 * Kafka 事件投递网关。
 *
 * <p>基于 {@link KafkaTemplate} 实现，将 Outbox 消息投递到 Kafka。仅当 classpath 存在 {@code KafkaTemplate}
 * 且容器中有 {@link KafkaTemplate} Bean 时自动装配。
 *
 * <p>投递策略：
 *
 * <ul>
 *   <li>Topic：固定为 {@code ydsz-outbox-events}（可通过构造参数覆盖）
 *   <li>Key：使用 {@code eventType} 作为 Kafka 分区键，相同类型的事件路由到同一分区
 *   <li>Header：tenantId / traceId / idempotencyKey / outboxId 以 Header 传递
 * </ul>
 *
 * <p>投递语义：单条同步投递（等待 ack，超时降级为失败）；批量投递逐条调用单条发送，失败不影响其他消息。
 *
 * @author ydsz-team
 * @since 26.09.13
 */
public class KafkaEventPublishGateway implements EventPublishGateway {

  /** 日志实例 */
  private static final Logger LOG = LoggerFactory.getLogger(KafkaEventPublishGateway.class);

  /** 默认 Topic 名称 */
  private static final String DEFAULT_TOPIC = "ydsz-outbox-events";

  /** 默认发送超时（秒） */
  private static final long DEFAULT_SEND_TIMEOUT_SECONDS = 5;

  /** Kafka 模板 */
  private final KafkaTemplate<String, String> kafkaTemplate;

  /** 目标 Topic */
  private final String topic;

  /** 发送超时（秒） */
  private final long sendTimeoutSeconds;

  /**
   * 构造 Kafka 投递网关。
   *
   * @param kafkaTemplate Kafka 模板
   * @param topic 目标 Topic（null 或空时使用默认值 ydsz-outbox-events）
   */
  public KafkaEventPublishGateway(KafkaTemplate<String, String> kafkaTemplate, String topic) {
    this(kafkaTemplate, topic, DEFAULT_SEND_TIMEOUT_SECONDS);
  }

  /**
   * 构造 Kafka 投递网关（指定超时）。
   *
   * @param kafkaTemplate Kafka 模板
   * @param topic 目标 Topic（null 或空时使用默认值 ydsz-outbox-events）
   * @param sendTimeoutSeconds 发送超时（秒）
   */
  public KafkaEventPublishGateway(
      KafkaTemplate<String, String> kafkaTemplate, String topic, long sendTimeoutSeconds) {
    this.kafkaTemplate = kafkaTemplate;
    this.topic = (topic != null && !topic.isBlank()) ? topic : DEFAULT_TOPIC;
    this.sendTimeoutSeconds = sendTimeoutSeconds > 0 ? sendTimeoutSeconds : DEFAULT_SEND_TIMEOUT_SECONDS;
  }

  /**
   * 投递单条消息到 Kafka。
   *
   * @param message Outbox 消息
   * @return true 投递成功，false 投递失败
   */
  @Override
  public boolean publish(OutboxMessage message) {
    String eventType = message.getEventType() != null ? message.getEventType() : "unknown";
    ProducerRecord<String, String> record = buildRecord(message, eventType);
    try {
      SendResult<String, String> result =
          kafkaTemplate.send(record).get(sendTimeoutSeconds, TimeUnit.SECONDS);
      if (result != null && result.getRecordMetadata() != null) {
        LOG.debug(
            "Kafka publish OK: id={}, topic={}, partition={}, offset={}",
            message.getId(),
            topic,
            result.getRecordMetadata().partition(),
            result.getRecordMetadata().offset());
        return true;
      }
      LOG.warn("Kafka publish returned null result: id={}", message.getId());
      return false;
    } catch (TimeoutException e) {
      LOG.error(
          "Kafka publish timeout: id={}, timeout={}s, error={}",
          message.getId(),
          sendTimeoutSeconds,
          e.getMessage());
      return false;
    } catch (ExecutionException e) {
      LOG.error(
          "Kafka publish error: id={}, error={}",
          message.getId(),
          e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn("Kafka publish interrupted: id={}", message.getId());
      return false;
    } catch (Exception e) {
      LOG.error("Kafka publish error: id={}, error={}", message.getId(), e.getMessage());
      return false;
    }
  }

  /**
   * 批量投递消息到 Kafka（逐条同步发送，失败不影响其他消息）。
   *
   * @param messages Outbox 消息列表
   * @return 每条消息的投递结果（true=成功，false=失败），顺序与输入一致
   */
  @Override
  public List<Boolean> publishBatch(List<OutboxMessage> messages) {
    if (messages == null || messages.isEmpty()) {
      return List.of();
    }
    if (messages.size() == 1) {
      return List.of(publish(messages.get(0)));
    }

    List<Boolean> results = new ArrayList<>(messages.size());
    int successCount = 0;
    int failCount = 0;
    for (OutboxMessage message : messages) {
      boolean success = publish(message);
      results.add(success);
      if (success) {
        successCount++;
      } else {
        failCount++;
      }
    }
    LOG.info(
        "Kafka batch publish completed: total={}, success={}, fail={}",
        messages.size(),
        successCount,
        failCount);
    return results;
  }

  /**
   * 构建 Kafka ProducerRecord。
   *
   * @param message Outbox 消息
   * @param eventType 事件类型（用作分区键）
   * @return ProducerRecord
   */
  private ProducerRecord<String, String> buildRecord(OutboxMessage message, String eventType) {
    List<Header> headers = new ArrayList<>();
    if (message.getTenantId() != null) {
      headers.add(new RecordHeader("tenantId", message.getTenantId().getBytes(StandardCharsets.UTF_8)));
    }
    if (message.getTraceId() != null) {
      headers.add(new RecordHeader("traceId", message.getTraceId().getBytes(StandardCharsets.UTF_8)));
    }
    if (message.getIdempotencyKey() != null) {
      headers.add(
          new RecordHeader(
              "idempotencyKey", message.getIdempotencyKey().getBytes(StandardCharsets.UTF_8)));
    }
    headers.add(
        new RecordHeader(
            "outboxId",
            message.getId() != null ? message.getId().toString().getBytes(StandardCharsets.UTF_8) : new byte[0]));
    headers.add(
        new RecordHeader(
            "eventType", eventType.getBytes(StandardCharsets.UTF_8)));

    ProducerRecord<String, String> record =
        new ProducerRecord<>(topic, null, eventType, message.getPayload(), headers);
    return record;
  }
}
