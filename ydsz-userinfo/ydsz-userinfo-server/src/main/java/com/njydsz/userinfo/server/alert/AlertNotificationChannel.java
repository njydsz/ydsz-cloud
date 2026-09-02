package com.njydsz.userinfo.server.alert;

import com.njydsz.userinfo.domain.alert.SecurityAlert;

/**
 * 告警通知渠道接口。
 *
 * <p>定义告警通知的发送渠道，实现类可对接企业微信、钉钉、邮件、短信等告警通道。
 *
 * <p>各渠道实现应具备幂等性：同一告警重复发送不应导致重复通知（通过告警 ID 去重）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface AlertNotificationChannel {

  /**
   * 发送告警通知。
   *
   * <p>实现类应处理发送失败的情况，记录日志但不抛出异常（避免影响其他渠道的发送）。
   *
   * @param alert 安全告警
   */
  void sendAlert(SecurityAlert alert);

  /**
   * 获取渠道名称（用于日志和监控）。
   *
   * @return 渠道名称
   */
  String getChannelName();

  /**
   * 判断渠道是否可用（如配置是否完整）。
   *
   * @return true 表示渠道可用
   */
  default boolean isAvailable() {
    return true;
  }
}
