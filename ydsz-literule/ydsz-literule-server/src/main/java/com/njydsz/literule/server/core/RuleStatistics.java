package com.njydsz.literule.server.core;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.sentry.SentryObservation;
import com.njydsz.common.sentry.domain.AlertEvent;
import com.njydsz.common.sentry.domain.AlertSeverity;
import com.njydsz.literule.domain.vo.RuleEngineStatsVO;

/**
 * 规则引擎统计记录器
 *
 * <p>封装评估统计计数器、慢规则检测和告警逻辑，从 {@link DefaultRuleEngine} 提取，使引擎核心聚焦评估编排。
 *
 * <h3>线程安全</h3>
 *
 * <p>所有计数器使用 {@link AtomicLong}，按规则明细使用 {@link ConcurrentHashMap}。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
public class RuleStatistics {

  /** 总评估次数 */
  private final AtomicLong totalEvaluations = new AtomicLong(0);

  /** 总触发次数 */
  private final AtomicLong totalTriggered = new AtomicLong(0);

  /** 总异常次数 */
  private final AtomicLong totalErrors = new AtomicLong(0);

  /** 总耗时（毫秒） */
  private final AtomicLong totalElapsedMs = new AtomicLong(0);

  /** 按规则编码的统计明细 */
  private final ConcurrentHashMap<String, RuleEngineStatsVO.RuleStat> perRuleStats =
      new ConcurrentHashMap<>();

  /** 是否启用统计 */
  private volatile boolean statsEnabled = true;

  /** 慢规则阈值（毫秒），0 表示不启用 */
  private volatile long slowRuleThresholdMs = 0L;

  /** 监控指标（可选） */
  private volatile RuleMetrics metrics;

  public RuleStatistics() {}

  /**
   * 记录评估统计
   *
   * @param ruleCode 规则编码
   * @param triggered 是否触发
   * @param error 是否异常
   * @param elapsedMs 耗时
   */
  public void record(String ruleCode, boolean triggered, boolean error, long elapsedMs) {
    if (!statsEnabled) {
      return;
    }
    totalEvaluations.incrementAndGet();
    totalElapsedMs.addAndGet(elapsedMs);
    if (triggered) {
      totalTriggered.incrementAndGet();
    }
    if (error) {
      totalErrors.incrementAndGet();
    }
    perRuleStats.compute(
        ruleCode,
        (k, v) -> {
          if (v == null) {
            v = RuleEngineStatsVO.RuleStat.builder().build();
          }
          v.setExecutions(v.getExecutions() + 1);
          if (triggered) {
            v.setTriggered(v.getTriggered() + 1);
          }
          if (error) {
            v.setErrors(v.getErrors() + 1);
          }
          v.setTotalElapsedMs(v.getTotalElapsedMs() + elapsedMs);
          return v;
        });
    // 慢规则告警
    checkSlowRule(ruleCode, elapsedMs);
  }

  /**
   * 慢规则检测与告警
   *
   * @param ruleCode 规则编码
   * @param elapsedMs 耗时
   */
  private void checkSlowRule(String ruleCode, long elapsedMs) {
    if (slowRuleThresholdMs <= 0 || elapsedMs < slowRuleThresholdMs) {
      return;
    }
    if (metrics != null) {
      metrics.recordSlowRule(ruleCode, elapsedMs, slowRuleThresholdMs);
    }
    // sentry 告警收敛
    SentryObservation.alert(
        AlertEvent.builder()
            .name("literule.slow_rule")
            .severity(AlertSeverity.P2)
            .summary("慢规则检测：规则 " + ruleCode + " 评估耗时超阈值")
            .description("规则评估耗时 " + elapsedMs + "ms，超过阈值 " + slowRuleThresholdMs + "ms")
            .category("performance")
            .labels(
                Map.of(
                    "rule_code",
                    ruleCode,
                    "elapsed_ms",
                    String.valueOf(elapsedMs),
                    "threshold_ms",
                    String.valueOf(slowRuleThresholdMs)))
            .build());
    log.warn(
        "[LiteRule-SlowRule] rule={}, elapsed={}ms, threshold={}ms",
        ruleCode,
        elapsedMs,
        slowRuleThresholdMs);
  }

  /**
   * 获取统计快照
   *
   * @param registeredRules 当前注册规则数
   * @param lastEvaluatedRules 上次评估规则数
   * @return 统计快照
   */
  public RuleEngineStatsVO snapshot(int registeredRules, int lastEvaluatedRules) {
    Map<String, RuleEngineStatsVO.RuleStat> snapshot = new HashMap<>(perRuleStats.size());
    perRuleStats.forEach(
        (k, v) ->
            snapshot.put(
                k,
                RuleEngineStatsVO.RuleStat.builder()
                    .executions(v.getExecutions())
                    .triggered(v.getTriggered())
                    .errors(v.getErrors())
                    .totalElapsedMs(v.getTotalElapsedMs())
                    .build()));
    return RuleEngineStatsVO.builder()
        .totalEvaluations(totalEvaluations.get())
        .totalTriggered(totalTriggered.get())
        .totalErrors(totalErrors.get())
        .totalElapsedMs(totalElapsedMs.get())
        .registeredRules(registeredRules)
        .lastEvaluatedRules(lastEvaluatedRules)
        .perRuleStats(snapshot)
        .build();
  }

  /** 重置统计 */
  public void reset() {
    totalEvaluations.set(0);
    totalTriggered.set(0);
    totalErrors.set(0);
    totalElapsedMs.set(0);
    perRuleStats.clear();
  }

  /**
   * 移除指定规则的统计明细
   *
   * @param ruleCode 规则编码
   */
  public void removeRuleStats(String ruleCode) {
    perRuleStats.remove(ruleCode);
  }

  // ==================== Getter/Setter ====================

  public void setStatsEnabled(boolean statsEnabled) {
    this.statsEnabled = statsEnabled;
  }

  public boolean isStatsEnabled() {
    return statsEnabled;
  }

  public void setSlowRuleThresholdMs(long slowRuleThresholdMs) {
    this.slowRuleThresholdMs = slowRuleThresholdMs;
  }

  public long getSlowRuleThresholdMs() {
    return slowRuleThresholdMs;
  }

  public void setMetrics(RuleMetrics metrics) {
    this.metrics = metrics;
  }

  public RuleMetrics getMetrics() {
    return metrics;
  }

  public long getTotalEvaluations() {
    return totalEvaluations.get();
  }

  public long getTotalTriggered() {
    return totalTriggered.get();
  }

  public long getTotalErrors() {
    return totalErrors.get();
  }

  public long getTotalElapsedMs() {
    return totalElapsedMs.get();
  }
}
