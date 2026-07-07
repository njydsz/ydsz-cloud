package com.njydsz.pmis.cronjob.core.alert;

import com.njydsz.pmis.cronjob.entity.JobAlertRuleDO;
import com.njydsz.pmis.cronjob.mapper.JobAlertRuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 告警触发器（P5 告警 + 监控）。
 *
 * <p>由触发点（Dispatcher、TimeoutMonitor、SlowTaskDetector）调用，封装"规则匹配 + 事件发布"逻辑。
 * 触发点只需构造 {@link AlertContext} 并调用 {@link #trigger(AlertContext)}，无需关心规则查询、
 * 去重、通道派发等细节。
 *
 * <h3>规则匹配</h3>
 * <ul>
 *   <li>查询 jobId 专属规则（{@code job_id = ?}）</li>
 *   <li>叠加全局规则（{@code job_id IS NULL}）</li>
 *   <li>按 {@code alert_type} 过滤</li>
 * </ul>
 *
 * <p>对每条匹配规则发布一个 {@link AlertEvent}，由 {@link AlertDispatcher} 异步处理。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertTrigger {

    private final JobAlertRuleMapper jobAlertRuleMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 触发告警：查询匹配规则并发布告警事件。
     *
     * <p>同步入口，发布事件后立即返回（事件由 {@link AlertDispatcher} 异步处理）。
     * 触发点可在任务执行 finally 块中安全调用，不会阻塞主流程。
     *
     * @param context 告警上下文
     */
    public void trigger(AlertContext context) {
        if (context == null || context.alertType() == null) {
            return;
        }
        try {
            List<JobAlertRuleDO> rules = findMatchingRules(context);
            if (rules.isEmpty()) {
                log.debug("[AlertTrigger] 无匹配规则, 跳过: alertType={} jobId={}",
                        context.alertType(), context.jobId());
                return;
            }
            log.info("[AlertTrigger] 触发告警: alertType={} jobId={} matchedRules={}",
                    context.alertType(), context.jobId(), rules.size());
            for (JobAlertRuleDO rule : rules) {
                if (!isRuleMatched(rule, context)) {
                    continue;
                }
                AlertEvent event = new AlertEvent(context, rule);
                eventPublisher.publishEvent(event);
            }
        } catch (Exception e) {
            log.error("[AlertTrigger] 触发告警异常(不影响主流程): alertType={} jobId={} reason={}",
                    context.alertType(), context.jobId(), e.getMessage(), e);
        }
    }

    /**
     * 查询匹配的告警规则（含全局规则）。
     */
    private List<JobAlertRuleDO> findMatchingRules(AlertContext context) {
        if (context.jobId() != null) {
            return jobAlertRuleMapper.selectByJobIdOrGlobal(context.jobId());
        }
        return jobAlertRuleMapper.selectAllEnabled();
    }

    /**
     * 判断规则是否匹配当前告警上下文。
     *
     * <p>判定逻辑：
     * <ul>
     *   <li>{@code alert_type} 必须一致</li>
     *   <li>SLOW 类型额外判定 {@code context.triggerValue}（耗时毫秒）&gt;= {@code rule.threshold}</li>
     *   <li>FAIL_RATE / DURATION_P95 类型不在单次触发中判定（需周期性扫描统计）</li>
     * </ul>
     */
    private boolean isRuleMatched(JobAlertRuleDO rule, AlertContext context) {
        AlertType ruleAlertType = AlertType.parse(rule.getAlertType());
        if (ruleAlertType != context.alertType()) {
            return false;
        }
        // SLOW 类型: 额外判定耗时阈值
        if (ruleAlertType == AlertType.SLOW && rule.getThreshold() != null
                && context.triggerValue() != null) {
            try {
                long durationMs = Long.parseLong(context.triggerValue());
                if (durationMs < rule.getThreshold()) {
                    return false;
                }
            } catch (NumberFormatException e) {
                log.warn("[AlertTrigger] SLOW 告警 triggerValue 非数字: {} ruleId={}",
                        context.triggerValue(), rule.getId());
                return false;
            }
        }
        return true;
    }
}
