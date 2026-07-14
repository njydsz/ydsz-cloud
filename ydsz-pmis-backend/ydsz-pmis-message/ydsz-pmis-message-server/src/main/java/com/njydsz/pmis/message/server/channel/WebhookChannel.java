package com.njydsz.pmis.message.server.channel.impl;

import java.util.HashMap;
import java.util.Map;

import com.njydsz.pmis.common.util.json.JsonUtils;

import jakarta.annotation.PostConstruct;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.common.util.SnowflakeIdGenerator;
import com.njydsz.pmis.message.server.channel.MessageChannel;
import com.njydsz.pmis.message.server.config.ChannelProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Webhook 通道实现。
 *
 * <p>通过 HTTP POST 将通知推送到用户配置的 Webhook URL，请求体格式
 * {@code {"text":"消息内容","title":"消息标题"}}，兼容常见群机器人协议。
 *
 * <p>URL 解析优先级：
 * <ol>
 *   <li>消息参数 {@code params.webhookUrl}（显式指定，最高优先级）</li>
 *   <li>{@code request.receiver}（以 http 开头时视为 Webhook URL）</li>
 *   <li>系统配置 {@code pmis.webhook.default-url}（兜底默认地址）</li>
 * </ol>
 *
 * <p>超时取 {@code pmis.webhook.connect-timeout / read-timeout}。发送失败被捕获并转为失败结果。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookChannel implements MessageChannel {

    /** 通道类型 */
    private static final String CHANNEL_TYPE = "WEBHOOK";

    /** 通道配置（提供 default-url / 超时） */
    private final ChannelProperties channelProperties;

    /** HTTP 客户端，在 {@link #init()} 中按配置超时构建 */
    RestClient restClient;

    /**
     * 注入配置后按 {@code pmis.webhook.connect-timeout / read-timeout} 构建 RestClient。
     */
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
     * 发送 Webhook 通知：构造 JSON 请求体并 POST 到目标 URL，根据 HTTP 状态码判断成功 / 失败。
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
        payload.put("title", request.getSubject() == null ? "PMIS 通知" : request.getSubject());

        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(JsonUtils.toJson(payload))
                    .retrieve()
                    .toEntity(String.class);
            int statusCode = response.getStatusCode().value();
            if (response.getStatusCode().is2xxSuccessful()) {
                String traceId = CHANNEL_TYPE + "-" + SnowflakeIdGenerator.nextTraceId();
                log.info("[WEBHOOK] 发送成功: url={} status={}", webhookUrl, statusCode);
                return MessageResult.ok(CHANNEL_TYPE, traceId);
            }
            log.error("[WEBHOOK] 发送失败: url={} status={} body={}",
                    webhookUrl, statusCode, response.getBody());
            return MessageResult.fail(CHANNEL_TYPE, "HTTP " + statusCode);
        } catch (Exception e) {
            log.error("[WEBHOOK] 发送异常: url={} reason={}", webhookUrl, e.getMessage(), e);
            return MessageResult.fail(CHANNEL_TYPE, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
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
        if (StringUtils.hasText(receiver)
                && receiver.trim().toLowerCase().startsWith("http")) {
            return receiver.trim();
        }
        String defaultUrl = channelProperties.getWebhook().getDefaultUrl();
        if (StringUtils.hasText(defaultUrl)) {
            return defaultUrl.trim();
        }
        return null;
    }
}
