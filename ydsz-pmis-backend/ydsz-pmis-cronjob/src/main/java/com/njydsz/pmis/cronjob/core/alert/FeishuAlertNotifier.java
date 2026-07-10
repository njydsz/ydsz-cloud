package com.njydsz.pmis.cronjob.core.alert;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.cronjob.config.AlertProperties;
import com.njydsz.pmis.cronjob.entity.job.JobAlertRuleDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 飞书群机器人通知器（P1-5 告警渠道扩展）。
 *
 * <p>通过飞书自定义机器人 Webhook 推送 interactive card 消息到指定群。
 * Webhook URL 通过 {@code pmis.cronjob.alert.feishu.webhook-url} 配置。
 *
 * <p>飞书机器人 API 文档：
 * <a href="https://open.feishu.cn/document/client-docs/bot-v3/add-custom-bot">
 * https://open.feishu.cn/document/client-docs/bot-v3/add-custom-bot</a>
 *
 * <p>异常处理：发送失败仅记录 WARN 日志，不抛出异常（不影响其他通道通知）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "pmis.cronjob.alert.feishu", name = "enabled", havingValue = "true")
@ConditionalOnMissingBean(name = "feishuAlertNotifier")
public class FeishuAlertNotifier extends AbstractAlertNotifier {

    private final AlertProperties alertProperties;
    private final RestTemplate restTemplate;

    /**
     * 生产构造：从 {@link AlertProperties} 读取配置并构建 RestTemplate。
     *
     * @param alertProperties 告警配置
     */
    public FeishuAlertNotifier(AlertProperties alertProperties) {
        this.alertProperties = alertProperties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = alertProperties.getHttpTimeout() != null
                ? alertProperties.getHttpTimeout()
                : Duration.ofSeconds(5);
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 测试构造：注入自定义 RestTemplate（便于 mock）。
     *
     * @param alertProperties 告警配置
     * @param restTemplate    RestTemplate（测试可 mock）
     */
    FeishuAlertNotifier(AlertProperties alertProperties, RestTemplate restTemplate) {
        this.alertProperties = alertProperties;
        this.restTemplate = restTemplate;
    }

    @Override
    public AlertChannel supportedChannel() {
        return AlertChannel.FEISHU;
    }

    @Override
    public void notify(AlertContext context, JobAlertRuleDO rule, List<String> receivers) throws AlertSendException {
        AlertProperties.Feishu config = alertProperties.getFeishu();
        if (!config.isEnabled()) {
            log.debug("[Feishu] 通道未启用, 跳过: ruleId={}", rule.getId());
            return;
        }
        if (config.getWebhookUrl() == null || config.getWebhookUrl().isBlank()) {
            log.warn("[Feishu] Webhook URL 未配置, 跳过: ruleId={}", rule.getId());
            return;
        }

        String title = buildTitle(context, rule);
        String content = buildContent(context, rule);
        String jsonBody = buildCardPayload(title, content);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);

        log.debug("[Feishu] 发送告警: ruleId={} title={}", rule.getId(), title);
        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(
                    config.getWebhookUrl(), request, String.class);
            log.info("[Feishu] 告警发送成功: ruleId={} resp={}", rule.getId(), truncate(resp.getBody()));
        } catch (Exception e) {
            // 发送失败仅记录日志，不抛出异常，不影响其他通道通知
            log.warn("[Feishu] 告警发送失败: ruleId={} reason={}", rule.getId(), e.getMessage());
        }
    }

    /**
     * 构建飞书 interactive card 消息体。
     *
     * @param title   告警标题
     * @param content 告警内容（lark_md 格式）
     * @return JSON 字符串
     */
    private String buildCardPayload(String title, String content) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("msg_type", "interactive");

        Map<String, Object> card = new HashMap<>();

        // 卡片头部
        Map<String, Object> header = new HashMap<>();
        Map<String, Object> headerTitle = new HashMap<>();
        headerTitle.put("tag", "plain_text");
        headerTitle.put("content", title);
        header.put("title", headerTitle);
        header.put("template", "red");
        card.put("header", header);

        // 卡片内容区
        List<Map<String, Object>> elements = new ArrayList<>();
        Map<String, Object> div = new HashMap<>();
        Map<String, Object> divText = new HashMap<>();
        divText.put("tag", "lark_md");
        divText.put("content", content);
        div.put("tag", "div");
        div.put("text", divText);
        elements.add(div);
        card.put("elements", elements);

        payload.put("card", card);
        return JSON.toJSONString(payload);
    }

    private String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
