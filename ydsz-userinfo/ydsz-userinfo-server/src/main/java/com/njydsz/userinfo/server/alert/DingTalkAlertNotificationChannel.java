package com.njydsz.userinfo.server.alert;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
 * 钉钉机器人告警通知渠道（P2 告警通知扩展）。
 *
 * <p>通过钉钉企业机器人 Webhook 推送安全告警消息。
 * 支持文本和 markdown 两种消息格式（默认使用 markdown）。
 *
 * <p><b>配置项：</b>
 *
 * <ul>
 *   <li>{@code ydsz.userinfo.alert.dingtalk.webhook-url} — 钉钉机器人 Webhook URL（必填）</li>
 *   <li>{@code ydsz.userinfo.alert.dingtalk.secret} — 加签密钥（可选，机器人安全设置开启"加签"时必填）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@Slf4j
@Component
@Order(200)
@ConditionalOnProperty(prefix = "ydsz.userinfo.alert.dingtalk", name = "webhook-url")
public class DingTalkAlertNotificationChannel implements AlertNotificationChannel {

  /** 渠道名称 */
  private static final String CHANNEL_NAME = "DINGTALK";

  /** 消息载荷初始容量 */
  private static final int PAYLOAD_INITIAL_CAPACITY = 8;

  /** 消息体内 markdown 对象初始容量 */
  private static final int MARKDOWN_INITIAL_CAPACITY = 4;

  /** HMAC 签名算法 */
  private static final String HMAC_ALGORITHM = "HmacSHA256";

  /** URL 编码字符集名称 */
  private static final String URL_ENCODING_CHARSET = StandardCharsets.UTF_8.name();

  @Value("${ydsz.userinfo.alert.dingtalk.webhook-url:}")
  private String webhookUrl;

  @Value("${ydsz.userinfo.alert.dingtalk.secret:}")
  private String signingSecret;

  private final RestTemplate restTemplate;

  /**
   * 构造钉钉告警通知渠道。
   *
   * @param restTemplate RestTemplate Bean
   */
  public DingTalkAlertNotificationChannel(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  @Override
  public void sendAlert(SecurityAlert alert) {
    try {
      String url = buildSignedUrl();

      // 构建钉钉消息格式
      Map<String, Object> payload = buildPayload(alert);

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);

      HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
      restTemplate.postForObject(url, request, String.class);

      log.info("钉钉告警通知发送成功: alertId={}, type={}", alert.id(), alert.alertType());
    } catch (Exception e) {
      log.warn("钉钉告警通知发送失败: alertId={}, error={}", alert.id(), e.getMessage());
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
   * 构建带加签的 Webhook URL（如配置了加签密钥）。
   *
   * @return 完整 URL
   */
  private String buildSignedUrl() {
    if (signingSecret == null || signingSecret.isBlank()) {
      return webhookUrl;
    }

    try {
      // 钉钉加签规则：timestamp + "\n" + secret 做 HMAC-SHA256 + Base64 + URLEncode
      long timestamp = System.currentTimeMillis();
      String stringToSign = timestamp + "\n" + signingSecret;

      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(
          signingSecret.getBytes(), HMAC_ALGORITHM));
      byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
      String sign = URLEncoder.encode(
          Base64.getEncoder().encodeToString(signData),
          URL_ENCODING_CHARSET);

      return webhookUrl + "&timestamp=" + timestamp + "&sign=" + sign;
    } catch (Exception e) {
      log.warn("构建钉钉加签 URL 失败: {}", e.getMessage());
      return webhookUrl;
    }
  }

  /**
   * 构建钉钉消息载荷（markdown 格式）。
   *
   * @param alert 安全告警
   * @return 载荷 Map
   */
  private Map<String, Object> buildPayload(SecurityAlert alert) {
    Map<String, Object> payload = new HashMap<>(PAYLOAD_INITIAL_CAPACITY);

    // 钉钉 markdown 消息
    String riskEmoji = switch (alert.riskLevel().name()) {
      case "CRITICAL" -> "🚨";
      case "HIGH" -> "⚠️";
      case "MEDIUM" -> "⚡";
      default -> "ℹ️";
    };

    String markdownContent = String.format(
        "%s **安全告警通知**\n\n"
            + "---\n\n"
            + "**等级**: %s\n\n"
            + "**类型**: %s\n\n"
            + "**用户**: %s(%s)\n\n"
            + "**来源IP**: %s\n\n"
            + "**标题**: %s\n\n"
            + "**内容**: %s\n\n"
            + "**时间**: %s",
        riskEmoji,
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
    markdown.put("title", "安全告警: " + alert.title());
    markdown.put("text", markdownContent);

    payload.put("msgtype", "markdown");
    payload.put("markdown", markdown);

    return payload;
  }
}
