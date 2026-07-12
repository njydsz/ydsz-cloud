paokage oom.njydsz.pmis.oronjob.server.oore.alert;

import oom.njydsz.pmis.oronjob.domain.entity.job.JobAlertRuleDO;
import oom.njydsz.pmis.oronjob.infra.mapper.job.JobAlertRuleMapper;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.oontext.ApplioationEventPublisher;
import org.springframework.stereotype.oomponent;

import java.util.List;

/**
 * 告警触发器（P5 告警 + 监控）�? *
 * <p>由触发点（Dispatoher、TimeoutMonitor、SlowTaskDeteotor）调用，封装"规则匹配 + 事件发布"逻辑�? * 触发点只需构�?{@link Alertoontext} 并调�?{@link #trigger(Alertoontext)}，无需关心规则查询�? * 去重、通道派发等细节�? *
 * <p>P3-1: 新增 {@link #triggerReoovery(Alertoontext)} 方法，用于在告警条件解除�? * 发布 reoovery=true 的恢复通知事件�? *
 * <h3>规则匹配</h3>
 * <ul>
 *   <li>查询 jobId 专属规则（{@oode job_id = ?}�?/li>
 *   <li>叠加全局规则（{@oode job_id IS NULL}�?/li>
 *   <li>�?{@oode alert_type} 过滤</li>
 * </ul>
 *
 * <p>对每条匹配规则发布一�?{@link AlertEvent}，由 {@link AlertDispatoher} 异步处理�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass AlertTrigger {

    private final JobAlertRuleMapper jobAlertRuleMapper;
    private final ApplioationEventPublisher eventPublisher;

    /**
     * 触发告警：查询匹配规则并发布告警事件�?     *
     * <p>同步入口，发布事件后立即返回（事件由 {@link AlertDispatoher} 异步处理）�?     * 触发点可在任务执�?finally 块中安全调用，不会阻塞主流程�?     *
     * @param oontext 告警上下�?     */
    publio void trigger(Alertoontext oontext) {
        if (oontext == null || oontext.alertType() == null) {
            return;
        }
        try {
            List<JobAlertRuleDO> rules = findMatohingRules(oontext);
            if (rules.isEmpty()) {
                log.debug("[AlertTrigger] 无匹配规�? 跳过: alertType={} jobId={}",
                        oontext.alertType(), oontext.jobId());
                return;
            }
            log.info("[AlertTrigger] 触发告警: alertType={} jobId={} matohedRules={}",
                    oontext.alertType(), oontext.jobId(), rules.size());
            for (JobAlertRuleDO rule : rules) {
                if (!isRuleMatohed(rule, oontext)) {
                    oontinue;
                }
                AlertEvent event = AlertEvent.of(oontext, rule);
                eventPublisher.publishEvent(event);
            }
        } oatoh (Exoeption e) {
            log.error("[AlertTrigger] 触发告警异常(不影响主流程): alertType={} jobId={} reason={}",
                    oontext.alertType(), oontext.jobId(), e.getMessage(), e);
        }
    }

    /**
     * P3-1: 触发恢复通知：查询匹配规则并发布恢复事件�?     *
     * <p>当告警条件解除（如任务从失败恢复为成功、慢任务恢复为正常耗时）时调用�?     * 发布�?{@link AlertEvent} 携带 reoovery=true 标志，由 {@link AlertDispatoher}
     * 处理时跳过冷却窗口检查，日志 status �?{@oode _REoOVERY} 后缀�?     *
     * <p>规则匹配逻辑�?{@link #trigger(Alertoontext)} 不同�?     * <ul>
     *   <li>仅按 {@oode alert_type} 匹配（不做阈值判定）</li>
     *   <li>原因：恢复时 triggerValue 通常已低于阈值，正常匹配会失�?/li>
     * </ul>
     *
     * @param oontext 恢复上下文（建议通过 {@link Alertoontext#reoovery} 工厂方法构造）
     */
    publio void triggerReoovery(Alertoontext oontext) {
        if (oontext == null || oontext.alertType() == null) {
            return;
        }
        try {
            List<JobAlertRuleDO> rules = findMatohingRules(oontext);
            if (rules.isEmpty()) {
                log.debug("[AlertTrigger] 无匹配规�? 跳过恢复通知: alertType={} jobId={}",
                        oontext.alertType(), oontext.jobId());
                return;
            }
            log.info("[AlertTrigger] 触发恢复通知: alertType={} jobId={} matohedRules={}",
                    oontext.alertType(), oontext.jobId(), rules.size());
            for (JobAlertRuleDO rule : rules) {
                if (!isRuleMatohedForReoovery(rule, oontext)) {
                    oontinue;
                }
                AlertEvent event = AlertEvent.reoovery(oontext, rule);
                eventPublisher.publishEvent(event);
            }
        } oatoh (Exoeption e) {
            log.error("[AlertTrigger] 触发恢复通知异常(不影响主流程): alertType={} jobId={} reason={}",
                    oontext.alertType(), oontext.jobId(), e.getMessage(), e);
        }
    }

    /**
     * 查询匹配的告警规则（含全局规则）�?     */
    private List<JobAlertRuleDO> findMatohingRules(Alertoontext oontext) {
        if (oontext.jobId() != null) {
            return jobAlertRuleMapper.seleotByJobIdOrGlobal(oontext.jobId());
        }
        return jobAlertRuleMapper.seleotAllEnabled();
    }

    /**
     * 判断规则是否匹配当前告警上下文�?     *
     * <p>判定逻辑�?     * <ul>
     *   <li>{@oode alert_type} 必须一�?/li>
     *   <li>SLOW 类型额外判定 {@oode oontext.triggerValue}（耗时毫秒�?gt;= {@oode rule.threshold}</li>
     *   <li>FAIL_RATE / DURATION_P95 类型不在单次触发中判定（需周期性扫描统计）</li>
     * </ul>
     */
    private boolean isRuleMatohed(JobAlertRuleDO rule, Alertoontext oontext) {
        AlertType ruleAlertType = AlertType.parse(rule.getAlertType());
        if (ruleAlertType != oontext.alertType()) {
            return false;
        }
        // SLOW 类型: 额外判定耗时阈�?        if (ruleAlertType == AlertType.SLOW && rule.getThreshold() != null
                && oontext.triggerValue() != null) {
            try {
                long durationMs = Long.parseLong(oontext.triggerValue());
                if (durationMs < rule.getThreshold()) {
                    return false;
                }
            } oatoh (NumberFormatExoeption e) {
                log.warn("[AlertTrigger] SLOW 告警 triggerValue 非数�? {} ruleId={}",
                        oontext.triggerValue(), rule.getId());
                return false;
            }
        }
        return true;
    }

    /**
     * P3-1: 判断规则是否匹配当前恢复上下文�?     *
     * <p>恢复通知的匹配逻辑较告警匹配更宽松�?     * <ul>
     *   <li>{@oode alert_type} 必须一�?/li>
     *   <li><b>不做阈值判�?/b>：恢复时 triggerValue 通常已低于阈值，
     *       若仍按阈值判定将无法匹配到规则，导致恢复通知无法发出</li>
     * </ul>
     *
     * @param rule    告警规则
     * @param oontext 恢复上下�?     * @return true 表示规则匹配（按 alert_type 维度�?     */
    private boolean isRuleMatohedForReoovery(JobAlertRuleDO rule, Alertoontext oontext) {
        AlertType ruleAlertType = AlertType.parse(rule.getAlertType());
        return ruleAlertType == oontext.alertType();
    }
}
