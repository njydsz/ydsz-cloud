package com.remisoft.common.webhook;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.remisoft.common.json.RemiJson;

/**
 * Webhook 投递器默认实现。
 *
 * <p>使用内存 ConcurrentHashMap 管理订阅，RestTemplate 投递事件，
 * HMAC-SHA256 签名，简单重试（3 次指数退避）。
 *
 * <p>通过 {@code @ConditionalOnMissingBean} 注册，业务方可覆盖此 Bean
 * 提供自定义实现（如基于 Redis 持久化订阅、异步线程池投递等）。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Component
@ConditionalOnMissingBean(WebhookDispatcher.class)
public class DefaultWebhookDispatcher implements WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DefaultWebhookDispatcher.class);

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_HEADER = "X-Webhook-Signature";
    private static final int MAX_RETRIES = 3;

    private final Map<String, WebhookSubscription> subscriptions = new ConcurrentHashMap<>();
    private final ObjectProvider<RestTemplate> restTemplateProvider;

    public DefaultWebhookDispatcher(ObjectProvider<RestTemplate> restTemplateProvider) {
        this.restTemplateProvider = restTemplateProvider;
    }

    @Override
    public void register(WebhookSubscription subscription) {
        if (subscription == null || subscription.getId() == null) {
            return;
        }
        subscriptions.put(subscription.getId(), subscription);
        log.info("[WebhookDispatcher] 注册订阅: id={} url={} events={}",
                subscription.getId(), subscription.getCallbackUrl(), subscription.getEventTypes());
    }

    @Override
    public void unregister(String subscriptionId) {
        WebhookSubscription removed = subscriptions.remove(subscriptionId);
        if (removed != null) {
            log.info("[WebhookDispatcher] 注销订阅: id={} url={}", subscriptionId, removed.getCallbackUrl());
        }
    }

    @Override
    public void dispatch(String eventType, Map<String, Object> payload) {
        if (eventType == null || payload == null) {
            return;
        }
        List<WebhookSubscription> matched = findMatchingSubscriptions(eventType);
        if (matched.isEmpty()) {
            return;
        }
        String jsonPayload = RemiJson.toJson(payload);
        for (WebhookSubscription sub : matched) {
            dispatchWithRetry(sub, eventType, jsonPayload);
        }
    }

    private List<WebhookSubscription> findMatchingSubscriptions(String eventType) {
        List<WebhookSubscription> result = new ArrayList<>();
        for (WebhookSubscription sub : subscriptions.values()) {
            if (Boolean.FALSE.equals(sub.getEnabled())) {
                continue;
            }
            if (sub.getEventTypes() == null || sub.getEventTypes().isBlank()) {
                continue;
            }
            for (String type : sub.getEventTypes().split(",")) {
                if (eventType.equalsIgnoreCase(type.trim())) {
                    result.add(sub);
                    break;
                }
            }
        }
        return result;
    }

    private void dispatchWithRetry(WebhookSubscription sub, String eventType, String jsonPayload) {
        RestTemplate restTemplate = restTemplateProvider.getIfAvailable();
        if (restTemplate == null) {
            log.warn("[WebhookDispatcher] RestTemplate 未配置,跳过投递: id={} event={}",
                    sub.getId(), eventType);
            return;
        }
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                if (sub.getSecret() != null && !sub.getSecret().isBlank()) {
                    headers.set(SIGNATURE_HEADER, sign(jsonPayload, sub.getSecret()));
                }
                HttpEntity<String> entity = new HttpEntity<>(jsonPayload, headers);
                restTemplate.postForEntity(sub.getCallbackUrl(), entity, String.class);
                log.debug("[WebhookDispatcher] 投递成功: id={} event={} attempt={}",
                        sub.getId(), eventType, attempt);
                return;
            } catch (Exception e) {
                log.warn("[WebhookDispatcher] 投递失败: id={} event={} attempt={} err={}",
                        sub.getId(), eventType, attempt, e.getMessage());
                if (attempt >= MAX_RETRIES) {
                    log.error("[WebhookDispatcher] 投递最终失败: id={} event={}",
                            sub.getId(), eventType, e);
                    return;
                }
                sleepBackoff(attempt);
            }
        }
    }

    private String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(raw);
        } catch (Exception e) {
            log.warn("[WebhookDispatcher] 签名失败: {}", e.getMessage());
            return "";
        }
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(1000L * attempt * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
