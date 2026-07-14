package com.njydsz.pmis.message.server.channel.impl;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.common.util.CryptoSignUtil;
import com.njydsz.pmis.common.util.SnowflakeIdGenerator;
import com.njydsz.pmis.message.server.channel.MessageChannel;
import com.njydsz.pmis.message.server.config.ChannelProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 钉钉群机器人通道。
 *
 * <p>通过钉钉自定义机器人 Webhook 推送通知，支持 text / markdown 两种消息类型。
 * 启用加签安全模式时，需配置 {@code pmis.channel.dingtalk.secret}，通道会自动计算
 * HMAC-SHA256 签名并附加到请求 URL。
 *
 * <p>URL 解析优先级：
 * <ol>
 *   <li>{@code params.dingtalkToken}（显式 access_token，最高优先级）</li>
 *   <li>{@code receiver} 以 http 开头时视为完整 Webhook URL</li>
 *   <li>{@code receiver} 视为 access_token，拼接默认 URL 前缀</li>
 *   <li>{@code pmis.channel.dingtalk.default-token}（兜底）</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DingTalkChannel implements MessageChannel {

    /** 通道类型 */
    private static final String CHANNEL_TYPE = "DINGTALK";

    /** 钉钉机器人 Webhook URL 前缀 */
    private static final String WEBHOOK_PREFIX =
            "https://oapi.dingtalk.com/robot/send?access_token=";

    /** 通道配置（提供 default-token / secret / 超时） */
    private final ChannelProperties channelProperties;

    /** HTTP 客户端，在 {@link #init()} 中按配置超时构建 */
    RestClient restClient;

    /**
     * 注入配置后按 {@code pmis.channel.dingtalk.connect-timeout / read-timeout} 构建 RestClient。
     */
    @PostConstruct
    public void init() {
        ChannelProperties.DingTalkConfig cfg = channelProperties.getChannel().getDingtalk();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(cfg.getConnectTimeout());
        factory.setReadTimeout(cfg.getReadTimeout());
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * 通道类型。
     *
     * @return DINGTALK
     */
    @Override
    public String channelType() {
        return CHANNEL_TYPE;
    }

    /**
     * 发送钉钉消息：构造 text / markdown 请求体并 POST 到 Webhook URL，
     * 根据响应 errcode 判断成功 / 失败。
     *
     * @param request 消息请求
     * @return 发送结果
     */
    @Override
    public MessageResult send(MessageRequest request) {
        String webhookUrl = resolveUrl(request);
        if (!StringUtils.hasText(webhookUrl)) {
            log.warn("[DINGTALK] 未配置 access_token，跳过发送: receiver={}", request.getReceiver());
            return MessageResult.fail(CHANNEL_TYPE, "钉钉 access_token 未配置");
        }

        String secret = channelProperties.getChannel().getDingtalk().getSecret();
        if (StringUtils.hasText(secret)) {
            String signedUrl = appendSign(webhookUrl, secret);
            if (signedUrl == null) {
                return MessageResult.fail(CHANNEL_TYPE, "钉钉加签失败,请检查 secret 配置");
            }
            webhookUrl = signedUrl;
        }

        Map<String, Object> payload = buildPayload(request);

        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(JSON.toJSONString(payload))
                    .retrieve()
                    .toEntity(String.class);
            String traceId = CHANNEL_TYPE + "-" + SnowflakeIdGenerator.nextTraceId();

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = JSON.parseObject(response.getBody());
                int errcode = ((Number) body.getOrDefault("errcode", -1)).intValue();
                if (errcode == 0) {
                    log.info("[DINGTALK] 发送成功");
                    return MessageResult.ok(CHANNEL_TYPE, traceId);
                }
                String errmsg = (String) body.getOrDefault("errmsg", "unknown");
                log.error("[DINGTALK] 发送失败: errcode={} errmsg={}", errcode, errmsg);
                return MessageResult.fail(CHANNEL_TYPE, "errcode=" + errcode + ", errmsg=" + errmsg);
            }
            log.error("[DINGTALK] 发送失败: status={}", response.getStatusCode());
            return MessageResult.fail(CHANNEL_TYPE, "HTTP " + response.getStatusCode());
        } catch (Exception e) {
            log.error("[DINGTALK] 发送异常: reason={}", e.getMessage(), e);
            return MessageResult.fail(CHANNEL_TYPE, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 构造钉钉消息请求体。
     * <ul>
     *   <li>msgType=markdown：{@code {"msgtype":"markdown","markdown":{"title":"标题","text":"内容"}}}</li>
     *   <li>默认 text：{@code {"msgtype":"text","text":{"content":"内容"}}}</li>
     * </ul>
     *
     * @param request 消息请求
     * @return 请求体 Map
     */
    Map<String, Object> buildPayload(MessageRequest request) {
        String content = request.getContent() == null ? "" : request.getContent();
        String subject = request.getSubject() == null ? "PMIS 通知" : request.getSubject();
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
            markdown.put("title", subject);
            markdown.put("text", content);
            payload.put("markdown", markdown);
        } else {
            Map<String, Object> text = new HashMap<>();
            text.put("content", content);
            payload.put("text", text);
        }
        return payload;
    }

    /**
     * 解析 Webhook URL，优先级：params.dingtalkToken &gt; receiver(http) &gt; receiver(token) &gt; 默认配置。
     *
     * @param request 消息请求
     * @return 解析到的 URL，无则返回 null
     */
    String resolveUrl(MessageRequest request) {
        Map<String, Object> params = request.getParams();
        if (params != null) {
            Object explicit = params.get("dingtalkToken");
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
        String defaultToken = channelProperties.getChannel().getDingtalk().getDefaultToken();
        if (StringUtils.hasText(defaultToken)) {
            return WEBHOOK_PREFIX + defaultToken.trim();
        }
        return null;
    }

    /**
     * 计算加签并附加到 URL。
     *
     * <p>签名算法：HMAC-SHA256(timestamp + "\n" + secret, secret) → Base64 → URLEncode。
     * timestamp 为毫秒。
     *
     * <p>P1-1: 委托到 CryptoSignUtil 统一实现。
     *
     * @param url    原始 Webhook URL
     * @param secret 加签密钥
     * @return 附加 timestamp & sign 后的 URL
     */
    String appendSign(String url, String secret) {
        try {
            long timestamp = System.currentTimeMillis();
            String stringToSign = timestamp + "\n" + secret;
            String sign = URLEncoder.encode(
                    CryptoSignUtil.hmacSha256Base64(stringToSign, secret),
                    StandardCharsets.UTF_8);
            return url + "&timestamp=" + timestamp + "&sign=" + sign;
        } catch (Exception e) {
            log.error("[DINGTALK] 加签失败,放弃发送: {}", e.getMessage(), e);
            return null;
        }
    }
}
