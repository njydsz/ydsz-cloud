package com.njydsz.userinfo.server.alert;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.njydsz.userinfo.domain.alert.SecurityAlert;

/**
 * Webhook 告警通知渠道（P2 告警通知扩展）。
 *
 * <p>通过 HTTP POST 将告警信息推送到配置的 webhook URL（如企业微信机器人、
 * Slack、Discord、飞书等），实现实时告警通知。
 *
 * <p><b>配置项：</b>
 *
 * <ul>
 *   <li>{@code ydsz.userinfo.alert.webhook.url} — Webhook 端点 URL（必填，为空时禁用）</li>
 *   <li>{@code ydsz.userinfo.alert.webhook.secret} — HMAC 签名密钥（可选）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@Slf4j
@Component
@Order(100)
@ConditionalOnProperty(prefix = "ydsz.userinfo.alert.webhook", name = "url")
public class WebhookAlertNotificationChannel implements AlertNotificationChannel {

  /** 渠道名称 */
  private static final String CHANNEL_NAME = "WEBHOOK";

  /** 请求超时时间（毫秒） */
  private static final int TIMEOUT_MS = 5000;

  /** 毫秒转换秒的除数 */
  private static final long MILLISECONDS_PER_SECOND = 1000L;

  /** Webhook 载荷初始容量 */
  private static final int PAYLOAD_INITIAL_CAPACITY = 8;

  /** 消息体内 markdown 对象初始容量 */
  private static final int MARKDOWN_INITIAL_CAPACITY = 4;

  @Value("${ydsz.userinfo.alert.webhook.url:}")
  private String webhookUrl;

  @Value("${ydsz.userinfo.alert.webhook.secret:}")
  private String signingSecret;

  private final RestTemplate restTemplate;

  /**
   * 构造 Webhook 告警通知渠道。
   *
   * @param restTemplate RestTemplate Bean
   */
  public WebhookAlertNotificationChannel(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  @Override
  public void sendAlert(SecurityAlert alert) {
    try {
      // 构建 JSON 载荷
      Map<String, Object> payload = buildPayload(alert);

      // 构建请求头
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      // 如有签名密钥，添加 HMAC 签名头
      if (signingSecret != null && !signingSecret.isBlank()) {
        String timestamp = String.valueOf(System.currentTimeMillis() / MILLISECONDS_PER_SECOND);
        headers.set("X-Sign-Timestamp", timestamp);
        headers.set("X-Sign", generateHmacSign(timestamp));
      }

      HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
      restTemplate.postForObject(webhookUrl, request, String.class);

      log.info("Webhook 告警通知发送成功: alertId={}, type={}", alert.id(), alert.alertType());
    } catch (Exception e) {
      // 告警通知失败不应影响主流程，仅记录日志
      log.warn("Webhook 告警通知发送失败: alertId={}, url={}, error={}",
          alert.id(), webhookUrl, e.getMessage());
    }
  }

  @Override
  public String getChannelName() {
    return CHANNEL_NAME;
  }

  @Override
  public boolean isAvailable() {
    return webhookUrl != null && !webhookUrl.isBlank();
  }

  /**
   * 构建 Webhook 载荷（企业微信机器人/飞书 bot 通用 markdown 格式）。
   *
   * @param alert 安全告警
   * @return 载荷 Map
   */
  private Map<String, Object> buildPayload(SecurityAlert alert) {
    Map<String, Object> payload = new HashMap<>(PAYLOAD_INITIAL_CAPACITY);

    // 企业微信 bot markdown 格式
    String riskLevelColor = switch (alert.riskLevel().name()) {
      case "CRITICAL" -> "red";
      case "HIGH" -> "orange";
      case "MEDIUM" -> "yellow";
      default -> "green";
    };

    String markdownContent = String.format(
        "**安全告警通知**\n"
            + ">等级: <font color=\"%s\">%s</font>\n"
            + ">类型: %s\n"
            + ">用户: %s(%s)\n"
            + ">IP: %s\n"
            + ">标题: %s\n"
            + ">内容: %s\n"
            + ">时间: %s",
        riskLevelColor,
        alert.riskLevel(),
        alert.alertType(),
        alert.username(),
        alert.userId(),
        alert.sourceIp(),
        alert.title(),
        alert.content(),
        alert.createdAt()
    );

    Map<String, Object> markdown = new HashMap<>(MARKDOWN_INITIAL_CAPACITY);
    markdown.put("content", markdownContent);

    payload.put("msgtype", "markdown");
    payload.put("markdown", markdown);

    return payload;
  }

  /**
   * 生成 HMAC-SHA256 签名（企业微信 bot 要求）。
   *
   * @param timestamp 时间戳字符串
   * @return Base64 编码的 HMAC 签名
   */
  private String generateHmacSign(String timestamp) {
    try {
      String data = timestamp + "\n" + signingSecret;
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(
          signingSecret.getBytes(), "HmacSHA256"));
      byte[] hash = mac.doFinal(data.getBytes());
      return Base64.getEncoder().encodeToString(hash);
    } catch (Exception e) {
      log.warn("生成 HMAC 签名失败: {}", e.getMessage());
      return "";
    }
  }
}
