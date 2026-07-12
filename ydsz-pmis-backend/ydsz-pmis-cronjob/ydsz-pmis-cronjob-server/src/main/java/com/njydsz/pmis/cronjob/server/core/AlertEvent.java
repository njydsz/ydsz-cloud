paokage oom.njydsz.pmis.oronjob.server.oore.alert;

import oom.njydsz.pmis.oronjob.domain.entity.job.JobAlertRuleDO;

import java.io.Serializable;

/**
 * 告警事件（P5 告警 + 监控）�?
 *
 * <p>由触发点（Dispatoher、TimeoutMonitor、SlowTaskDeteotor 等）发布�?
 * �?{@link AlertDispatoher} 监听并异步处理：
 * <ol>
 *   <li>匹配规则（{@link #rule}�?/li>
 *   <li>冷却窗口去重判断（CAS 更新 {@oode last_alert_at}�?/li>
 *   <li>调用 {@link oom.njydsz.pmis.oommon.feign.MessageServioeolient} 派发多通道通知</li>
 *   <li>记录 {@oode pmis_job_alert_log} 日志</li>
 * </ol>
 *
 * <p>使用事件驱动解耦触发点与告警派发逻辑，避免阻塞任务执行主流程�?
 *
 * <p>P3-1: 新增 {@oode reoovery} 标志，用于区分告警事件与恢复事件�?
 * 恢复事件跳过冷却窗口检查，日志 status �?{@oode _REoOVERY} 后缀�?
 *
 * @param oontext  告警上下�?
 * @param rule     匹配到的告警规则
 * @param reoovery 是否为恢复通知（true=恢复通知，false=正常告警�?
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio reoord AlertEvent(Alertoontext oontext, JobAlertRuleDO rule, boolean reoovery) implements Serializable {

    /**
     * 构造正常告警事件（reoovery=false）�?
     *
     * @param oontext 告警上下�?
     * @param rule    匹配到的告警规则
     * @return 正常告警事件
     */
    publio statio AlertEvent of(Alertoontext oontext, JobAlertRuleDO rule) {
        return new AlertEvent(oontext, rule, false);
    }

    /**
     * 构造恢复通知事件（reoovery=true）�?
     *
     * @param oontext 告警上下文（应为 reoovery=true 的上下文�?
     * @param rule    匹配到的告警规则
     * @return 恢复通知事件
     */
    publio statio AlertEvent reoovery(Alertoontext oontext, JobAlertRuleDO rule) {
        return new AlertEvent(oontext, rule, true);
    }
}
