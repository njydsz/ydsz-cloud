package com.njydsz.pmis.cronjob.core.alert;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.cronjob.config.AlertProperties;
import com.njydsz.pmis.cronjob.entity.JobAlertRuleDO;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 短信通知器（P1-5 告警渠道扩展）。
 *
 * <p>简化实现：通过配置的 HTTP Webhook URL 转发短信通知（POST JSON），
 * 由业务侧（如 message-service）调用阿里云/腾讯云短信 API 实际发送，
 * 避免 cronjob 模块直接依赖短信 SDK。
 *
 * <p>请求体格式：
 * <pre>
 * POST {webhook-url}
 * Content-Type: application/json
 *
 * {
 *   "phoneNumbers": "13800000000,13900000000",
 *   "title": "[ERROR] FAIL - 任务xxx",
 *   "content": "## 告警详情\n..."
 * }
 * </pre>
 *
 * <p>手机号来源优先级：规则 receivers > 配置 phone-numbers。
 *
 * <p>异常处理：发送失败仅记录 WARN 日志，不抛出异常（不影响其他通道通知）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "pmis.cronjob.alert.sms", name = "enabled", havingValue = "true")
@ConditionalOnMissingBean(name = "smsAlertNotifier")
public class SmsAlertNotifier extends AbstractAlertNotifier {

    private final AlertProperties alertProperties;
    private final RestTemplate restTemplate;

    /**
     * 生产构造：从 {@link AlertProperties} 读取配置并构建 RestTemplate。
     *
     * @param alertProperties 告警配置
     */
    public SmsAlertNotifier(AlertProperties alertProperties) {
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
    SmsAlertNotifier(AlertProperties alertProperties, RestTemplate restTemplate) {
        this.alertProperties = alertProperties;
        this.restTemplate = restTemplate;
    }

    @Override
    public AlertChannel supportedChannel() {
        return AlertChannel.SMS;
    }

    @Override
    public void notify(AlertContext context, JobAlertRuleDO rule, List<String> receivers) throws AlertSendException {
        AlertProperties.Sms config = alertProperties.getSms();
        if (!config.isEnabled()) {
            log.debug("[SMS] 通道未启用, 跳过: ruleId={}", rule.getId());
            return;
        }
        if (config.getWebhookUrl() == null || config.getWebhookUrl().isBlank()) {
            log.warn("[SMS] Webhook URL 未配置, 跳过: ruleId={}", rule.getId());
            return;
        }

        // 手机号优先取 receivers，其次取配置的 phone-numbers
        String phoneNumbers = resolvePhoneNumbers(receivers, config.getPhoneNumbers());
        if (phoneNumbers == null || phoneNumbers.isBlank()) {
            log.warn("[SMS] 手机号列表为空, 跳过: ruleId={}", rule.getId());
            return;
        }

        String title = buildTitle(context, rule);
        String content = buildContent(context, rule);

        Map<String, Object> payload = new HashMap<>();
        payload.put("phoneNumbers", phoneNumbers);
        payload.put("title", title);
        payload.put("content", content);
        payload.put("ruleId", rule.getId());
        payload.put("ruleName", rule.getRuleName());
        payload.put("alertType", rule.getAlertType());
        payload.put("alertLevel", rule.getAlertLevel());
        payload.put("jobId", context.jobId());
        payload.put("jobKey", context.jobKey());
        payload.put("jobName", context.jobName());
        payload.put("triggerValue", context.triggerValue());
        payload.put("threshold", rule.getThreshold());
        payload.put("errorMessage", context.errorMessage());
        payload.put("traceId", context.traceId());
        payload.put("triggerLogId", context.triggerLogId());
        payload.put("tenantId", context.tenantId());

        String jsonBody = JSON.toJSONString(payload);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);

        log.debug("[SMS] 发送告警短信: ruleId={} phones={}", rule.getId(), maskPhones(phoneNumbers));
        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(
                    config.getWebhookUrl(), request, String.class);
            log.info("[SMS] 告警短信发送成功: ruleId={} resp={}", rule.getId(), truncate(resp.getBody()));
        } catch (Exception e) {
            // 发送失败仅记录日志，不抛出异常，不影响其他通道通知
            log.warn("[SMS] 告警短信发送失败: ruleId={} reason={}", rule.getId(), e.getMessage());
        }
    }

    /**
     * 解析手机号列表：优先使用 receivers，其次使用配置的 phone-numbers。
     *
     * @param receivers           规则接收人列表
     * @param configPhoneNumbers  配置的默认手机号（逗号分隔）
     * @return 逗号分隔的手机号字符串
     */
    private String resolvePhoneNumbers(List<String> receivers, String configPhoneNumbers) {
        if (receivers != null && !receivers.isEmpty()) {
            return String.join(",", receivers);
        }
        return configPhoneNumbers;
    }

    /**
     * 脱敏手机号（保留前 3 后 4）。
     */
    private String maskPhones(String phones) {
        if (phones == null || phones.isBlank()) {
            return "";
        }
        String[] parts = phones.split(",");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            String p = parts[i].trim();
            if (p.length() > 7) {
                sb.append(p, 0, 3).append("****").append(p.substring(p.length() - 4));
            } else {
                sb.append("***");
            }
        }
        return sb.toString();
    }

    private String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
