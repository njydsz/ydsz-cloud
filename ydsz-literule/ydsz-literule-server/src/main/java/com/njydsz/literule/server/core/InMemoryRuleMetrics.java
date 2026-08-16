package com.njydsz.literule.server.core;

import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.literule.api.RuleSeverity;

/**
 * 内存版规则监控指标实现（降级/测试场景）。
 *
 * <p>当 Micrometer {@code MeterRegistry} 不可用时使用此实现， 仅维护内存计数器，不暴露 Prometheus 指标。
 *
 * @since 1.1.0
 * @author ydsz-team
 */
@Slf4j
public class InMemoryRuleMetrics implements RuleMetrics {

  private final AtomicLong totalEvaluations = new AtomicLong(0);
  private final AtomicLong totalTriggered = new AtomicLong(0);
  private final AtomicLong totalErrors = new AtomicLong(0);
  private final AtomicLong totalElapsedMs = new AtomicLong(0);
  private volatile int registeredRules = 0;
  private volatile int lastEvaluatedRules = 0;

  @Override
  public void recordEvaluation(
      String ruleCode,
      String scenario,
      boolean triggered,
      RuleSeverity severity,
      boolean error,
      long elapsedMs) {
    totalEvaluations.incrementAndGet();
    totalElapsedMs.addAndGet(elapsedMs);
    if (triggered) totalTriggered.incrementAndGet();
    if (error) totalErrors.incrementAndGet();
  }

  @Override
  public void recordBreakerState(String ruleCode, String state) {
    log.debug("[LiteRule-Metrics] 规则 {} 熔断状态: {}", ruleCode, state);
  }

  @Override
  public void recordTraceQueueSize(int queueSize) {
    log.debug("[LiteRule-Metrics] Trace 队列积压: {}", queueSize);
  }

  @Override
  public void recordRegisteredRules(int count) {
    this.registeredRules = count;
  }

  @Override
  public void recordEvaluatedRules(int count) {
    this.lastEvaluatedRules = count;
  }

  @Override
  public void recordSlowRule(String ruleCode, long elapsedMs, long thresholdMs) {
    log.debug(
        "[LiteRule-Metrics] 慢规则: rule={}, elapsed={}ms, threshold={}ms",
        ruleCode,
        elapsedMs,
        thresholdMs);
  }

  /**
   * 获取累计规则评估总次数。
   *
   * @return 累计评估次数
   */
  public long getTotalEvaluations() {
    return totalEvaluations.get();
  }

  /**
   * 获取累计规则命中（触发）次数。
   *
   * @return 累计触发次数
   */
  public long getTotalTriggered() {
    return totalTriggered.get();
  }

  /**
   * 获取累计规则执行错误次数。
   *
   * @return 累计错误次数
   */
  public long getTotalErrors() {
    return totalErrors.get();
  }

  /**
   * 获取累计规则执行耗时（毫秒）。
   *
   * @return 累计耗时（毫秒）
   */
  public long getTotalElapsedMs() {
    return totalElapsedMs.get();
  }

  public int getRegisteredRules() {
    return registeredRules;
  }

  public int getLastEvaluatedRules() {
    return lastEvaluatedRules;
  }
}
