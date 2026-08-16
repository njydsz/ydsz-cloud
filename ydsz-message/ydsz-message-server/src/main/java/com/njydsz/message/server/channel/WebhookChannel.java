package com.njydsz.message.server.channel;

import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.message.server.config.ChannelProperties;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Webhook 通道实现（P1-F1: 新增 HMAC-SHA256 签名验证）。
 *
 * <p>通过 HTTP POST 将通知推送到用户配置的 Webhook URL，请求体格式 {@code {"text":"消息内容","title":"消息标题"}}，兼容常见群机器人协议。
 *
 * <p><b>签名机制（可选，默认开启）：</b>
 *
 * <ul>
 *   <li>签名算法：HMAC-SHA256
 *   <li>签名字符串：{@code timestamp + "\n" + secret}（钉钉风格）
 *   <li>请求头：{@code X-Webhook-Timestamp} / {@code X-Webhook-Signature}
 *   <li>密钥来源：消息参数 {@code params.webhookSecret} 或配置 {@code ydsz.webhook.secret}
 * </ul>
 *
 * <p>URL 解析优先级：
 *
 * <ol>
 *   <li>消息参数 {@code params.webhookUrl}（显式指定，最高优先级）
 *   <li>{@code request.receiver}（以 http 开头时视为 Webhook URL）
 *   <li>系统配置 {@code ydsz.webhook.default-url}（兜底默认地址）
 * </ol>
 *
 * <p>超时取 {@code ydsz.webhook.connect-timeout / read-timeout}。发送失败被捕获并转为失败结果。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookChannel implements MessageChannel {

  /** 通道类型 */
  private static final String CHANNEL_TYPE = "WEBHOOK";

  /** HMAC-SHA256 算法名 */
  private static final String HMAC_SHA256_ALGO = "HmacSHA256";

  /** 签名时间戳请求头 */
  private static final String HEADER_TIMESTAMP = "X-Webhook-Timestamp";

  /** 签名值请求头 */
  private static final String HEADER_SIGNATURE = "X-Webhook-Signature";

  /** 通道配置（提供 default-url / 超时 / 签名密钥） */
  /** 分布式 ID 生成器 */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  private final ChannelProperties channelProperties;

  /** HTTP 客户端，在 {@link #init()} 中按配置超时构建 */
  RestClient restClient;

  /** 注入配置后按 {@code ydsz.webhook.connect-timeout / read-timeout} 构建 RestClient。 */
  @PostConstruct
  public void init() {
    ChannelProperties.WebhookConfig cfg = channelProperties.getWebhook();
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(cfg.getConnectTimeout());
    factory.setReadTimeout(cfg.getReadTimeout());
    this.restClient = RestClient.builder().requestFactory(factory).build();
  }

  /**
   * 通道类型。
   *
   * @return WEBHOOK
   */
  @Override
  public String channelType() {
    return CHANNEL_TYPE;
  }

  /**
   * 发送 Webhook 通知：构造 JSON 请求体、可选签名、POST 到目标 URL， 根据 HTTP 状态码判断成功 / 失败。
   *
   * @param request 消息请求
   * @return 发送结果
   */
  @Override
  public MessageResult send(MessageRequest request) {
    String webhookUrl = resolveUrl(request);
    if (!StringUtils.hasText(webhookUrl)) {
      log.warn("[WEBHOOK] 未配置 Webhook URL，跳过发送: receiver={}", request.getReceiver());
      return MessageResult.fail(CHANNEL_TYPE, "Webhook URL 未配置");
    }
    Map<String, Object> payload = new HashMap<>();
    payload.put("text", request.getContent() == null ? "" : request.getContent());
    payload.put("title", request.getSubject() == null ? "YDSZ 通知" : request.getSubject());
    // 添加 msgId 供下游去重追踪
    if (request.getMessageId() != null) {
      payload.put("msgId", request.getMessageId());
    }
    String body = YdszJson.toJson(payload);
    try {
      // 构建请求（含可选签名头）
      long timestamp = System.currentTimeMillis();
      String secret = resolveSecret(request);
      RestClient.RequestBodySpec bodySpec =
          restClient
              .post()
              .uri(webhookUrl)
              .contentType(MediaType.APPLICATION_JSON)
              .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
      // 签名：如果配置了 secret 则添加时间戳和签名头
      if (StringUtils.hasText(secret)) {
        String signContent = timestamp + "\n" + secret;
        String signature = hmacSha256(signContent, secret);
        bodySpec.header(HEADER_TIMESTAMP, String.valueOf(timestamp));
        bodySpec.header(HEADER_SIGNATURE, signature);
        log.debug("[WEBHOOK] 已添加签名: timestamp={}", timestamp);
      }
      ResponseEntity<String> response = bodySpec.body(body).retrieve().toEntity(String.class);
      int statusCode = response.getStatusCode().value();
      if (response.getStatusCode().is2xxSuccessful()) {
        String traceId = CHANNEL_TYPE + "-" + String.valueOf(snowflakeIdGenerator.nextId());
        log.info("[WEBHOOK] 发送成功: url={} status={}", maskUrl(webhookUrl), statusCode);
        return MessageResult.ok(CHANNEL_TYPE, traceId);
      }
      log.error(
          "[WEBHOOK] 发送失败: url={} status={} body={}",
          maskUrl(webhookUrl),
          statusCode,
          response.getBody());
      return MessageResult.fail(CHANNEL_TYPE, "HTTP " + statusCode);
    } catch (Exception e) {
      log.error("[WEBHOOK] 发送异常: url={} reason={}", maskUrl(webhookUrl), e.getMessage(), e);
      return MessageResult.fail(CHANNEL_TYPE, e.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }

  /**
   * 计算 HMAC-SHA256 签名并 Base64 编码。
   *
   * @param data 待签名内容
   * @param secret 签名密钥
   * @return Base64 编码的签名字符串
   */
  private String hmacSha256(String data, String secret) {
    try {
      Mac mac = Mac.getInstance(HMAC_SHA256_ALGO);
      SecretKeySpec secretKeySpec =
          new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256_ALGO);
      mac.init(secretKeySpec);
      byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(hash);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("HMAC-SHA256 签名失败: " + e.getMessage(), e);
    }
  }

  /**
   * 解析 Webhook 签名密钥，优先级：params.webhookSecret &gt; 配置默认值。
   *
   * @param request 消息请求
   * @return 签名密钥，无则返回 null
   */
  private String resolveSecret(MessageRequest request) {
    Map<String, Object> params = request.getParams();
    if (params != null) {
      Object secret = params.get("webhookSecret");
      if (secret instanceof String s && StringUtils.hasText(s)) {
        return s.trim();
      }
    }
    ChannelProperties.WebhookConfig cfg = channelProperties.getWebhook();
    return cfg.getSecret();
  }

  /**
   * 脱敏 Webhook URL（移除 access_token 等敏感参数用于日志）。
   *
   * @param url 原始 URL
   * @return 脱敏后的 URL
   */
  private String maskUrl(String url) {
    if (!StringUtils.hasText(url)) {
      return "";
    }
    return url.replaceAll("(access_token|secret|key)=[^&]*", "$1=***");
  }

  /**
   * 解析 Webhook URL，优先级：params.webhookUrl &gt; receiver(http 开头) &gt; 默认配置。
   *
   * @param request 消息请求
   * @return 解析到的 URL，无则返回 null
   */
  String resolveUrl(MessageRequest request) {
    Map<String, Object> params = request.getParams();
    if (params != null) {
      Object explicit = params.get("webhookUrl");
      if (explicit instanceof String s && StringUtils.hasText(s)) {
        return s.trim();
      }
    }
    String receiver = request.getReceiver();
    if (StringUtils.hasText(receiver) && receiver.trim().toLowerCase().startsWith("http")) {
      return receiver.trim();
    }
    String defaultUrl = channelProperties.getWebhook().getDefaultUrl();
    if (StringUtils.hasText(defaultUrl)) {
      return defaultUrl.trim();
    }
    return null;
  }
}
