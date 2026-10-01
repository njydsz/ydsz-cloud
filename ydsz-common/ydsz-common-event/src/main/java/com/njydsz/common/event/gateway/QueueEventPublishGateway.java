package com.njydsz.common.event.gateway;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import com.njydsz.common.event.model.OutboxMessage;
import com.njydsz.common.queue.domain.QueueMessage;
import com.njydsz.common.queue.enums.QueueType;
import com.njydsz.common.queue.queue.IMessageQueue;
import com.njydsz.common.queue.queue.IMessageQueueProvider;
import com.njydsz.common.queue.service.IMessagePublisher;

/**
 * 基于 common-queue 抽象的统一事件投递网关。
 *
 * <p>使用 {@link IMessageQueueProvider} 创建消息队列实例，底层 MQ 引擎由配置 {@code
 * ydsz.event.outbox.queue.type} 决定（STREAM / ROCKET / KAFKA）。
 *
 * <p>将 OutboxMessage 包装为 QueueMessage，tenantId / traceId / idempotencyKey / eventType
 * 放入 headers，由 common-queue 统一投递。
 *
 * @author ydsz-team
 * @since 26.09.30
 */
public class QueueEventPublishGateway implements EventPublishGateway {

  /** 日志实例 */
  private static final Logger LOG = LoggerFactory.getLogger(QueueEventPublishGateway.class);

  /** 默认 Topic 名称 */
  private static final String DEFAULT_TOPIC = "ydsz-outbox-events";

  /** 消息发布者 */
  private final IMessagePublisher publisher;

  /**
   * 构造函数
   *
   * @param provider IMessageQueueProvider 提供者
   * @param queueType 队列类型字符串
   */
  public QueueEventPublishGateway(ObjectProvider<IMessageQueueProvider> provider, String queueType) {
    QueueType type = QueueType.fromValue(queueType);
    if (type == null) {
      type = QueueType.STREAM;
    }
    IMessageQueueProvider queueProvider = provider.getIfAvailable();
    if (queueProvider == null) {
      throw new IllegalStateException("IMessageQueueProvider not available in container");
    }
    IMessageQueue queue = queueProvider.createMessageQueue(type);
    this.publisher = queue.createPublisher(DEFAULT_TOPIC);
    LOG.info("QueueEventPublishGateway initialized: queueType={}, topic={}", type, DEFAULT_TOPIC);
  }

  /**
   * 投递单条消息到消息队列
   *
   * @param message Outbox 消息
   * @return true 投递成功，false 投递失败
   * @throws Throwable 投递异常
   */
  @Override
  public boolean publish(OutboxMessage message) throws Throwable {
    try {
      QueueMessage queueMessage = buildQueueMessage(message);
      publisher.publish(queueMessage);
      LOG.debug("Queue publish OK: id={}, eventType={}", message.getId(), message.getEventType());
      return true;
    } catch (Exception e) {
      LOG.error("Queue publish error: id={}, error={}", message.getId(), e.getMessage());
      throw e;
    }
  }

  /**
   * 批量投递消息到消息队列
   *
   * <p>通过 publisher.publishBatch() 利用 common-queue 的批量发布能力， 底层实现可利用原生批量 API（如
   * Redis Pipeline、Kafka batch）提升吞吐量。
   *
   * @param messages Outbox 消息列表
   * @return 每条消息的投递结果（true=成功，false=失败），顺序与输入一致
   * @throws Throwable 投递异常
   */
  @Override
  public List<Boolean> publishBatch(List<OutboxMessage> messages) throws Throwable {
    if (messages == null || messages.isEmpty()) {
      return List.of();
    }
    if (messages.size() == 1) {
      return List.of(publish(messages.get(0)));
    }

    try {
      List<QueueMessage> queueMessages = new ArrayList<>(messages.size());
      for (OutboxMessage msg : messages) {
        queueMessages.add(buildQueueMessage(msg));
      }
      publisher.publishBatch(queueMessages);
      LOG.debug("Queue batch publish OK: count={}", messages.size());
      List<Boolean> results = new ArrayList<>(messages.size());
      for (int i = 0; i < messages.size(); i++) {
        results.add(true);
      }
      return results;
    } catch (Exception e) {
      LOG.warn("Queue batch publish failed, falling back to single publish: error={}", e.getMessage());
      List<Boolean> results = new ArrayList<>(messages.size());
      for (OutboxMessage msg : messages) {
        try {
          results.add(publish(msg));
        } catch (Throwable ex) {
          results.add(false);
        }
      }
      return results;
    }
  }

  /**
   * 将 OutboxMessage 包装为 QueueMessage，设置 headers
   *
   * @param message Outbox 消息
   * @return QueueMessage 实例
   */
  private QueueMessage buildQueueMessage(OutboxMessage message) {
    QueueMessage queueMessage = QueueMessage.of(message.getPayload());
    if (message.getTenantId() != null) {
      queueMessage.addHeader("tenantId", message.getTenantId());
    }
    if (message.getTraceId() != null) {
      queueMessage.addHeader("traceId", message.getTraceId());
    }
    if (message.getIdempotencyKey() != null) {
      queueMessage.addHeader("idempotencyKey", message.getIdempotencyKey());
    }
    if (message.getEventType() != null) {
      queueMessage.addHeader("eventType", message.getEventType());
    }
    queueMessage.addHeader("outboxId", message.getId());
    return queueMessage;
  }
}
