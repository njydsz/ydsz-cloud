package com.njydsz.pmis.cronjob.core.alert;

import com.alibaba.fastjson2.JSON;
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
 * 企业微信群机器人通知器（P5 告警 + 监控）。
 *
 * <p>通过企业微信群机器人 Webhook 推送 Markdown 消息到指定群。
 * Webhook URL 通过 {@code pmis.cronjob.alert.wecom.webhook-url} 配置。
 *
 * <p>企业微信机器人 API 文档：
 * <a href="https://developer.work.weixin.qq.com/document/path/91770">
 * https://developer.work.weixin.qq.com/document/path/91770</a>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "pmis.cronjob.alert.wecom", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnMissingBean(name = "weComAlertNotifier")
public class WeComAlertNotifier extends AbstractAlertNotifier {

    private final AlertProperties alertProperties;

    @Override
    public AlertChannel supportedChannel() {
        return AlertChannel.WECOM;
    }

    @Override
    public void notify(AlertContext context, JobAlertRuleDO rule, List<String> receivers) throws AlertSendException {
        AlertProperties.Wecom config = alertProperties.getWecom();
        if (config.getWebhookUrl() == null || config.getWebhookUrl().isBlank()) {
            log.warn("[WeCom] Webhook URL 未配置, 跳过: ruleId={}", rule.getId());
            throw new AlertSendException("企业微信 Webhook URL 未配置");
        }

        String title = buildTitle(context, rule);
        String content = buildContent(context, rule);

        Map<String, Object> payload = new HashMap<>();
        payload.put("msgtype", "markdown");
        Map<String, Object> markdown = new HashMap<>();
        markdown.put("content", "**" + title + "**\n\n" + content);
        payload.put("markdown", markdown);

        String jsonBody = JSON.toJSONString(payload);
        log.debug("[WeCom] 发送告警: ruleId={} title={}", rule.getId(), title);
        AlertHttpClient httpClient = new AlertHttpClient(alertProperties);
        String resp = httpClient.postJson(config.getWebhookUrl(), jsonBody, null);
        log.info("[WeCom] 告警发送成功: ruleId={} resp={}", rule.getId(), truncate(resp));
    }

    private String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
