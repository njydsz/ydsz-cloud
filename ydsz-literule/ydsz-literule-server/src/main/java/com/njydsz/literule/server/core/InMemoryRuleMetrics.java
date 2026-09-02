package com.njydsz.literule.server.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.literule.domain.enums.RuleSeverity;

/**
 * 内存版规则监控指标实现（降级/测试场景）。
 *
 * <p>当 Micrometer {@code MeterRegistry} 不可用时使用此实现， 仅维护内存计数器，不暴露 Prometheus 指标。
 *
 * <p>E3 增强：维护 per-rule 统计（评估/触发/错误/耗时）， 支持慢规则 Top N 与热点规则 Top N 看板查询。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Slf4j
public class InMemoryRuleMetrics implements RuleMetrics {

    /** 耗时样本环形桶容量 */
  private static final int SAMPLE_CAPACITY = 128;

  /** P99 分位量化 */
  private static final double P99_QUANTILE = 0.99;

  private final AtomicLong totalEvaluations = new AtomicLong(0);
  private final AtomicLong totalTriggered = new AtomicLong(0);
  private final AtomicLong totalErrors = new AtomicLong(0);
  private final AtomicLong totalElapsedMs = new AtomicLong(0);
  private volatile int registeredRules = 0;
  private volatile int lastEvaluatedRules = 0;

  /** 单规则统计（E3） */
  private static final class PerRuleStat {
    final LongAdder evaluations = new LongAdder();
    final LongAdder triggered = new LongAdder();
    final LongAdder errors = new LongAdder();
    final LongAdder elapsedMs = new LongAdder();
    volatile long maxMs = 0;
    /** 耗时样本（固定容量环形桶，用于 P99 估算） */
    final long[] samples = new long[SAMPLE_CAPACITY];
    volatile int sampleIndex = 0;
  }

  /** 规则编码 -> 单规则统计 */
  private final Map<String, PerRuleStat> perRuleStats = new ConcurrentHashMap<>();

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
    if (triggered) {
      totalTriggered.incrementAndGet();
    }
    if (error) {
      totalErrors.incrementAndGet();
    }
    if (ruleCode == null) {
      return;
    }
    // E3 per-rule 统计
    PerRuleStat stat = perRuleStats.computeIfAbsent(ruleCode, k -> new PerRuleStat());
    stat.evaluations.increment();
    if (triggered) {
      stat.triggered.increment();
    }
    if (error) {
      stat.errors.increment();
    }
    stat.elapsedMs.add(elapsedMs);
    if (elapsedMs > stat.maxMs) {
      stat.maxMs = elapsedMs;
    }
    // 环形采样（P99 估算）
    int idx = stat.sampleIndex;
    stat.samples[idx & (stat.samples.length - 1)] = elapsedMs;
    stat.sampleIndex = idx + 1;
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

  // ==================== E3 慢规则 / 热点规则看板 ====================

  @Override
  public List<RuleStatSnapshot> getSlowRuleStats(int topN) {
    return snapshotStats(topN, Comparator.comparingDouble(RuleStatSnapshot::avgMs).reversed());
  }

  @Override
  public List<RuleStatSnapshot> getHotRuleStats(int topN) {
    return snapshotStats(topN, Comparator.comparingLong(RuleStatSnapshot::evaluations).reversed());
  }

  /** 生成规则统计快照并按比较器排序 */
  private List<RuleStatSnapshot> snapshotStats(int topN, Comparator<RuleStatSnapshot> order) {
    int limit = topN > 0 ? topN : 10;
    List<RuleStatSnapshot> result = new ArrayList<>(perRuleStats.size());
    for (Map.Entry<String, PerRuleStat> entry : perRuleStats.entrySet()) {
      PerRuleStat stat = entry.getValue();
      long evaluations = stat.evaluations.sum();
      long elapsed = stat.elapsedMs.sum();
      double avgMs = evaluations > 0 ? (double) elapsed / evaluations : 0.0;
      result.add(
          new RuleStatSnapshot(
              entry.getKey(),
              evaluations,
              stat.triggered.sum(),
              stat.errors.sum(),
              avgMs,
              stat.maxMs,
              estimateP99(stat)));
    }
    result.sort(order);
    if (result.size() > limit) {
      return List.copyOf(result.subList(0, limit));
    }
    return List.copyOf(result);
  }

  /** 从环形样本估算 P99 耗时（样本不足时返回最大耗时） */
  private long estimateP99(PerRuleStat stat) {
    int count = Math.min(stat.sampleIndex, stat.samples.length);
    if (count == 0) {
      return 0;
    }
    long[] copy = new long[count];
    System.arraycopy(stat.samples, 0, copy, 0, count);
    Arrays.sort(copy);
    int p99Index = (int) Math.ceil(count * P99_QUANTILE) - 1;
    return copy[Math.max(0, p99Index)];
  }
}
