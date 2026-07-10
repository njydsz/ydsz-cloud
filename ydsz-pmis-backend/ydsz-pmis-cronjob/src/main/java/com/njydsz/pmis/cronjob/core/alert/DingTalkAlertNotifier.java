package com.njydsz.pmis.cronjob.core.alert;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.njydsz.pmis.cronjob.config.AlertProperties;
import com.njydsz.pmis.cronjob.entity.job.JobAlertRuleDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 钉钉群机器人通知器（P5 告警 + 监控）。
 *
 * <p>通过钉钉自定义机器人 Webhook 推送 Markdown 消息到指定群。
 * Webhook URL 通过 {@code pmis.cronjob.alert.dingtalk.webhook-url} 配置。
 *
 * <p>钉钉机器人 API 文档：
 * <a href="https://open.dingtalk.com/document/robots/custom-robot-access">
 * https://open.dingtalk.com/document/robots/custom-robot-access</a>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "pmis.cronjob.alert.dingtalk", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnMissingBean(name = "dingTalkAlertNotifier")
public class DingTalkAlertNotifier extends AbstractAlertNotifier {

    private final AlertProperties alertProperties;

    @Override
    public AlertChannel supportedChannel() {
        return AlertChannel.DINGTALK;
    }

    @Override
    public void notify(AlertContext context, JobAlertRuleDO rule, List<String> receivers) throws AlertSendException {
        AlertProperties.Dingtalk config = alertProperties.getDingtalk();
        if (config.getWebhookUrl() == null || config.getWebhookUrl().isBlank()) {
            log.warn("[DingTalk] Webhook URL 未配置, 跳过: ruleId={}", rule.getId());
            throw new AlertSendException("钉钉 Webhook URL 未配置");
        }

        String title = buildTitle(context, rule);
        String content = buildContent(context, rule);
        // @ 指定接收人（钉钉手机号）
        String atJson = buildAtPayload(receivers);

        Map<String, Object> payload = new HashMap<>();
        payload.put("msgtype", "markdown");
        Map<String, Object> markdown = new HashMap<>();
        markdown.put("title", title);
        markdown.put("text", content + (atJson.isEmpty() ? "" : "\n\n" + atJson));
        payload.put("markdown", markdown);
        payload.put("at", buildAtField(receivers));

        String jsonBody = JSON.toJSONString(payload);
        log.debug("[DingTalk] 发送告警: ruleId={} title={} url={}",
                rule.getId(), title, maskUrl(config.getWebhookUrl()));
        AlertHttpClient httpClient = new AlertHttpClient(alertProperties);
        String resp = httpClient.postJson(config.getWebhookUrl(), jsonBody, null);
        log.info("[DingTalk] 告警发送成功: ruleId={} resp={}", rule.getId(), truncate(resp));
    }

    /**
     * 构建 @ 接收人字段（钉钉手机号列表）。
     */
    private Map<String, Object> buildAtField(List<String> receivers) {
        Map<String, Object> at = new HashMap<>();
        if (receivers == null || receivers.isEmpty()) {
            at.put("isAtAll", false);
            at.put("atMobiles", new JSONArray());
        } else {
            at.put("isAtAll", false);
            at.put("atMobiles", receivers);
        }
        return at;
    }

    /**
     * 构建 @ 文本（用于 Markdown 内容）。
     */
    private String buildAtPayload(List<String> receivers) {
        if (receivers == null || receivers.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String r : receivers) {
            sb.append("@").append(r).append(" ");
        }
        return sb.toString().trim();
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
