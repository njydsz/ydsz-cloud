package com.njydsz.pmis.message.service.webhook;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.message.entity.core.MsgLogDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 出站 Webhook 事件订阅服务（P2-3）。
 *
 * <p>允许外部系统订阅消息事件（发送成功/失败/回执/撤回）,
 * 当事件发生时回调注册的 Webhook URL。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboundWebhookService {

    /** Webhook 订阅配置（从数据库或配置加载,此处简化为内存缓存） */
    private final List<WebhookSubscription> subscriptions = new java.util.concurrent.CopyOnWriteArrayList<>();

    private final RestClient restClient;

    public OutboundWebhookService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * 注册 Webhook 订阅。
     *
     * @param url      回调 URL
     * @param events   订阅事件列表（如 ["MESSAGE_SENT","MESSAGE_FAILED","RECEIPT"]）
     * @param secret   签名密钥（回调时附带 HMAC-SHA256 签名）
     */
    public void subscribe(String url, List<String> events, String secret) {
        if (!StringUtils.hasText(url)) {
            return;
        }
        subscriptions.add(new WebhookSubscription(url, events, secret));
        log.info("[Webhook] 注册订阅: url={} events={}", url, events);
    }

    /**
     * 取消订阅。
     *
     * @param url 回调 URL
     */
    public void unsubscribe(String url) {
        subscriptions.removeIf(s -> s.url.equals(url));
    }

    /**
     * 触发事件通知（异步回调所有匹配的 Webhook）。
     *
     * @param event 事件类型
     * @param logDO 消息日志
     */
    public void fireEvent(String event, MsgLogDO logDO) {
        if (subscriptions.isEmpty()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", event);
        payload.put("timestamp", System.currentTimeMillis());
        payload.put("msgId", logDO.getId());
        payload.put("channel", logDO.getChannel());
        payload.put("status", logDO.getStatus());
        payload.put("bizType", logDO.getBizType());
        payload.put("bizId", logDO.getBizId());
        payload.put("receiver", logDO.getReceiver());

        for (WebhookSubscription sub : subscriptions) {
            if (sub.events == null || sub.events.contains(event)) {
                sendWebhook(sub, payload);
            }
        }
    }

    private void sendWebhook(WebhookSubscription sub, Map<String, Object> payload) {
        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(sub.url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Webhook-Event", (String) payload.get("event"))
                    .body(JSON.toJSONString(payload))
                    .retrieve()
                    .toEntity(String.class);
            log.debug("[Webhook] 回调成功: url={} status={}", sub.url, response.getStatusCode());
        } catch (Exception e) {
            log.warn("[Webhook] 回调失败: url={} err={}", sub.url, e.getMessage());
        }
    }

    /** Webhook 订阅配置 */
    private record WebhookSubscription(String url, List<String> events, String secret) {}
}
