package com.njydsz.userinfo.server.alert;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.njydsz.common.notify.helper.NotifyHelper;
import com.njydsz.userinfo.domain.alert.SecurityAlert;

/**
 * SMTP 邮件告警通知渠道（P2 告警通知扩展）。
 *
 * <p>通过 ydsz-common-notify 统一发信能力发送安全告警邮件。
 * 发送能力由 {@link NotifyHelper} 提供（底层委托 ydsz-common-notify 的 EmailNotifySender，
 * 自动具备 SMTP 健康检查、XSS 清洗、DKIM 签名、发送指标、追踪像素等企业级能力）。
 *
 * <p><b>配置项：</b>
 *
 * <ul>
 *   <li>{@code ydsz.userinfo.alert.email.enabled} — 是否启用邮件告警</li>
 *   <li>{@code ydsz.userinfo.alert.email.from} — 发件人地址</li>
 *   <li>{@code ydsz.userinfo.alert.email.to} — 收件人地址（多个用逗号分隔）</li>
 *   <li>{@code spring.mail.host} — SMTP 服务器地址（通过 Spring Boot 自动配置提供）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@Slf4j
@Component
@Order(300)
@ConditionalOnProperty(prefix = "ydsz.userinfo.alert.email", name = "enabled", havingValue = "true")
public class SmtpAlertNotificationChannel implements AlertNotificationChannel {

  /** 渠道名称 */
  private static final String CHANNEL_NAME = "EMAIL";

  /** 统一通知辅助类（业务入口） */
  private final NotifyHelper notifyHelper;

  /**
   * 构造邮件告警通知渠道。
   *
   * <p>通过 {@link NotifyHelper} 发送邮件，复用 ydsz-common-notify 的企业级特性。
   *
   * @param notifyHelper 统一通知辅助类
   */
  public SmtpAlertNotificationChannel(NotifyHelper notifyHelper) {
    this.notifyHelper = notifyHelper;
  }

  @Override
  public void sendAlert(SecurityAlert alert) {
    String to = System.getProperty("ydsz.userinfo.alert.email.to", "admin@ydsz.top");
    if (to == null || to.isBlank()) {
      log.warn("邮件告警收件人未配置: alertId={}", alert.id());
      return;
    }
    String[] recipients = to.split(",");
    for (String recipient : recipients) {
      String trimmed = recipient.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      try {
        notifyHelper.sendEmail(trimmed, buildSubject(alert), buildBody(alert));
        log.info("邮件告警通知发送成功: alertId={}, to={}, type={}",
            alert.id(), trimmed, alert.alertType());
      } catch (Exception e) {
        log.warn("邮件告警通知发送失败: alertId={}, to={}, error={}",
            alert.id(), trimmed, e.getMessage());
      }
    }
  }

  @Override
  public String getChannelName() {
    return CHANNEL_NAME;
  }

  @Override
  public boolean isAvailable() {
    return notifyHelper != null;
  }

  /**
   * 构建邮件主题。
   *
   * @param alert 安全告警
   * @return 邮件主题
   */
  private String buildSubject(SecurityAlert alert) {
    return String.format("【安全告警-%s】%s - %s",
        alert.riskLevel(), alert.alertType(), alert.title());
  }

  /**
   * 构建邮件正文。
   *
   * @param alert 安全告警
   * @return 邮件正文
   */
  private String buildBody(SecurityAlert alert) {
    return String.format(
        "安全告警通知\n"
            + "====================\n"
            + "等级: %s\n"
            + "类型: %s\n"
            + "用户: %s(%s)\n"
            + "来源IP: %s\n"
            + "标题: %s\n"
            + "内容: %s\n"
            + "时间: %s\n"
            + "状态: %s\n",
        alert.riskLevel(),
        alert.alertType(),
        alert.username(),
        alert.userId(),
        alert.sourceIp(),
        alert.title(),
        alert.content(),
        alert.createdAt(),
        alert.status()
    );
  }
}
