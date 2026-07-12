package com.njydsz.pmis.common.webhook;

import com.njydsz.pmis.common.util.CryptoSignUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一 Webhook 分发器（P2-1 架构优化）。
 *
 * <p>替代 message 模块的 WebhookChannel 和 cronjob 模块的 WebhookEventDispatcher。
 * 各模块通过此统一分发器发送 Webhook 通知。
 *
 * <h3>功能</h3>
 * <ul>
 *   <li>Webhook 订阅管理（注册/查询/删除）</li>
 *   <li>HMAC-SHA256 签名（X-Webhook-Signature 头）</li>
 *   <li>异步 HTTP POST 投递</li>
 *   <li>重试机制（最多 3 次）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component
public class WebhookDispatcher {

    private final RestTemplate restTemplate;
    private final Map<String, WebhookSubscription> subscriptions = new ConcurrentHashMap<>();

    /**
     * 默认构造器。
     */
    public WebhookDispatcher() {
        this(new RestTemplate());
    }

    /**
     * 注入式构造器。
     *
     * @param restTemplate RestTemplate 实例
     */
    public WebhookDispatcher(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 注册 Webhook 订阅。
     *
     * @param subscription 订阅信息
     */
    public void register(WebhookSubscription subscription) {
        if (subscription == null || subscription.getId() == null) {
            return;
        }
        subscriptions.put(subscription.getId(), subscription);
        log.info("[WebhookDispatcher] 注册订阅: id={} url={} events={}",
                subscription.getId(), subscription.getCallbackUrl(), subscription.getEventTypes());
    }

    /**
     * 取消注册。
     *
     * @param subscriptionId 订阅 ID
     */
    public void unregister(String subscriptionId) {
        subscriptions.remove(subscriptionId);
    }

    /**
     * 获取所有订阅。
     *
     * @return 订阅列表
     */
    public List<WebhookSubscription> getSubscriptions() {
        return List.copyOf(subscriptions.values());
    }

    /**
     * 分发事件到所有匹配的 Webhook 订阅。
     *
     * @param eventType 事件类型
     * @param payload   事件负载
     */
    public void dispatch(String eventType, Map<String, Object> payload) {
        for (WebhookSubscription sub : subscriptions.values()) {
            if (!sub.isEnabled()) continue;
            if (!matchesEvent(sub.getEventTypes(), eventType)) continue;

            try {
                sendWebhook(sub, eventType, payload);
            } catch (Exception e) {
                log.warn("[WebhookDispatcher] 投递失败: id={} url={} err={}",
                        sub.getId(), sub.getCallbackUrl(), e.getMessage());
            }
        }
    }

    /**
     * 发送单个 Webhook 请求。
     */
    private void sendWebhook(WebhookSubscription sub, String eventType, Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Webhook-Event", eventType);

        // HMAC-SHA256 签名（P1-1: 委托到 CryptoSignUtil 统一实现）
        if (sub.getSecret() != null && !sub.getSecret().isBlank()) {
            String signature = CryptoSignUtil.hmacSha256Hex(payload.toString(), sub.getSecret());
            headers.set("X-Webhook-Signature", "sha256=" + signature);
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        sub.getCallbackUrl(), HttpMethod.POST, entity, String.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("[WebhookDispatcher] 投递成功: id={} url={} event={}",
                            sub.getId(), sub.getCallbackUrl(), eventType);
                    return;
                }
                log.warn("[WebhookDispatcher] 投递非 2xx: id={} status={} attempt={}/{}",
                        sub.getId(), response.getStatusCode(), attempt, maxRetries);
            } catch (Exception e) {
                log.warn("[WebhookDispatcher] 投递异常: id={} attempt={}/{} err={}",
                        sub.getId(), attempt, maxRetries, e.getMessage());
            }
        }
    }

    /**
     * 检查事件类型是否匹配订阅。
     */
    private boolean matchesEvent(String eventTypes, String eventType) {
        if (eventTypes == null || eventTypes.isBlank()) {
            return true; // 空 = 订阅全部
        }
        return List.of(eventTypes.split(","))
                .stream()
                .map(String::trim)
                .anyMatch(eventType::equalsIgnoreCase);
    }

}
