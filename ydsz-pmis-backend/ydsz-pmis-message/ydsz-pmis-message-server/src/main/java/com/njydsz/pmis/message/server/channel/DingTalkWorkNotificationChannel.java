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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 钉钉工作通知通道（企业内部应用）�? *
 * <p>P0-2: 通过钉钉开放平台企业内部应用发送工作通知(与群机器人不�?
 * 工作通知可指�?userId 定向发�?支持 text/markdown/actionCard 消息类型)�? *
 * <p>流程�? * <ol>
 *   <li>AppKey + AppSecret �?获取 access_token(缓存 Redis,7200s)</li>
 *   <li>调用 {@code /topapi/message/corpconversation/asyncsend_v2} 发送工作通知</li>
 * </ol>
 *
 * <p>未配�?AppKey 时降级为 mock 输出日志,保证开发环境可运行�? *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DingTalkWorkNotificationChannel implements MessageChannel {

    private static final String CHANNEL_TYPE = "DINGTALK_WORK";
    private static final String TOKEN_CACHE_KEY = "pmis:msg:dingtalk:work:access_token";
    private static final Duration TOKEN_TTL = Duration.ofSeconds(7200);

    private final ChannelProperties channelProperties;
    private final StringRedisTemplate redisTemplate;

    RestClient restClient;

    @PostConstruct
    public void init() {
        ChannelProperties.DingTalkWorkConfig cfg = channelProperties.getChannel().getDingtalkWork();
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
        ChannelProperties.DingTalkWorkConfig cfg = channelProperties.getChannel().getDingtalkWork();

        // 降级 mock
        if (!cfg.isEnabled() || !StringUtils.hasText(cfg.getAppKey())) {
            log.warn("[DINGTALK_WORK] 未启用或未配�?AppKey, 降级 mock: receiver={} content={}",
                    request.getReceiver(), truncate(request.getContent(), 100));
            return MessageResult.ok(CHANNEL_TYPE, "mock-" + System.currentTimeMillis());
        }

        String accessToken = getAccessToken(cfg);
        if (accessToken == null) {
            return MessageResult.fail(CHANNEL_TYPE, "获取钉钉 access_token 失败");
        }

        String receiver = request.getReceiver();
        if (!StringUtils.hasText(receiver)) {
            return MessageResult.fail(CHANNEL_TYPE, "接收�?userId)不能为空");
        }

        Map<String, Object> payload = buildPayload(request, cfg.getAgentId(), receiver);
        String url = cfg.getBaseUrl() + "/topapi/message/corpconversation/asyncsend_v2?access_token=" + accessToken;

        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(JSON.toJSONString(payload))
                    .retrieve()
                    .toEntity(String.class);
            String traceId = CHANNEL_TYPE + "-" + SnowflakeIdGenerator.nextTraceId();

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = JSON.parseObject(response.getBody());
                int errcode = ((Number) body.getOrDefault("errcode", -1)).intValue();
                if (errcode == 0) {
                    log.info("[DINGTALK_WORK] 发送成�? receiver={}", receiver);
                    return MessageResult.ok(CHANNEL_TYPE, traceId);
                }
                String errmsg = (String) body.getOrDefault("errmsg", "unknown");
                log.error("[DINGTALK_WORK] 发送失�? errcode={} errmsg={}", errcode, errmsg);
                return MessageResult.fail(CHANNEL_TYPE, "errcode=" + errcode + ", errmsg=" + errmsg);
            }
            log.error("[DINGTALK_WORK] 发送失�? status={}", response.getStatusCode());
            return MessageResult.fail(CHANNEL_TYPE, "HTTP " + response.getStatusCode());
        } catch (Exception e) {
            log.error("[DINGTALK_WORK] 发送异�? reason={}", e.getMessage(), e);
            return MessageResult.fail(CHANNEL_TYPE, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 获取钉钉 access_token（Redis 缓存，提前续期）�?     */
    private String getAccessToken(ChannelProperties.DingTalkWorkConfig cfg) {
        try {
            String cached = redisTemplate.opsForValue().get(TOKEN_CACHE_KEY);
            if (StringUtils.hasText(cached)) {
                return cached;
            }
            String url = cfg.getBaseUrl() + "/gettoken?appkey=" + cfg.getAppKey()
                    + "&appsecret=" + cfg.getAppSecret();
            ResponseEntity<String> response = restClient.get().uri(url).retrieve().toEntity(String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = JSON.parseObject(response.getBody());
                int errcode = ((Number) body.getOrDefault("errcode", -1)).intValue();
                if (errcode == 0) {
                    String token = (String) body.get("access_token");
                    redisTemplate.opsForValue().set(TOKEN_CACHE_KEY, token, TOKEN_TTL.minusSeconds(300));
                    log.info("[DINGTALK_WORK] 刷新 access_token 成功");
                    return token;
                }
                log.error("[DINGTALK_WORK] 获取 access_token 失败: errcode={} errmsg={}",
                        errcode, body.get("errmsg"));
            }
        } catch (Exception e) {
            log.error("[DINGTALK_WORK] 获取 access_token 异常: {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * 构造钉钉工作通知请求体�?     */
    private Map<String, Object> buildPayload(MessageRequest request, Long agentId, String receiver) {
        String content = request.getContent() == null ? "" : request.getContent();
        String subject = request.getSubject() == null ? "PMIS 通知" : request.getSubject();
        String msgType = "text";
        if (request.getParams() != null) {
            Object mt = request.getParams().get("msgType");
            if (mt instanceof String s && ("markdown".equalsIgnoreCase(s) || "action_card".equalsIgnoreCase(s))) {
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
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
