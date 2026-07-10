package com.njydsz.pmis.cronjob.core.sla;

import com.njydsz.pmis.common.util.TraceIdUtil;
import com.njydsz.pmis.cronjob.config.CronjobProperties;
import com.njydsz.pmis.cronjob.core.alert.AlertContext;
import com.njydsz.pmis.cronjob.core.alert.AlertTrigger;
import com.njydsz.pmis.cronjob.core.alert.AlertType;
import com.njydsz.pmis.cronjob.core.leader.LeaderElector;
import com.njydsz.pmis.cronjob.entity.alert.JobSlaDO;
import com.njydsz.pmis.cronjob.mapper.alert.JobSlaMapper;
import com.njydsz.pmis.cronjob.service.alert.JobSlaService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SLA 违约扫描器（P2-7 SLA 管理）。
 *
 * <p>仅当 {@code pmis.cronjob.leader.enabled=true} 且当前节点是 Leader 时启用。
 * 每 5 分钟扫描所有启用的 SLA 规则，对每条规则调用 {@link JobSlaService#checkViolation(String)}
 * 检查违约情况，违约时复用 {@link AlertTrigger} 触发告警。
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li><b>Leader 独占</b>：通过 {@link LeaderElector#isLeader(String)} 判定，
 *       避免多实例重复扫描</li>
 *   <li><b>复用告警体系</b>：违约时通过 {@link AlertTrigger#trigger(AlertContext)}
 *       发布告警事件，由 {@code AlertDispatcher} 统一去重与派发</li>
 *   <li><b>容错</b>：单条 SLA 规则评估异常不影响其他规则</li>
 * </ul>
 *
 * <h3>告警类型映射</h3>
 * <ul>
 *   <li>MAX_DURATION → {@link AlertType#DURATION_P95}（复用耗时告警通道）</li>
 *   <li>FAIL_RATE → {@link AlertType#FAIL_RATE}（复用失败率告警通道）</li>
 *   <li>SUCCESS_RATE → {@link AlertType#FAIL_RATE}（成功率低等价于失败率高）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(LeaderElector.class)
public class SlaScanner {

    private final JobSlaMapper jobSlaMapper;
    private final JobSlaService jobSlaService;
    private final AlertTrigger alertTrigger;
    private final LeaderElector leaderElector;
    private final CronjobProperties cronjobProperties;

    private String leaderRole;

    @PostConstruct
    public void init() {
        this.leaderRole = cronjobProperties.getLeader().getRole();
        if (cronjobProperties.getLeader().isEnabled()) {
            log.info("[SlaScanner] 初始化完成, role={}", leaderRole);
        } else {
            log.info("[SlaScanner] leader.enabled=false, SLA 扫描不启用");
        }
    }

    /**
     * 定时扫描 SLA 违约（每 5 分钟一次）。
     *
     * <p>使用 {@code fixedDelayString} 而非 {@code fixedRateString}，
     * 避免上次扫描耗时较长时任务堆积。
     */
    @Scheduled(fixedDelayString = "${pmis.cronjob.sla.scan-interval-ms:300000}")
    public void scan() {
        if (!cronjobProperties.getLeader().isEnabled()) {
            return;
        }
        if (!leaderElector.isLeader(leaderRole)) {
            return;
        }
        try {
            scanSlaViolations();
        } catch (Exception e) {
            log.error("[SlaScanner] 扫描异常: role={} reason={}", leaderRole, e.getMessage(), e);
        }
    }

    /**
     * 扫描所有启用的 SLA 规则，检查违约并触发告警。
     */
    void scanSlaViolations() {
        List<JobSlaDO> rules = jobSlaMapper.selectAllEnabled();
        if (rules.isEmpty()) {
            return;
        }
        log.debug("[SlaScanner] 扫描 SLA 规则: count={}", rules.size());
        int violationCount = 0;
        for (JobSlaDO sla : rules) {
            try {
                violationCount += evaluateSla(sla);
            } catch (Exception e) {
                log.error("[SlaScanner] 评估 SLA 规则失败: slaId={} jobId={} reason={}",
                        sla.getId(), sla.getJobId(), e.getMessage(), e);
            }
        }
        if (violationCount > 0) {
            log.info("[SlaScanner] 扫描完成: totalRules={} violations={}", rules.size(), violationCount);
        }
    }

    /**
     * 评估单条 SLA 规则，违约时触发告警。
     *
     * @param sla SLA 规则
     * @return 违约数量（0 表示无违约）
     */
    private int evaluateSla(JobSlaDO sla) {
        List<JobSlaService.SlaViolation> violations = jobSlaService.checkViolation(sla.getJobId());
        if (violations.isEmpty()) {
            return 0;
        }
        for (JobSlaService.SlaViolation v : violations) {
            AlertType alertType = mapMetricToAlertType(v.metric());
            AlertContext context = AlertContext.of(
                    alertType,
                    v.jobId(),
                    v.jobKey(),
                    null,
                    null,
                    v.actual(),
                    buildErrorMessage(v),
                    TraceIdUtil.get(),
                    null
            );
            alertTrigger.trigger(context);
            log.info("[SlaScanner] SLA 违约告警: slaId={} jobId={} metric={} actual={} threshold={} level={}",
                    v.ruleId(), v.jobId(), v.metric(), v.actual(), v.threshold(), v.alertLevel());
        }
        return violations.size();
    }

    /**
     * 将 SLA 违约指标映射到告警类型。
     *
     * <p>SUCCESS_RATE 违约（成功率低于阈值）等价于 FAIL_RATE 告警，
     * 复用现有告警规则通道，避免新增告警类型。
     */
    private AlertType mapMetricToAlertType(String metric) {
        return switch (metric) {
            case "MAX_DURATION" -> AlertType.DURATION_P95;
            case "FAIL_RATE", "SUCCESS_RATE" -> AlertType.FAIL_RATE;
            default -> AlertType.FAIL_RATE;
        };
    }

    /**
     * 构造违约错误信息（用于告警文案展示）。
     */
    private String buildErrorMessage(JobSlaService.SlaViolation v) {
        return String.format("SLA 违约: metric=%s actual=%s threshold=%s level=%s",
                v.metric(), v.actual(), v.threshold(), v.alertLevel());
    }
}
