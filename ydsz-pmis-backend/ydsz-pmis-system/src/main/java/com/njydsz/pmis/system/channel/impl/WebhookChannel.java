package com.njydsz.pmis.system.channel.impl;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.system.channel.MessageChannel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Webhook 通道实现
 *
 * <p>通过 HTTP POST 将通知推送到用户配置的 Webhook URL，
 * 可对接企业微信 / 钉钉 / 飞书群机器人等场景。
 *
 * <p>URL 解析优先级：
 * <ol>
 *   <li>消息参数 params.webhookUrl（显式指定，最高优先级）</li>
 *   <li>request.receiver（以 http 开头时视为 Webhook URL）</li>
 *   <li>系统配置 pmis.webhook.default-url（兜底默认地址）</li>
 * </ol>
 *
 * <p>请求体格式：{@code {"text": "消息内容", "title": "消息标题"}}，
 * 兼容常见群机器人协议。发送失败被捕获并转为失败结果，不影响主流程。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class WebhookChannel implements MessageChannel {

    /** 系统配置的默认 Webhook URL（兜底） */
    @Value("${pmis.webhook.default-url:}")
    private String defaultUrl;

    /** 连接超时（毫秒） */
    @Value("${pmis.webhook.connect-timeout:5000}")
    private int connectTimeout;

    /** 读取超时（毫秒） */
    @Value("${pmis.webhook.read-timeout:10000}")
    private int readTimeout;

    /** HTTP 客户端（容器中无 RestTemplate Bean 时自建） */
    private final RestTemplate restTemplate;

    /**
     * 构造方法，RestTemplate 可选注入。
     *
     * <p>容器中存在已配置的 RestTemplate Bean 时复用，否则自建默认实例，
     * 超时在 {@link #initTimeout()} 中按配置设置。
     *
     * @param restTemplate 容器中已配置的 RestTemplate（可选）
     */
    public WebhookChannel(@Autowired(required = false) RestTemplate restTemplate) {
        this.restTemplate = restTemplate != null ? restTemplate : new RestTemplate();
    }

    /**
     * 注入配置后为自建 RestTemplate 设置连接 / 读取超时。
     * 若使用外部注入的 RestTemplate（非默认工厂）则不覆盖其配置。
     */
    @PostConstruct
    public void initTimeout() {
        if (restTemplate.getRequestFactory() instanceof SimpleClientHttpRequestFactory factory) {
            factory.setConnectTimeout(connectTimeout);
            factory.setReadTimeout(readTimeout);
        }
    }

    /**
     * 通道类型
     *
     * @return 通道类型字符串 WEBHOOK
     */
    @Override
    public String channelType() {
        return "WEBHOOK";
    }

    /**
     * 发送 Webhook 通知：构造 JSON 请求体并 POST 到目标 URL，
     * 根据 HTTP 响应状态码判断成功 / 失败。
     *
     * @param request 消息请求
     * @return 发送结果（含追踪 ID 与失败时的状态码 / 错误信息）
     */
    @Override
    public MessageResult send(MessageRequest request) {
        String webhookUrl = resolveUrl(request);
        if (!StringUtils.hasText(webhookUrl)) {
            log.warn("[WEBHOOK] 未配置 Webhook URL，跳过发送: receiver={}", request.getReceiver());
            return MessageResult.fail("WEBHOOK", "Webhook URL 未配置");
        }

        // 请求体：{"text": "消息内容", "title": "消息标题"}
        Map<String, Object> payload = new HashMap<>();
        payload.put("text", request.getContent() == null ? "" : request.getContent());
        payload.put("title", request.getSubject() == null ? "PMIS 通知" : request.getSubject());

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(payload), headers);

            ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, entity, String.class);
            int statusCode = response.getStatusCode().value();
            if (response.getStatusCode().is2xxSuccessful()) {
                String traceId = "WEBHOOK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
                log.info("[WEBHOOK] 发送成功: url={} status={}", webhookUrl, statusCode);
                return MessageResult.ok("WEBHOOK", traceId);
            }
            log.error("[WEBHOOK] 发送失败: url={} status={} body={}",
                    webhookUrl, statusCode, response.getBody());
            return MessageResult.fail("WEBHOOK", "HTTP " + statusCode);
        } catch (Exception e) {
            log.error("[WEBHOOK] 发送异常: url={} reason={}", webhookUrl, e.getMessage(), e);
            return MessageResult.fail("WEBHOOK", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 解析 Webhook URL，优先级：params.webhookUrl &gt; receiver(http 开头) &gt; 默认配置。
     *
     * @param request 消息请求
     * @return 解析到的 URL，无则返回 null
     */
    private String resolveUrl(MessageRequest request) {
        Map<String, Object> params = request.getParams();
        if (params != null) {
            Object explicit = params.get("webhookUrl");
            if (explicit instanceof String && StringUtils.hasText((String) explicit)) {
                return ((String) explicit).trim();
            }
        }
        String receiver = request.getReceiver();
        if (StringUtils.hasText(receiver) && receiver.trim().toLowerCase().startsWith("http")) {
            return receiver.trim();
        }
        if (StringUtils.hasText(defaultUrl)) {
            return defaultUrl.trim();
        }
        return null;
    }
}
