package com.njydsz.message.domain.event;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 消息已定时领域事件。
 *
 * <p>在消息被定时（延迟到 DND 结束后或其他原因）时发布，携带计划发送时间。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
public class MessageScheduledEvent extends MessageDomainEvent {

  @Serial private static final long serialVersionUID = 1L;

  /** 计划发送时间 */
  private final LocalDateTime scheduledAt;

  /** 定时原因（DND / USER_REQUEST 等） */
  private final String reason;

  /**
   * 构造消息已定时事件。
   *
   * @param tenantId 租户 ID
   * @param messageId 消息 ID
   * @param scheduledAt 计划发送时间
   * @param reason 定时原因
   */
  public MessageScheduledEvent(
      String tenantId, String messageId, LocalDateTime scheduledAt, String reason) {
    super(tenantId, messageId, null);
    this.scheduledAt = scheduledAt;
    this.reason = reason;
  }

  public LocalDateTime getScheduledAt() {
    return scheduledAt;
  }

  public String getReason() {
    return reason;
  }
}
