package com.njydsz.literule.server.core;

import java.util.List;

import com.njydsz.literule.api.RuleSeverity;

/**
 * 规则引擎监控指标接口。
 *
 * <p>P1-5: 从类重构为接口，解决 Java 单继承限制。 {@link MicrometerRuleMetrics} 可同时继承 {@code SentryMetricsAdapter}
 * 和实现本接口， 满足 ArchUnit R25 架构规则（所有 *Metrics 类必须继承 SentryMetricsAdapter）。
 *
 * <p>双轨制实现：
 *
 * <ul>
 *   <li>{@link MicrometerRuleMetrics} — Micrometer 实现，暴露 Prometheus 指标
 *   <li>{@link InMemoryRuleMetrics} — 内存计数器实现，测试/降级场景使用
 * </ul>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public interface RuleMetrics {

  /** 记录单次评估 */
  void recordEvaluation(
      String ruleCode,
      String scenario,
      boolean triggered,
      RuleSeverity severity,
      boolean error,
      long elapsedMs);

  /** 记录熔断状态 */
  void recordBreakerState(String ruleCode, String state);

  /** 记录 Trace 队列积压 */
  void recordTraceQueueSize(int queueSize);

  /** 记录当前注册规则数 */
  void recordRegisteredRules(int count);

  /** 记录单次评估遍历的规则数 */
  void recordEvaluatedRules(int count);

  /** 记录慢规则告警 */
  void recordSlowRule(String ruleCode, long elapsedMs, long thresholdMs);

  /**
   * 获取累计评估次数（健康检查读取入口）
   *
   * @return 累计评估次数
   * @since 1.0.0
   */
  long getTotalEvaluations();

  /**
   * 获取累计触发次数（健康检查读取入口）
   *
   * @return 累计触发次数
   * @since 1.0.0
   */
  long getTotalTriggered();

  /**
   * 获取累计异常次数（健康检查读取入口）
   *
   * @return 累计异常次数
   * @since 1.0.0
   */
  long getTotalErrors();

  /**
   * 获取当前注册规则数（健康检查读取入口）
   *
   * @return 当前注册规则数
   * @since 1.0.0
   */
  int getRegisteredRules();

  /**
   * 获取最近一次评估遍历的规则数（统计快照读取入口）
   *
   * @return 最近一次评估遍历的规则数
   * @since 1.0.0
   */
  int getLastEvaluatedRules();

  // ==================== E3 慢规则 / 热点规则看板 ====================

  /**
   * 慢规则 Top N（E3 看板）
   *
   * <p>按平均耗时倒序返回规则级统计。默认实现返回空列表（Micrometer 场景建议通过 Prometheus/Grafana 查询）；
   * 内存实现（{@link InMemoryRuleMetrics}）提供完整统计。
   *
   * @param topN 返回条数
   * @return 规则统计快照列表（按平均耗时倒序）
   * @since 1.0.0
   */
  default List<RuleStatSnapshot> getSlowRuleStats(int topN) {
    return List.of();
  }

  /**
   * 热点规则 Top N（E3 看板）
   *
   * <p>按评估次数倒序返回规则级统计。默认实现返回空列表； 内存实现（{@link InMemoryRuleMetrics}）提供完整统计。
   *
   * @param topN 返回条数
   * @return 规则统计快照列表（按评估次数倒序）
   * @since 1.0.0
   */
  default List<RuleStatSnapshot> getHotRuleStats(int topN) {
    return List.of();
  }

  /**
   * 规则级统计快照（E3 看板数据单元）
   *
   * @param ruleCode 规则编码
   * @param evaluations 评估次数
   * @param triggered 触发次数
   * @param errors 错误次数
   * @param avgMs 平均耗时（毫秒）
   * @param maxMs 最大耗时（毫秒）
   * @param p99Ms P99 耗时（毫秒）
   */
  record RuleStatSnapshot(
      String ruleCode,
      long evaluations,
      long triggered,
      long errors,
      double avgMs,
      long maxMs,
      long p99Ms) {}
}
