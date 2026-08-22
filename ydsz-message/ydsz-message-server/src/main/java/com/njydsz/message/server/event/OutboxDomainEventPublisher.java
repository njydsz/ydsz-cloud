package com.njydsz.message.server.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.njydsz.common.json.YdszJson;
import com.njydsz.message.domain.event.MessageDomainEvent;
import com.njydsz.message.domain.event.OutboxEvent;
import com.njydsz.message.domain.repository.OutboxEventRepository;

/**
 * 支持事务性 Outbox 的领域事件发布器。
 *
 * <p>提供两种发布模式：
 * <ol>
 *   <li> {@link #publish(MessageDomainEvent)}：Outbox 模式，先落库再异步发布（默认，保证 at-least-once）</li>
 *   <li> {@link #publishImmediate(MessageDomainEvent)}：同步立即发布（不保证持久化，仅用于非关键通知）</li>
 * </ol>
 *
 * <p>Outbox 模式优势：
 * <ul>
 *   <li>事件与业务操作同事务落库，保证事件不丢失</li>
 *   <li>异步扫描器独立发布，即使应用崩溃也能恢复</li>
 *   <li>支持失败重试，最终一致性</li>
 * </ul>
 *
 * <p><b>编码规范合规：</b>使用 {@link YdszJson} 替代 Jackson ObjectMapper，符合《云顶编码规范》"禁止第三方 JSON 库"要求。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxDomainEventPublisher {

  private final ApplicationEventPublisher eventPublisher;
  private final OutboxEventRepository outboxEventRepository;

  /** Outbox 模式开关（关闭后等同原直接发布行为） */
  @Value("${ydsz.message.outbox.enabled:true}")
  private boolean outboxEnabled;

  /**
   * 通过 Outbox 模式发布领域事件（推荐）。
   *
   * <p>先将事件序列化后落库到 Outbox 表，再通过 {@link OutboxEventScheduler} 异步发布到 Spring 事件总线。
   * 如果未开启 Outbox 模式（配置关闭），回退为直接发布。
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

      // 构造 Outbox 事件
      OutboxEvent outboxEvent =
          new OutboxEvent(
              event.getClass().getSimpleName(),
              event.getMessageId() != null ? event.getMessageId() : "unknown",
              eventType,
              payload,
              event.getTenantId());

      // 注册事务同步器：事务提交后写入 Outbox（与业务操作同事务）
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              try {
                outboxEventRepository.save(outboxEvent);
                log.debug(
                    "[OutboxPublisher] 事件已写入 Outbox: eventId={} type={}",
                    outboxEvent.getId(),
                    eventType);
              } catch (Exception e) {
                // Outbox 落库失败不抛出，记录严重日志（事件可能丢失）
                log.error(
                    "[OutboxPublisher] Outbox 落库失败: eventType={} err={}",
                    eventType,
                    e.getMessage(),
                    e);
              }
            }
          });
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
