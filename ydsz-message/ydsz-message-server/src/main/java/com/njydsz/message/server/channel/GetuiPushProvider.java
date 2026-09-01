package com.njydsz.message.server.channel.push;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.util.collection.MapUtils;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.message.domain.vo.MsgTemplateVO;
import com.njydsz.message.server.config.MessageProperties;

/**
 * 个推（GeTui）V2 推送服务商实现。
 *
 * <p>通过个推 REST API（{@code /v2/{appId}/push/single/cid}）发送单推， 鉴权使用 {@link GetuiPushSigner} 计算
 * SHA-256 签名，token 内存缓存（默认 23h）。
 *
 * <p>仅当 {@code ydsz.message.push.provider=getui} 时装配；凭证缺失时返回 fail （由 {@link
 * com.njydsz.message.server.channel.impl.PushChannel} 自动降级到 Mock）。
 *
 * <p>目标设备标识来源：优先 {@code channelMeta.deviceToken}，回退 {@code receiver}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ydsz.message.push", name = "provider", havingValue = "getui")
public class GetuiPushProvider implements PushProvider {
  /** Token 有效小时数 */
  private static final long TOKEN_VALID_HOURS = 23;

  /** 每小时毫秒数 */
  private static final long MILLIS_PER_HOUR = 3600 * 1000;


  private final MessageProperties.GetuiPushConfig config;
  private final RestTemplate restTemplate;

  /** 分布式 ID 生成器 */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  /** 鉴权 token 缓存（个推 token 默认 24h，提前 1h 失效） */
  private volatile String cachedToken;

  private volatile long tokenExpireAt;

  /**
   * 生产构造：从 {@link MessageProperties} 读取个推配置并构建 RestTemplate。
   * 
   *
   * @param messageProperties 参数说明
   * @param snowflakeIdGenerator 参数说明   */
  public GetuiPushProvider(
      MessageProperties messageProperties, SnowflakeIdGenerator snowflakeIdGenerator) {
    this.config = messageProperties.getPush().getGetui();
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(config.getConnectTimeout());
    factory.setReadTimeout(config.getReadTimeout());
    this.restTemplate = new RestTemplate(factory);
    this.snowflakeIdGenerator = snowflakeIdGenerator;
  }

  /**
   * 测试构造：注入自定义 config 与 RestTemplate（便于 mock）。
   *
   * <p>ID 生成器使用默认实例（workerId/datacenterId 自动计算）， 保证 {@code send()} 中生成 request_id 不依赖外部配置。
   *
   * @param config 个推配置
   * @param restTemplate RestTemplate（测试可 mock）
   */
  GetuiPushProvider(MessageProperties.GetuiPushConfig config, RestTemplate restTemplate) {
    this.config = config;
    this.restTemplate = restTemplate;
    this.snowflakeIdGenerator = new SnowflakeIdGenerator();
  }

  @Override
  public String providerType() {
    return "getui";
  }

  @Override
  public MessageResult send(MessageRequest request, MsgTemplateVO template) {
    String cid = extractClientId(request);
    if (!StringUtils.hasText(cid)) {
      return MessageResult.fail("PUSH", null, "推送目标 clientId/deviceToken 不能为空", "推送目标 clientId/deviceToken 不能为空", null);
    }
    if (!StringUtils.hasText(config.getAppId())
        || !StringUtils.hasText(config.getAppKey())
        || !StringUtils.hasText(config.getMasterSecret())) {
      return MessageResult.fail("PUSH", null, "个推凭证未配置", "个推凭证未配置", null);
    }
    try {
      String token = getToken();
      String url = config.getBaseUrl() + "/v2/" + config.getAppId() + "/push/single/cid";
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.set("token", token);
      Map<String, Object> body = new HashMap<>();
      body.put("request_id", String.valueOf(snowflakeIdGenerator.nextId()));
      body.put("audience", Map.of("cid", new String[] {cid}));
      String title = StringUtils.hasText(request.getSubject()) ? request.getSubject() : "通知";
      body.put(
          "push_message",
          Map.of(
              "notification",
              Map.of(
                  "title",
                  title,
                  "body",
                  request.getContent() == null ? "" : request.getContent())));
      HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
      ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);
      Map<String, Object> json = YdszJson.parseMap(resp.getBody());
      String code = MapUtils.getString(json, "code");
      if ("10000".equals(code)) {
        String taskId = MapUtils.getString(json, "data");
        log.info("[GetuiPush] 推送成功: cid={} taskId={}", cid, taskId);
        return MessageResult.ok("PUSH", "GETUI-" + taskId);
      }
      log.warn(
          "[GetuiPush] 推送失败: cid={} code={} msg={}", cid, code, MapUtils.getString(json, "msg"));
      return MessageResult.fail(
          "PUSH", null, code + ": " + MapUtils.getString(json, "msg"),
          code + ": " + MapUtils.getString(json, "msg"), null);
    } catch (Exception e) {
      log.error("[GetuiPush] 推送异常: cid={} err={}", cid, e.getMessage(), e);
      return MessageResult.fail(
          "PUSH", null, e.getClass().getSimpleName() + ": " + e.getMessage(),
          e.getClass().getSimpleName() + ": " + e.getMessage(), null);
    }
  }

  /**
   * 提取设备标识：优先 channelMeta.deviceToken，回退 receiver。
   *
   * @param request 消息请求
   * @return 设备标识
   */
  private String extractClientId(MessageRequest request) {
    Map<String, String> meta = request.getChannelMeta();
    if (meta != null && StringUtils.hasText(meta.get("deviceToken"))) {
      return meta.get("deviceToken");
    }
    return request.getReceiver();
  }

  /**
   * 获取个推鉴权 token（双重检查锁 + 内存缓存）。
   *
   * @return 鉴权 token
   */
  private String getToken() {
    if (cachedToken != null && System.currentTimeMillis() < tokenExpireAt) {
      return cachedToken;
    }
    synchronized (this) {
      if (cachedToken != null && System.currentTimeMillis() < tokenExpireAt) {
        return cachedToken;
      }
      String timestamp = String.valueOf(System.currentTimeMillis());
      String sign = GetuiPushSigner.sign(config.getAppKey(), timestamp, config.getMasterSecret());
      String url = config.getBaseUrl() + "/v2/" + config.getAppId() + "/auth";
      Map<String, Object> body = new HashMap<>();
      body.put("sign", sign);
      body.put("timestamp", timestamp);
      body.put("appkey", config.getAppKey());
      ResponseEntity<String> resp = restTemplate.postForEntity(url, body, String.class);
      Map<String, Object> json = YdszJson.parseMap(resp.getBody());
      if ("10000".equals(MapUtils.getString(json, "code"))) {
        Map<String, Object> data = MapUtils.safeCastMap(json.get("data"));
        cachedToken = MapUtils.getString(data, "token");
        tokenExpireAt = System.currentTimeMillis() + TOKEN_VALID_HOURS * MILLIS_PER_HOUR;
        return cachedToken;
      }
      throw new IllegalStateException("个推鉴权失败: " + MapUtils.getString(json, "msg"));
    }
  }
}
