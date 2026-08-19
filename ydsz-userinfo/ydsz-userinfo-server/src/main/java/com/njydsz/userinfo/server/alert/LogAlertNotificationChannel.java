package com.njydsz.userinfo.server.alert;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.userinfo.domain.alert.SecurityAlert;

/**
 * 日志告警通知渠道。
 *
 * <p>将告警信息输出到日志，作为默认的告警通知方式。生产环境可替换为邮件/企业微信/钉钉等通道。
 *
 * @author ydsz-team
 * @since 2.18.0
 */
@Slf4j
@Component
public class LogAlertNotificationChannel implements AlertNotificationChannel {

  @Override
  public void sendAlert(SecurityAlert alert) {
    log.warn(
        "安全告警通知 [{}] - 类型: {}, 风险等级: {}, 用户: {}({}), IP: {}, 标题: {}, 内容: {}",
        getChannelName(),
        alert.alertType(),
        alert.riskLevel(),
        alert.username(),
        alert.userId(),
        alert.sourceIp(),
        alert.title(),
        alert.content());
  }

  @Override
  public String getChannelName() {
    return "LOG";
  }
}
