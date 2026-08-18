package com.njydsz.message.server.event;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.njydsz.common.lock.annotation.DistributedScheduled;
import com.njydsz.message.domain.event.OutboxEvent;
import com.njydsz.message.domain.repository.OutboxEventRepository;
import com.njydsz.message.server.metric.MessageMetrics;

/**
 * Outbox 事件扫描发布调度器。
 *
 * <p>定时扫描 {@code ydsz_msg_outbox} 表中 PENDING 事件，反序列化为领域事件后发布到 Spring 事件总线。
 * 发布成功标记为 PUBLISHED，失败则根据重试次数决定重试或标记为 FAILED。
 *
 * <p>多实例部署通过 {@link DistributedScheduled} 分布式锁保证只有一个实例执行扫描。
 *
 * @author ydsz-team
 * @since 1.2.0
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

  private final OutboxEventRepository outboxEventRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final MessageMetrics messageMetrics;
  private final ObjectMapper objectMapper;

  /** 每次扫描最大处理数量 */
  private static final int SCAN_BATCH_SIZE = 100;

  /** 事件发布最大重试次数 */
  private static final int MAX_PUBLISH_RETRIES = 5;

  /**
   * 定时扫描并发布待处理的 Outbox 事件。
   *
   * <p>扫描创建时间早于当前时间 5 秒以上的 PENDING 事件，避免与正在写入的事务冲突。
   */
  @Scheduled(fixedDelayString = "${ydsz.message.outbox.scan-interval-ms:5000}")
  @DistributedScheduled(lockKey = "message:outbox-publish", leaseTime = 30)
  public void scanAndPublish() {
    LocalDateTime cutoffTime = LocalDateTime.now().minusSeconds(5);
    List<OutboxEvent> pendingEvents =
        outboxEventRepository.findPending(SCAN_BATCH_SIZE, cutoffTime);

    if (pendingEvents.isEmpty()) {
      return;
    }

    log.info("[OutboxScheduler] 扫描到 {} 条待发布事件", pendingEvents.size());

    int published = 0;
    int failed = 0;

    for (OutboxEvent outboxEvent : pendingEvents) {
      // CAS 抢占：PENDING → PUBLISHING
      if (!outboxEventRepository.markPublishing(outboxEvent.getId())) {
        // 已被其他实例抢占
        continue;
      }

      try {
        // 反序列化并发布领域事件
        publishDomainEvent(outboxEvent);
        // 标记为已发布
        outboxEventRepository.markPublished(outboxEvent.getId());
        published++;
      } catch (Exception e) {
        log.error(
            "[OutboxScheduler] 事件发布失败: eventId={} eventType={} err={}",
            outboxEvent.getId(),
            outboxEvent.getEventType(),
            e.getMessage(),
            e);
        // 标记失败或重试
        outboxEventRepository.markFailed(outboxEvent.getId(), MAX_PUBLISH_RETRIES);
        failed++;
      }
    }

    if (published > 0 || failed > 0) {
      log.info("[OutboxScheduler] 发布完成: published={} failed={}", published, failed);
    }
  }

  /**
   * 将 Outbox 事件反序列化为领域事件并发布到 Spring 事件总线。
   *
   * @param outboxEvent Outbox 事件
   */
  private void publishDomainEvent(OutboxEvent outboxEvent) throws Exception {
    // 通过 eventType 反射构造目标事件
    String eventType = outboxEvent.getEventType();
    String payload = outboxEvent.getPayload();

    // 先尝试以 eventType 作为全限定类名解析
    Object domainEvent = deserializeEvent(eventType, payload);

    if (domainEvent != null) {
      eventPublisher.publishEvent(domainEvent);
      messageMetrics.recordSend("OUTBOX", "SUCCESS", 0);
      log.debug(
          "[OutboxScheduler] 事件已发布: eventId={} type={}",
          outboxEvent.getId(),
          eventType);
    } else {
      // 无法解析的事件类型，记录 WARN 但不抛异常（避免阻塞其他事件）
      log.warn(
          "[OutboxScheduler] 未知事件类型，跳过: eventId={} type={}",
          outboxEvent.getId(),
          eventType);
    }
  }

  /**
   * 根据事件类型和 JSON 负载反序列化领域事件。
   *
   * @param eventType 事件类型（类名）
   * @param payload JSON 负载
   * @return 反序列化后的领域事件对象
   */
  private Object deserializeEvent(String eventType, String payload) {
    try {
      // 尝试全限定类名
      Class<?> eventClass = Class.forName(eventType);
      return objectMapper.readValue(payload, eventClass);
    } catch (ClassNotFoundException e) {
      // 尝试从领域事件包查找
      try {
        String fqcn = "com.njydsz.message.domain.event." + eventType;
        Class<?> eventClass = Class.forName(fqcn);
        return objectMapper.readValue(payload, eventClass);
      } catch (ClassNotFoundException | com.fasterxml.jackson.core.JsonProcessingException ex) {
        log.warn("无法反序列化事件: type={} err={}", eventType, ex.getMessage());
        return null;
      }
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      log.warn("事件 JSON 解析失败: type={} err={}", eventType, e.getMessage());
      return null;
    }
  }
}
