package com.njydsz.cronjob.server.core.alert;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.njydsz.common.event.model.StandardEventTypes;
import com.njydsz.common.event.service.OutboxService;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.tree.ArrayNode;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.feign.NotificationClient;
import com.njydsz.common.feign.dto.RealtimePushDTO;
import com.njydsz.cronjob.domain.entity.job.JobAlertLog;
import com.njydsz.cronjob.domain.entity.job.JobAlertRule;
import com.njydsz.cronjob.infra.mapper.job.JobAlertLogMapper;
import com.njydsz.cronjob.infra.mapper.job.JobAlertRuleMapper;
import com.njydsz.common.notify.helper.NotifyHelper;
import com.njydsz.cronjob.server.core.AlertSendException;
import com.njydsz.cronjob.server.metrics.CronjobMetrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 告警派发器（P5 告警 + 监控）。
 *
 * <p>监听 {@link AlertEvent}，执行以下流程：
 * <ol>
 *   <li><b>冷却去重</b>：通过 CAS 更新 {@code ydsz_job_alert_rule.last_alert_at}，
 *       仅当上次告警时间早于冷却窗口起点时才更新成功（分布式环境下保证同一规则不重复告警）</li>
 *   <li><b>通道路由</b>：解析规则配置的 channels JSON，逐通道构建 MessageRequest</li>
 *   <li><b>统一派发</b>：通过 NotificationClient Feign 委托到 message 模块，
 *       由 message 模块路由到具体通道实现，单个通道失败不影响其他通道（status=PARTIAL）</li>
 *   <li><b>日志持久化</b>：将告警派发结果记录到 {@code ydsz_job_alert_log}，便于审计与效果统计</li>
 *   <li><b>实时广播</b>：通过 NotificationClient Feign 广播告警到前端 WebSocket</li>
 * </ol>
 *
 * <p><b>P0-1-fix</b>：移除了原来发布 {@code UnifiedAlertEvent} 的逻辑。
 * 原实现既直接调用 {@code NotificationClient.sendMessage()} 发送告警消息，
 * 又发布 {@code UnifiedAlertEvent} 事件，而 {@code UnifiedAlertDispatcher} 消费该事件后
 * 会再次调用 {@code NotificationClient.sendMessage()}，导致同一告警被发送两次。
 * 现在改为直接调用 {@code NotificationClient.broadcast()} 实现实时广播，
 * 消息发送仅由本类执行一次。
 *
 * <p>使用 {@code @Async} 异步执行，避免阻塞任务执行主流程。
 *
 * <p>P3-1: 支持告警恢复通知。当 {@link AlertEvent#recovery()}=true 时：
 * <ul>
 *   <li>跳过冷却窗口检查（恢复通知不需要去重）</li>
 *   <li>持久化的日志 status 带 {@code _RECOVERY} 后缀（如 SUCCESS_RECOVERY）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertDispatcher {

    private final JobAlertRuleMapper jobAlertRuleMapper;
    private final JobAlertLogMapper jobAlertLogMapper;
    /** P6-2: Prometheus 指标收集器（可选注入，未配置时不记录指标） */
    private final ObjectProvider<CronjobMetrics> cronjobMetricsProvider;
    /** 统一通知客户端 */
    private final NotificationClient notificationClient;
    /** P0-9: 告警智能降噪管理器（可选注入，仅 ydsz.cronjob.alert-dedup.enabled=true 时启用） */
    private final ObjectProvider<AlertDedupManager> alertDedupManagerProvider;
    /** P2-10: common-notify 通知助手（可选注入，IM 渠道直推） */
    private final ObjectProvider<NotifyHelper> notifyHelperProvider;
    /** Outbox 事件服务（可选依赖，发布任务告警/执行结果领域事件） */
    private final ObjectProvider<OutboxService> outboxServiceProvider;

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 监听告警事件，异步派发通知。
     *
     * @param event 告警事件（recovery=true 时为恢复通知）
     */
    @Async
    @EventListener
    public void onAlertEvent(AlertEvent event) {
        try {
            dispatch(event.context(), event.rule());
            // 发布任务告警领域事件到 Outbox
            publishOutboxEvent(
                    event.recovery() ? StandardEventTypes.JOB_EXECUTION_SUCCESS : StandardEventTypes.JOB_EXECUTION_FAILED,
                    event.context().jobId(),
                    Map.of("ruleId", event.rule().getId(), "jobKey", event.context().jobKey(),
                            "recovery", event.recovery()));
        } catch (Exception e) {
            log.error("[AlertDispatcher] 告警派发异常: ruleId={} jobId={} recovery={} reason={}",
                    event.rule().getId(), event.context().jobId(), event.recovery(), e.getMessage(), e);
        }
    }

    /**
     * 执行告警派发（同步入口，便于单元测试）。
     *
     * <p>P3-1: 当 {@code context.recovery()}=true 时，跳过冷却窗口检查，
     * 且持久化的日志 status 带 {@code _RECOVERY} 后缀。
     *
     * @param context 告警上下文（recovery=true 表示恢复通知）
     * @param rule    匹配到的告警规则
     */
    void dispatch(AlertContext context, JobAlertRule rule) {
        boolean recovery = context.recovery();

        // 1. 冷却窗口去重：CAS 更新 last_alert_at（恢复通知跳过冷却）
        if (!recovery && !acquireAlertSlot(rule)) {
            log.info("[AlertDispatcher] 规则在冷却期内, 跳过本次告警: ruleId={} ruleName={} jobId={}",
                    rule.getId(), rule.getRuleName(), context.jobId());
            // P6-2: 记录告警指标（冷却跳过）
            recordAlertMetrics(rule.getAlertType(), "SKIPPED");
            return;
        }

        // P0-9: 智能降噪（恢复通知跳过 dedup，确保 resolved 通知必达）
        // 在冷却窗口通过后、通道解析前进行窗口聚合+频次升级判断
        // 未启用 alert-dedup 时降级放行原始通道
        String effectiveChannelsJson = rule.getChannels();
        boolean escalated = false;
        if (!recovery) {
            AlertDedupManager dedupManager = alertDedupManagerProvider.getIfAvailable();
            if (dedupManager != null) {
                AlertDedupManager.DedupDecision decision = dedupManager.checkAndDedup(
                        String.valueOf(rule.getId()),
                        context.jobId(),
                        rule.getAlertType(),
                        rule.getChannels());
                if (!decision.send()) {
                    log.info("[AlertDispatcher] 智能降噪抑制告警: ruleId={} alertType={} jobId={}",
                            rule.getId(), rule.getAlertType(), context.jobId());
                    recordAlertMetrics(rule.getAlertType(), "SUPPRESSED");
                    return;
                }
                // 升级时使用 dedup 合并后的通道（含追加的升级通道）
                if (decision.escalated() && decision.channels() != null
                        && !decision.channels().equals(rule.getChannels())) {
                    // 转换为 JSON 数组格式供 parseChannels 使用
                    effectiveChannelsJson = decisionChannelsToJson(decision.channels());
                    escalated = true;
                }
            }
        }

        // 2. 解析通道与接收人（使用 dedup 决策后的通道）
        List<AlertChannel> channels = parseChannels(effectiveChannelsJson);
        List<String> receivers = parseReceivers(rule.getReceivers());

        if (channels.isEmpty()) {
            log.warn("[AlertDispatcher] 规则未配置有效通道, 跳过: ruleId={} ruleName={} recovery={}",
                    rule.getId(), rule.getRuleName(), recovery);
            // P6-2: 记录告警指标（无通道跳过）
            recordAlertMetrics(rule.getAlertType(), "SKIPPED");
            return;
        }

        // 3. 多通道派发（单通道失败不影响其他）
        List<String> failedChannels = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();
        for (AlertChannel channel : channels) {
            try {
                sendViaMessageCenter(channel, context, rule, receivers);
                log.info("[AlertDispatcher] 通道派发成功: channel={} ruleId={} jobId={} recovery={} escalated={}",
                        channel, rule.getId(), context.jobId(), recovery, escalated);
            } catch (AlertSendException e) {
                log.warn("[AlertDispatcher] 通道派发失败: channel={} ruleId={} recovery={} reason={}",
                        channel, rule.getId(), recovery, e.getMessage());
                failedChannels.add(channel.name());
                errorMessages.add(channel + ": " + e.getMessage());
            } catch (Exception e) {
                log.error("[AlertDispatcher] 通道派发异常: channel={} ruleId={} recovery={}",
                        channel, rule.getId(), recovery, e);
                failedChannels.add(channel.name());
                errorMessages.add(channel + ": " + e.getClass().getSimpleName());
            }
        }

        // 4. 持久化告警日志（恢复通知 status 带 _RECOVERY 后缀）
        String status = determineStatus(channels.size(), failedChannels.size(), recovery);
        String errorMessage = errorMessages.isEmpty() ? null : String.join(" | ", errorMessages);
        persistAlertLog(context, rule, status, errorMessage);
        // P6-2: 记录告警指标
        recordAlertMetrics(rule.getAlertType(), status);

        log.info("[AlertDispatcher] 告警派发完成: ruleId={} ruleName={} channels={} failed={} status={} recovery={}",
                rule.getId(), rule.getRuleName(), channels.size(), failedChannels.size(), status, recovery);

        // P0-1-fix: 直接广播告警到前端（替代原来发布 UnifiedAlertEvent 导致的重复发送）
        // 通知实时广播，由前端 WebSocket 推送给在线用户。
        broadcastAlert(context, rule, recovery);
    }

    /**
     * 广播告警到前端 WebSocket（实时推送）。
     *
     * <p>推送失败时静默降级，不影响告警主流程。
     *
     * @param context  告警上下文
     * @param rule     告警规则
     * @param recovery 是否为恢复通知
     */
    private void broadcastAlert(AlertContext context, JobAlertRule rule, boolean recovery) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("alertCode", "CRONJOB-" + System.currentTimeMillis() + "-" + rule.getId());
            payload.put("alertType", rule.getAlertType());
            payload.put("alertLevel", rule.getAlertLevel());
            payload.put("title", buildTitle(context, rule));
            payload.put("content", buildContent(context, rule));
            payload.put("sourceModule", "cronjob");
            payload.put("sourceId", context.jobId());
            payload.put("recovery", recovery);
            payload.put("traceId", context.traceId());
            notificationClient.broadcast("ALERT", new RealtimePushDTO(payload));
        } catch (Exception e) {
            log.debug("[AlertDispatcher] 实时广播降级忽略: ruleId={} err={}",
                    rule.getId(), e.getMessage());
        }
    }

    /**
     * CAS 更新 last_alert_at（冷却窗口去重）。
     *
     * <p>分布式环境下多个触发点可能同时尝试告警，通过 SQL 层面的 CAS：
     * {@code UPDATE ... WHERE last_alert_at IS NULL OR last_alert_at < cooldownBefore}
     * 保证仅有一个节点能成功更新，从而实现分布式去重。
     *
     * @param rule 告警规则
     * @return true 表示可以告警（更新成功）；false 表示在冷却期内（更新失败）
     */
    private boolean acquireAlertSlot(JobAlertRule rule) {
        int cooldownMinutes = rule.getCooldownMinutes() != null ? rule.getCooldownMinutes() : 0;
        if (cooldownMinutes <= 0) {
            // 无冷却时间，直接放行
            return true;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cooldownBefore = now.minusMinutes(cooldownMinutes);
        int updated = jobAlertRuleMapper.updateLastAlertAtIfNotInCooldown(
                rule.getId(), now, cooldownBefore);
        return updated > 0;
    }

    /**
     * 解析规则配置的通道 JSON 数组。
     *
     * @param channelsJson 通道 JSON 字符串（如 {@code ["EMAIL","DINGTALK"]}）
     * @return 解析后的通道列表；解析失败返回空列表
     */
    private List<AlertChannel> parseChannels(String channelsJson) {
        if (channelsJson == null || channelsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            ArrayNode array = YdszJson.parseArrayNode(channelsJson);
            List<AlertChannel> channels = new ArrayList<>(array.size());
            for (int i = 0; i < array.size(); i++) {
                AlertChannel channel = AlertChannel.parse(array.getString(i));
                if (channel != null) {
                    channels.add(channel);
                }
            }
            return channels;
        } catch (Exception e) {
            log.warn("[AlertDispatcher] 解析通道 JSON 失败: channels={} reason={}",
                    channelsJson, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 解析规则配置的接收人 JSON 数组。
     *
     * @param receiversJson 接收人 JSON 字符串
     * @return 接收人列表；解析失败或为空返回空列表
     */
    private List<String> parseReceivers(String receiversJson) {
        if (receiversJson == null || receiversJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            ArrayNode array = YdszJson.parseArrayNode(receiversJson);
            List<String> receivers = new ArrayList<>(array.size());
            for (int i = 0; i < array.size(); i++) {
                String receiver = array.getString(i);
                if (receiver != null && !receiver.isBlank()) {
                    receivers.add(receiver.trim());
                }
            }
            return receivers;
        } catch (Exception e) {
            log.warn("[AlertDispatcher] 解析接收人 JSON 失败: receivers={} reason={}",
                    receiversJson, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Dispatch alert via message module using Feign.
     * <p>Builds MessageRequest and calls NotificationClient.sendMessage(),
     * message module routes to specific channel implementation.
     */
    private void sendViaMessageCenter(AlertChannel channel, AlertContext context,
                                          JobAlertRule rule, List<String> receivers) {
        String title = buildTitle(context, rule);
        String content = buildContent(context, rule);
        MessageRequest request = new MessageRequest();
        request.setChannel(channel.name());
        request.setSubject(title);
        request.setContent(content);
        request.setBizType("CRONJOB_ALERT");
        request.setBizId(String.valueOf(rule.getId()));
        request.setReceiver(receivers.isEmpty() ? null : String.join(",", receivers));
        Map<String, Object> params = new HashMap<>();
        params.put("ruleId", rule.getId());
        params.put("ruleName", rule.getRuleName());
        params.put("alertType", rule.getAlertType());
        params.put("alertLevel", rule.getAlertLevel());
        params.put("jobId", context.jobId());
        params.put("jobKey", context.jobKey());
        params.put("jobName", context.jobName());
        params.put("triggerValue", context.triggerValue());
        params.put("threshold", rule.getThreshold());
        params.put("errorMessage", context.errorMessage());
        params.put("traceId", context.traceId());
        params.put("triggerLogId", context.triggerLogId());
        params.put("tenantId", context.tenantId());
        params.put("recovery", context.recovery());
        params.put("receivers", receivers);
        request.setParams(params);
        try {
            BaseResponse<MessageResult> result = notificationClient.sendMessage(request);
            if (result == null || !result.isSuccess()) {
                String reason = result != null && result.getMsg() != null
                         ? result.getMsg() : "unknown";
                throw new AlertSendException("message module returned failure: " + reason);
            }
            MessageResult msgResult = result.getData();
            if (msgResult != null && !msgResult.isSuccess()) {
                throw new AlertSendException(
                         msgResult.getErrorMessage() != null ? msgResult.getErrorMessage() : "send failed");
            }
        } catch (AlertSendException e) {
            // P2-10: 主渠道失败时，尝试通过 common-notify IM 渠道直推
            tryNotifyViaHelper(context, receivers);
            throw e;
        } catch (Exception e) {
            throw new AlertSendException("Feign call error: " + e.getMessage(), e);
        }
        // P2-10: 主渠道成功后，补充发送 IM 通知（非阻塞，失败不影响主流程）
        tryNotifyViaHelper(context, receivers);
    }

    /**
     * P2-10: 通过 NotifyHelper 发送 IM 通知（非阻塞，失败静默跳过）。
     */
    private void tryNotifyViaHelper(AlertContext context, List<String> receivers) {
        try {
            NotifyHelper helper = notifyHelperProvider.getIfAvailable();
            if (helper == null) {
                return;
            }
            String title = context.recovery() ? "任务恢复通知" : "任务告警通知";
            String content = String.format("任务: %s (%s)\n错误: %s\n日志ID: %s",
                    context.jobName() != null ? context.jobName() : context.jobKey(),
                    context.jobKey(),
                    context.errorMessage(),
                    context.triggerLogId());
            helper.sendInAppBatch(receivers, title, content);
        } catch (Exception e) {
            log.debug("[AlertDispatcher] IM 通知发送失败(非阻塞): jobKey={} reason={}",
                    context.jobKey(), e.getMessage());
        }
    }
    private String buildTitle(AlertContext context, JobAlertRule rule) {
        String prefix = context.recovery() ? "[recovery] " : "";
        return String.format("%s[%s] %s - %s",
                prefix,
                rule.getAlertLevel(),
                rule.getAlertType(),
                context.jobName() != null ? context.jobName()
                        : (context.jobKey() != null ? context.jobKey() : "global"));
    }
    private String buildContent(AlertContext context, JobAlertRule rule) {
        StringBuilder sb = new StringBuilder();
        sb.append(context.recovery() ? "## Alert Recovery\n\n" : "## Alert Details\n\n");
        sb.append("| Field | Value |\n|------|----|\n");
        sb.append("| Rule | ").append(rule.getRuleName()).append(" |\n");
        sb.append("| Type | ").append(rule.getAlertType()).append(" |\n");
        sb.append("| Level | ").append(rule.getAlertLevel()).append(" |\n");
        if (context.jobKey() != null) {
            sb.append("| Job Key | ").append(context.jobKey()).append(" |\n");
        }
        if (context.jobName() != null) {
            sb.append("| Job Name | ").append(context.jobName()).append(" |\n");
        }
        if (context.triggerValue() != null) {
            sb.append("| Trigger Value | ").append(context.triggerValue()).append(" |\n");
        }
        if (rule.getThreshold() != null) {
            sb.append("| Threshold | ").append(rule.getThreshold()).append(" |\n");
        }
        if (context.errorMessage() != null) {
            sb.append("| Error | ").append(escapeMarkdown(context.errorMessage())).append(" |\n");
        }
        if (context.triggerLogId() != null) {
            sb.append("| Log ID | ").append(context.triggerLogId()).append(" |\n");
        }
        if (context.traceId() != null) {
            sb.append("| Trace ID | ").append(context.traceId()).append(" |\n");
        }
        sb.append("| Time | ").append(LocalDateTime.now().format(TIME_FORMATTER)).append(" |\n");
        return sb.toString();
    }

    private String escapeMarkdown(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("|", "\\|").replace("\n", " ");
    }

    /**
     * 根据失败通道数量确定告警状态。
     *
     * <p>P3-1: 恢复通知的 status 带 {@code _RECOVERY} 后缀（如 SUCCESS_RECOVERY），
     * 便于在告警日志中区分告警与恢复记录。
     *
     * @param totalChannels   总通道数
     * @param failedChannels  失败通道数
     * @param recovery        是否为恢复通知
     * @return SUCCESS/PARTIAL/FAILED（恢复通知带 _RECOVERY 后缀）
     */
    private String determineStatus(int totalChannels, int failedChannels, boolean recovery) {
        String base;
        if (failedChannels == 0) {
            base = "SUCCESS";
        } else if (failedChannels >= totalChannels) {
            base = "FAILED";
        } else {
            base = "PARTIAL";
        }
        return recovery ? base + "_RECOVERY" : base;
    }

    /**
     * P6-2: 记录告警派发指标。
     *
     * <p>使用 try-catch 包裹，确保指标记录失败不影响主流程。
     *
     * @param alertType 告警类型
     * @param status    派发结果
     */
    private void recordAlertMetrics(String alertType, String status) {
        CronjobMetrics metrics = cronjobMetricsProvider.getIfAvailable();
        if (metrics == null) {
            return;
        }
        try {
            metrics.incAlertDispatched(alertType, status);
        } catch (Exception e) {
            log.debug("[AlertDispatcher] 指标记录失败(不影响主流程): reason={}", e.getMessage());
        }
    }

    /**
     * 持久化告警日志（P3-1-merge: 写入 ydsz_alert_dispatch 表）。
     */
    private void persistAlertLog(AlertContext context, JobAlertRule rule,
                                  String status, String errorMessage) {
        try {
            JobAlertLog alertLog = new JobAlertLog();
            // P3-1-merge: 生成唯一 alert_code
            alertLog.setAlertCode("CRONJOB-" + System.currentTimeMillis() + "-" + rule.getId());
            // P3-1-merge: 标记来源为 CRONJOB
            alertLog.setSourceType("CRONJOB");
            alertLog.setRuleId(rule.getId());
            alertLog.setRuleName(rule.getRuleName());
            alertLog.setJobId(context.jobId());
            alertLog.setJobKey(context.jobKey());
            alertLog.setAlertType(rule.getAlertType());
            alertLog.setAlertLevel(rule.getAlertLevel());
            alertLog.setTriggerValue(context.triggerValue());
            alertLog.setThreshold(rule.getThreshold());
            // P3-1-merge: channels 从 JSON 数组转为逗号分隔
            alertLog.setChannels(convertChannelsToCsv(rule.getChannels()));
            alertLog.setStatus(status);
            alertLog.setErrorMessage(errorMessage);
            alertLog.setTraceId(context.traceId());
            alertLog.setTriggerLogId(context.triggerLogId());
            alertLog.setTenantId(context.tenantId());
            alertLog.setCreatedAt(LocalDateTime.now());
            alertLog.setDeleted(0);
            jobAlertLogMapper.insert(alertLog);
        } catch (Exception e) {
            // 日志写入失败不影响告警主流程
            log.error("[AlertDispatcher] 告警日志写入失败: ruleId={} jobId={} reason={}",
                    rule.getId(), context.jobId(), e.getMessage(), e);
        }
    }

    /**
     * P3-1-merge: 将 JSON 数组通道格式转换为逗号分隔格式。
     * 如 ["EMAIL","DINGTALK"] → EMAIL,DINGTALK
     */
    private String convertChannelsToCsv(String channelsJson) {
        if (channelsJson == null || channelsJson.isBlank()) {
            return "INAPP";
        }
        try {
            ArrayNode array = YdszJson.parseArrayNode(channelsJson);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < array.size(); i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append(array.getString(i));
            }
            return sb.length() > 0 ? sb.toString() : "INAPP";
        } catch (Exception e) {
            return "INAPP";
        }
    }

    /**
     * P0-9: 将 dedup 决策的逗号分隔通道转换为 JSON 数组字符串。
     * 反向操作 convertChannelsToCsv，便于复用 parseChannels。
     *
     * @param channelsCsv 逗号分隔通道，如 "EMAIL,DINGTALK,SMS"
     * @return JSON 数组字符串，如 ["EMAIL","DINGTALK","SMS"]
     */
    private String decisionChannelsToJson(String channelsCsv) {
        if (channelsCsv == null || channelsCsv.isBlank()) {
            return "[]";
        }
        String[] parts = channelsCsv.split(",");
        ArrayNode array = new ArrayNode();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                array.add(trimmed);
            }
        }
        return YdszJson.toJson(array);
    }

    /**
     * 发布领域事件到 Outbox（可选依赖，不存在时安全降级）
     */
    private void publishOutboxEvent(String eventType, String aggregateId, Object payload) {
        OutboxService outboxService = outboxServiceProvider.getIfAvailable();
        if (outboxService == null) {
            return;
        }
        try {
            outboxService.appendToOutbox("Job", aggregateId, eventType,
                    YdszJson.toJson(payload));
        } catch (Exception e) {
            log.warn("[AlertDispatcher] Failed to publish outbox event: type={}, id={}, error={}",
                    eventType, aggregateId, e.getMessage());
        }
    }
}
