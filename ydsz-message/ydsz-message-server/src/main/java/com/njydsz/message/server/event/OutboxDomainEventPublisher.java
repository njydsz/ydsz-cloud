package com.njydsz.message.server.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.event.publish.DomainEventPublisher;
import com.njydsz.common.json.YdszJson;
import com.njydsz.message.domain.event.MessageDomainEvent;

/**
 * 消息模块事务性 Outbox 发布适配器 — 将 {@link MessageDomainEvent} 适配为 common-event 的 {@link DomainEvent}，
 * 委托 {@link DomainEventPublisher} 统一门面写入 Outbox。
 *
 * <p>设计说明：因 {@link MessageDomainEvent} 是消息模块独立基类（未继承 common-event 的 {@link DomainEvent}），
 * 本适配器负责将领域事件字段映射为标准 DomainEvent，以便复用 OutboxService 的事务性写入管道。
 *
 * <p>两种发布模式：
 * <ol>
 *   <li>{@link #publish(MessageDomainEvent)}：Outbox 模式（事务内），委托 {@link DomainEventPublisher} 写入 Outbox 表</li>
 *   <li>{@link #publishImmediate(MessageDomainEvent)}：同步立即发布（不经过 Outbox，仅用于非事务回退）</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @since 26.09.30 重构为委托 common-event DomainEventPublisher 门类，删除自建 OutboxMessage 构建逻辑
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxDomainEventPublisher {

  private final ObjectProvider<DomainEventPublisher> domainEventPublisherProvider;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * 通过 Outbox 模式发布领域事件（推荐）。
   *
   * <p>仅在事务上下文中使用 Outbox 模式（保证 at-least-once 语义）；未在事务中时回退为直接发布。
   *
   * @param event 领域事件
   */
  public void publish(MessageDomainEvent event) {
    if (event == null) {
      return;
    }

    // 未在事务中，回退为直接发布
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      publishImmediate(event);
      return;
    }

    DomainEventPublisher publisher = domainEventPublisherProvider.getIfAvailable();
    if (publisher == null) {
      log.debug("[OutboxPublisher] DomainEventPublisher 不可用，回退直接发布: eventType={}",
          event.eventType());
      publishImmediate(event);
      return;
    }

    try {
      // 适配 MessageDomainEvent → DomainEvent（Builder 模式复用 common-event 标准管道）
      DomainEvent domainEvent = DomainEvent.builder()
          .aggregateType("Message")
          .aggregateId(event.getMessageId() != null ? event.getMessageId() : event.getEventId())
          .eventType(event.getClass().getName())
          .topic("message")
          .payload(YdszJson.toJson(event))
          .idempotencyKey(event.getEventId())
          .metadata("tenantId", event.getTenantId())
          .metadata("batchId", event.getBatchId())
          .build();
      publisher.publish(domainEvent);
      log.debug("[OutboxPublisher] 事件已写入 Outbox: eventId={} type={}",
          event.getEventId(), event.eventType());
    } catch (Exception e) {
      log.error("[OutboxPublisher] 事件发布失败，回退直接发布: eventType={} err={}",
          event.eventType(), e.getMessage());
      publishImmediate(event);
    }
  }

  /** 异步消息投递事件类型（内部使用，不注册到 DomainEventTypes） */
  private static final String EVENT_TYPE_ASYNC_DISPATCH = "MessageAsyncDispatch";

  /**
   * 通过 Outbox 发布异步消息投递指令。
   *
   * <p>将任意序列化的投递载荷写入 Outbox，由 {@link com.njydsz.message.server.producer.MessageOutboxGateway}
   * 异步投递到 MQ。适用于需要将 MQ 发送延迟到事务提交后的场景。
   *
   * @param aggregateId 聚合根 ID（通常为消息 ID）
   * @param payload 已序列化的投递载荷 JSON
   * @param idempotencyKey 幂等键（通常为消息 ID，保证重复消费安全）
   */
  public void publishAggregateDispatch(String aggregateId, String payload, String idempotencyKey) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      log.warn("[OutboxPublisher] 非事务上下文，跳过 Outbox 写入: aggregateId={}", aggregateId);
      return;
    }
    DomainEventPublisher publisher = domainEventPublisherProvider.getIfAvailable();
    if (publisher == null) {
      log.warn("[OutboxPublisher] DomainEventPublisher 不可用，跳过 Outbox 写入: aggregateId={}",
          aggregateId);
      return;
    }
    DomainEvent domainEvent = DomainEvent.builder()
        .aggregateType("Message")
        .aggregateId(aggregateId)
        .eventType(EVENT_TYPE_ASYNC_DISPATCH)
        .topic("message")
        .payload(payload)
        .idempotencyKey(idempotencyKey)
        .build();
    publisher.publish(domainEvent);
    log.debug("[OutboxPublisher] 异步投递指令已写入 Outbox: aggregateId={}", aggregateId);
  }

  /**
   * 同步立即发布领域事件（不经过 Outbox）。
   *
   * <p>适用于非关键性事件（如统计更新），不保证持久化。发布失败仅记日志不影响主流程。
   *
   * @param event 领域事件
   */
  public void publishImmediate(MessageDomainEvent event) {
    if (event == null) {
      return;
    }
    try {
      eventPublisher.publishEvent(event);
      log.debug("[OutboxPublisher] 同步事件已发布: type={}", event.eventType());
    } catch (Exception e) {
      log.warn("[OutboxPublisher] 同步事件发布失败: type={} err={}", event.eventType(),
          e.getMessage());
    }
  }
}
