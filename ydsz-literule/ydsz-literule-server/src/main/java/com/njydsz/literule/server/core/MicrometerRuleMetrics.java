package com.njydsz.literule.server.core;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

import com.njydsz.common.sentry.adapter.SentryMetricsAdapter;
import com.njydsz.literule.api.RuleSeverity;

/**
 * 基于 Micrometer 的规则监控指标实现。
 *
 * <p>P1-5: 继承 {@link SentryMetricsAdapter} 统一指标基类，满足 ArchUnit R25 架构规则。 同时实现 {@link RuleMetrics}
 * 接口，保持与规则引擎的依赖契约不变。
 *
 * <p>当 classpath 中存在 {@link MeterRegistry} 时，由 {@code LiteRuleAutoConfiguration} 自动装配，将所有规则指标暴露到
 * Prometheus。
 *
 * <p>暴露的 Prometheus 指标：
 *
 * <ul>
 *   <li>{@code ydsz_literule_rule_evaluations_total{rule_code,scenario}} — 评估总次数
 *   <li>{@code ydsz_literule_rule_triggered_total{rule_code,severity}} — 触发总次数
 *   <li>{@code ydsz_literule_rule_errors_total{rule_code}} — 异常总次数
 *   <li>{@code ydsz_literule_rule_eval_duration{rule_code}} — 评估耗时分布（P50/P95/P99）
 *   <li>{@code ydsz_literule_breaker_state{rule_code}} — 熔断状态（0/1/2）
 *   <li>{@code ydsz_literule_trace_queue_size} — Trace 队列积压（Gauge）
 *   <li>{@code ydsz_literule_registered_rules} — 当前注册规则数（Gauge）
 *   <li>{@code ydsz_literule_evaluated_rules} — 单次评估遍历规则数（Gauge）
 *   <li>{@code ydsz_literule_slow_rule_total{rule_code}} — 慢规则计数
 * </ul>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@ConditionalOnClass(MeterRegistry.class)
public class MicrometerRuleMetrics extends SentryMetricsAdapter implements RuleMetrics {

  private final AtomicInteger lastTraceQueueSize = new AtomicInteger(0);
  private final AtomicInteger lastRegisteredRules = new AtomicInteger(0);
  private final AtomicInteger lastEvaluatedRules = new AtomicInteger(0);

  /** 累计评估/触发/异常计数（健康检查读取入口，与 Prometheus Counter 双写） */
  private final AtomicLong totalEvaluations = new AtomicLong(0);

  private final AtomicLong totalTriggered = new AtomicLong(0);
  private final AtomicLong totalErrors = new AtomicLong(0);

  public MicrometerRuleMetrics() {
    super("ydsz_literule_");
    gaugeRef("trace_queue_size", lastTraceQueueSize, AtomicInteger::doubleValue);
    gaugeRef("registered_rules", lastRegisteredRules, AtomicInteger::doubleValue);
    gaugeRef("evaluated_rules", lastEvaluatedRules, AtomicInteger::doubleValue);
  }

  @Override
  public void recordEvaluation(
      String ruleCode,
      String scenario,
      boolean triggered,
      RuleSeverity severity,
      boolean error,
      long elapsedMs) {
    incrementCounter(
        "rule_evaluations_total", "rule_code", safe(ruleCode), "scenario", safe(scenario));
    totalEvaluations.incrementAndGet();

    if (triggered) {
      incrementCounter(
          "rule_triggered_total",
          "rule_code",
          safe(ruleCode),
          "severity",
          severity == null ? "INFO" : severity.getCode());
      totalTriggered.incrementAndGet();
    }

    if (error) {
      incrementCounter("rule_errors_total", "rule_code", safe(ruleCode));
      totalErrors.incrementAndGet();
    }

    recordTimer("rule_eval_duration", elapsedMs, "rule_code", safe(ruleCode));
  }

  @Override
  public void recordBreakerState(String ruleCode, String state) {
    int value =
        switch (state) {
          case "OPEN" -> 1;
          case "HALF_OPEN" -> 2;
          default -> 0;
        };
    gaugeRef(
        "breaker_state",
        new AtomicInteger(value),
        AtomicInteger::doubleValue,
        "rule_code",
        safe(ruleCode));
  }

  @Override
  public void recordTraceQueueSize(int queueSize) {
    lastTraceQueueSize.set(queueSize);
  }

  @Override
  public void recordRegisteredRules(int count) {
    lastRegisteredRules.set(count);
  }

  @Override
  public void recordEvaluatedRules(int count) {
    lastEvaluatedRules.set(count);
  }

  @Override
  public void recordSlowRule(String ruleCode, long elapsedMs, long thresholdMs) {
    incrementCounter("slow_rule_total", "rule_code", safe(ruleCode));
  }

  @Override
  public long getTotalEvaluations() {
    return totalEvaluations.get();
  }

  @Override
  public long getTotalTriggered() {
    return totalTriggered.get();
  }

  @Override
  public long getTotalErrors() {
    return totalErrors.get();
  }

  @Override
  public int getRegisteredRules() {
    return lastRegisteredRules.get();
  }

  @Override
  public int getLastEvaluatedRules() {
    return lastEvaluatedRules.get();
  }
}
