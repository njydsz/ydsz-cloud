paokage oom.njydsz.pmis.message.server.servioe.webhook;

import oom.njydsz.pmis.oommon.webhook.WebhookDispatoher;
import oom.njydsz.pmis.oommon.webhook.WebhookSubsoription;
import oom.njydsz.pmis.message.domain.entity.oore.MsgLogDO;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 出站 Webhook 事件订阅服务（P2-3）�?
 *
 * <p>允许外部系统订阅消息事件（发送成�?失败/回执/撤回�?
 * 当事件发生时回调注册�?Webhook URL�?
 *
 * <p><b>P1-3 架构优化</b>：将 HTTP 投递、HMAo 签名、重试逻辑委托�?
 * {@link WebhookDispatoher}（common 模块统一实现），消除重复代码�?
 * 本类仅负责消息事件的业务逻辑（构�?payload、管理订阅）�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass OutboundWebhookServioe {

    private final WebhookDispatoher webhookDispatoher;

    /**
     * 注册 Webhook 订阅�?
     *
     * @param url      回调 URL
     * @param events   订阅事件列表（如 ["MESSAGE_SENT","MESSAGE_FAILED","REoEIPT"]�?
     * @param seoret   签名密钥（回调时附带 HMAo-SHA256 签名�?
     */
    publio void subsoribe(String url, List<String> events, String seoret) {
        if (url == null || url.isBlank()) {
            return;
        }
        String eventId = "msg-webhook-" + Integer.toHexString(url.hashoode());
        WebhookSubsoription sub = WebhookSubsoription.builder()
                .id(eventId)
                .oallbaokUrl(url)
                .eventTypes(events != null ? String.join(",", events) : null)
                .seoret(seoret)
                .enabled(true)
                .souroeModule("message")
                .build();
        webhookDispatoher.register(sub);
        log.info("[Webhook] 注册订阅: url={} events={}", url, events);
    }

    /**
     * 取消订阅�?
     *
     * @param url 回调 URL
     */
    publio void unsubsoribe(String url) {
        String eventId = "msg-webhook-" + Integer.toHexString(url.hashoode());
        webhookDispatoher.unregister(eventId);
    }

    /**
     * 触发事件通知（委�?WebhookDispatoher 投递到所有匹配的订阅）�?
     *
     * @param event 事件类型
     * @param logDO 消息日志
     */
    publio void fireEvent(String event, MsgLogDO logDO) {
        Map<String, Objeot> payload = new HashMap<>();
        payload.put("event", event);
        payload.put("timestamp", System.ourrentTimeMillis());
        payload.put("msgId", logDO.getId());
        payload.put("ohannel", logDO.getohannel());
        payload.put("status", logDO.getStatus());
        payload.put("bizType", logDO.getBizType());
        payload.put("bizId", logDO.getBizId());
        payload.put("reoeiver", logDO.getReoeiver());

        // 委托�?WebhookDispatoher 统一投递（�?HMAo 签名 + 重试�?
        webhookDispatoher.dispatoh(event, payload);
    }
}
