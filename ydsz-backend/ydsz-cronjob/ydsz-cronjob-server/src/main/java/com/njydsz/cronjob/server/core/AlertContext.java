package com.njydsz.cronjob.server.core.alert;

import java.io.Serializable;

/**
 * 告警上下文（P5 告警 + 监控）。
 *
 * <p>封装一次告警触发时的完整上下文信息，由触发点（如 Dispatcher、TimeoutMonitor）
 * 构造并传递给 {@link AlertDispatcher}。Dispatcher 根据 context 匹配告警规则、
 * 执行去重判断并调用 {@link com.njydsz.common.feign.NotificationClient} 派发通知。
 *
 * <p>使用 record 保证不可变性，避免多线程（@Async 监听器）下的可见性问题。
 *
 * <p>P3-1: 新增 {@code recovery} 标志，用于区分告警通知与恢复通知。
 * 恢复通知跳过冷却窗口检查，日志 status 带 {@code _RECOVERY} 后缀。
 *
 * @param alertType       告警类型
 * @param jobId           任务 ID（NULL 表示全局告警）
 * @param jobKey          任务 KEY（冗余，用于日志展示）
 * @param jobName         任务名称（用于告警文案）
 * @param triggerLogId    触发该告警的任务日志 ID（关联 ydsz_job_log.id）
 * @param triggerValue    触发时的实际值（如失败率 85.5、耗时 5000）
 * @param errorMessage    错误信息（任务失败时的异常摘要）
 * @param traceId         链路追踪 ID
 * @param tenantId        租户 ID
 * @param recovery        是否为恢复通知（true=恢复通知，false=正常告警）
 * @author ydsz-team
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
        String tenantId,
        boolean recovery
) implements Serializable {

    /**
     * 构造正常告警上下文（recovery=false）。
     *
     * @param alertType    告警类型
     * @param jobId        任务 ID（NULL 表示全局告警）
     * @param jobKey       任务 KEY
     * @param jobName      任务名称
     * @param triggerLogId 触发日志 ID
     * @param triggerValue 触发值
     * @param errorMessage 错误信息
     * @param traceId      链路追踪 ID
     * @param tenantId     租户 ID
     * @return 正常告警上下文
     */
    public static AlertContext of(AlertType alertType, String jobId, String jobKey, String jobName,
                                   String triggerLogId, String triggerValue, String errorMessage,
                                   String traceId, String tenantId) {
        return new AlertContext(alertType, jobId, jobKey, jobName, triggerLogId,
                triggerValue, errorMessage, traceId, tenantId, false);
    }

    /**
     * 构造恢复通知上下文（recovery=true）。
     *
     * <p>恢复通知用于告警条件解除时通知用户，例如任务从失败恢复为成功、
     * 慢任务恢复为正常耗时。Dispatcher 处理恢复通知时会跳过冷却窗口检查。
     *
     * @param alertType    告警类型
     * @param jobId        任务 ID（NULL 表示全局告警）
     * @param jobKey       任务 KEY
     * @param jobName      任务名称
     * @param triggerLogId 触发日志 ID
     * @param triggerValue 触发值（恢复时的当前值）
     * @param errorMessage 错误信息（恢复时通常为 null）
     * @param traceId      链路追踪 ID
     * @param tenantId     租户 ID
     * @return 恢复通知上下文
     */
    public static AlertContext recovery(AlertType alertType, String jobId, String jobKey, String jobName,
                                         String triggerLogId, String triggerValue, String errorMessage,
                                         String traceId, String tenantId) {
        return new AlertContext(alertType, jobId, jobKey, jobName, triggerLogId,
                triggerValue, errorMessage, traceId, tenantId, true);
    }
}
