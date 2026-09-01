package com.njydsz.message.domain.event;

import java.io.Serial;

/**
 * 消息已撤回领域事件。
 *
 * <p>在消息撤回操作完成后发布，携带通道与撤回结果。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class MessageRecalledEvent extends MessageDomainEvent {

  @Serial private static final long serialVersionUID = 1L;

  /** 通道 */
  private final String channel;

  /** 撤回是否成功 */
  private final boolean recallSucceeded;

  /** 撤回失败原因（成功时为 null） */
  private final String failureReason;

  /**
   * 构造消息已撤回事件。
   *
   * @param tenantId 租户 ID
   * @param messageId 消息 ID
   * @param channel 通道
   * @param recallSucceeded 撤回是否成功
   * @param failureReason 失败原因
   */
  public MessageRecalledEvent(
      String tenantId,
      String messageId,
      String channel,
      boolean recallSucceeded,
      String failureReason) {
    super(tenantId, messageId, null);
    this.channel = channel;
    this.recallSucceeded = recallSucceeded;
    this.failureReason = failureReason;
  }

  public String getChannel() {
    return channel;
  }

  public boolean isRecallSucceeded() {
    return recallSucceeded;
  }

  public String getFailureReason() {
    return failureReason;
  }
}
