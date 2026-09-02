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
 *
 * @author ydsz-team
 * @since 26.09.01
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
  /**
   * 构造初始上下文（用于 doSend 路径）。
   *
   * <p>适用于正文已在调用侧渲染完毕的场景：模板编码与模板参数留空，直接携带成品内容进入发送链路。 发送结果、耗时与开始时间戳由链路后续环节填充，此处分别置为 {@code null} 与 {@code 0}。
   *
   * @param channel 通知渠道，决定最终由哪个渠道策略执行发送
   * @param receiver 接收者标识，如手机号、邮箱或站内信用户 ID
   * @param title 消息标题，纯内容发送时允许为空
   * @param content 已渲染完毕的消息正文，纯内容发送时必填
   * @param userId 用户 ID，可选；用于偏好查询与免打扰判定，无用户归属时传 {@code null}
   * @param templateCode 模板编码，可选；纯内容发送时传 {@code null}
   * @param notifyType 通知类型，用于审计与统计归类
   * @param tenantId 租户 ID，可选；多租户隔离时使用，单租户场景传 {@code null}
   * @return 初始发送上下文实例，不会为 {@code null}
   */
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

  /**
   * 构造初始上下文（用于 doSendTemplate 路径）。
   *
   * <p>适用于按模板渲染的场景：正文留空交由模板引擎渲染，通知类型固定为 {@link NotifyType#TEMPLATE}。
   * 模板编码与模板参数必须有值，否则渲染阶段无法定位模板或替换变量。
   *
   * @param channel 通知渠道，决定最终由哪个渠道策略执行发送
   * @param receiver 接收者标识，如手机号、邮箱或站内信用户 ID
   * @param templateCode 模板编码，<strong>必填</strong>，用于检索并渲染模板
   * @param templateParams 模板变量参数，<strong>必填</strong>；支持 {@code Map} 或 POJO， 缺失的变量会在渲染前校验阶段被拦截
   * @param title 消息标题，可选；为 {@code null} 时通常由模板自身提供标题
   * @param tenantId 租户 ID，可选；多租户隔离时使用，单租户场景传 {@code null}
   * @return 初始发送上下文实例，不会为 {@code null}
   */
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
