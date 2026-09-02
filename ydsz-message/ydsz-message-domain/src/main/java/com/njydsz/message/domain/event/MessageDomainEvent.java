package com.njydsz.message.domain.event;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 消息领域事件基类。
 *
 * <p>所有消息模块的领域事件继承此基类，携带事件 ID、发生时间、租户 ID 等通用元数据。 事件通过 Spring ApplicationEvent
 * 机制发布，由订阅者异步处理（如更新统计表、触发 Webhook、写入审计日志等）。
 *
 * <p><b>事件分类：</b>
 *
 * <ul>
 *   <li>生命周期事件：MessageSent / MessageScheduled / MessageRecalled
 *   <li>状态变更事件：MessageStatusChanged
 *   <li>业务拦截事件：MessageSkipped / MessageSuppressed
 *   <li>批次事件：BatchCompleted / BatchProgressChanged
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public abstract class MessageDomainEvent implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 事件唯一 ID */
  private final String eventId = UUID.randomUUID().toString();

  /** 事件发生时间 */
  private final LocalDateTime occurredAt = LocalDateTime.now();

  /** 租户 ID */
  private final String tenantId;

  /** 关联消息 ID（可选） */
  private final String messageId;

  /** 关联批次 ID（可选） */
  private final String batchId;

  /**
   * 构造领域事件。
   *
   * @param tenantId 租户 ID
   * @param messageId 消息 ID（可为 null）
   * @param batchId 批次 ID（可为 null）
   */
  protected MessageDomainEvent(String tenantId, String messageId, String batchId) {
    this.tenantId = tenantId;
    this.messageId = messageId;
    this.batchId = batchId;
  }

  public String getEventId() {
    return eventId;
  }

  public LocalDateTime getOccurredAt() {
    return occurredAt;
  }

  public String getTenantId() {
    return tenantId;
  }

  public String getMessageId() {
    return messageId;
  }

  public String getBatchId() {
    return batchId;
  }

  /**
   * 事件类型标识（用于序列化 / 路由）。
   *
   * @return 事件类型名（默认为类名）
   */
  public String eventType() {
    return this.getClass().getSimpleName();
  }
}
