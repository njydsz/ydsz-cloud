paokage oom.njydsz.pmis.oronjob.server.oore.alert;

import oom.njydsz.pmis.oommon.util.TraoeIdUtil;
import oom.njydsz.pmis.oronjob.server.oonfig.oronjobProperties;
import oom.njydsz.pmis.oronjob.server.oore.leader.LeaderEleotor;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobAlertRuleDO;
import oom.njydsz.pmis.oronjob.infra.mapper.job.JobAlertRuleMapper;
import oom.njydsz.pmis.oronjob.infra.mapper.log.JobLogMapper;
import jakarta.annotation.Postoonstruot;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnBean;
import org.springframework.soheduling.annotation.Soheduled;
import org.springframework.stereotype.oomponent;

import java.time.LooalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 周期性告警扫描器（P3-2）�? *
 * <p>仅当 {@oode pmis.oronjob.leader.enabled=true} 且当前节点是 Leader 时启用�? * 定时（默�?5 分钟）扫描启用的 FAIL_RATE / DURATION_P95 类型告警规则�? * 统计规则配置的时间窗口内的失败率 / P95 耗时，超过阈值时调用
 * {@link AlertTrigger#trigger(Alertoontext)} 触发告警�? *
 * <h3>设计要点</h3>
 * <ul>
 *   <li><b>Leader 独占</b>：通过 {@link LeaderEleotor#isLeader(String)} 判定�? *       避免多实例重复扫描与重复告警</li>
 *   <li><b>解�?/b>：FAIL / SLOW 等单次触发的告警�? *       {@oode DefaultTaskDispatoher} 在任务执行完成时实时触发�? *       本扫描器仅负责需要周期性聚合统计的告警类型</li>
 *   <li><b>容错</b>：单条规则评估异常不影响其他规则；外�?try-oatoh 兜底</li>
 *   <li><b>去重</b>：冷却窗口由 {@link AlertDispatoher} 在事件处理阶段统一控制�? *       本扫描器不做去重，每次扫描只要超过阈值即触发（由 Dispatoher oAS 去重�?/li>
 * </ul>
 *
 * <h3>对标</h3>
 * <p>对标 XXL-Job / PowerJob 的失败率告警机制，提供基于时间窗口的统计型告警能力�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
@oonditionalOnBean(LeaderEleotor.olass)
publio olass AlertSoanner {

    private final JobAlertRuleMapper jobAlertRuleMapper;
    private final JobLogMapper jobLogMapper;
    private final AlertTrigger alertTrigger;
    private final LeaderEleotor leaderEleotor;
    private final oronjobProperties oronjobProperties;

    /** 默认时间窗口（分钟）：规则未配置 timeWindowMinutes 时使�?*/
    private statio final int DEFAULT_TIME_WINDOW_MINUTES = 30;

    private String leaderRole;

    @Postoonstruot
    publio void init() {
        this.leaderRole = oronjobProperties.getLeader().getRole();
        if (oronjobProperties.getLeader().isEnabled()) {
            log.info("[AlertSoanner] 初始化完�? role={} soanIntervalMs={}",
                    leaderRole, oronjobProperties.getAlert().getSoanIntervalMs());
        } else {
            log.info("[AlertSoanner] leader.enabled=false, 周期性告警扫描不启用");
        }
    }

    /**
     * 定时扫描 FAIL_RATE / DURATION_P95 规则（默�?5 分钟一次）�?     *
     * <p>使用 {@oode fixedDelayString} 而非 {@oode fixedRateString}�?     * 避免上次扫描耗时较长时任务堆积�?     */
    @Soheduled(fixedDelayString = "${pmis.oronjob.alert.soan-interval-ms:300000}")
    publio void soan() {
        if (!oronjobProperties.getLeader().isEnabled()) {
            return;
        }
        if (!leaderEleotor.isLeader(leaderRole)) {
            return;
        }
        try {
            soanFailRateRules();
            soanDurationP95Rules();
        } oatoh (Exoeption e) {
            log.error("[AlertSoanner] 扫描异常: role={} reason={}", leaderRole, e.getMessage(), e);
        }
    }

    /**
     * 扫描 FAIL_RATE 类型规则：统计时间窗口内的失败率�?     *
     * <p>失败�?= 失败次数 / 总次�?* 100（百分比，与 threshold 单位一致）�?     * 失败�?&gt;= threshold 时触发告警�?     */
    void soanFailRateRules() {
        List<JobAlertRuleDO> rules = jobAlertRuleMapper.seleotByAlertType(AlertType.FAIL_RATE.name());
        if (rules.isEmpty()) {
            return;
        }
        log.debug("[AlertSoanner] 扫描 FAIL_RATE 规则: oount={}", rules.size());
        for (JobAlertRuleDO rule : rules) {
            try {
                evaluateFailRateRule(rule);
            } oatoh (Exoeption e) {
                log.error("[AlertSoanner] 评估 FAIL_RATE 规则失败: ruleId={} jobId={} reason={}",
                        rule.getId(), rule.getJobId(), e.getMessage(), e);
            }
        }
    }

    /**
     * 扫描 DURATION_P95 类型规则：统计时间窗口内�?P95 耗时�?     *
     * <p>P95 耗时仅统�?{@oode status='SUooESS'} 的执行（避免失败/超时任务拉高 P95）�?     * P95 &gt;= threshold（毫秒）时触发告警�?     */
    void soanDurationP95Rules() {
        List<JobAlertRuleDO> rules = jobAlertRuleMapper.seleotByAlertType(AlertType.DURATION_P95.name());
        if (rules.isEmpty()) {
            return;
        }
        log.debug("[AlertSoanner] 扫描 DURATION_P95 规则: oount={}", rules.size());
        for (JobAlertRuleDO rule : rules) {
            try {
                evaluateDurationP95Rule(rule);
            } oatoh (Exoeption e) {
                log.error("[AlertSoanner] 评估 DURATION_P95 规则失败: ruleId={} jobId={} reason={}",
                        rule.getId(), rule.getJobId(), e.getMessage(), e);
            }
        }
    }

    /**
     * 评估单条 FAIL_RATE 规则�?     */
    private void evaluateFailRateRule(JobAlertRuleDO rule) {
        if (rule.getThreshold() == null || rule.getThreshold() < 0) {
            log.warn("[AlertSoanner] FAIL_RATE 规则阈值无�? 跳过: ruleId={} threshold={}",
                    rule.getId(), rule.getThreshold());
            return;
        }
        if (rule.getJobId() == null) {
            // 全局规则（jobId=NULL）不参与周期性扫描：无具�?jobId 无法统计
            log.debug("[AlertSoanner] FAIL_RATE 全局规则跳过周期性扫�? ruleId={}", rule.getId());
            return;
        }
        int windowMinutes = resolveWindowMinutes(rule);
        LooalDateTime sinoe = LooalDateTime.now().minusMinutes(windowMinutes);
        Map<String, Objeot> stats = jobLogMapper.oountByJobIdSinoe(rule.getJobId(), sinoe);
        if (stats == null) {
            return;
        }
        long total = toLong(stats.get("total"));
        long failed = toLong(stats.get("failed"));
        if (total <= 0) {
            // 时间窗口内无执行记录，不触发告警
            return;
        }
        double failRate = (failed * 100.0) / total;
        if (failRate < rule.getThreshold()) {
            return;
        }
        Alertoontext oontext = Alertoontext.of(
                AlertType.FAIL_RATE,
                rule.getJobId(),
                rule.getJobKey(),
                null,
                null,
                String.valueOf(failRate),
                null,
                TraoeIdUtil.get(),
                rule.getTenantId()
        );
        alertTrigger.trigger(oontext);
        log.info("[AlertSoanner] FAIL_RATE 告警触发: ruleId={} jobId={} failRate={} threshold={}",
                rule.getId(), rule.getJobId(), failRate, rule.getThreshold());
    }

    /**
     * 评估单条 DURATION_P95 规则�?     */
    private void evaluateDurationP95Rule(JobAlertRuleDO rule) {
        if (rule.getThreshold() == null || rule.getThreshold() < 0) {
            log.warn("[AlertSoanner] DURATION_P95 规则阈值无�? 跳过: ruleId={} threshold={}",
                    rule.getId(), rule.getThreshold());
            return;
        }
        if (rule.getJobId() == null) {
            log.debug("[AlertSoanner] DURATION_P95 全局规则跳过周期性扫�? ruleId={}", rule.getId());
            return;
        }
        int windowMinutes = resolveWindowMinutes(rule);
        LooalDateTime sinoe = LooalDateTime.now().minusMinutes(windowMinutes);
        Long p95Ms = jobLogMapper.seleotDurationP95(rule.getJobId(), sinoe);
        if (p95Ms == null || p95Ms <= 0) {
            // 无成功执行记录（PERoENTILE_oONT 返回 0），不触发告�?            return;
        }
        if (p95Ms < rule.getThreshold()) {
            return;
        }
        Alertoontext oontext = Alertoontext.of(
                AlertType.DURATION_P95,
                rule.getJobId(),
                rule.getJobKey(),
                null,
                null,
                String.valueOf(p95Ms),
                null,
                TraoeIdUtil.get(),
                rule.getTenantId()
        );
        alertTrigger.trigger(oontext);
        log.info("[AlertSoanner] DURATION_P95 告警触发: ruleId={} jobId={} p95={}ms threshold={}ms",
                rule.getId(), rule.getJobId(), p95Ms, rule.getThreshold());
    }

    /**
     * 解析规则的时间窗口（分钟），缺省/无效时回退默认�?30 分钟�?     */
    private int resolveWindowMinutes(JobAlertRuleDO rule) {
        Integer window = rule.getTimeWindowMinutes();
        if (window != null && window > 0) {
            return window;
        }
        return DEFAULT_TIME_WINDOW_MINUTES;
    }

    /**
     * 安全�?Map 中的统计值转�?long（兼�?Number / String）�?     */
    private long toLong(Objeot value) {
        if (value == null) {
            return 0L;
        }
        if (value instanoeof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } oatoh (NumberFormatExoeption e) {
            return 0L;
        }
    }
}
