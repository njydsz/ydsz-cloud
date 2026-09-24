package com.njydsz.message.server.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.njydsz.common.event.model.OutboxMessage;
import com.njydsz.common.event.service.OutboxService;
import com.njydsz.common.json.YdszJson;
import com.njydsz.message.domain.event.MessageDomainEvent;

/**
 * 事务性 Outbox 领域事件发布器 — 委托 common-event 标准体系。
 *
 * <p>提供两种发布模式：
 * <ol>
 *   <li>{@link #publish(MessageDomainEvent)}：Outbox 模式，委托 {@link OutboxService} 写入 ydsz_com_outbox 表</li>
 *   <li>{@link #publishImmediate(MessageDomainEvent)}：同步立即发布（不经过 Outbox，仅用于非关键通知）</li>
 * </ol>
 *
 * <p>Outbox 模式通过 {@link OutboxService#appendToOutbox(OutboxMessage.OutboxMessageBuilder)} 实现，
 * 业务操作与 Outbox 写入在同一事务中完成，保证 at-least-once 语义。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @since 26.09.29 迁移至 common-event OutboxService，删除自建 OutboxEvent/OutboxEntry/OutboxEventRepository
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxDomainEventPublisher {

  private final ApplicationEventPublisher eventPublisher;
  private final OutboxService outboxService;

  /** Outbox 模式开关（关闭后等同原直接发布行为） */
  @Value("${ydsz.message.outbox.enabled:true}")
  private boolean outboxEnabled;

  /**
   * 通过 Outbox 模式发布领域事件（推荐）。
   *
   * <p>委托 {@link OutboxService} 将事件写入标准 Outbox 表，事务提交后由 OutboxProcessor 异步投递。
   * 如果未开启 Outbox 模式（配置关闭）或未在事务中，回退为直接发布。
   *
   * @param event 领域事件
   */
  public void publish(MessageDomainEvent event) {
    if (event == null) {
      return;
    }

    // Outbox 模式未开启或未在事务中，直接发布
    if (!outboxEnabled || !TransactionSynchronizationManager.isSynchronizationActive()) {
      publishImmediate(event);
      return;
    }

    try {
      // 序列化事件载荷（使用 YdszJson，符合编码规范）
      String payload = YdszJson.toJson(event);
      String eventType = event.getClass().getName();
      String messageId = event.getMessageId() != null ? event.getMessageId() : "unknown";

      // 委托 OutboxService 写入标准 Outbox（事务内原子性与业务操作一致）
      outboxService.appendToOutbox(
          OutboxMessage.builder()
              .aggregateType("Message")
              .aggregateId(messageId)
              .eventType(eventType)
              .payload(payload)
              .idempotencyKey(messageId));

      log.debug(
          "[OutboxPublisher] 事件已写入 Outbox: messageId={} type={}",
          messageId,
          eventType);
    } catch (Exception e) {
      log.error(
          "[OutboxPublisher] 事件序列化失败，回退直接发布: eventType={} err={}",
          event.eventType(),
          e.getMessage());
      // 序列化失败时回退为直接发布
      publishImmediate(event);
    }
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
      log.warn(
          "[OutboxPublisher] 同步事件发布失败: type={} err={}",
          event.eventType(),
          e.getMessage());
    }
  }
}
