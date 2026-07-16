package com.njydsz.literule.server.core;

import java.util.concurrent.TimeUnit;

import com.njydsz.literule.api.RuleSeverity;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

/**
 * 基于 Micrometer 的规则监控指标实现
 *
 * <p>当 classpath 中存在 {@link MeterRegistry} 时，由 {@code LiteRuleAutoConfiguration}
 * 自动装配，将所有规则指标暴露到 Prometheus。
 *
 * <p>暴露的 Prometheus 指标：
 * <ul>
 *   <li>{@code literule_rule_evaluations_total{rule_code,scenario,}} — 评估总次数</li>
 *   <li>{@code literule_rule_triggered_total{rule_code,severity,}} — 触发总次数</li>
 *   <li>{@code literule_rule_errors_total{rule_code,}} — 异常总次数</li>
 *   <li>{@code literule_rule_eval_duration_seconds{rule_code,}} — 评估耗时分布（P50/P95/P99）</li>
 *   <li>{@code literule_breaker_state{rule_code,state,}} — 熔断状态（0/1）</li>
 *   <li>{@code literule_trace_queue_size} — Trace 队列积压（Gauge）</li>
 *   <li>{@code literule_registered_rules} — 当前注册规则数（Gauge，用于评估 RETE 引入必要性）</li>
 *   <li>{@code literule_evaluated_rules} — 单次评估遍历规则数（Gauge）</li>
 * </ul>
 *
 * <p>不依赖任何 Spring 注解，可被 Spring Boot 以外的框架使用。
 *
 * @since 1.4.0
 */
public class MicrometerRuleMetrics extends RuleMetrics {

    private final MeterRegistry registry;
    private volatile int lastTraceQueueSize = 0;
    private volatile int lastRegisteredRules = 0;
    private volatile int lastEvaluatedRules = 0;

    public MicrometerRuleMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordEvaluation(String ruleCode, String scenario, boolean triggered,
                                  RuleSeverity severity, boolean error, long elapsedMs) {
        super.recordEvaluation(ruleCode, scenario, triggered, severity, error, elapsedMs);

        Tags tags = Tags.of("rule_code", ruleCode == null ? "unknown" : ruleCode)
                .and("scenario", scenario == null ? "DEFAULT" : scenario);

        registry.counter("literule_rule_evaluations_total", tags).increment();

        if (triggered) {
            Tags triggeredTags = Tags.of("rule_code", ruleCode == null ? "unknown" : ruleCode)
                    .and("severity", severity == null ? "INFO" : severity.getCode());
            registry.counter("literule_rule_triggered_total", triggeredTags).increment();
        }

        if (error) {
            registry.counter("literule_rule_errors_total",
                    Tags.of("rule_code", ruleCode == null ? "unknown" : ruleCode)).increment();
        }

        // 耗时分布（Timer 自动产出 P50/P95/P99）
        Timer timer = Timer.builder("literule_rule_eval_duration")
                .tag("rule_code", ruleCode == null ? "unknown" : ruleCode)
                .register(registry);
        timer.record(elapsedMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void recordBreakerState(String ruleCode, String state) {
        super.recordBreakerState(ruleCode, state);
        // 用 gauge 暴露熔断状态（0=CLOSED, 1=OPEN, 2=HALF_OPEN）
        int value = switch (state) {
            case "OPEN" -> 1;
            case "HALF_OPEN" -> 2;
            default -> 0;
        };
        registry.gauge("literule_breaker_state",
                Tags.of("rule_code", ruleCode == null ? "unknown" : ruleCode),
                value);
    }

    @Override
    public void recordTraceQueueSize(int queueSize) {
        super.recordTraceQueueSize(queueSize);
        lastTraceQueueSize = queueSize;
        registry.gauge("literule_trace_queue_size", Tags.empty(), lastTraceQueueSize);
    }

    @Override
    public void recordRegisteredRules(int count) {
        super.recordRegisteredRules(count);
        lastRegisteredRules = count;
        registry.gauge("literule_registered_rules", Tags.empty(), lastRegisteredRules);
    }

    @Override
    public void recordEvaluatedRules(int count) {
        super.recordEvaluatedRules(count);
        lastEvaluatedRules = count;
        registry.gauge("literule_evaluated_rules", Tags.empty(), lastEvaluatedRules);
    }

    /**
     * 暴露慢规则计数器到 Prometheus（P2-4）
     *
     * <p>指标：{@code literule_slow_rule_total{rule_code,}}
     */
    @Override
    public void recordSlowRule(String ruleCode, long elapsedMs, long thresholdMs) {
        super.recordSlowRule(ruleCode, elapsedMs, thresholdMs);
        registry.counter("literule_slow_rule_total",
                Tags.of("rule_code", ruleCode == null ? "unknown" : ruleCode)).increment();
    }
}
