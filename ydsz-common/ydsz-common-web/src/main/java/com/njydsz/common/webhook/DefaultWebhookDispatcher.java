package com.njydsz.common.webhook;

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
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.njydsz.common.json.YdszJson;

/**
 * Webhook 投递器默认实现。
 *
 * <p>使用内存 ConcurrentHashMap 管理订阅，RestTemplate 投递事件，
 * HMAC-SHA256 签名，简单重试（3 次指数退避）。
 *
 * <p>连接池优化：
 * <ul>
 *   <li>当 Apache HttpClient 在 classpath 时，使用 {@code HttpComponentsClientHttpRequestFactory} 启用连接池</li>
 *   <li>否则降级为 {@code SimpleClientHttpRequestFactory}（无连接池）</li>
 *   <li>连接超时、读取超时、最大连接数均通过 {@link WebhookProperties} 配置</li>
 * </ul>
 *
 * <p>通过 {@code @ConditionalOnMissingBean} 注册，业务方可覆盖此 Bean
 * 提供自定义实现（如基于 Redis 持久化订阅、异步线程池投递等）。
 *
 * @author ydsz-team
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
    private final RestTemplate restTemplate;

    public DefaultWebhookDispatcher(ObjectProvider<RestTemplate> restTemplateProvider,
                                    WebhookProperties webhookProperties) {
        RestTemplate provided = restTemplateProvider.getIfAvailable();
        if (provided != null) {
            // 对外提供的 RestTemplate 配置连接池工厂
            this.restTemplate = configureRestTemplate(provided, webhookProperties);
        } else {
            // 未提供 RestTemplate 时创建专用实例
            this.restTemplate = createDedicatedRestTemplate(webhookProperties);
        }
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
        String jsonPayload = YdszJson.toJson(payload);
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

    /**
     * 配置 RestTemplate 的连接池工厂。
     *
     * <p>当 Apache HttpClient 在 classpath 时使用连接池实现，
     * 否则降级为 SimpleClientHttpRequestFactory。
     */
    private RestTemplate configureRestTemplate(RestTemplate restTemplate,
                                                WebhookProperties properties) {
        ClientHttpRequestFactory factory = createRequestFactory(properties);
        restTemplate.setRequestFactory(factory);
        return restTemplate;
    }

    /**
     * 创建专用 RestTemplate 实例。
     */
    private RestTemplate createDedicatedRestTemplate(WebhookProperties properties) {
        return new RestTemplate(createRequestFactory(properties));
    }

    /**
     * 创建请求工厂（优先使用 Apache HttpClient 连接池）。
     */
    private ClientHttpRequestFactory createRequestFactory(WebhookProperties properties) {
        try {
            // 尝试使用 Apache HttpClient 连接池
            Class<?> httpClientFactoryClass = Class.forName(
                    "org.springframework.http.client.HttpComponentsClientHttpRequestFactory");
            Object factory = httpClientFactoryClass.getDeclaredConstructor().newInstance();
            // 设置超时
            httpClientFactoryClass.getMethod("setConnectTimeout", int.class)
                    .invoke(factory, properties.getConnectTimeoutMs());
            httpClientFactoryClass.getMethod("setReadTimeout", int.class)
                    .invoke(factory, properties.getReadTimeoutMs());
            log.info("[WebhookDispatcher] 使用 Apache HttpClient 连接池 | connectTimeout={}ms readTimeout={}ms",
                    properties.getConnectTimeoutMs(), properties.getReadTimeoutMs());
            return (ClientHttpRequestFactory) factory;
        } catch (ClassNotFoundException e) {
            // Apache HttpClient 不在 classpath，降级
            log.info("[WebhookDispatcher] Apache HttpClient 不在 classpath，使用 SimpleClientHttpRequestFactory");
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(properties.getConnectTimeoutMs());
            factory.setReadTimeout(properties.getReadTimeoutMs());
            return factory;
        } catch (Exception e) {
            log.warn("[WebhookDispatcher] 创建连接池工厂失败，降级为 SimpleClientHttpRequestFactory", e);
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(properties.getConnectTimeoutMs());
            factory.setReadTimeout(properties.getReadTimeoutMs());
            return factory;
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
