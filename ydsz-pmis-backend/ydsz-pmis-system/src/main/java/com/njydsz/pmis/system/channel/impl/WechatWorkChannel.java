package com.njydsz.pmis.system.channel.impl;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.common.util.SnowflakeIdGenerator;
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

/**
 * 企业微信群机器人通道
 *
 * <p>通过企业微信群机器人 Webhook 推送通知，支持 text / markdown 两种消息类型。
 * 与钉钉不同，企业微信群机器人无需加签，仅需 key 即可发送。
 *
 * <p>接收人（receiver）解析优先级：
 * <ol>
 *   <li>params.wechatWorkKey（显式指定 key，最高优先级）</li>
 *   <li>receiver 以 http 开头时视为完整 Webhook URL</li>
 *   <li>receiver 视为 key，拼接默认 URL 前缀</li>
 *   <li>系统配置 pmis.channel.wechat-work.default-key（兜底）</li>
 * </ol>
 *
 * <p>消息类型：params.msgType = "markdown" 时发送 markdown，否则发送 text。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
@Component
public class WechatWorkChannel implements MessageChannel {

    /** 企业微信机器人 Webhook URL 前缀 */
    private static final String WEBHOOK_PREFIX =
            "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=";

    /** 系统配置的默认 key（兜底） */
    @Value("${pmis.channel.wechat-work.default-key:}")
    private String defaultKey;

    /** 连接超时（毫秒） */
    @Value("${pmis.channel.wechat-work.connect-timeout:5000}")
    private int connectTimeout;

    /** 读取超时（毫秒） */
    @Value("${pmis.channel.wechat-work.read-timeout:10000}")
    private int readTimeout;

    /** HTTP 客户端（容器中无 RestTemplate Bean 时自建） */
    private final RestTemplate restTemplate;

    /**
     * 构造方法，RestTemplate 可选注入。
     *
     * @param restTemplate 容器中已配置的 RestTemplate（可选）
     */
    public WechatWorkChannel(@Autowired(required = false) RestTemplate restTemplate) {
        this.restTemplate = restTemplate != null ? restTemplate : new RestTemplate();
    }

    /**
     * 注入配置后为自建 RestTemplate 设置连接 / 读取超时。
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
     * @return 通道类型字符串 WECHAT_WORK
     */
    @Override
    public String channelType() {
        return "WECHAT_WORK";
    }

    /**
     * 发送企业微信消息：构造 text / markdown 请求体并 POST 到 Webhook URL，
     * 根据响应 errcode 判断成功 / 失败。
     *
     * @param request 消息请求
     * @return 发送结果（含追踪 ID 与失败时的错误信息）
     */
    @Override
    public MessageResult send(MessageRequest request) {
        String webhookUrl = resolveUrl(request);
        if (!StringUtils.hasText(webhookUrl)) {
            log.warn("[WECHAT_WORK] 未配置 key，跳过发送: receiver={}", request.getReceiver());
            return MessageResult.fail("WECHAT_WORK", "企业微信 key 未配置");
        }

        Map<String, Object> payload = buildPayload(request);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(payload), headers);

            ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, entity, String.class);
            String traceId = "WECHAT_WORK-" + SnowflakeIdGenerator.nextTraceId();

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // 企业微信返回 errcode=0 表示成功
                Map<String, Object> body = JSON.parseObject(response.getBody());
                int errcode = ((Number) body.getOrDefault("errcode", -1)).intValue();
                if (errcode == 0) {
                    log.info("[WECHAT_WORK] 发送成功");
                    return MessageResult.ok("WECHAT_WORK", traceId);
                }
                String errmsg = (String) body.getOrDefault("errmsg", "unknown");
                log.error("[WECHAT_WORK] 发送失败: errcode={} errmsg={}", errcode, errmsg);
                return MessageResult.fail("WECHAT_WORK", "errcode=" + errcode + ", errmsg=" + errmsg);
            }
            log.error("[WECHAT_WORK] 发送失败: status={}", response.getStatusCode());
            return MessageResult.fail("WECHAT_WORK", "HTTP " + response.getStatusCode());
        } catch (Exception e) {
            log.error("[WECHAT_WORK] 发送异常: reason={}", e.getMessage(), e);
            return MessageResult.fail("WECHAT_WORK", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 构造企业微信消息请求体。
     * <ul>
     *   <li>msgType=markdown：{@code {"msgtype":"markdown","markdown":{"content":"内容"}}}</li>
     *   <li>默认 text：{@code {"msgtype":"text","text":{"content":"内容"}}}</li>
     * </ul>
     *
     * @param request 消息请求
     * @return 请求体 Map
     */
    private Map<String, Object> buildPayload(MessageRequest request) {
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
    private String resolveUrl(MessageRequest request) {
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
        if (StringUtils.hasText(defaultKey)) {
            return WEBHOOK_PREFIX + defaultKey.trim();
        }
        return null;
    }
}
