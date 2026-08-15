package com.njydsz.literule.server.metrics;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

/**
 * 规则引擎运行态 Metrics 静态持有者。
 *
 * <p>为规则引擎核心路径提供 Micrometer 指标注册与累加能力，
 * 通过静态方法方便业务代码（如 {@code DefaultRuleEngine}、{@code ParallelRuleEvaluator}）埋点。
 *
 * <p>可测试设计：{@link #registry} 字段通过 {@link #bindTo(MeterRegistry)} 写入，
 * 单元测试中注入 {@code SimpleMeterRegistry} 即可验证计数器和计时器行为。
 *
 * <p>暴露的 Prometheus 指标：
 * <ul>
 *   <li>{@code literule.hit_total{rule_id,tag}} — 规则命中计数</li>
 *   <li>{@code literule.evaluation_duration{rule_id}} — 规则评估耗时分布</li>
 *   <li>{@code literule.error_total{rule_id}} — 规则评估失败计数</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class LiteruleMetricsHolder {

    private static final String METRIC_PREFIX = "literule.";

    /** Micrometer 注册表（由 Spring 容器或测试初始化） */
    private static volatile MeterRegistry registry;

    /** Counter 实例缓存，避免重复构建 */
    private static final Map<String, Counter> counterCache = new ConcurrentHashMap<>();

    /** Timer 实例缓存，避免重复构建 */
    private static final Map<String, Timer> timerCache = new ConcurrentHashMap<>();

    private LiteruleMetricsHolder() {
        throw new UnsupportedOperationException("utility class");
    }

    /**
     * 绑定 Micrometer 注册表（启动时由 Spring 容器调用或测试手动注入）。
     *
     * @param reg Micrometer MeterRegistry
     */
    public static void bindTo(MeterRegistry reg) {
        registry = reg;
    }

    /**
     * 获取当前绑定的 MeterRegistry（用于测试验证或空判断）。
     *
     * @return 当前 MeterRegistry，可能为 null
     */
    public static MeterRegistry getRegistry() {
        return registry;
    }

    // ======================== 规则命中计数 ========================

    /**
     * 递增规则命中计数（{@code literule.hit_total}）。
     *
     * @param ruleId 规则编码（rule_id 标签）
     * @param tag    场景/标签（tag 标签，如 "DEFAULT" / "APPROVE"）
     */
    public static void incrementHit(String ruleId, String tag) {
        Counter counter = counterCache.computeIfAbsent(
                cacheKey("hit_total", ruleId, tag),
                k -> Counter.builder(METRIC_PREFIX + "hit_total")
                        .tags(Tags.of("rule_id", safe(ruleId), "tag", safe(tag)))
                        .register(registry));
        counter.increment();
    }

    // ======================== 规则评估耗时 ========================

    /**
     * 记录规则评估耗时（{@code literule.evaluation_duration}）。
     *
     * @param ruleId 规则编码
     * @param millis 评估耗时（毫秒）
     */
    public static void recordEvaluationDuration(String ruleId, long millis) {
        if (millis < 0) {
            return;
        }
        Timer timer = timerCache.computeIfAbsent(
                cacheKey("evaluation_duration", ruleId),
                k -> Timer.builder(METRIC_PREFIX + "evaluation_duration")
                        .tags(Tags.of("rule_id", safe(ruleId)))
                        .register(registry));
        timer.record(Duration.ofMillis(millis));
    }

    // ======================== 规则评估失败计数 ========================

    /**
     * 递增规则评估失败计数（{@code literule.error_total}）。
     *
     * @param ruleId 规则编码
     */
    public static void incrementError(String ruleId) {
        Counter counter = counterCache.computeIfAbsent(
                cacheKey("error_total", ruleId),
                k -> Counter.builder(METRIC_PREFIX + "error_total")
                        .tags(Tags.of("rule_id", safe(ruleId)))
                        .register(registry));
        counter.increment();
    }

    // ======================== 内部工具 ========================

    private static String cacheKey(String name, String... tags) {
        if (tags == null || tags.length == 0) {
            return name;
        }
        StringBuilder sb = new StringBuilder(name);
        for (String tag : tags) {
            sb.append(':').append(tag);
        }
        return sb.toString();
    }

    private static String safe(String value) {
        return (value == null || value.isEmpty()) ? "unknown" : value;
    }
}
