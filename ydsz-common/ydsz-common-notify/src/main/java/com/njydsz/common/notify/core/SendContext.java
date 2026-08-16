package com.njydsz.common.notify.core;

import com.njydsz.common.notify.enums.NotifyChannel;
import com.njydsz.common.notify.enums.NotifyType;

/**
 * 发送链路上下文
 *
 * <p>封装一次通知发送所需的全部参数和可变状态，供 {@link SendChain} 各步骤传递。 使用 record 保证不可变参数部分的安全性，可变状态通过单独字段维护。
 *
 * @param channel 通知渠道
 * @param receiver 接收者标识
 * @param title 消息标题
 * @param content 消息内容
 * @param userId 用户 ID（可选）
 * @param templateCode 模板编码（可选）
 * @param notifyType 通知类型
 * @param tenantId 租户 ID（可选）
 * @param templateParams 模板参数（可选）
 * @param sendResult 发送结果（由链路填充）
 * @param durationNanos 发送耗时纳秒（由链路填充）
 * @param startTime 开始时间戳纳秒（由链路填充）
 */
public record SendContext(
    NotifyChannel channel,
    String receiver,
    String title,
    String content,
    String userId,
    String templateCode,
    NotifyType notifyType,
    String tenantId,
    Object templateParams,
    NotifySendResult sendResult,
    long durationNanos,
    long startTime) {
  /** 构造初始上下文（用于 doSend 路径） */
  public static SendContext forSend(
      NotifyChannel channel,
      String receiver,
      String title,
      String content,
      String userId,
      String templateCode,
      NotifyType notifyType,
      String tenantId) {
    return new SendContext(
        channel,
        receiver,
        title,
        content,
        userId,
        templateCode,
        notifyType,
        tenantId,
        null,
        null,
        0L,
        0L);
  }

  /** 构造初始上下文（用于 doSendTemplate 路径） */
  public static SendContext forTemplate(
      NotifyChannel channel,
      String receiver,
      String templateCode,
      Object templateParams,
      String title,
      String tenantId) {
    return new SendContext(
        channel,
        receiver,
        title,
        null,
        null,
        templateCode,
        NotifyType.TEMPLATE,
        tenantId,
        templateParams,
        null,
        0L,
        0L);
  }

  /** 更新发送结果 */
  public SendContext withResult(NotifySendResult result) {
    return new SendContext(
        channel,
        receiver,
        title,
        content,
        userId,
        templateCode,
        notifyType,
        tenantId,
        templateParams,
        result,
        durationNanos,
        startTime);
  }

  /** 更新耗时信息 */
  public SendContext withTiming(long startTime, long durationNanos) {
    return new SendContext(
        channel,
        receiver,
        title,
        content,
        userId,
        templateCode,
        notifyType,
        tenantId,
        templateParams,
        sendResult,
        durationNanos,
        startTime);
  }

  /** 是否已有结果（短路标记） */
  public boolean hasResult() {
    return sendResult != null;
  }
}
