package com.njydsz.message.domain.event;

import com.njydsz.message.domain.enums.core.MessageChannelEnum;
import com.njydsz.message.domain.enums.core.MessageStatusEnum;
import java.io.Serial;

/**
 * 消息状态变更领域事件。
 *
 * <p>在消息状态发生变更时发布，携带旧状态、新状态与通道信息。 订阅者可据此更新统计表、触发状态流转审计、写入事件溯源日志等。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
public class MessageStatusChangedEvent extends MessageDomainEvent {

  @Serial private static final long serialVersionUID = 1L;

  /** 变更前状态 */
  private final MessageStatusEnum oldStatus;

  /** 变更后状态 */
  private final MessageStatusEnum newStatus;

  /** 通道 */
  private final MessageChannelEnum channel;

  /**
   * 构造消息状态变更事件。
   *
   * @param tenantId 租户 ID
   * @param messageId 消息 ID
   * @param oldStatus 变更前状态
   * @param newStatus 变更后状态
   * @param channel 通道
   */
  public MessageStatusChangedEvent(
      String tenantId,
      String messageId,
      MessageStatusEnum oldStatus,
      MessageStatusEnum newStatus,
      MessageChannelEnum channel) {
    super(tenantId, messageId, null);
    this.oldStatus = oldStatus;
    this.newStatus = newStatus;
    this.channel = channel;
  }

  public MessageStatusEnum getOldStatus() {
    return oldStatus;
  }

  public MessageStatusEnum getNewStatus() {
    return newStatus;
  }

  public MessageChannelEnum getChannel() {
    return channel;
  }
}
