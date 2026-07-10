package com.njydsz.pmis.cronjob.core.alert;

import com.njydsz.pmis.cronjob.entity.job.JobAlertRuleDO;

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
 * <p>P3-1: 新增 {@code recovery} 标志，用于区分告警事件与恢复事件。
 * 恢复事件跳过冷却窗口检查，日志 status 带 {@code _RECOVERY} 后缀。
 *
 * @param context  告警上下文
 * @param rule     匹配到的告警规则
 * @param recovery 是否为恢复通知（true=恢复通知，false=正常告警）
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public record AlertEvent(AlertContext context, JobAlertRuleDO rule, boolean recovery) implements Serializable {

    /**
     * 构造正常告警事件（recovery=false）。
     *
     * @param context 告警上下文
     * @param rule    匹配到的告警规则
     * @return 正常告警事件
     */
    public static AlertEvent of(AlertContext context, JobAlertRuleDO rule) {
        return new AlertEvent(context, rule, false);
    }

    /**
     * 构造恢复通知事件（recovery=true）。
     *
     * @param context 告警上下文（应为 recovery=true 的上下文）
     * @param rule    匹配到的告警规则
     * @return 恢复通知事件
     */
    public static AlertEvent recovery(AlertContext context, JobAlertRuleDO rule) {
        return new AlertEvent(context, rule, true);
    }
}
