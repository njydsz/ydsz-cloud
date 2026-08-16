package com.njydsz.gateway.config;

import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;

import com.njydsz.common.notify.helper.NotifyHelper;

import lombok.extern.slf4j.Slf4j;

/**
 * GAP-P1-1 + GAP-P1-2: 网关告警通知服务
 *
 * <p>集成 ydsz-common-notify 的 {@link NotifyHelper}，在网关关键事件触发时发送实时 IM 通知。
 * 使用 {@code @Value} 注入告警目标 Webhook URL，未配置时不发送告警。
 *
 * <h3>告警场景</h3>
 * <ul>
 *   <li>限流连续触发 — 高 QPS / 攻击流量</li>
 *   <li>IP 黑名单连续命中 — 恶意 IP 访问</li>
 *   <li>下游服务 502/504 — 服务不可用</li>
 *   <li>Redis 限流降级切换 — 限流熔断器打开</li>
 * </ul>
 *
 * <p>使用 ObjectProvider 实现可选依赖，当 NotifyHelper 不可用时不影响网关正常运行。
 *
 * <p><b>收敛说明</b>：使用 {@link NotifyHelper} 替代直接使用 {@code NotifyService} 或
 * {@code NotificationClient} Feign。详见 {@code docs/module-review/ADR-001-notify-message-convergence.md}。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
public class GatewayAlertService {

    /** 通知辅助类（可选依赖） */
    private final ObjectProvider<NotifyHelper> notifyHelperProvider;

    /** 告警目标 DingTalk Webhook URL（含 access_token） */
    private final String alertWebhookUrl;

    /** 上次告警时间戳（简单限流，避免同一事件刷屏） */
    private volatile long lastRatelimitAlertTs = 0;
    private volatile long lastBlacklistAlertTs = 0;
    private volatile long lastDownstreamAlertTs = 0;

    /** 告警间隔（毫秒），同一类事件 60 秒内只通知一次 */
    private static final long ALERT_INTERVAL_MS = 60_000L;

    /**
     * 构造网关告警服务
     *
     * @param notifyHelperProvider 通知辅助类提供者（可选）
     * @param alertWebhookUrl      告警目标 Webhook URL
     */
    public GatewayAlertService(ObjectProvider<NotifyHelper> notifyHelperProvider,
                               String alertWebhookUrl) {
        this.notifyHelperProvider = notifyHelperProvider;
        this.alertWebhookUrl = alertWebhookUrl;
    }

    /**
     * 限流触发告警
     *
     * @param dimension 限流维度（IP/USER/TENANT）
     * @param identity  限流标识
     * @param path      请求路径
     */
    public void alertRatelimitTriggered(String dimension, String identity, String path) {
        long now = System.currentTimeMillis();
        if (now - lastRatelimitAlertTs < ALERT_INTERVAL_MS) {
            return;
        }
        lastRatelimitAlertTs = now;

        sendAlert("【高】网关限流触发",
                Map.of(
                        "维度", dimension,
                        "标识", maskIdentity(identity),
                        "路径", path,
                        "时间", Instant.now().toString()
                ));
    }

    /**
     * IP 黑名单命中告警
     *
     * @param clientIp 客户端 IP
     * @param path     请求路径
     */
    public void alertBlacklistHit(String clientIp, String path) {
        long now = System.currentTimeMillis();
        if (now - lastBlacklistAlertTs < ALERT_INTERVAL_MS) {
            return;
        }
        lastBlacklistAlertTs = now;

        sendAlert("【严重】IP 黑名单命中",
                Map.of(
                        "IP", clientIp,
                        "路径", path,
                        "时间", Instant.now().toString()
                ));
    }

    /**
     * 下游服务不可用告警
     *
     * @param routeId    路由 ID
     * @param targetUri  目标 URI
     * @param statusCode HTTP 状态码
     */
    public void alertDownstreamUnavailable(String routeId, String targetUri, int statusCode) {
        long now = System.currentTimeMillis();
        if (now - lastDownstreamAlertTs < ALERT_INTERVAL_MS) {
            return;
        }
        lastDownstreamAlertTs = now;

        sendAlert("【严重】下游服务不可用",
                Map.of(
                        "路由", routeId,
                        "目标", targetUri,
                        "状态码", String.valueOf(statusCode),
                        "时间", Instant.now().toString()
                ));
    }

    /**
     * 发送告警通知
     *
     * @param title   告警标题
     * @param details 告警详情
     */
    private void sendAlert(String title, Map<String, String> details) {
        NotifyHelper helper = notifyHelperProvider.getIfAvailable();
        if (helper == null) {
            log.debug("[GatewayAlert] NotifyHelper 不可用，跳过告警: {}", title);
            return;
        }
        if (alertWebhookUrl == null || alertWebhookUrl.isBlank()) {
            log.debug("[GatewayAlert] 未配置告警 Webhook URL，跳过告警: {}", title);
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append(title).append("\n");
        details.forEach((k, v) -> message.append(k).append(": ").append(v).append("\n"));

        helper.sendDingTalk(alertWebhookUrl, title, message.toString());
        log.info("[GatewayAlert] 告警已发送: {}", title);
    }

    /**
     * 身份标识脱敏
     */
    private String maskIdentity(String identity) {
        if (identity == null || identity.length() <= 4) {
            return "***";
        }
        return identity.substring(0, 2) + "***" + identity.substring(identity.length() - 2);
    }
}
