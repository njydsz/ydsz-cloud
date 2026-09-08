package com.njydsz.userinfo.server.alert;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import com.njydsz.userinfo.domain.alert.SecurityAlert;

/**
 * SMTP 邮件告警通知渠道（P2 告警通知扩展）。
 *
 * <p>通过 SMTP 邮件发送安全告警通知至安全管理员邮箱。
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

  private final MailSender mailSender;

  /**
   * 构造邮件告警通知渠道。
   *
   * <p>当 Spring Boot 未配置 Spring Mail 时，{@link JavaMailSenderImpl} 会作为 fallback 注入，
   * 但发送时会失败。此时 {@link #isAvailable()} 返回 false。
   *
   * @param mailSender MailSender Bean
   */
  public SmtpAlertNotificationChannel(MailSender mailSender) {
    this.mailSender = mailSender;
  }

  @Override
  public void sendAlert(SecurityAlert alert) {
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(System.getProperty("ydsz.userinfo.alert.email.from", "security@ydsz.top"));
      message.setTo(System.getProperty("ydsz.userinfo.alert.email.to", "admin@ydsz.top").split(","));
      message.setSubject(buildSubject(alert));
      message.setText(buildBody(alert));

      mailSender.send(message);

      log.info("邮件告警通知发送成功: alertId={}, type={}", alert.id(), alert.alertType());
    } catch (Exception e) {
      log.warn("邮件告警通知发送失败: alertId={}, error={}", alert.id(), e.getMessage());
    }
  }

  @Override
  public String getChannelName() {
    return CHANNEL_NAME;
  }

  @Override
  public boolean isAvailable() {
    return mailSender != null;
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
