package com.njydsz.message.domain.enums.core;

/**
 * 消息发送通道枚举（8 通道统一抽象）
 *
 * <p>对应 SQL {@code ydsz_msg_log.channel} 与 {@code ydsz_msg_template.channel} 的 CHECK 约束取值。
 * 每个枚举值对应一个 {@code ChannelStrategy} 实现，由 {@code ChannelRouter} 按通道类型分发。
 *
 * <p><b>通道分类：</b>
 *
 * <ul>
 *   <li><b>传统通道</b>：SMS（短信）、EMAIL（邮件）、PUSH（App 推送）、INAPP（站内信）
 *   <li><b>IM 通道</b>：HMAC（HMAC 签名群机器人）、HMAC_WORK（HMAC 工作通知）、
 *       WECOM（企业微信群机器人）、WECOM_APP（企业微信应用消息）、POST（Post 消息群机器人）
 *   <li><b>扩展通道</b>：WEBHOOK（自定义 Webhook）
 * </ul>
 *
 * <p><b>通道能力差异：</b>不同通道支持的富文本格式、字符长度限制、速率限制、回执能力均不同， 由 {@code ChannelStrategy} 实现类各自处理降级和适配逻辑。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum MessageChannelEnum {

  /** 短信 */
  SMS,
  /** 邮件 */
  EMAIL,
  /** App 推送 */
  PUSH,
  /** 站内信 */
  INAPP,
  /** Webhook */
  WEBHOOK,
  /** HMAC 签名群机器人 */
  HMAC,
  /** HMAC 工作通知(企业内部应用) */
  HMAC_WORK,
  /** 企业微信群机器人 */
  WECOM,
  /** 企业微信应用消息(企业内部应用) */
  WECOM_APP,
  /** Post 消息群机器人 */
  POST,
  /** 微信小程序订阅消息 */
  WX_MINI,
  /** 支付宝小程序模板消息 */
  ALIPAY_MINI;

  /**
   * 安全解析通道字符串（大小写无关），非法时抛出 IllegalArgumentException。
   *
   * @param value 通道字符串
   * @return 通道枚举
   */
  public static MessageChannelEnum parse(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("消息通道不能为空");
    }
    try {
      return MessageChannelEnum.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("不支持的消息通道: " + value);
    }
  }
}
