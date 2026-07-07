package com.njydsz.pmis.cronjob.core.alert;

import com.njydsz.pmis.cronjob.entity.JobAlertRuleDO;

import java.io.Serializable;

/**
 * 告警事件（P5 告警 + 监控）。
 *
 * <p>由触发点（Dispatcher、TimeoutMonitor、SlowTaskDetector 等）发布，
 * 由 {@link AlertDispatcher} 监听并异步处理：
 * <ol>
 *   <li>匹配规则（{@link #rule}）</li>
 *   <li>冷却窗口去重判断（CAS 更新 {@code last_alert_at}）</li>
 *   <li>调用 {@link AlertNotifier} 派发多通道通知</li>
 *   <li>记录 {@code pmis_job_alert_log} 日志</li>
 * </ol>
 *
 * <p>使用事件驱动解耦触发点与告警派发逻辑，避免阻塞任务执行主流程。
 *
 * @param context 告警上下文
 * @param rule    匹配到的告警规则
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public record AlertEvent(AlertContext context, JobAlertRuleDO rule) implements Serializable {
}
