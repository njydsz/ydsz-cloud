package com.remisoft.message.server.service.webhook;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.remisoft.common.webhook.WebhookDispatcher;
import com.remisoft.common.webhook.WebhookSubscription;
import com.remisoft.message.domain.entity.core.MsgLog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 出站 Webhook 事件订阅服务（P2-3）。
 *
 * <p>允许外部系统订阅消息事件（发送成功/失败/回执/撤回）,
 * 当事件发生时回调注册的 Webhook URL。
 *
 * <p><b>P1-3 架构优化</b>：将 HTTP 投递、HMAC 签名、重试逻辑委托到
 * {@link WebhookDispatcher}（common 模块统一实现），消除重复代码。
 * 本类仅负责消息事件的业务逻辑（构造 payload、管理订阅）。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboundWebhookService {

    private final WebhookDispatcher webhookDispatcher;

    /**
     * 注册 Webhook 订阅。
     *
     * @param url      回调 URL
     * @param events   订阅事件列表（如 ["MESSAGE_SENT","MESSAGE_FAILED","RECEIPT"]）
     * @param secret   签名密钥（回调时附带 HMAC-SHA256 签名）
     */
    public void subscribe(String url, List<String> events, String secret) {
        if (url == null || url.isBlank()) {
            return;
        }
        String eventId = "msg-webhook-" + Integer.toHexString(url.hashCode());
        WebhookSubscription sub = WebhookSubscription.builder()
                .id(eventId)
                .callbackUrl(url)
                .eventTypes(events != null ? String.join(",", events) : null)
                .secret(secret)
                .enabled(true)
                .sourceModule("message")
                .build();
        webhookDispatcher.register(sub);
        log.info("[Webhook] 注册订阅: url={} events={}", url, events);
    }

    /**
     * 取消订阅。
     *
     * @param url 回调 URL
     */
    public void unsubscribe(String url) {
        String eventId = "msg-webhook-" + Integer.toHexString(url.hashCode());
        webhookDispatcher.unregister(eventId);
    }

    /**
     * 触发事件通知（委托 WebhookDispatcher 投递到所有匹配的订阅）。
     *
     * @param event 事件类型
     * @param logDO 消息日志
     */
    public void fireEvent(String event, MsgLog logDO) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", event);
        payload.put("timestamp", System.currentTimeMillis());
        payload.put("msgId", logDO.getId());
        payload.put("channel", logDO.getChannel());
        payload.put("status", logDO.getStatus());
        payload.put("bizType", logDO.getBizType());
        payload.put("bizId", logDO.getBizId());
        payload.put("receiver", logDO.getReceiver());

        // 委托到 WebhookDispatcher 统一投递（含 HMAC 签名 + 重试）
        webhookDispatcher.dispatch(event, payload);
    }
}
