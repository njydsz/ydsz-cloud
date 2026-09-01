package com.njydsz.message.domain.event;

import java.io.Serial;

import com.njydsz.message.domain.enums.core.MessageChannelEnum;

/**
 * 消息被抑制领域事件。
 *
 * <p>在消息被频控 / 抑制规则拦截时发布，携带抑制原因、原始通道与业务类型。 用于审计、风控分析与频控规则优化。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class MessageSuppressedEvent extends MessageDomainEvent {

  @Serial private static final long serialVersionUID = 1L;

  /** 抑制原因 */
  private final String suppressReason;

  /** 原始通道 */
  private final MessageChannelEnum originalChannel;

  /** 业务类型 */
  private final String bizType;

  /**
   * 构造消息被抑制事件。
   *
   * @param tenantId 租户 ID
   * @param messageId 消息 ID（可为 null，抑制发生在日志入库前）
   * @param suppressReason 抑制原因
   * @param originalChannel 原始通道
   * @param bizType 业务类型
   */
  public MessageSuppressedEvent(
      String tenantId,
      String messageId,
      String suppressReason,
      MessageChannelEnum originalChannel,
      String bizType) {
    super(tenantId, messageId, null);
    this.suppressReason = suppressReason;
    this.originalChannel = originalChannel;
    this.bizType = bizType;
  }

  public String getSuppressReason() {
    return suppressReason;
  }

  public MessageChannelEnum getOriginalChannel() {
    return originalChannel;
  }

  public String getBizType() {
    return bizType;
  }
}
