package com.njydsz.message.server.event;

import java.time.Instant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.njydsz.common.event.model.OutboxMessage;
import com.njydsz.common.event.repository.OutboxRepository;
import com.njydsz.message.domain.dto.MessageItemRequestDTO;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.lock.annotation.DistributedScheduled;
import com.njydsz.message.server.metric.MessageMetrics;
import com.njydsz.message.server.producer.MessageQueueOperations;

/**
 * Outbox 事件扫描发布调度器，定时扫描待处理事件并分发到 MQ 或 Spring 事件总线。
 *
 * <p>扫描 Outbox 表中 PENDING 事件（委托 common-event OutboxRepository），
 * 根据事件类型分发：MessageAsyncDispatch → 反序列化为 MessageItemRequestDTO 投递到 MQ；
 * 其他领域事件 → 发布到 Spring 事件总线。
 * 发布成功标记为 SENT，失败则根据重试次数决定指数退避重试或标记为 DEAD_LETTER。
 * 多实例部署通过 DistributedScheduled 分布式锁保证单实例执行扫描。
 *
 * @author ydsz
 * @since 26.09.24
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "ydsz.message.outbox",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OutboxEventScheduler {

  /** 异步消息投递事件类型常量 */
  private static final String EVENT_TYPE_ASYNC_DISPATCH = "MessageAsyncDispatch";

  private final OutboxRepository outboxRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final MessageMetrics messageMetrics;
  private final ObjectProvider<MessageQueueOperations> mqOperationsProvider;

  /** 每次扫描最大处理数量 */
  private static final int SCAN_BATCH_SIZE = 100;

  /** 事件发布最大重试次数 */
  private static final int MAX_PUBLISH_RETRIES = 5;

  /**
   * 定时扫描并发布待处理的 Outbox 事件。
   *
   * <p>扫描 PENDING 状态事件（经 {@link OutboxRepository#findPending} 内部校验 next_retry_at），
   * 分发到 MQ 或 Spring 事件总线。
   */
  @Scheduled(fixedDelayString = "${ydsz.message.outbox.scan-interval-ms:5000}")
  @DistributedScheduled(lockKey = "message:outbox-publish", leaseTime = 30)
  public void scanAndPublish() {
    java.util.List<OutboxMessage> pendingEvents =
        outboxRepository.findPending(SCAN_BATCH_SIZE);

    if (pendingEvents.isEmpty()) {
      return;
    }

    log.info("[OutboxScheduler] 扫描到 {} 条待发布事件", pendingEvents.size());

    int published = 0;
    int failed = 0;

    for (OutboxMessage outboxMessage : pendingEvents) {
      // CAS 抢占：PENDING → PROCESSING
      if (!outboxRepository.claimForProcessing(outboxMessage.getId())) {
        // 已被其他实例抢占
        continue;
      }

      try {
        // 根据事件类型分发
        dispatchOutboxEvent(outboxMessage);
        // 标记为已发送
        outboxRepository.markAsSent(outboxMessage.getId());
        published++;
      } catch (Exception e) {
        log.error(
            "[OutboxScheduler] 事件发布失败: eventId={} eventType={} err={}",
            outboxMessage.getId(),
            outboxMessage.getEventType(),
            e.getMessage(),
            e);
        // 标记失败（内部根据 retryCount 与 maxRetries 决定重试或死信）
        handleFailure(outboxMessage, e.getMessage());
        failed++;
      }
    }

    if (published > 0 || failed > 0) {
      log.info("[OutboxScheduler] 发布完成: published={} failed={}", published, failed);
    }
  }

  /**
   * 处理投递失败：计算退避时间并更新状态。
   *
   * @param message Outbox 消息
   * @param errorMessage 错误信息
   */
  private void handleFailure(OutboxMessage message, String errorMessage) {
    long backoffSeconds = calculateBackoff(message.getRetryCount());
    outboxRepository.markAsFailed(message.getId(), errorMessage, backoffSeconds);
  }

  /**
   * 指数退避计算。
   *
   * @param retryCount 当前重试次数
   * @return 退避秒数
   */
  private long calculateBackoff(long retryCount) {
    int shift = Math.min((int) retryCount, 30);
    long backoff = (long) (Math.pow(2, shift) * 1000);
    return Math.min(backoff, 300);
  }

  /**
   * 根据 Outbox 事件类型分发到不同处理器。
   *
   * <ul>
   *   <li>{@code MessageAsyncDispatch} —— 反序列化为 {@link MessageItemRequestDTO} 后投递到 MQ</li>
   *   <li>其他 —— 发布到 Spring 事件总线</li>
   * </ul>
   *
   * @param outboxMessage Outbox 消息
   */
  private void dispatchOutboxEvent(OutboxMessage outboxMessage) {
    try {
      if (EVENT_TYPE_ASYNC_DISPATCH.equals(outboxMessage.getEventType())) {
        // 异步消息投递：反序列化后发送到 MQ
        dispatchAsyncMessage(outboxMessage);
      } else {
        // 领域事件：发布到 Spring 事件总线
        publishDomainEvent(outboxMessage);
      }
    } catch (Exception e) {
      log.error(
          "[OutboxScheduler] 事件分发异常: eventId={} type={} err={}",
          outboxMessage.getId(),
          outboxMessage.getEventType(),
          e.getMessage(),
          e);
    }
  }

  /**
   * 异步消息投递：将 Outbox 事件反序列化为 {@link MessageItemRequestDTO} 后投递到 MQ。
   *
   * @param outboxMessage Outbox 消息
   */
  private void dispatchAsyncMessage(OutboxMessage outboxMessage) {
    MessageItemRequestDTO request = YdszJson.fromJson(outboxMessage.getPayload(), MessageItemRequestDTO.class);
    if (request == null) {
      log.error(
          "[OutboxScheduler] 异步消息反序列化失败: eventId={} aggregateId={}",
          outboxMessage.getId(),
          outboxMessage.getAggregateId());
      return;
    }
    MessageQueueOperations mqOps = mqOperationsProvider.getIfAvailable();
    if (mqOps == null) {
      log.warn(
          "[OutboxScheduler] MQ 未配置,异步消息投递跳过: eventId={} aggregateId={}",
          outboxMessage.getId(),
          outboxMessage.getAggregateId());
      return;
    }
    mqOps.asyncSend(request);
    messageMetrics.recordSend("OUTBOX", "SUCCESS", 0);
    log.info(
        "[OutboxScheduler] 异步消息已投递 MQ: eventId={} aggregateId={} channel={}",
        outboxMessage.getId(),
        outboxMessage.getAggregateId(),
        request.getChannel());
  }

  /**
   * 将 Outbox 事件反序列化为领域事件并发布到 Spring 事件总线。
   *
   * <p>使用 {@link YdszJson} 进行 JSON 反序列化，符合《云顶编码规范》"禁止第三方 JSON 库"要求。
   *
   * @param outboxMessage Outbox 消息
   */
  private void publishDomainEvent(OutboxMessage outboxMessage) {
    String eventType = outboxMessage.getEventType();
    String payload = outboxMessage.getPayload();
    try {
      // 先尝试以 eventType 作为全限定类名解析
      Object domainEvent = deserializeEvent(eventType, payload);

      if (domainEvent != null) {
        eventPublisher.publishEvent(domainEvent);
        messageMetrics.recordSend("OUTBOX", "SUCCESS", 0);
        log.debug(
            "[OutboxScheduler] 事件已发布: eventId={} type={}",
            outboxMessage.getId(),
            eventType);
      } else {
        // 无法解析的事件类型，记录 WARN 但不抛异常（避免阻塞其他事件）
        log.warn(
            "[OutboxScheduler] 未知事件类型，跳过: eventId={} type={}",
            outboxMessage.getId(),
            eventType);
      }
    } catch (Exception e) {
      log.error(
          "[OutboxScheduler] 领域事件发布异常: eventId={} type={} err={}",
          outboxMessage.getId(),
          eventType,
          e.getMessage(),
          e);
    }
  }

  /**
   * 根据事件类型和 JSON 负载反序列化领域事件。
   *
   * <p>使用 {@link YdszJson} 替代 Jackson，符合编码规范。
   *
   * @param eventType 事件类型（类名）
   * @param payload JSON 负载
   * @return 反序列化后的领域事件对象
   */
  private Object deserializeEvent(String eventType, String payload) {
    try {
      // 尝试全限定类名
      Class<?> eventClass = Class.forName(eventType);
      return YdszJson.fromJson(payload, eventClass);
    } catch (ClassNotFoundException e) {
      // 尝试从领域事件包查找
      try {
        String fqcn = "com.njydsz.message.domain.event." + eventType;
        Class<?> eventClass = Class.forName(fqcn);
        return YdszJson.fromJson(payload, eventClass);
      } catch (ClassNotFoundException ex) {
        log.warn("无法反序列化事件: type={} err={}", eventType, ex.getMessage(), ex);
        return null;
      } catch (Exception ex) {
        log.warn("事件 JSON 解析失败: type={} err={}", eventType, ex.getMessage(), ex);
        return null;
      }
    } catch (Exception e) {
      log.warn("事件 JSON 解析失败: type={} err={}", eventType, e.getMessage(), e);
      return null;
    }
  }
}
