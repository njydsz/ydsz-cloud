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
 * @since 26.09.01
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

  /**
   * 创建初始回执（待投递状态）。
   *
   * <p>创建时间与最后更新时间同为当前时刻，错误信息为 {@code null}。 此时消息尚未真正投递，需由调用方在投递动作发生后用 {@link #markDelivered()} 或
   * {@link #markFailed(String)} 推进状态。
   *
   * @param messageId 消息唯一标识，用于与发送侧记录关联
   * @param channel 通知渠道，如短信、邮件、站内信
   * @param receiver 接收者标识，<strong>应传入脱敏后的值</strong>，避免回执中留存手机号等敏感信息
   * @return 状态为 {@code PENDING} 的新回执实例，不会为 {@code null}
   */
  public static NotifyReceipt pending(String messageId, String channel, String receiver) {
    Instant now = Instant.now();
    return new NotifyReceipt(messageId, channel, receiver, ReceiptStatus.PENDING, now, now, null);
  }

  /**
   * 标记为已投递，返回携带新状态的副本。
   *
   * <p>保留原创建时间、仅刷新最后更新时间，并清空错误信息——由失败转为投递成功时不应残留上一次的失败原因。
   * 本 record 不可变，原实例不受影响，调用方必须使用返回值继续后续处理。
   *
   * @return 状态为 {@code DELIVERED} 的新回执实例，不会为 {@code null}
   */
  public NotifyReceipt markDelivered() {
    return new NotifyReceipt(
        messageId, channel, receiver, ReceiptStatus.DELIVERED, createdAt, Instant.now(), null);
  }

  /**
   * 标记为投递失败，返回携带失败原因的副本。
   *
   * <p>保留原创建时间、仅刷新最后更新时间。本 record 不可变，原实例不受影响； 忽略返回值会导致状态推进丢失。
   *
   * @param errorMessage 失败原因描述，允许为 {@code null}（仅记录状态不记录原因）； 建议传入渠道返回的原始错误信息以便排查
   * @return 状态为 {@code FAILED} 的新回执实例，不会为 {@code null}
   */
  public NotifyReceipt markFailed(String errorMessage) {
    return new NotifyReceipt(
        messageId, channel, receiver, ReceiptStatus.FAILED, createdAt, Instant.now(), errorMessage);
  }

  /**
   * 标记为已读，返回携带新状态的副本。
   *
   * <p>已读依赖接收方主动回执（如站内信打开、埋点上报），发送链路不会自动置位， 因此多数渠道的回执会停留在 {@code DELIVERED} 而不再流转到本状态。
   * 保留原创建时间、仅刷新最后更新时间并清空错误信息。
   *
   * @return 状态为 {@code READ} 的新回执实例，不会为 {@code null}
   */
  public NotifyReceipt markRead() {
    return new NotifyReceipt(
        messageId, channel, receiver, ReceiptStatus.READ, createdAt, Instant.now(), null);
  }
}
