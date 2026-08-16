package com.njydsz.message.server.channel.impl;

import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.message.server.channel.MessageChannel;
import com.njydsz.message.server.config.ChannelProperties;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * 企业微信应用消息通道（企业内部应用）。
 *
 * <p>P0-2: 通过企业微信开放平台企业内部应用发送应用消息(与群机器人不同, 应用消息可指定 userId 定向发送,支持 text/markdown/textcard 消息类型)。
 *
 * <p>流程：
 *
 * <ol>
 *   <li>CorpID + CorpSecret → 获取 access_token(缓存 Redis,7200s)
 *   <li>调用 {@code /cgi-bin/message/send} 发送应用消息
 * </ol>
 *
 * <p>未配置 CorpID 时降级为 mock 输出日志,保证开发环境可运行。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeComAppChannel implements MessageChannel {

  private static final String CHANNEL_TYPE = "WECOM_APP";
  private static final String TOKEN_CACHE_KEY_PREFIX = "ydsz:msg:wecom:app:access_token:";
  private static final Duration TOKEN_TTL = Duration.ofSeconds(7200);

  /** 分布式 ID 生成器 */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  private final ChannelProperties channelProperties;
  private final RedisStringOps redisStringOps;

  RestClient restClient;

  /**
   * 初始化企业微信应用通道：构建带超时配置的 {@link RestClient}。
   *
   * <p>连接/读取超时取自 {@code wecomApp} 配置。该客户端供 {@link #send} 调用， 未启用或 Mock 降级场景下 {@code send}
   * 不依赖真实网络，本方法仍照常构建以避免空指针。
   */
  @PostConstruct
  public void init() {
    ChannelProperties.WeComAppConfig cfg = channelProperties.getChannel().getWecomApp();
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(cfg.getConnectTimeout());
    factory.setReadTimeout(cfg.getReadTimeout());
    this.restClient = RestClient.builder().requestFactory(factory).build();
  }

  @Override
  public String channelType() {
    return CHANNEL_TYPE;
  }

  @Override
  public MessageResult send(MessageRequest request) {
    ChannelProperties.WeComAppConfig cfg = channelProperties.getChannel().getWecomApp();

    // 降级 mock
    if (!cfg.isEnabled() || !StringUtils.hasText(cfg.getCorpId())) {
      log.warn(
          "[WECOM_APP] 未启用或未配置 CorpID, 降级 mock: receiver={} content={}",
          request.getReceiver(),
          truncate(request.getContent(), 100));
      return MessageResult.ok(CHANNEL_TYPE, "mock-" + System.currentTimeMillis());
    }

    String accessToken = getAccessToken(cfg);
    if (accessToken == null) {
      return MessageResult.fail(CHANNEL_TYPE, "获取企微 access_token 失败");
    }

    String receiver = request.getReceiver();
    if (!StringUtils.hasText(receiver)) {
      return MessageResult.fail(CHANNEL_TYPE, "接收人(userId)不能为空");
    }

    Map<String, Object> payload = buildPayload(request, cfg.getAgentId(), receiver);
    String url = cfg.getBaseUrl() + "/cgi-bin/message/send?access_token=" + accessToken;

    try {
      ResponseEntity<String> response =
          restClient
              .post()
              .uri(url)
              .contentType(MediaType.APPLICATION_JSON)
              .body(YdszJson.toJson(payload))
              .retrieve()
              .toEntity(String.class);
      String traceId = CHANNEL_TYPE + "-" + String.valueOf(snowflakeIdGenerator.nextId());

      if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
        Map<String, Object> body = YdszJson.parseMap(response.getBody());
        int errcode = ((Number) body.getOrDefault("errcode", -1)).intValue();
        if (errcode == 0) {
          log.info("[WECOM_APP] 发送成功: receiver={}", receiver);
          return MessageResult.ok(CHANNEL_TYPE, traceId);
        }
        String errmsg = (String) body.getOrDefault("errmsg", "unknown");
        log.error("[WECOM_APP] 发送失败: errcode={} errmsg={}", errcode, errmsg);
        return MessageResult.fail(CHANNEL_TYPE, "errcode=" + errcode + ", errmsg=" + errmsg);
      }
      log.error("[WECOM_APP] 发送失败: status={}", response.getStatusCode());
      return MessageResult.fail(CHANNEL_TYPE, "HTTP " + response.getStatusCode());
    } catch (Exception e) {
      log.error("[WECOM_APP] 发送异常: reason={}", e.getMessage(), e);
      return MessageResult.fail(CHANNEL_TYPE, e.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }

  /** 获取企微 access_token（Redis 缓存，提前续期）。 */
  private String getAccessToken(ChannelProperties.WeComAppConfig cfg) {
    try {
      String cacheKey = TOKEN_CACHE_KEY_PREFIX + cfg.getCorpId();
      String cached = redisStringOps.get(cacheKey, String.class);
      if (StringUtils.hasText(cached)) {
        return cached;
      }
      String url =
          cfg.getBaseUrl()
              + "/cgi-bin/gettoken?corpid="
              + cfg.getCorpId()
              + "&corpsecret="
              + cfg.getCorpSecret();
      ResponseEntity<String> response = restClient.get().uri(url).retrieve().toEntity(String.class);
      if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
        Map<String, Object> body = YdszJson.parseMap(response.getBody());
        int errcode = ((Number) body.getOrDefault("errcode", -1)).intValue();
        if (errcode == 0) {
          String token = (String) body.get("access_token");
          redisStringOps.set(cacheKey, token, TOKEN_TTL.minusSeconds(300));
          log.info("[WECOM_APP] 刷新 access_token 成功: corpId={}", cfg.getCorpId());
          return token;
        }
        log.error(
            "[WECOM_APP] 获取 access_token 失败: errcode={} errmsg={}", errcode, body.get("errmsg"));
      }
    } catch (Exception e) {
      log.error("[WECOM_APP] 获取 access_token 异常: {}", e.getMessage(), e);
    }
    return null;
  }

  /** 构造企微应用消息请求体。 */
  private Map<String, Object> buildPayload(
      MessageRequest request, Integer agentId, String receiver) {
    String content = request.getContent() == null ? "" : request.getContent();
    String subject = request.getSubject() == null ? "YDSZ 通知" : request.getSubject();
    String msgType = "text";
    if (request.getParams() != null) {
      Object mt = request.getParams().get("msgType");
      if (mt instanceof String s
          && ("markdown".equalsIgnoreCase(s) || "textcard".equalsIgnoreCase(s))) {
        msgType = "markdown".equalsIgnoreCase(s) ? "markdown" : "textcard";
      }
    }

    Map<String, Object> payload = new HashMap<>();
    payload.put("touser", receiver);
    payload.put("msgtype", msgType);
    payload.put("agentid", agentId);

    if ("markdown".equals(msgType)) {
      Map<String, Object> markdown = new HashMap<>();
      markdown.put("content", content);
      payload.put("markdown", markdown);
    } else if ("textcard".equals(msgType)) {
      Map<String, Object> textcard = new HashMap<>();
      textcard.put("title", subject);
      textcard.put("description", content);
      textcard.put(
          "url",
          request.getParams() != null ? request.getParams().getOrDefault("actionUrl", "") : "");
      payload.put("textcard", textcard);
    } else {
      Map<String, Object> text = new HashMap<>();
      text.put("content", content);
      payload.put("text", text);
    }
    return payload;
  }

  private String truncate(String s, int max) {
    if (s == null) return "";
    return s.length() > max ? s.substring(0, max) + "..." : s;
  }
}
