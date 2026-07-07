package com.njydsz.pmis.cronjob.core.alert;

import java.io.Serializable;

/**
 * 告警上下文（P5 告警 + 监控）。
 *
 * <p>封装一次告警触发时的完整上下文信息，由触发点（如 Dispatcher、TimeoutMonitor）
 * 构造并传递给 {@link AlertDispatcher}。Dispatcher 根据 context 匹配告警规则、
 * 执行去重判断并调用 {@link AlertNotifier} 派发通知。
 *
 * <p>使用 record 保证不可变性，避免多线程（@Async 监听器）下的可见性问题。
 *
 * @param alertType       告警类型
 * @param jobId           任务 ID（NULL 表示全局告警）
 * @param jobKey          任务 KEY（冗余，用于日志展示）
 * @param jobName         任务名称（用于告警文案）
 * @param triggerLogId    触发该告警的任务日志 ID（关联 pmis_job_log.id）
 * @param triggerValue    触发时的实际值（如失败率 85.5、耗时 5000）
 * @param errorMessage    错误信息（任务失败时的异常摘要）
 * @param traceId         链路追踪 ID
 * @param tenantId        租户 ID
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public record AlertContext(
        AlertType alertType,
        String jobId,
        String jobKey,
        String jobName,
        String triggerLogId,
        String triggerValue,
        String errorMessage,
        String traceId,
        String tenantId
) implements Serializable {
}
