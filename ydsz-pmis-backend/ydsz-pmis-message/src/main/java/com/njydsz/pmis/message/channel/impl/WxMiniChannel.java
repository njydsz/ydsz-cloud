package com.njydsz.pmis.message.channel.impl;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.common.util.SnowflakeIdGenerator;
import com.njydsz.pmis.message.channel.MessageChannel;
import com.njydsz.pmis.message.config.MessageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 微信小程序订阅消息通道实现。
 *
 * <p>实现 {@link MessageChannel} SPI，通过微信小程序订阅消息 API 下发通知。
 * 需要用户在小程序端主动订阅消息模板后才能发送，每次发送消耗一次订阅配额。
 *
 * <p>降级策略：未配置 AppID/AppSecret 或 provider=mock 时降级为日志输出。
 *
 * <p>API 流程：
 * <ol>
 *   <li>获取 access_token（缓存到 Redis，7200s 有效期）</li>
 *   <li>调用 subscribeMessage/send 下发订阅消息</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "pmis.message.wx-mini", name = "provider", havingValue = "wechat", matchIfMissing = false)
public class WxMiniChannel implements MessageChannel {

    private static final String CHANNEL_TYPE = "WX_MINI";

    /** 微信 access_token Redis 缓存 key */
    private static final String ACCESS_TOKEN_CACHE_KEY = "pmis:wx:mini:access_token";

    private final MessageProperties messageProperties;
    private final RestTemplate restTemplate;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    public WxMiniChannel(MessageProperties messageProperties,
                         org.springframework.data.redis.core.StringRedisTemplate redisTemplate) {
        this.messageProperties = messageProperties;
        this.redisTemplate = redisTemplate;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String channelType() {
        return CHANNEL_TYPE;
    }

    @Override
    public MessageResult send(MessageRequest request) {
        if (request.getReceiver() == null || request.getReceiver().isBlank()) {
            return MessageResult.fail(CHANNEL_TYPE, "微信小程序接收人(OpenID)不能为空");
        }

        MessageProperties.WxMiniConfig config = messageProperties.getWxMini();
        if (config == null || !StringUtils.hasText(config.getAppId())
                || !StringUtils.hasText(config.getAppSecret())) {
            log.warn("[WxMiniChannel] 未配置 AppID/AppSecret,降级为日志输出: receiver={}",
                    request.getReceiver());
            return mockSend(request);
        }

        try {
            String accessToken = getAccessToken(config);
            if (accessToken == null) {
                return MessageResult.fail(CHANNEL_TYPE, "获取微信 access_token 失败");
            }

            String url = config.getBaseUrl()
                    + "/cgi-bin/message/subscribe/send?access_token=" + accessToken;

            Map<String, Object> body = Map.of(
                    "touser", request.getReceiver(),
                    "template_id", request.getTemplateCode() != null ? request.getTemplateCode() : "",
                    "page", "pages/index/index",
                    "data", buildTemplateData(request),
                    "miniprogram_state", "formal"
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> resp = restTemplate.postForEntity(url, entity, Map.class);
            Map<?, ?> resultBody = resp.getBody();

            if (resultBody != null && Integer.valueOf(0).equals(resultBody.get("errcode"))) {
                String traceId = "WX_MINI-" + SnowflakeIdGenerator.nextTraceId();
                log.info("[WxMiniChannel] 发送成功: receiver={} template={}",
                        request.getReceiver(), request.getTemplateCode());
                return MessageResult.ok(CHANNEL_TYPE, traceId);
            } else {
                String errMsg = resultBody != null ? String.valueOf(resultBody.get("errmsg")) : "未知错误";
                log.error("[WxMiniChannel] 发送失败: receiver={} errcode={} errmsg={}",
                        request.getReceiver(),
                        resultBody != null ? resultBody.get("errcode") : "N/A", errMsg);
                return MessageResult.fail(CHANNEL_TYPE, "微信小程序发送失败: " + errMsg);
            }
        } catch (Exception e) {
            log.error("[WxMiniChannel] 发送异常: receiver={} err={}",
                    request.getReceiver(), e.getMessage(), e);
            return MessageResult.fail(CHANNEL_TYPE, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 获取微信 access_token（Redis 缓存，7200s 有效期）。
     */
    private String getAccessToken(MessageProperties.WxMiniConfig config) {
        try {
            String cached = redisTemplate.opsForValue().get(ACCESS_TOKEN_CACHE_KEY);
            if (StringUtils.hasText(cached)) {
                return cached;
            }
            String url = config.getBaseUrl()
                    + "/cgi-bin/token?grant_type=client_credential"
                    + "&appid=" + config.getAppId()
                    + "&secret=" + config.getAppSecret();
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
            Map<?, ?> body = resp.getBody();
            if (body != null && body.containsKey("access_token")) {
                String token = (String) body.get("access_token");
                int expiresIn = body.containsKey("expires_in") ? (Integer) body.get("expires_in") : 7200;
                redisTemplate.opsForValue().set(ACCESS_TOKEN_CACHE_KEY, token,
                        java.time.Duration.ofSeconds(expiresIn - 300));
                return token;
            }
            log.error("[WxMiniChannel] 获取 access_token 失败: {}",
                    body != null ? body.get("errmsg") : "null response");
            return null;
        } catch (Exception e) {
            log.error("[WxMiniChannel] 获取 access_token 异常: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 构造模板消息 data 字段。
     * 微信小程序订阅消息的 data 格式为 { "key": { "value": "xxx" } }
     */
    private Map<String, Object> buildTemplateData(MessageRequest request) {
        if (request.getParams() == null) {
            return Map.of();
        }
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        for (Map.Entry<String, Object> entry : request.getParams().entrySet()) {
            result.put(entry.getKey(), Map.of("value",
                    entry.getValue() == null ? "" : String.valueOf(entry.getValue())));
        }
        return result;
    }

    /**
     * Mock 发送（开发环境降级）。
     */
    private MessageResult mockSend(MessageRequest request) {
        String traceId = "WX_MINI-MOCK-" + SnowflakeIdGenerator.nextTraceId();
        log.info("[WxMiniChannel][MOCK] 模拟发送: receiver={} template={} content={}",
                request.getReceiver(), request.getTemplateCode(), request.getContent());
        return MessageResult.ok(CHANNEL_TYPE, traceId);
    }
}
