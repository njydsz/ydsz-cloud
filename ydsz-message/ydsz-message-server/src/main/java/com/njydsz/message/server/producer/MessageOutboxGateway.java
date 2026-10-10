package com.njydsz.message.server.producer;


import com.njydsz.common.locales.util.I18n;import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.njydsz.common.event.gateway.EventPublishGateway;
import com.njydsz.common.event.model.OutboxMessage;
import com.njydsz.common.json.YdszJson;
import com.njydsz.message.domain.dto.MessageItemRequestDTO;

/**
 * 消息模块 Outbox 投递网关 — 实现 {@link EventPublishGateway} SPI。
 *
 * <p>替代原 {@code OutboxEventScheduler} 的事件分发逻辑，由 common-event 的 {@code OutboxProcessor} 统一调度投递。
 *
 * <p>分发规则：
 *
 * <ul>
 *   <li>{@code MessageAsyncDispatch} — 反序列化为 {@link MessageItemRequestDTO} 后投递到 MQ</li>
 *   <li>其他领域事件 — 反序列化为领域事件对象后发布到 Spring 事件总线</li>
 * </ul>
 *
 * <p>设计对齐 literule 的 {@code RuleConfigOutboxGateway}（EventPublishGateway SPI 典范）。
 *
 * @author ydsz-team
 * @since 26.09.30
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageOutboxGateway implements EventPublishGateway {

  /** 异步消息投递事件类型常量（与 MessageSendTxService / MessageServiceImpl 写入的 eventType 保持一致） */
  private static final String EVENT_TYPE_ASYNC_DISPATCH = "MessageAsyncDispatch";

  private final ApplicationEventPublisher eventPublisher;
  private final ObjectProvider<MessageQueueOperations> mqOperationsProvider;

  /**
   * 投递 Outbox 消息到目标通道。
   *
   * <p>根据 eventType 路由：
   *
   * <ul>
   *   <li>{@code MessageAsyncDispatch} → 反序列化为 {@link MessageItemRequestDTO} 投递到 MQ</li>
   *   <li>其他 → 反序列化为领域事件发布到 Spring 事件总线</li>
   * </ul>
   *
   * @param message Outbox 消息
   * @return true 投递成功；false 投递失败（触发重试）
   * @throws Throwable 投递异常
   */
  @Override
  public boolean publish(OutboxMessage message) throws Throwable {
    if (message == null) {
      return true;
    }
    String eventType = message.getEventType();

    if (EVENT_TYPE_ASYNC_DISPATCH.equals(eventType)) {
      return dispatchAsyncMessage(message);
    }
    return publishDomainEvent(message);
  }

  /**
   * 异步消息投递：将 Outbox 事件反序列化为 {@link MessageItemRequestDTO} 后投递到 MQ。
   *
   * @param outboxMessage Outbox 消息
   * @return true 投递成功；false 投递失败（触发重试）
   */
  private boolean dispatchAsyncMessage(OutboxMessage outboxMessage) {
    MessageItemRequestDTO request = YdszJson.fromJson(outboxMessage.getPayload(),
        MessageItemRequestDTO.class);
    if (request == null) {
      log.warn(
          "[MessageOutboxGateway] 异步消息反序列化失败: eventId={} aggregateId={}",
          outboxMessage.getId(),
          outboxMessage.getAggregateId());
      // 反序列化失败返回 true，避免无限重试不可恢复的消息
      return true;
    }
    MessageQueueOperations mqOps = mqOperationsProvider.getIfAvailable();
    if (mqOps == null) {
      log.warn(
          "[MessageOutboxGateway] MQ 未配置，异步消息投递跳过: eventId={} aggregateId={}",
          outboxMessage.getId(),
          outboxMessage.getAggregateId());
      // MQ 不可用时返回 true，不阻塞后续消息投递
      return true;
    }
    try {
      mqOps.asyncSend(request);
      log.info(
          "[MessageOutboxGateway] 异步消息已投递 MQ: eventId={} aggregateId={} channel={}",
          outboxMessage.getId(),
          outboxMessage.getAggregateId(),
          request.getChannel());
      return true;
    } catch (Exception e) {
      log.error(
          "[MessageOutboxGateway] 异步消息投递失败: eventId={} err={}",
          outboxMessage.getId(),
          e.getMessage());
      // 发送失败返回 false，由 OutboxProcessor 重试
      return false;
    }
  }

  /**
   * 将 Outbox 事件反序列化为领域事件并发布到 Spring 事件总线。
   *
   * @param outboxMessage Outbox 消息
   * @return true 投递成功或无法解析（跳过）；false 投递失败
   */
  private boolean publishDomainEvent(OutboxMessage outboxMessage) {
    String eventType = outboxMessage.getEventType();
    String payload = outboxMessage.getPayload();
    try {
      Object domainEvent = deserializeEvent(eventType, payload);
      if (domainEvent == null) {
        // 无法解析返回 true，避免无限重试
        log.warn(
            "[MessageOutboxGateway] 未知事件类型，跳过: eventId={} type={}",
            outboxMessage.getId(),
            eventType);
        return true;
      }
      eventPublisher.publishEvent(domainEvent);
      log.debug(
          "[MessageOutboxGateway] 领域事件已发布: eventId={} type={}",
          outboxMessage.getId(),
          eventType);
      return true;
    } catch (Exception e) {
      log.error(
          "[MessageOutboxGateway] 领域事件发布异常: eventId={} type={} err={}",
          outboxMessage.getId(),
          eventType,
          e.getMessage());
      return false;
    }
  }

  /**
   * 根据事件类型全限定类名和 JSON 负载反序列化领域事件。
   *
   * <p>先尝试以 eventType 作为全限定类名解析；若失败，尝试拼接领域事件包名前缀。
   *
   * @param eventType 事件类型（全简称或简称）
   * @param payload JSON 负载
   * @return 反序列化后的领域事件对象，无法解析时返回 null
   */
  private Object deserializeEvent(String eventType, String payload) {
    try {
      Class<?> eventClass = Class.forName(eventType);
      return YdszJson.fromJson(payload, eventClass);
    } catch (ClassNotFoundException e) {
      try {
        String fqcn = "com.njydsz.message.domain.event." + eventType;
        Class<?> eventClass = Class.forName(fqcn);
        return YdszJson.fromJson(payload, eventClass);
      } catch (ClassNotFoundException ex) {
        log.warn(I18n.message("message.log.other.MessageOutboxGateway_type_{}_err_{}.46cd99"), eventType,
            ex.getMessage());
        return null;
      } catch (Exception ex) {
        log.warn(I18n.message("message.log.other.MessageOutboxGateway_JSON_type_{}_err_{}.2a6846"), eventType,
            ex.getMessage());
        return null;
      }
    } catch (Exception e) {
      log.warn(I18n.message("message.log.other.MessageOutboxGateway_type_{}_err_{}.983402"), eventType,
          e.getMessage());
      return null;
    }
  }
}
