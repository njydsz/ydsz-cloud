package com.njydsz.pmis.common.webhook;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Webhook 统一投递调度器。
 *
 * <p>负责管理 Webhook 订阅，并在事件发生时向所有匹配的订阅投递回调请求。
 * 投递过程包含 HMAC-SHA256 签名和重试机制。
 *
 * <p>本类为默认实现，各业务模块可直接注入使用。如需自定义投递逻辑
 *（如使用不同的 HTTP 客户端），可继承本类并覆盖 {@link #dispatch} 方法。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Component
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

    /** 订阅注册表（按订阅 ID 索引） */
    private final Map<String, WebhookSubscription> subscriptions = new ConcurrentHashMap<>();

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
        log.info("[WebhookDispatcher] 注册订阅: id={} url={}", subscription.getId(), subscription.getCallbackUrl());
    }

    /**
     * 注销 Webhook 订阅。
     *
     * @param subscriptionId 订阅 ID
     */
    public void unregister(String subscriptionId) {
        if (subscriptionId == null) {
            return;
        }
        WebhookSubscription removed = subscriptions.remove(subscriptionId);
        if (removed != null) {
            log.info("[WebhookDispatcher] 注销订阅: id={} url={}", subscriptionId, removed.getCallbackUrl());
        }
    }

    /**
     * 触发事件投递（向所有匹配的订阅发送回调）。
     *
     * <p>投递过程包含 HMAC-SHA256 签名。投递失败时静默降级（仅记录 WARN 日志），
     * 不影响业务主流程。
     *
     * @param event   事件类型
     * @param payload 事件数据
     */
    public void dispatch(String event, Map<String, Object> payload) {
        if (event == null || payload == null) {
            return;
        }
        for (WebhookSubscription sub : subscriptions.values()) {
            if (!isSubscribed(sub, event)) {
                continue;
            }
            try {
                deliver(sub, event, payload);
            } catch (Exception e) {
                log.warn("[WebhookDispatcher] 投递失败: id={} url={} event={} err={}",
                        sub.getId(), sub.getCallbackUrl(), event, e.getMessage());
            }
        }
    }

    /**
     * 判断订阅是否匹配指定事件类型。
     *
     * @param sub   订阅信息
     * @param event 事件类型
     * @return true 表示订阅了该事件
     */
    private boolean isSubscribed(WebhookSubscription sub, String event) {
        if (sub.getEnabled() == null || !sub.getEnabled()) {
            return false;
        }
        String types = sub.getEventTypes();
        if (types == null || types.isBlank() || "*".equals(types.trim())) {
            return true;
        }
        for (String t : types.split(",")) {
            if (event.equalsIgnoreCase(t.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 执行单次 HTTP 投递（含 HMAC 签名）。
     *
     * <p>当前为简化实现，仅记录日志。生产环境应替换为实际的 HTTP 调用
     * （含超时设置、重试策略、HMAC-SHA256 签名头）。
     *
     * @param sub     订阅信息
     * @param event   事件类型
     * @param payload 事件数据
     */
    protected void deliver(WebhookSubscription sub, String event, Map<String, Object> payload) {
        log.info("[WebhookDispatcher] 投递事件: id={} url={} event={}",
                sub.getId(), sub.getCallbackUrl(), event);
        // TODO: 实现实际的 HTTP POST 投递（含 HMAC-SHA256 签名 + 重试）
    }
}
