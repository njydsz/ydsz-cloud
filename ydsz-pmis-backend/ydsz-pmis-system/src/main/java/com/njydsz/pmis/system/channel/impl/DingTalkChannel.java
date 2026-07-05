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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 钉钉群机器人通道
 *
 * <p>通过钉钉自定义机器人 Webhook 推送通知，支持 text / markdown 两种消息类型。
 * 启用加签安全模式时，需配置 {@code pmis.channel.dingtalk.secret}，
 * 通道会自动计算 HMAC-SHA256 签名并附加到请求 URL。
 *
 * <p>接收人（receiver）解析优先级：
 * <ol>
 *   <li>params.dingtalkToken（显式指定 access_token，最高优先级）</li>
 *   <li>receiver 以 http 开头时视为完整 Webhook URL</li>
 *   <li>receiver 视为 access_token，拼接默认 URL 前缀</li>
 *   <li>系统配置 pmis.channel.dingtalk.default-token（兜底）</li>
 * </ol>
 *
 * <p>消息类型：params.msgType = "markdown" 时发送 markdown，否则发送 text。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
@Component
public class DingTalkChannel implements MessageChannel {

    /** 钉钉机器人 Webhook URL 前缀 */
    private static final String WEBHOOK_PREFIX =
            "https://oapi.dingtalk.com/robot/send?access_token=";

    /** 系统配置的默认 access_token（兜底） */
    @Value("${pmis.channel.dingtalk.default-token:}")
    private String defaultToken;

    /** 加签密钥（可选，配置后启用加签安全模式） */
    @Value("${pmis.channel.dingtalk.secret:}")
    private String secret;

    /** 连接超时（毫秒） */
    @Value("${pmis.channel.dingtalk.connect-timeout:5000}")
    private int connectTimeout;

    /** 读取超时（毫秒） */
    @Value("${pmis.channel.dingtalk.read-timeout:10000}")
    private int readTimeout;

    /** HTTP 客户端（容器中无 RestTemplate Bean 时自建） */
    private final RestTemplate restTemplate;

    /**
     * 构造方法，RestTemplate 可选注入。
     *
     * @param restTemplate 容器中已配置的 RestTemplate（可选）
     */
    public DingTalkChannel(@Autowired(required = false) RestTemplate restTemplate) {
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
     * @return 通道类型字符串 DING_TALK
     */
    @Override
    public String channelType() {
        return "DING_TALK";
    }

    /**
     * 发送钉钉消息：构造 text / markdown 请求体并 POST 到 Webhook URL，
     * 根据响应 errcode 判断成功 / 失败。
     *
     * @param request 消息请求
     * @return 发送结果（含追踪 ID 与失败时的错误信息）
     */
    @Override
    public MessageResult send(MessageRequest request) {
        String webhookUrl = resolveUrl(request);
        if (!StringUtils.hasText(webhookUrl)) {
            log.warn("[DING_TALK] 未配置 access_token，跳过发送: receiver={}", request.getReceiver());
            return MessageResult.fail("DING_TALK", "钉钉 access_token 未配置");
        }

        // 加签模式：附加 timestamp & sign
        if (StringUtils.hasText(secret)) {
            webhookUrl = appendSign(webhookUrl);
        }

        Map<String, Object> payload = buildPayload(request);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(payload), headers);

            ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, entity, String.class);
            String traceId = "DING_TALK-" + SnowflakeIdGenerator.nextTraceId();

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // 钉钉返回 errcode=0 表示成功
                Map<String, Object> body = JSON.parseObject(response.getBody());
                int errcode = ((Number) body.getOrDefault("errcode", -1)).intValue();
                if (errcode == 0) {
                    log.info("[DING_TALK] 发送成功");
                    return MessageResult.ok("DING_TALK", traceId);
                }
                String errmsg = (String) body.getOrDefault("errmsg", "unknown");
                log.error("[DING_TALK] 发送失败: errcode={} errmsg={}", errcode, errmsg);
                return MessageResult.fail("DING_TALK", "errcode=" + errcode + ", errmsg=" + errmsg);
            }
            log.error("[DING_TALK] 发送失败: status={}", response.getStatusCode());
            return MessageResult.fail("DING_TALK", "HTTP " + response.getStatusCode());
        } catch (Exception e) {
            log.error("[DING_TALK] 发送异常: reason={}", e.getMessage(), e);
            return MessageResult.fail("DING_TALK", e.getClass().getSimpleName() + ": " + e.getMessage());
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
    private Map<String, Object> buildPayload(MessageRequest request) {
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
    private String resolveUrl(MessageRequest request) {
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
        if (StringUtils.hasText(defaultToken)) {
            return WEBHOOK_PREFIX + defaultToken.trim();
        }
        return null;
    }

    /**
     * 计算加签并附加到 URL。
     *
     * <p>签名算法：HMAC-SHA256(timestamp + "\n" + secret, secret) → Base64 → URLEncode
     *
     * @param url 原始 Webhook URL
     * @return 附加 timestamp & sign 后的 URL
     */
    private String appendSign(String url) {
        try {
            long timestamp = System.currentTimeMillis();
            String stringToSign = timestamp + "\n" + secret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            String sign = URLEncoder.encode(Base64.getEncoder().encodeToString(signData), StandardCharsets.UTF_8);
            return url + "&timestamp=" + timestamp + "&sign=" + sign;
        } catch (Exception e) {
            log.warn("[DING_TALK] 加签失败，使用原始 URL: {}", e.getMessage());
            return url;
        }
    }
}
