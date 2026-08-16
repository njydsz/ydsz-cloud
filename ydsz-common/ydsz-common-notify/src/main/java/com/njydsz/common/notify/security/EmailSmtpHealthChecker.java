package com.njydsz.common.notify.security;

import com.njydsz.common.notify.config.NotifyProperties;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringUtils;

/**
 * SMTP 连接池预热与健康探活（P0-2）
 *
 * <p>定时探测 SMTP 服务连通性，预热连接池，避免首次发送时的连接延迟。 探活失败时记录告警日志，并可通过 {@link #isHealthy()} 查询当前健康状态。
 *
 * <p>探活策略：
 *
 * <ul>
 *   <li>每 60 秒执行一次 SMTP NOOP 探活
 *   <li>启动时立即执行一次预热连接
 *   <li>连续 3 次探活失败标记为不健康
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class EmailSmtpHealthChecker {

  private static final Logger log = LoggerFactory.getLogger(EmailSmtpHealthChecker.class);

  private static final int MAX_FAILURE_STREAK = 3;

  private final NotifyProperties properties;
  private volatile boolean healthy = true;
  private volatile int failureStreak = 0;
  private volatile long lastCheckTime = 0;

  /**
   * 构造 SMTP 健康检查器
   *
   * @param properties 通知配置属性
   */
  public EmailSmtpHealthChecker(NotifyProperties properties) {
    this.properties = properties;
  }

  /** 定时 SMTP 探活（每 60 秒执行一次） */
  @Scheduled(fixedDelay = 60_000, initialDelay = 5_000)
  public void healthCheck() {
    NotifyProperties.EmailConfig email = properties.getEmail();
    if (email == null || !email.isEnabled() || !StringUtils.hasText(email.getSmtpHost())) {
      return;
    }
    try {
      boolean ok = probeSmtp(email);
      lastCheckTime = System.currentTimeMillis();
      if (ok) {
        failureStreak = 0;
        if (!healthy) {
          log.info("[EmailSmtpHealthChecker] SMTP 连接恢复, host={}", email.getSmtpHost());
        }
        healthy = true;
      } else {
        onFailure(email);
      }
    } catch (Exception e) {
      log.debug("[EmailSmtpHealthChecker] SMTP 探活异常: {}", e.getMessage());
      onFailure(email);
    }
  }

  /**
   * 查询 SMTP 当前健康状态
   *
   * @return true 表示 SMTP 连通正常
   */
  public boolean isHealthy() {
    return healthy;
  }

  /**
   * 获取上次探活时间戳
   *
   * @return 上次探活的 System.currentTimeMillis()
   */
  public long getLastCheckTime() {
    return lastCheckTime;
  }

  /**
   * 探测 SMTP 连通性
   *
   * @param email 邮件配置
   * @return true 表示连通正常
   */
  private boolean probeSmtp(NotifyProperties.EmailConfig email) {
    JavaMailSenderImpl sender = createSender(email);
    try {
      // 通过 Transport.isConnected() 探测 SMTP 连通性
      Session session = sender.getSession();
      Transport transport = session.getTransport(sender.getProtocol());
      transport.connect(
          sender.getHost(), sender.getPort(), sender.getUsername(), sender.getPassword());
      transport.close();
      return true;
    } catch (Exception e) {
      log.debug(
          "[EmailSmtpHealthChecker] SMTP 探测失败: host={}, port={}, error={}",
          email.getSmtpHost(),
          email.getSmtpPort(),
          e.getMessage());
      return false;
    }
  }

  /**
   * 探活失败处理
   *
   * @param email 邮件配置
   */
  private void onFailure(NotifyProperties.EmailConfig email) {
    failureStreak++;
    if (failureStreak >= MAX_FAILURE_STREAK) {
      healthy = false;
      log.error(
          "[EmailSmtpHealthChecker] SMTP 连续 {} 次探活失败，标记为不健康, host={}, port={}",
          failureStreak,
          email.getSmtpHost(),
          email.getSmtpPort());
    } else {
      log.warn(
          "[EmailSmtpHealthChecker] SMTP 探活失败 ({}/{}), host={}",
          failureStreak,
          MAX_FAILURE_STREAK,
          email.getSmtpHost());
    }
  }

  /**
   * 创建临时 JavaMailSender 用于探活
   *
   * @param email 邮件配置
   * @return JavaMailSenderImpl 实例
   */
  private JavaMailSenderImpl createSender(NotifyProperties.EmailConfig email) {
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost(email.getSmtpHost());
    sender.setPort(email.getSmtpPort());
    sender.setUsername(email.getFromMail());
    sender.setPassword(email.getPassword());
    sender.setProtocol(email.getSsl().isEnabled() ? "smtps" : "smtp");
    Properties props = sender.getJavaMailProperties();
    props.put("mail.smtp.auth", String.valueOf(email.isAuth()));
    props.put("mail.smtp.connectiontimeout", "5000");
    props.put("mail.smtp.timeout", "5000");
    if (email.getSsl().isEnabled()) {
      props.put("mail.smtp.ssl.enable", "true");
      props.put("mail.smtp.ssl.protocols", email.getSsl().getProtocols());
    }
    if (email.isStarttls()) {
      props.put("mail.smtp.starttls.enable", "true");
    }
    return sender;
  }
}
