package com.njydsz.cronjob.server.core.alert;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.njydsz.common.lock.annotation.DistributedScheduled;
import com.njydsz.common.util.id.TracerUtils;
import com.njydsz.cronjob.domain.entity.job.JobAlertRule;
import com.njydsz.cronjob.domain.repository.JobAlertRuleRepository;
import com.njydsz.cronjob.domain.repository.JobLogRepository;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.leader.LeaderElector;

/**
 * 周期性告警扫描器（P3-2）。
 *
 * <p>仅当 {@code ydsz.cronjob.leader.enabled=true} 且当前节点是 Leader 时启用。 定时（默认 5 分钟）扫描启用的 FAIL_RATE /
 * DURATION_P95 类型告警规则， 统计规则配置的时间窗口内的失败率 / P95 耗时，超过阈值时调用 {@link AlertTrigger#trigger(AlertContext)}
 * 触发告警。
 *
 * <h3>设计要点</h3>
 *
 * <ul>
 *   <li><b>Leader 独占</b>：通过 {@link LeaderElector#isLeader(String)} 判定， 避免多实例重复扫描与重复告警
 *   <li><b>解耦</b>：FAIL / SLOW 等单次触发的告警由 {@code DefaultTaskDispatcher} 在任务执行完成时实时触发；
 *       本扫描器仅负责需要周期性聚合统计的告警类型
 *   <li><b>容错</b>：单条规则评估异常不影响其他规则；外层 try-catch 兜底
 *   <li><b>去重</b>：冷却窗口由 {@link AlertDispatcher} 在事件处理阶段统一控制， 本扫描器不做去重，每次扫描只要超过阈值即触发（由 Dispatcher
 *       CAS 去重）
 * </ul>
 *
 * <h3>对标</h3>
 *
 * <p>对标 XXL-Job / PowerJob 的失败率告警机制，提供基于时间窗口的统计型告警能力。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(LeaderElector.class)
public class AlertScanner {

  private final JobAlertRuleRepository jobAlertRuleRepository;
  private final JobLogRepository jobLogRepository;
  private final AlertTrigger alertTrigger;
  private final LeaderElector leaderElector;
  private final CronjobProperties cronjobProperties;

  /** 默认时间窗口（分钟）：规则未配置 timeWindowMinutes 时使用 */
  private static final int DEFAULT_TIME_WINDOW_MINUTES = 30;

  private String leaderRole;

  /**
   * 初始化扫描器：解析 Leader 角色名并输出启用状态。
   *
   * <p>由 {@code @PostConstruct} 在 Bean 创建后调用，缓存 leader 角色名， 供 {@link #scan()} 通过 {@link
   * LeaderElector#isLeader(String)} 判定本节点是否执行扫描。
   */
  @PostConstruct
  public void init() {
    this.leaderRole = cronjobProperties.getLeader().getRole();
    if (cronjobProperties.getLeader().isEnabled()) {
      log.info(
          "[AlertScanner] 初始化完成, role={} scanIntervalMs={}",
          leaderRole,
          cronjobProperties.getAlert().getScanIntervalMs());
    } else {
      log.info("[AlertScanner] leader.enabled=false, 周期性告警扫描不启用");
    }
  }

  /**
   * 定时扫描 FAIL_RATE / DURATION_P95 规则（默认 5 分钟一次）。
   *
   * <p>使用 {@code fixedDelayString} 而非 {@code fixedRateString}， 避免上次扫描耗时较长时任务堆积。
   */
  @DistributedScheduled(lockKey = "cronjob:alert-scan")
  @Scheduled(fixedDelayString = "${ydsz.cronjob.alert.scan-interval-ms:300000}")
  public void scan() {
    if (!cronjobProperties.getLeader().isEnabled()) {
      return;
    }
    if (!leaderElector.isLeader(leaderRole)) {
      return;
    }
    try {
      scanFailRateRules();
      scanDurationP95Rules();
    } catch (Exception e) {
      log.error("[AlertScanner] 扫描异常: role={} reason={}", leaderRole, e.getMessage(), e);
    }
  }

  /**
   * 扫描 FAIL_RATE 类型规则：统计时间窗口内的失败率。
   *
   * <p>失败率 = 失败次数 / 总次数 * 100（百分比，与 threshold 单位一致）。 失败率 &gt;= threshold 时触发告警。
   */
  void scanFailRateRules() {
    List<JobAlertRule> rules = jobAlertRuleRepository.selectByAlertType(AlertType.FAIL_RATE.name());
    if (rules.isEmpty()) {
      return;
    }
    log.debug("[AlertScanner] 扫描 FAIL_RATE 规则: count={}", rules.size());
    for (JobAlertRule rule : rules) {
      try {
        evaluateFailRateRule(rule);
      } catch (Exception e) {
        log.error(
            "[AlertScanner] 评估 FAIL_RATE 规则失败: ruleId={} jobId={} reason={}",
            rule.getId(),
            rule.getJobId(),
            e.getMessage(),
            e);
      }
    }
  }

  /**
   * 扫描 DURATION_P95 类型规则：统计时间窗口内的 P95 耗时。
   *
   * <p>P95 耗时仅统计 {@code status='SUCCESS'} 的执行（避免失败/超时任务拉高 P95）。 P95 &gt;= threshold（毫秒）时触发告警。
   */
  void scanDurationP95Rules() {
    List<JobAlertRule> rules = jobAlertRuleRepository.selectByAlertType(AlertType.DURATION_P95.name());
    if (rules.isEmpty()) {
      return;
    }
    log.debug("[AlertScanner] 扫描 DURATION_P95 规则: count={}", rules.size());
    for (JobAlertRule rule : rules) {
      try {
        evaluateDurationP95Rule(rule);
      } catch (Exception e) {
        log.error(
            "[AlertScanner] 评估 DURATION_P95 规则失败: ruleId={} jobId={} reason={}",
            rule.getId(),
            rule.getJobId(),
            e.getMessage(),
            e);
      }
    }
  }

  /** 评估单条 FAIL_RATE 规则。 */
  private void evaluateFailRateRule(JobAlertRule rule) {
    if (rule.getThreshold() == null || rule.getThreshold() < 0) {
      log.warn(
          "[AlertScanner] FAIL_RATE 规则阈值无效, 跳过: ruleId={} threshold={}",
          rule.getId(),
          rule.getThreshold());
      return;
    }
    if (rule.getJobId() == null) {
      // 全局规则（jobId=NULL）不参与周期性扫描：无具体 jobId 无法统计
      log.debug("[AlertScanner] FAIL_RATE 全局规则跳过周期性扫描: ruleId={}", rule.getId());
      return;
    }
    int windowMinutes = resolveWindowMinutes(rule);
    LocalDateTime since = LocalDateTime.now().minusMinutes(windowMinutes);
    Map<String, Object> stats = jobLogRepository.countByJobIdSince(rule.getJobId(), since);
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
    AlertContext context =
        AlertContext.of(
            AlertType.FAIL_RATE,
            rule.getJobId(),
            rule.getJobKey(),
            null,
            null,
            String.valueOf(failRate),
            null,
            TracerUtils.getTraceId(),
            rule.getTenantId());
    alertTrigger.trigger(context);
    log.info(
        "[AlertScanner] FAIL_RATE 告警触发: ruleId={} jobId={} failRate={} threshold={}",
        rule.getId(),
        rule.getJobId(),
        failRate,
        rule.getThreshold());
  }

  /** 评估单条 DURATION_P95 规则。 */
  private void evaluateDurationP95Rule(JobAlertRule rule) {
    if (rule.getThreshold() == null || rule.getThreshold() < 0) {
      log.warn(
          "[AlertScanner] DURATION_P95 规则阈值无效, 跳过: ruleId={} threshold={}",
          rule.getId(),
          rule.getThreshold());
      return;
    }
    if (rule.getJobId() == null) {
      log.debug("[AlertScanner] DURATION_P95 全局规则跳过周期性扫描: ruleId={}", rule.getId());
      return;
    }
    int windowMinutes = resolveWindowMinutes(rule);
    LocalDateTime since = LocalDateTime.now().minusMinutes(windowMinutes);
    Long p95Ms = jobLogRepository.selectDurationP95(rule.getJobId(), since);
    if (p95Ms == null || p95Ms <= 0) {
      // 无成功执行记录（PERCENTILE_CONT 返回 0），不触发告警
      return;
    }
    if (p95Ms < rule.getThreshold()) {
      return;
    }
    AlertContext context =
        AlertContext.of(
            AlertType.DURATION_P95,
            rule.getJobId(),
            rule.getJobKey(),
            null,
            null,
            String.valueOf(p95Ms),
            null,
            TracerUtils.getTraceId(),
            rule.getTenantId());
    alertTrigger.trigger(context);
    log.info(
        "[AlertScanner] DURATION_P95 告警触发: ruleId={} jobId={} p95={}ms threshold={}ms",
        rule.getId(),
        rule.getJobId(),
        p95Ms,
        rule.getThreshold());
  }

  /** 解析规则的时间窗口（分钟），缺省/无效时回退默认值 30 分钟。 */
  private int resolveWindowMinutes(JobAlertRule rule) {
    Integer window = rule.getTimeWindowMinutes();
    if (window != null && window > 0) {
      return window;
    }
    return DEFAULT_TIME_WINDOW_MINUTES;
  }

  /** 安全将 Map 中的统计值转为 long（兼容 Number / String）。 */
  private long toLong(Object value) {
    if (value == null) {
      return 0L;
    }
    if (value instanceof Number n) {
      return n.longValue();
    }
    try {
      return Long.parseLong(value.toString());
    } catch (NumberFormatException e) {
      return 0L;
    }
  }
}
