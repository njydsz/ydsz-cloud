package com.njydsz.pmis.cronjob.core.alert;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.cronjob.config.AlertProperties;
import com.njydsz.pmis.cronjob.entity.JobAlertRuleDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用 Webhook 通知器（P5 告警 + 监控）。
 *
 * <p>将告警信息以 JSON 格式 POST 到业务系统自定义的 Webhook URL，
 * 由业务系统自行处理（如转发到 Slack、Teams、自建告警中心等）。
 *
 * <p>Webhook URL 通过 {@code pmis.cronjob.alert.webhook.webhook-url} 配置，
 * 自定义请求头通过 {@code pmis.cronjob.alert.webhook.headers}（JSON 字符串）配置。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "pmis.cronjob.alert.webhook", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnMissingBean(name = "webhookAlertNotifier")
public class WebhookAlertNotifier extends AbstractAlertNotifier {

    private final AlertProperties alertProperties;

    @Override
    public AlertChannel supportedChannel() {
        return AlertChannel.WEBHOOK;
    }

    @Override
    public void notify(AlertContext context, JobAlertRuleDO rule, List<String> receivers) throws AlertSendException {
        AlertProperties.Webhook config = alertProperties.getWebhook();
        if (config.getWebhookUrl() == null || config.getWebhookUrl().isBlank()) {
            log.warn("[Webhook] Webhook URL 未配置, 跳过: ruleId={}", rule.getId());
            throw new AlertSendException("Webhook URL 未配置");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("title", buildTitle(context, rule));
        payload.put("content", buildContent(context, rule));
        payload.put("alertType", rule.getAlertType());
        payload.put("alertLevel", rule.getAlertLevel());
        payload.put("ruleId", rule.getId());
        payload.put("ruleName", rule.getRuleName());
        payload.put("jobId", context.jobId());
        payload.put("jobKey", context.jobKey());
        payload.put("jobName", context.jobName());
        payload.put("triggerValue", context.triggerValue());
        payload.put("threshold", rule.getThreshold());
        payload.put("errorMessage", context.errorMessage());
        payload.put("traceId", context.traceId());
        payload.put("triggerLogId", context.triggerLogId());
        payload.put("tenantId", context.tenantId());
        payload.put("receivers", receivers);

        String jsonBody = JSON.toJSONString(payload);
        Map<String, String> headers = parseHeaders(config.getHeaders());
        log.debug("[Webhook] 发送告警: ruleId={} url={}", rule.getId(), maskUrl(config.getWebhookUrl()));
        AlertHttpClient httpClient = new AlertHttpClient(alertProperties);
        String resp = httpClient.postJson(config.getWebhookUrl(), jsonBody, headers);
        log.info("[Webhook] 告警发送成功: ruleId={} resp={}", rule.getId(), truncate(resp));
    }

    /**
     * 解析自定义请求头 JSON。
     */
    private Map<String, String> parseHeaders(String headersJson) {
        if (headersJson == null || headersJson.isBlank()) {
            return null;
        }
        try {
            JSONObject obj = JSON.parseObject(headersJson);
            Map<String, String> headers = new HashMap<>();
            for (String key : obj.keySet()) {
                headers.put(key, obj.getString(key));
            }
            return headers;
        } catch (Exception e) {
            log.warn("[Webhook] 解析自定义请求头失败: headers={} reason={}", headersJson, e.getMessage());
            return null;
        }
    }

    private String maskUrl(String url) {
        if (url == null || url.length() < 40) {
            return "***";
        }
        return url.substring(0, 40) + "***";
    }

    private String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
