package com.njydsz.pmis.message.server.channel.impl;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.common.util.SnowflakeIdGenerator;
import com.njydsz.pmis.message.server.channel.MessageChannel;
import com.njydsz.pmis.message.server.config.ChannelProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 飞书群机器人通道�? *
 * <p>通过飞书自定义机器人 Webhook 推送通知，支�?text / post 两种消息类型�? * 启用加签安全模式时，需配置 {@code pmis.channel.feishu.secret}，通道会自动计�? * HMAC-SHA256 签名并将 {@code timestamp / sign} 写入请求体�? *
 * <p>URL 解析优先级：
 * <ol>
 *   <li>{@code params.feishuHook}（显�?hook，可为完�?URL �?hook ID，最高优先级�?/li>
 *   <li>{@code receiver} �?http 开头时视为完整 Webhook URL，否则视�?hook ID</li>
 *   <li>{@code pmis.channel.feishu.default-hook}（兜底，可为完整 URL �?hook ID�?/li>
 * </ol>
 *
 * <p>飞书加签：timestamp 为秒级，签名字符�?{@code timestamp + "\n" + secret}�? * HMAC-SHA256 密钥�?secret，结�?Base64 编码�? *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeishuChannel implements MessageChannel {

    /** 通道类型 */
    private static final String CHANNEL_TYPE = "FEISHU";

    /** 飞书机器�?Webhook URL 前缀（hook ID 拼接此后缀�?*/
    private static final String WEBHOOK_PREFIX =
            "https://open.feishu.cn/open-apis/bot/v2/hook/";

    /** 通道配置（提�?default-hook / secret / 超时�?*/
    private final ChannelProperties channelProperties;

    /** HTTP 客户端，�?{@link #init()} 中按配置超时构建 */
    RestClient restClient;

    /**
     * 注入配置后按 {@code pmis.channel.feishu.connect-timeout / read-timeout} 构建 RestClient�?     */
    @PostConstruct
    public void init() {
        ChannelProperties.FeishuConfig cfg = channelProperties.getChannel().getFeishu();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(cfg.getConnectTimeout());
        factory.setReadTimeout(cfg.getReadTimeout());
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * 通道类型�?     *
     * @return FEISHU
     */
    @Override
    public String channelType() {
        return CHANNEL_TYPE;
    }

    /**
     * 发送飞书消息：构�?text / post 请求体（含可选加签字段）�?POST �?Webhook URL�?     * 根据响应 code 判断成功 / 失败�?     *
     * @param request 消息请求
     * @return 发送结�?     */
    @Override
    public MessageResult send(MessageRequest request) {
        String webhookUrl = resolveUrl(request);
        if (!StringUtils.hasText(webhookUrl)) {
            log.warn("[FEISHU] 未配�?hook，跳过发�? receiver={}", request.getReceiver());
            return MessageResult.fail(CHANNEL_TYPE, "飞书 hook 未配�?);
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
                // 飞书 v2 hook 返回 {"code":0,"msg":"success"}�? 表示成功
                int code = ((Number) body.getOrDefault("code", -1)).intValue();
                if (code == 0) {
                    log.info("[FEISHU] 发送成�?);
                    return MessageResult.ok(CHANNEL_TYPE, traceId);
                }
                String msg = (String) body.getOrDefault("msg", "unknown");
                log.error("[FEISHU] 发送失�? code={} msg={}", code, msg);
                return MessageResult.fail(CHANNEL_TYPE, "code=" + code + ", msg=" + msg);
            }
            log.error("[FEISHU] 发送失�? status={}", response.getStatusCode());
            return MessageResult.fail(CHANNEL_TYPE, "HTTP " + response.getStatusCode());
        } catch (Exception e) {
            log.error("[FEISHU] 发送异�? reason={}", e.getMessage(), e);
            return MessageResult.fail(CHANNEL_TYPE, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 构造飞书消息请求体（含可选加签字�?timestamp / sign）�?     * <ul>
     *   <li>msgType=post：post 富文本，�?title 与一�?text 内容</li>
     *   <li>默认 text：{@code {"msg_type":"text","content":{"text":"内容"}}}</li>
     * </ul>
     *
     * @param request 消息请求
     * @return 请求�?Map
     */
    Map<String, Object> buildPayload(MessageRequest request) {
        String content = request.getContent() == null ? "" : request.getContent();
        String subject = request.getSubject() == null ? "PMIS 通知" : request.getSubject();
        String msgType = "text";
        if (request.getParams() != null) {
            Object mt = request.getParams().get("msgType");
            if (mt instanceof String s && "post".equalsIgnoreCase(s)) {
                msgType = "post";
            }
        }

        Map<String, Object> payload = new HashMap<>();
        if ("post".equals(msgType)) {
            payload.put("msg_type", "post");
            Map<String, Object> contentWrapper = new HashMap<>();
            Map<String, Object> post = new HashMap<>();
            Map<String, Object> zhCn = new HashMap<>();
            zhCn.put("title", subject);
            List<Map<String, Object>> line = new ArrayList<>();
            Map<String, Object> textNode = new HashMap<>();
            textNode.put("tag", "text");
            textNode.put("text", content);
            line.add(textNode);
            List<List<Map<String, Object>>> contentList = new ArrayList<>();
            contentList.add(line);
            zhCn.put("content", contentList);
            post.put("zh_cn", zhCn);
            contentWrapper.put("post", post);
            payload.put("content", contentWrapper);
        } else {
            payload.put("msg_type", "text");
            Map<String, Object> textContent = new HashMap<>();
            textContent.put("text", content);
            payload.put("content", textContent);
        }

        String secret = channelProperties.getChannel().getFeishu().getSecret();
        if (StringUtils.hasText(secret)) {
            Map<String, String> sign = appendSign(secret);
            payload.put("timestamp", sign.get("timestamp"));
            payload.put("sign", sign.get("sign"));
        }
        return payload;
    }

    /**
     * 解析 Webhook URL，优先级：params.feishuHook &gt; receiver &gt; 默认配置�?     * hook 值以 http 开头时直接使用，否则拼接到飞书 Webhook 前缀�?     *
     * @param request 消息请求
     * @return 解析到的 URL，无则返�?null
     */
    String resolveUrl(MessageRequest request) {
        Map<String, Object> params = request.getParams();
        if (params != null) {
            Object explicit = params.get("feishuHook");
            if (explicit instanceof String s && StringUtils.hasText(s)) {
                return normalizeHook(s.trim());
            }
        }
        String receiver = request.getReceiver();
        if (StringUtils.hasText(receiver)) {
            return normalizeHook(receiver.trim());
        }
        String defaultHook = channelProperties.getChannel().getFeishu().getDefaultHook();
        if (StringUtils.hasText(defaultHook)) {
            return normalizeHook(defaultHook.trim());
        }
        return null;
    }

    /**
     * �?hook 值规范化为完�?Webhook URL：以 http 开头时直接返回，否则拼接前缀�?     *
     * @param hook hook 值（完整 URL �?hook ID�?     * @return 完整 Webhook URL
     */
    private String normalizeHook(String hook) {
        if (hook.toLowerCase().startsWith("http")) {
            return hook;
        }
        return WEBHOOK_PREFIX + hook;
    }

    /**
     * 计算飞书加签�?     *
     * <p>签名算法：HMAC-SHA256(timestamp + "\n" + secret, secret) �?Base64�?     * timestamp 为秒级�?     *
     * @param secret 加签密钥
     * @return �?timestamp �?sign �?Map
     */
    Map<String, String> appendSign(String secret) {
        try {
            long timestamp = System.currentTimeMillis() / 1000;
            String stringToSign = timestamp + "\n" + secret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            String sign = Base64.getEncoder().encodeToString(signData);
            Map<String, String> result = new HashMap<>();
            result.put("timestamp", String.valueOf(timestamp));
            result.put("sign", sign);
            return result;
        } catch (Exception e) {
            log.warn("[FEISHU] 加签失败，跳过签�? {}", e.getMessage());
            return new HashMap<>();
        }
    }
}
