package com.njydsz.message.server.channel;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.message.server.config.ChannelProperties;

/**
 * HMAC 工作通知通道（企业内部应用）。
 *
 * <p>通过 IM 开放平台企业内部应用发送工作通知(与群机器人不同, 工作通知可指定 userId 定向发送,支持 text/markdown/actionCard 消息类型)。
 *
 * <p>流程：
 *
 * <ol>
 *   <li>AppKey + AppSecret → 获取 access_token(缓存 Redis,7200s)
 *   <li>调用平台工作通知接口发送通知
 * </ol>
 *
 * <p>未配置 AppKey 时降级为 mock 输出日志,保证开发环境可运行。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HmacWorkNotificationChannel implements MessageChannel {
  /** Token 安全余量（秒） */
  private static final long TOKEN_SAFETY_MARGIN_SECONDS = 300;


  private static final String CHANNEL_TYPE = "HMAC_WORK";
  private static final String TOKEN_CACHE_KEY = "ydsz:msg:hmac:work:access_token";
  private static final Duration TOKEN_TTL = Duration.ofSeconds(7200);

  /** 分布式 ID 生成器 */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  private final ChannelProperties channelProperties;
  private final RedisStringOps redisStringOps;

  RestClient restClient;

  /**
   * 初始化工作通知通道：构建带超时配置的 {@link RestClient}。
   *
   * <p>连接/读取超时取自 {@code hmacWork} 配置。该客户端供 {@link #send} 调用， 未启用或 Mock 降级场景下 {@code send}
   * 不依赖真实网络，本方法仍照常构建以避免空指针。
   */
  @PostConstruct
  public void init() {
    ChannelProperties.HmacWorkConfig cfg = channelProperties.getChannel().getHmacWork();
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
    ChannelProperties.HmacWorkConfig cfg = channelProperties.getChannel().getHmacWork();

    // 降级 mock
    if (!cfg.isEnabled() || !StringUtils.hasText(cfg.getAppKey())) {
      log.warn(
          "[HMAC_WORK] 未启用或未配置 AppKey, 降级 mock: receiver={} content={}",
          request.getReceiver(),
          truncate(request.getContent(), 100));
      return MessageResult.ok(CHANNEL_TYPE, "mock-" + System.currentTimeMillis());
    }

    String accessToken = getAccessToken(cfg);
    if (accessToken == null) {
      return MessageResult.fail(CHANNEL_TYPE, "获取 access_token 失败");
    }

    String receiver = request.getReceiver();
    if (!StringUtils.hasText(receiver)) {
      return MessageResult.fail(CHANNEL_TYPE, "接收人(userId)不能为空");
    }

    Map<String, Object> payload = buildPayload(request, cfg.getAgentId(), receiver);
    String url =
        cfg.getBaseUrl()
            + "/topapi/message/corpconversation/asyncsend_v2?access_token="
            + accessToken;

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
          log.info("[HMAC_WORK] 发送成功: receiver={}", receiver);
          return MessageResult.ok(CHANNEL_TYPE, traceId);
        }
        String errmsg = (String) body.getOrDefault("errmsg", "unknown");
        log.error("[HMAC_WORK] 发送失败: errcode={} errmsg={}", errcode, errmsg);
        return MessageResult.fail(CHANNEL_TYPE, "errcode=" + errcode + ", errmsg=" + errmsg);
      }
      log.error("[HMAC_WORK] 发送失败: status={}", response.getStatusCode());
      return MessageResult.fail(CHANNEL_TYPE, "HTTP " + response.getStatusCode());
    } catch (Exception e) {
      log.error("[HMAC_WORK] 发送异常: reason={}", e.getMessage(), e);
      return MessageResult.fail(CHANNEL_TYPE, e.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }

  /**
   * 获取 access_token（Redis 缓存，提前续期）。
   *
   * @param cfg 参数说明
   * @return 返回值说明
   */
  private String getAccessToken(ChannelProperties.HmacWorkConfig cfg) {
    try {
      String cached = redisStringOps.get(TOKEN_CACHE_KEY, String.class);
      if (StringUtils.hasText(cached)) {
        return cached;
      }
      String url =
          cfg.getBaseUrl()
              + "/gettoken?appkey="
              + cfg.getAppKey()
              + "&appsecret="
              + cfg.getAppSecret();
      ResponseEntity<String> response = restClient.get().uri(url).retrieve().toEntity(String.class);
      if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
        Map<String, Object> body = YdszJson.parseMap(response.getBody());
        int errcode = ((Number) body.getOrDefault("errcode", -1)).intValue();
        if (errcode == 0) {
          String token = (String) body.get("access_token");
          redisStringOps.set(TOKEN_CACHE_KEY, token, TOKEN_TTL.minusSeconds(TOKEN_SAFETY_MARGIN_SECONDS));
          log.info("[HMAC_WORK] 刷新 access_token 成功");
          return token;
        }
        log.error(
            "[HMAC_WORK] 获取 access_token 失败: errcode={} errmsg={}",
            errcode,
            body.get("errmsg"));
      }
    } catch (Exception e) {
      log.error("[HMAC_WORK] 获取 access_token 异常: {}", e.getMessage(), e);
    }
    return null;
  }

  /** 构造工作通知请求体。 */
  private Map<String, Object> buildPayload(MessageRequest request, Long agentId, String receiver) {
    String content = request.getContent() == null ? "" : request.getContent();
    String subject = request.getSubject() == null ? "YDSZ 通知" : request.getSubject();
    String msgType = "text";
    if (request.getParams() != null) {
      Object mt = request.getParams().get("msgType");
      if (mt instanceof String s
          && ("markdown".equalsIgnoreCase(s) || "action_card".equalsIgnoreCase(s))) {
        msgType = "markdown".equalsIgnoreCase(s) ? "markdown" : "action_card";
      }
    }

    Map<String, Object> msg = new HashMap<>();
    if ("markdown".equals(msgType)) {
      msg.put("msgtype", "markdown");
      Map<String, Object> markdown = new HashMap<>();
      markdown.put("title", subject);
      markdown.put("text", content);
      msg.put("markdown", markdown);
    } else {
      msg.put("msgtype", "text");
      Map<String, Object> text = new HashMap<>();
      text.put("content", content);
      msg.put("text", text);
    }

    Map<String, Object> payload = new HashMap<>();
    payload.put("agent_id", agentId);
    payload.put("userid_list", receiver);
    payload.put("msg", msg);
    return payload;
  }

  private String truncate(String s, int max) {
    if (s == null) {
      return "";
    }
    return s.length() > max ? s.substring(0, max) + "..." : s;
  }
}
