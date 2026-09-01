package com.njydsz.message.server.channel.impl;

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
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.message.server.channel.MessageChannel;
import com.njydsz.message.server.config.ChannelProperties;

/**
 * 企业微信群机器人通道。
 *
 * <p>通过企业微信群机器人 Webhook 推送通知，支持 text / markdown 两种消息类型。 企业微信群机器人无需加签，仅需 key 即可发送。
 *
 * <p>URL 解析优先级：
 *
 * <ol>
 *   <li>{@code params.wechatWorkKey}（显式 key，最高优先级）
 *   <li>{@code receiver} 以 http 开头时视为完整 Webhook URL
 *   <li>{@code receiver} 视为 key，拼接默认 URL 前缀
 *   <li>{@code ydsz.channel.wechat-work.default-key}（兜底）
 * </ol>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WechatWorkChannel implements MessageChannel {

  /** 通道类型 */
  private static final String CHANNEL_TYPE = "WECOM";

  /** 企业微信机器人 Webhook URL 前缀 */
  private static final String WEBHOOK_PREFIX =
      "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=";

  /** 通道配置（提供 default-key / 超时） */
  /** 分布式 ID 生成器 */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  private final ChannelProperties channelProperties;

  /** HTTP 客户端，在 {@link #init()} 中按配置超时构建 */
  RestClient restClient;

  /** 注入配置后按 {@code ydsz.channel.wechat-work.connect-timeout / read-timeout} 构建 RestClient。 */
  @PostConstruct
  public void init() {
    ChannelProperties.WechatWorkConfig cfg = channelProperties.getChannel().getWechatWork();
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(cfg.getConnectTimeout());
    factory.setReadTimeout(cfg.getReadTimeout());
    this.restClient = RestClient.builder().requestFactory(factory).build();
  }

  /**
   * 通道类型。
   *
   * @return WECOM
   */
  @Override
  public String channelType() {
    return CHANNEL_TYPE;
  }

  /**
   * 发送企业微信消息：构造 text / markdown 请求体并 POST 到 Webhook URL， 根据响应 errcode 判断成功 / 失败。
   *
   * @param request 消息请求
   * @return 发送结果
   */
  @Override
  public MessageResult send(MessageRequest request) {
    String webhookUrl = resolveUrl(request);
    if (!StringUtils.hasText(webhookUrl)) {
      log.warn("[WECOM] 未配置 key，跳过发送: receiver={}", request.getReceiver());
      return MessageResult.fail(CHANNEL_TYPE, null, "企业微信 key 未配置", "企业微信 key 未配置", null);
    }

    Map<String, Object> payload = buildPayload(request);

    try {
      ResponseEntity<String> response =
          restClient
              .post()
              .uri(webhookUrl)
              .contentType(MediaType.APPLICATION_JSON)
              .body(YdszJson.toJson(payload))
              .retrieve()
              .toEntity(String.class);
      String traceId = CHANNEL_TYPE + "-" + String.valueOf(snowflakeIdGenerator.nextId());

      if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
        Map<String, Object> body = YdszJson.parseMap(response.getBody());
        int errcode = ((Number) body.getOrDefault("errcode", -1)).intValue();
        if (errcode == 0) {
          log.info("[WECOM] 发送成功");
          return MessageResult.ok(CHANNEL_TYPE, traceId);
        }
        String errmsg = (String) body.getOrDefault("errmsg", "unknown");
        log.error("[WECOM] 发送失败: errcode={} errmsg={}", errcode, errmsg);
        return MessageResult.fail(
            CHANNEL_TYPE, null, "errcode=" + errcode + ", errmsg=" + errmsg,
            "errcode=" + errcode + ", errmsg=" + errmsg, null);
      }
      log.error("[WECOM] 发送失败: status={}", response.getStatusCode());
      return MessageResult.fail(CHANNEL_TYPE, null, "HTTP " + response.getStatusCode(), "HTTP " + response.getStatusCode(), null);
    } catch (Exception e) {
      log.error("[WECOM] 发送异常: reason={}", e.getMessage(), e);
      return MessageResult.fail(
          CHANNEL_TYPE, null, e.getClass().getSimpleName() + ": " + e.getMessage(),
          e.getClass().getSimpleName() + ": " + e.getMessage(), null);
    }
  }

  /**
   * 构造企业微信消息请求体。
   *
   * <ul>
   *   <li>msgType=markdown：{@code {"msgtype":"markdown","markdown":{"content":"内容"}}}
   *   <li>默认 text：{@code {"msgtype":"text","text":{"content":"内容"}}}
   * </ul>
   *
   * @param request 消息请求
   * @return 请求体 Map
   */
  Map<String, Object> buildPayload(MessageRequest request) {
    String content = request.getContent() == null ? "" : request.getContent();
    String msgType = "text";
    if (request.getParams() != null) {
      Object mt = request.getParams().get("msgType");
      if (mt instanceof String s && "markdown".equalsIgnoreCase(s)) {
        msgType = "markdown";
      }
    }

    Map<String, Object> payload = new HashMap<>();
    payload.put("msgtype", msgType);
    if ("markdown".equals(msgType)) {
      Map<String, Object> markdown = new HashMap<>();
      markdown.put("content", content);
      payload.put("markdown", markdown);
    } else {
      Map<String, Object> text = new HashMap<>();
      text.put("content", content);
      payload.put("text", text);
    }
    return payload;
  }

  /**
   * 解析 Webhook URL，优先级：params.wechatWorkKey &gt; receiver(http) &gt; receiver(key) &gt; 默认配置。
   *
   * @param request 消息请求
   * @return 解析到的 URL，无则返回 null
   */
  String resolveUrl(MessageRequest request) {
    Map<String, Object> params = request.getParams();
    if (params != null) {
      Object explicit = params.get("wechatWorkKey");
      if (explicit instanceof String s && StringUtils.hasText(s)) {
        return WEBHOOK_PREFIX + s.trim();
      }
    }
    String receiver = request.getReceiver();
    if (StringUtils.hasText(receiver)) {
      String r = receiver.trim();
      if (r.toLowerCase().startsWith("http")) {
        return r;
      }
      return WEBHOOK_PREFIX + r;
    }
    String defaultKey = channelProperties.getChannel().getWechatWork().getDefaultKey();
    if (StringUtils.hasText(defaultKey)) {
      return WEBHOOK_PREFIX + defaultKey.trim();
    }
    return null;
  }
}
