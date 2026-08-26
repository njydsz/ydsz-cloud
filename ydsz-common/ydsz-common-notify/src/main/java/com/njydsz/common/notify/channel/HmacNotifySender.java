package com.njydsz.common.notify.channel;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.notify.core.NotifySendResult;
import com.njydsz.common.notify.enums.NotifyChannel;
import com.njydsz.common.notify.template.TemplateEngine;

/**
 * HMAC 签名通知发送器
 *
 * <p>通过群机器人 Webhook 发送消息，支持 HMAC-SHA256 签名校验。
 *
 * <p>支持安全设置：当配置了 secret 时，自动使用 HMAC-SHA256 签名校验， 将 {@code timestamp} 和 {@code sign} 参数拼接到 webhook
 * URL 中。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(prefix = "ydsz.notify.hmac", name = "webhook")
public class HmacNotifySender implements NotifyChannelStrategy {

  private static final Logger LOG = LoggerFactory.getLogger(HmacNotifySender.class);

  private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";

  @Value("${ydsz.notify.hmac.webhook:}")
  private String webhook;

  @Value("${ydsz.notify.hmac.secret:}")
  private String secret;

  private final RestTemplate restTemplate;
  private final TemplateEngine templateEngine;

  /**
   * 构造 HMAC 签名通知发送器
   *
   * @param restTemplate HTTP 请求客户端
   * @param templateEngine 模板引擎
   */
  public HmacNotifySender(RestTemplate restTemplate, TemplateEngine templateEngine) {
    this.restTemplate = restTemplate;
    this.templateEngine = templateEngine;
  }

  @Override
  public NotifyChannel getChannel() {
    return NotifyChannel.HMAC;
  }

  /**
   * 发送通知
   *
   * @param receiver 接收者（Webhook 模式下可为 null）
   * @param title 消息标题
   * @param content 消息内容
   * @return 发送结果
   */
  @Override
  public NotifySendResult send(String receiver, String title, String content) {
    if (!isEnabled()) {
      return NotifySendResult.failure("HMAC 通知未启用", getChannel().getName());
    }
    try {
      Map<String, Object> body =
          Map.of(
              "msgtype",
              "markdown",
              "markdown",
              Map.of("title", title, "text", "## " + title + "\n\n" + content));
      String json = YdszJson.toJson(body);
      String signedUrl = signWebhookUrl(webhook);
      String response =
          restTemplate.postForObject(
              signedUrl, new HttpEntity<>(json, NotifyChannelStrategy.jsonHeaders()), String.class);
      LOG.debug("HMAC 通知发送成功: {}", title);
      return NotifySendResult.success(response, getChannel().getName());
    } catch (Exception e) {
      LOG.error("HMAC 通知发送失败: {}", e.getMessage(), e);
      return NotifySendResult.failure(e.getMessage(), getChannel().getName());
    }
  }

  /**
   * 对 webhook URL 进行签名（当配置了 secret 时）
   *
   * <p>签名算法：HMAC-SHA256，待签名字符串为 {@code timestamp + "\n" + secret}。
   *
   * @param url 原始 webhook URL
   * @return 带签名参数的 URL
   */
  String signWebhookUrl(String url) {
    if (secret == null || secret.isEmpty()) {
      return url;
    }
    long timestamp = System.currentTimeMillis();
    try {
      Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256_ALGORITHM));
      byte[] signData = mac.doFinal((timestamp + "\n" + secret).getBytes(StandardCharsets.UTF_8));
      String sign =
          URLEncoder.encode(Base64.getEncoder().encodeToString(signData), StandardCharsets.UTF_8);
      return url + (url.contains("?") ? "&" : "?") + "timestamp=" + timestamp + "&sign=" + sign;
    } catch (Exception e) {
      LOG.error("HMAC webhook 签名失败: {}", e.getMessage(), e);
      return url;
    }
  }

  /**
   * 使用模板发送通知
   *
   * @param receiver 接收者（可为 null）
   * @param templateCode 模板编码
   * @param templateParams 模板参数
   * @return 发送结果
   */
  @Override
  public NotifySendResult sendTemplate(
      String receiver, String templateCode, Object templateParams) {
    Map<String, Object> params = extractParams(templateParams);
    String content = templateEngine.render(templateCode, params);
    return send(receiver, templateCode, content);
  }

  /**
   * 批量发送通知
   *
   * @param receivers 接收者列表
   * @param title 消息标题
   * @param content 消息内容
   * @return 发送结果
   */
  @Override
  public NotifySendResult batchSend(List<String> receivers, String title, String content) {
    return send(null, title, content);
  }

  /**
   * 判断渠道是否启用
   *
   * @return 启用返回 true，否则返回 false
   */
  @Override
  public boolean isEnabled() {
    return webhook != null && !webhook.isEmpty();
  }
}
