package com.njydsz.message.server.channel.impl;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.message.server.channel.MessageChannel;
import com.njydsz.message.server.config.MessageProperties;

/**
 * 微信小程序订阅消息通道实现。
 *
 * <p>实现 {@link MessageChannel} SPI，通过微信小程序订阅消息 API 下发通知。 需要用户在小程序端主动订阅消息模板后才能发送，每次发送消耗一次订阅配额。
 *
 * <p>降级策略：未配置 AppID/AppSecret 或 provider=mock 时降级为日志输出。
 *
 * <p>API 流程：
 *
 * <ol>
 *   <li>获取 access_token（缓存到 Redis，7200s 有效期）
 *   <li>调用 subscribeMessage/send 下发订阅消息
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(
    prefix = "ydsz.message.wx-mini",
    name = "provider",
    havingValue = "wechat",
    matchIfMissing = false)
public class WxMiniChannel implements MessageChannel {
  /** 默认 Token 有效期（秒） */
  private static final int DEFAULT_EXPIRES_IN = 7200;

  /** Token 安全余量（秒） */
  private static final int TOKEN_SAFETY_MARGIN_SECONDS = 300;


  private static final String CHANNEL_TYPE = "WX_MINI";

  /** 微信 access_token Redis 缓存 key */
  private static final String ACCESS_TOKEN_CACHE_KEY = "ydsz:wx:mini:access_token";

  private final MessageProperties messageProperties;
  private final RestTemplate restTemplate;
  private final RedisStringOps redisStringOps;
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  public WxMiniChannel(
      MessageProperties messageProperties,
      RestTemplate restTemplate,
      RedisStringOps redisStringOps,
      SnowflakeIdGenerator snowflakeIdGenerator) {
    this.messageProperties = messageProperties;
    this.restTemplate = restTemplate;
    this.redisStringOps = redisStringOps;
    this.snowflakeIdGenerator = snowflakeIdGenerator;
  }

  @Override
  public String channelType() {
    return CHANNEL_TYPE;
  }

  @Override
  public MessageResult send(MessageRequest request) {
    if (request.getReceiver() == null || request.getReceiver().isBlank()) {
      return MessageResult.fail(CHANNEL_TYPE, null, "微信小程序接收人(OpenID)不能为空", "微信小程序接收人(OpenID)不能为空", null);
    }

    MessageProperties.WxMiniConfig config = messageProperties.getWxMini();
    if (config == null
        || !StringUtils.hasText(config.getAppId())
        || !StringUtils.hasText(config.getAppSecret())) {
      log.warn("[WxMiniChannel] 未配置 AppID/AppSecret,降级为日志输出: receiver={}", request.getReceiver());
      return mockSend(request);
    }

    try {
      String accessToken = getAccessToken(config);
      if (accessToken == null) {
        return MessageResult.fail(CHANNEL_TYPE, null, "获取微信 access_token 失败", "获取微信 access_token 失败", null);
      }

      String url =
          config.getBaseUrl() + "/cgi-bin/message/subscribe/send?access_token=" + accessToken;

      Map<String, Object> body =
          Map.of(
              "touser",
              request.getReceiver(),
              "template_id",
              request.getTemplateCode() != null ? request.getTemplateCode() : "",
              "page",
              "pages/index/index",
              "data",
              buildTemplateData(request),
              "miniprogram_state",
              "formal");

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

      ResponseEntity<Map<String, Object>> resp =
          restTemplate.exchange(
              url,
              HttpMethod.POST,
              entity,
              new ParameterizedTypeReference<Map<String, Object>>() {});
      Map<?, ?> resultBody = resp.getBody();

      if (resultBody != null && Integer.valueOf(0).equals(resultBody.get("errcode"))) {
        String traceId = "WX_MINI-" + String.valueOf(snowflakeIdGenerator.nextId());
        log.info(
            "[WxMiniChannel] 发送成功: receiver={} template={}",
            request.getReceiver(),
            request.getTemplateCode());
        return MessageResult.ok(CHANNEL_TYPE, traceId);
      } else {
        String errMsg = resultBody != null ? String.valueOf(resultBody.get("errmsg")) : "未知错误";
        log.error(
            "[WxMiniChannel] 发送失败: receiver={} errcode={} errmsg={}",
            request.getReceiver(),
            resultBody != null ? resultBody.get("errcode") : "N/A",
            errMsg);
        return MessageResult.fail(CHANNEL_TYPE, null, "微信小程序发送失败: " + errMsg, "微信小程序发送失败: " + errMsg, null);
      }
    } catch (Exception e) {
      log.error(
          "[WxMiniChannel] 发送异常: receiver={} err={}", request.getReceiver(), e.getMessage(), e);
      return MessageResult.fail(
          CHANNEL_TYPE, null, e.getClass().getSimpleName() + ": " + e.getMessage(),
          e.getClass().getSimpleName() + ": " + e.getMessage(), null);
    }
  }

  /**
   * 获取微信 access_token（Redis 缓存，7200s 有效期）。
   *
   * @param config 参数说明
   * @return 返回值说明
   */
  private String getAccessToken(MessageProperties.WxMiniConfig config) {
    try {
      String cached = redisStringOps.get(ACCESS_TOKEN_CACHE_KEY, String.class);
      if (StringUtils.hasText(cached)) {
        return cached;
      }
      String url =
          config.getBaseUrl()
              + "/cgi-bin/token?grant_type=client_credential"
              + "&appid="
              + config.getAppId()
              + "&secret="
              + config.getAppSecret();
      ResponseEntity<Map<String, Object>> resp =
          restTemplate.exchange(
              url, HttpMethod.GET, null, new ParameterizedTypeReference<Map<String, Object>>() {});
      Map<?, ?> body = resp.getBody();
      if (body != null && body.containsKey("access_token")) {
        String token = (String) body.get("access_token");
        int expiresIn = body.containsKey("expires_in") ? (Integer) body.get("expires_in") : DEFAULT_EXPIRES_IN;
        redisStringOps.set(ACCESS_TOKEN_CACHE_KEY, token, Duration.ofSeconds(expiresIn - TOKEN_SAFETY_MARGIN_SECONDS));
        return token;
      }
      log.error(
          "[WxMiniChannel] 获取 access_token 失败: {}",
          body != null ? body.get("errmsg") : "null response");
      return null;
    } catch (Exception e) {
      log.error("[WxMiniChannel] 获取 access_token 异常: {}", e.getMessage(), e);
      return null;
    }
  }

  /** 构造模板消息 data 字段。 微信小程序订阅消息的 data 格式为 { "key": { "value": "xxx" } } */
  private Map<String, Object> buildTemplateData(MessageRequest request) {
    if (request.getParams() == null) {
      return Map.of();
    }
    Map<String, Object> result = new HashMap<>(request.getParams().size());
    for (Map.Entry<String, Object> entry : request.getParams().entrySet()) {
      result.put(
          entry.getKey(),
          Map.of("value", entry.getValue() == null ? "" : String.valueOf(entry.getValue())));
    }
    return result;
  }

  /**
   * Mock 发送（开发环境降级）。
   *
   * @param request 参数说明
   * @return 返回值说明
   */
  private MessageResult mockSend(MessageRequest request) {
    String traceId = "WX_MINI-MOCK-" + String.valueOf(snowflakeIdGenerator.nextId());
    log.info(
        "[WxMiniChannel][MOCK] 模拟发送: receiver={} template={} content={}",
        request.getReceiver(),
        request.getTemplateCode(),
        request.getContent());
    return MessageResult.ok(CHANNEL_TYPE, traceId);
  }
}
