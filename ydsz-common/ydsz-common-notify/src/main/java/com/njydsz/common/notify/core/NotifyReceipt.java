package com.njydsz.common.notify.core;

import java.time.Instant;

/**
 * 通知回执
 *
 * <p>表示一条通知的投递状态，用于追踪通知是否成功送达接收者。 回执状态包括：待投递（PENDING）、已投递（DELIVERED）、投递失败（FAILED）、已读（READ）。
 *
 * @param messageId 消息唯一标识
 * @param channel 通知渠道
 * @param receiver 接收者标识（脱敏存储）
 * @param status 回执状态
 * @param createdAt 创建时间
 * @param updatedAt 最后更新时间
 * @param errorMessage 错误信息（失败时）
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public record NotifyReceipt(
    String messageId,
    String channel,
    String receiver,
    ReceiptStatus status,
    Instant createdAt,
    Instant updatedAt,
    String errorMessage) {
  /** 回执状态枚举 */
  public enum ReceiptStatus {
    /** 待投递 */
    PENDING,
    /** 已投递 */
    DELIVERED,
    /** 投递失败 */
    FAILED,
    /** 已读（需接收者主动回执） */
    READ
  }

  /** 创建初始回执（待投递状态） */
  public static NotifyReceipt pending(String messageId, String channel, String receiver) {
    Instant now = Instant.now();
    return new NotifyReceipt(messageId, channel, receiver, ReceiptStatus.PENDING, now, now, null);
  }

  /** 标记为已投递 */
  public NotifyReceipt markDelivered() {
    return new NotifyReceipt(
        messageId, channel, receiver, ReceiptStatus.DELIVERED, createdAt, Instant.now(), null);
  }

  /** 标记为投递失败 */
  public NotifyReceipt markFailed(String errorMessage) {
    return new NotifyReceipt(
        messageId, channel, receiver, ReceiptStatus.FAILED, createdAt, Instant.now(), errorMessage);
  }

  /** 标记为已读 */
  public NotifyReceipt markRead() {
    return new NotifyReceipt(
        messageId, channel, receiver, ReceiptStatus.READ, createdAt, Instant.now(), null);
  }
}
