package com.njydsz.pmis.cronjob.core.alert;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.njydsz.pmis.cronjob.entity.JobAlertLogDO;
import com.njydsz.pmis.cronjob.entity.JobAlertRuleDO;
import com.njydsz.pmis.cronjob.mapper.JobAlertLogMapper;
import com.njydsz.pmis.cronjob.mapper.JobAlertRuleMapper;
import com.njydsz.pmis.cronjob.metrics.CronjobMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告警派发器（P5 告警 + 监控）。
 *
 * <p>监听 {@link AlertEvent}，执行以下流程：
 * <ol>
 *   <li><b>冷却去重</b>：通过 CAS 更新 {@code pmis_job_alert_rule.last_alert_at}，
 *       仅当上次告警时间早于冷却窗口起点时才更新成功（分布式环境下保证同一规则不重复告警）</li>
 *   <li><b>通道路由</b>：解析规则配置的 channels JSON，按通道查找对应的 {@link AlertNotifier} Bean</li>
 *   <li><b>多通道派发</b>：逐个通道调用 notifier，单个通道失败不影响其他通道（status=PARTIAL）</li>
 *   <li><b>日志持久化</b>：将告警派发结果记录到 {@code pmis_job_alert_log}，便于审计与效果统计</li>
 * </ol>
 *
 * <p>使用 {@code @Async} 异步执行，避免阻塞任务执行主流程。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertDispatcher {

    private final JobAlertRuleMapper jobAlertRuleMapper;
    private final JobAlertLogMapper jobAlertLogMapper;
    private final ApplicationContext applicationContext;
    /** P6-2: Prometheus 指标收集器（可选注入，未配置时不记录指标） */
    private final ObjectProvider<CronjobMetrics> cronjobMetricsProvider;

    /** AlertChannel → AlertNotifier 缓存（懒加载，避免启动顺序问题） */
    private final Map<AlertChannel, AlertNotifier> notifierCache = new ConcurrentHashMap<>();

    /** 缓存是否已初始化（懒加载标记，避免每次都同步） */
    private volatile boolean notifierCacheInitialized = false;

    /**
     * 监听告警事件，异步派发通知。
     *
     * @param event 告警事件
     */
    @Async
    @EventListener
    public void onAlertEvent(AlertEvent event) {
        try {
            dispatch(event.context(), event.rule());
        } catch (Exception e) {
            log.error("[AlertDispatcher] 告警派发异常: ruleId={} jobId={} reason={}",
                    event.rule().getId(), event.context().jobId(), e.getMessage(), e);
        }
    }

    /**
     * 执行告警派发（同步入口，便于单元测试）。
     *
     * @param context 告警上下文
     * @param rule    匹配到的告警规则
     */
    void dispatch(AlertContext context, JobAlertRuleDO rule) {
        // 1. 冷却窗口去重：CAS 更新 last_alert_at
        if (!acquireAlertSlot(rule)) {
            log.info("[AlertDispatcher] 规则在冷却期内, 跳过本次告警: ruleId={} ruleName={} jobId={}",
                    rule.getId(), rule.getRuleName(), context.jobId());
            // P6-2: 记录告警指标（冷却跳过）
            recordAlertMetrics(rule.getAlertType(), "SKIPPED");
            return;
        }

        // 2. 解析通道与接收人
        List<AlertChannel> channels = parseChannels(rule.getChannels());
        List<String> receivers = parseReceivers(rule.getReceivers());

        if (channels.isEmpty()) {
            log.warn("[AlertDispatcher] 规则未配置有效通道, 跳过: ruleId={} ruleName={}",
                    rule.getId(), rule.getRuleName());
            // P6-2: 记录告警指标（无通道跳过）
            recordAlertMetrics(rule.getAlertType(), "SKIPPED");
            return;
        }

        // 3. 多通道派发（单通道失败不影响其他）
        List<String> failedChannels = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();
        for (AlertChannel channel : channels) {
            try {
                AlertNotifier notifier = getNotifier(channel);
                if (notifier == null) {
                    log.warn("[AlertDispatcher] 通道未注册 Notifier, 跳过: channel={}", channel);
                    failedChannels.add(channel.name() + "(未注册)");
                    continue;
                }
                notifier.notify(context, rule, receivers);
                log.info("[AlertDispatcher] 通道派发成功: channel={} ruleId={} jobId={}",
                        channel, rule.getId(), context.jobId());
            } catch (AlertSendException e) {
                log.warn("[AlertDispatcher] 通道派发失败: channel={} ruleId={} reason={}",
                        channel, rule.getId(), e.getMessage());
                failedChannels.add(channel.name());
                errorMessages.add(channel + ": " + e.getMessage());
            } catch (Exception e) {
                log.error("[AlertDispatcher] 通道派发异常: channel={} ruleId={}",
                        channel, rule.getId(), e);
                failedChannels.add(channel.name());
                errorMessages.add(channel + ": " + e.getClass().getSimpleName());
            }
        }

        // 4. 持久化告警日志
        String status = determineStatus(channels.size(), failedChannels.size());
        String errorMessage = errorMessages.isEmpty() ? null : String.join(" | ", errorMessages);
        persistAlertLog(context, rule, status, errorMessage);
        // P6-2: 记录告警指标
        recordAlertMetrics(rule.getAlertType(), status);

        log.info("[AlertDispatcher] 告警派发完成: ruleId={} ruleName={} channels={} failed={} status={}",
                rule.getId(), rule.getRuleName(), channels.size(), failedChannels.size(), status);
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
    private boolean acquireAlertSlot(JobAlertRuleDO rule) {
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
            JSONArray array = JSON.parseArray(channelsJson);
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
            JSONArray array = JSON.parseArray(receiversJson);
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
     * 懒加载获取通道对应的通知器（首次调用时从 Spring 容器加载全部 AlertNotifier Bean）。
     *
     * @param channel 通道
     * @return 通知器实例；未注册返回 null
     */
    private AlertNotifier getNotifier(AlertChannel channel) {
        if (!notifierCacheInitialized) {
            synchronized (this) {
                if (!notifierCacheInitialized) {
                    initNotifierCache();
                    notifierCacheInitialized = true;
                }
            }
        }
        return notifierCache.get(channel);
    }

    /**
     * 初始化通知器缓存：从 Spring 容器加载全部 AlertNotifier Bean，按 supportedChannel 分组。
     */
    private void initNotifierCache() {
        Map<String, AlertNotifier> beans = applicationContext.getBeansOfType(AlertNotifier.class);
        for (AlertNotifier notifier : beans.values()) {
            AlertChannel channel = notifier.supportedChannel();
            if (channel != null) {
                notifierCache.put(channel, notifier);
                log.info("[AlertDispatcher] 注册通知器: channel={} class={}",
                        channel, notifier.getClass().getSimpleName());
            }
        }
        log.info("[AlertDispatcher] 通知器缓存初始化完成: count={}", notifierCache.size());
    }

    /**
     * 根据失败通道数量确定告警状态。
     *
     * @param totalChannels   总通道数
     * @param failedChannels  失败通道数
     * @return SUCCESS 全部成功 / PARTIAL 部分成功 / FAILED 全部失败
     */
    private String determineStatus(int totalChannels, int failedChannels) {
        if (failedChannels == 0) {
            return "SUCCESS";
        }
        if (failedChannels >= totalChannels) {
            return "FAILED";
        }
        return "PARTIAL";
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
     * 持久化告警日志。
     */
    private void persistAlertLog(AlertContext context, JobAlertRuleDO rule,
                                  String status, String errorMessage) {
        try {
            JobAlertLogDO alertLog = new JobAlertLogDO();
            alertLog.setRuleId(rule.getId());
            alertLog.setRuleName(rule.getRuleName());
            alertLog.setJobId(context.jobId());
            alertLog.setJobKey(context.jobKey());
            alertLog.setAlertType(rule.getAlertType());
            alertLog.setAlertLevel(rule.getAlertLevel());
            alertLog.setTriggerValue(context.triggerValue());
            alertLog.setThreshold(rule.getThreshold());
            alertLog.setChannels(rule.getChannels());
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
}
