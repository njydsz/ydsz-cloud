package com.njydsz.message.domain.event;

import java.io.Serial;

/**
 * 消息已发送领域事件。
 *
 * <p>在消息成功投递到通道后发布，携带通道、接收人（脱敏）、业务类型等信息。 订阅者可据此更新统计表、触发用户行为追踪等。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
public class MessageSentEvent extends MessageDomainEvent {

  @Serial private static final long serialVersionUID = 1L;

  /** 发送通道 */
  private final String channel;

  /** 接收人（脱敏） */
  private final String receiverMasked;

  /** 业务类型 */
  private final String bizType;

  /** 发送耗时（毫秒） */
  private final long elapsedMs;

  /**
   * 构造消息已发送事件。
   *
   * @param tenantId 租户 ID
   * @param messageId 消息 ID
   * @param channel 通道
   * @param receiverMasked 脱敏接收人
   * @param bizType 业务类型
   * @param elapsedMs 发送耗时
   */
  public MessageSentEvent(
      String tenantId,
      String messageId,
      String channel,
      String receiverMasked,
      String bizType,
      long elapsedMs) {
    super(tenantId, messageId, null);
    this.channel = channel;
    this.receiverMasked = receiverMasked;
    this.bizType = bizType;
    this.elapsedMs = elapsedMs;
  }

  public String getChannel() {
    return channel;
  }

  public String getReceiverMasked() {
    return receiverMasked;
  }

  public String getBizType() {
    return bizType;
  }

  public long getElapsedMs() {
    return elapsedMs;
  }
}
