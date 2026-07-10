package com.njydsz.pmis.cronjob.core.alert;

import com.alibaba.fastjson2.JSON;
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
 * 邮件通知器（P5 告警 + 监控）。
 *
 * <p>邮件发送优先通过 message-service 转发 URL（HTTP API）实现，避免 cronjob 模块
 * 直接引入 spring-boot-starter-mail 与 SMTP 配置。若未配置 service-url，则记录
 * WARN 日志并将告警内容输出（开发期便于本地调试）。
 *
 * <p>邮件服务转发 API 约定：
 * <pre>
 * POST {service-url}
 * Content-Type: application/json
 *
 * {
 *   "from": "alert@njydsz.com",
 *   "to": ["user1@example.com", "user2@example.com"],
 *   "subject": "[PMIS 告警] [WARN] FAIL - 任务xxx",
 *   "content": "## 告警详情\n..."
 * }
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "pmis.cronjob.alert.email", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnMissingBean(name = "emailAlertNotifier")
public class EmailAlertNotifier extends AbstractAlertNotifier {

    private final AlertProperties alertProperties;

    @Override
    public AlertChannel supportedChannel() {
        return AlertChannel.EMAIL;
    }

    @Override
    public void notify(AlertContext context, JobAlertRuleDO rule, List<String> receivers) throws AlertSendException {
        AlertProperties.Email config = alertProperties.getEmail();
        if (receivers == null || receivers.isEmpty()) {
            log.warn("[Email] 接收人列表为空, 跳过: ruleId={}", rule.getId());
            throw new AlertSendException("邮件接收人列表为空");
        }

        String subject = config.getSubjectPrefix() + " " + buildTitle(context, rule);
        String content = buildContent(context, rule);

        if (config.getServiceUrl() == null || config.getServiceUrl().isBlank()) {
            // 本地开发模式：仅打印日志，不实际发送
            log.warn("[Email] 邮件服务 URL 未配置, 仅记录内容(本地开发模式): to={} subject={}",
                    receivers, subject);
            log.info("[Email] 邮件内容预览:\n{}", content);
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("from", config.getFrom());
        payload.put("to", receivers);
        payload.put("subject", subject);
        payload.put("content", content);
        payload.put("contentType", "text/markdown");

        String jsonBody = JSON.toJSONString(payload);
        log.debug("[Email] 发送告警邮件: ruleId={} to={} subject={}",
                rule.getId(), receivers, subject);
        AlertHttpClient httpClient = new AlertHttpClient(alertProperties);
        httpClient.postJson(config.getServiceUrl(), jsonBody, null);
        log.info("[Email] 告警邮件发送成功: ruleId={} to={}", rule.getId(), receivers);
    }
}
