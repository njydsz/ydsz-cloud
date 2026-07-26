package com.njydsz.literule.server.core;

import java.util.concurrent.TimeUnit;

import com.njydsz.literule.api.RuleSeverity;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

/**
 * 基于 Micrometer 的规则监控指标实现。
 *
 * <p>P0-2 架构优化：统一指标前缀 {@code ydsz_literule_}（与 AbstractModuleMetrics 约定一致），
 * 新增 {@link #safe(String)} null 安全方法消除重复三元表达式。
 *
 * <p><b>双轨制设计说明</b>：本类继承 {@link RuleMetrics}（内存计数器基类），
 * 无法同时继承 {@code AbstractModuleMetrics}（Java 单继承限制）。
 * 因此采用约定一致方式：指标前缀 {@code ydsz_literule_} + safe() 方法，
 * 与 FlowMetrics / CronjobMetrics 等模块保持命名和风格统一。
 *
 * <p>当 classpath 中存在 {@link MeterRegistry} 时，由 {@code LiteRuleAutoConfiguration}
 * 自动装配，将所有规则指标暴露到 Prometheus。
 *
 * <p>暴露的 Prometheus 指标：
 * <ul>
 *   <li>{@code ydsz_literule_rule_evaluations_total{rule_code,scenario}} — 评估总次数</li>
 *   <li>{@code ydsz_literule_rule_triggered_total{rule_code,severity}} — 触发总次数</li>
 *   <li>{@code ydsz_literule_rule_errors_total{rule_code}} — 异常总次数</li>
 *   <li>{@code ydsz_literule_rule_eval_duration{rule_code}} — 评估耗时分布（P50/P95/P99）</li>
 *   <li>{@code ydsz_literule_breaker_state{rule_code}} — 熔断状态（0/1/2）</li>
 *   <li>{@code ydsz_literule_trace_queue_size} — Trace 队列积压（Gauge）</li>
 *   <li>{@code ydsz_literule_registered_rules} — 当前注册规则数（Gauge）</li>
 *   <li>{@code ydsz_literule_evaluated_rules} — 单次评估遍历规则数（Gauge）</li>
 *   <li>{@code ydsz_literule_slow_rule_total{rule_code}} — 慢规则计数</li>
 * </ul>
 *
 * <p><b>命名变更说明</b>：原 {@code literule_*} 指标名统一加 {@code ydsz_} 前缀，
 * 与其他业务模块保持一致。Grafana 看板需同步更新指标名。
 *
 * @since 1.0.0
 */
public class MicrometerRuleMetrics extends RuleMetrics {

    private static final String PREFIX = "ydsz_literule_";

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

        Tags tags = Tags.of("rule_code", safe(ruleCode))
                .and("scenario", safe(scenario));

        registry.counter(PREFIX + "rule_evaluations_total", tags).increment();

        if (triggered) {
            Tags triggeredTags = Tags.of("rule_code", safe(ruleCode))
                    .and("severity", severity == null ? "INFO" : severity.getCode());
            registry.counter(PREFIX + "rule_triggered_total", triggeredTags).increment();
        }

        if (error) {
            registry.counter(PREFIX + "rule_errors_total",
                    Tags.of("rule_code", safe(ruleCode))).increment();
        }

        Timer.builder(PREFIX + "rule_eval_duration")
                .tag("rule_code", safe(ruleCode))
                .register(registry)
                .record(elapsedMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void recordBreakerState(String ruleCode, String state) {
        super.recordBreakerState(ruleCode, state);
        int value = switch (state) {
            case "OPEN" -> 1;
            case "HALF_OPEN" -> 2;
            default -> 0;
        };
        registry.gauge(PREFIX + "breaker_state",
                Tags.of("rule_code", safe(ruleCode)),
                value);
    }

    @Override
    public void recordTraceQueueSize(int queueSize) {
        super.recordTraceQueueSize(queueSize);
        lastTraceQueueSize = queueSize;
        registry.gauge(PREFIX + "trace_queue_size", Tags.empty(), lastTraceQueueSize);
    }

    @Override
    public void recordRegisteredRules(int count) {
        super.recordRegisteredRules(count);
        lastRegisteredRules = count;
        registry.gauge(PREFIX + "registered_rules", Tags.empty(), lastRegisteredRules);
    }

    @Override
    public void recordEvaluatedRules(int count) {
        super.recordEvaluatedRules(count);
        lastEvaluatedRules = count;
        registry.gauge(PREFIX + "evaluated_rules", Tags.empty(), lastEvaluatedRules);
    }

    @Override
    public void recordSlowRule(String ruleCode, long elapsedMs, long thresholdMs) {
        super.recordSlowRule(ruleCode, elapsedMs, thresholdMs);
        registry.counter(PREFIX + "slow_rule_total",
                Tags.of("rule_code", safe(ruleCode))).increment();
    }

    private static String safe(String value) {
        return (value == null || value.isEmpty()) ? "unknown" : value;
    }
}
