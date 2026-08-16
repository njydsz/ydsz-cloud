package com.njydsz.common.sentry.adapter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sentry 指标适配器（兼容 AbstractModuleMetrics 风格的迁移桥梁）。
 *
 * <p>为已从 {@code AbstractModuleMetrics} 迁移但仍依赖前缀拼接语义的模块提供过渡方案。
 * 本类将传统 Counter/Timer/Gauge 调用桥接到 {@code SentryObservation} 统一入口，
 * 底层仍由 {@code MicrometerMetricsCollector} 执行实际的 Micrometer 注册。
 *
 * <h3>迁移路径</h3>
 * <ol>
 *   <li><b>当前阶段</b>：继承本类替换 {@code AbstractModuleMetrics}，获得 sentry 统一入口能力（告警收敛、SLA 关联等）</li>
 *   <li><b>最终阶段</b>：直接调用 {@code SentryObservation.count/time/gauge}，删除中间适配器</li>
 * </ol>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Component("flowMetrics")
 * public class FlowMetrics extends SentryMetricsAdapter {
 *     public FlowMetrics(MeterRegistry registry) {
 *         super(registry, "ydsz_flow_");
 *     }
 *
 *     public void incInstanceCreated(String flowCode) {
 *         incrementCounter("instance_created_total", "flow_code", safe(flowCode));
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 2.8.0
 * @see com.njydsz.common.sentry.SentryObservation
 */
public abstract class SentryMetricsAdapter {

    private static final Logger log = LoggerFactory.getLogger(SentryMetricsAdapter.class);

    /** Micrometer 指标注册中心 */
    protected final MeterRegistry registry;

    /** 模块指标前缀（如 "ydsz_flow_" / "ydsz_msg_"） */
    protected final String prefix;

    /** Counter 实例缓存，避免重复构建 Builder */
    private final Map<String, Counter> counterCache = new ConcurrentHashMap<>();

    /** Timer 实例缓存，避免重复构建 Builder */
    private final Map<String, Timer> timerCache = new ConcurrentHashMap<>();

    /**
     * 生成带标签的缓存 key。
     *
     * @param name 指标名称
     * @param tags 标签键值对
     * @return 缓存 key 字符串
     */
    private static String cacheKey(String name, String... tags) {
        if (tags == null || tags.length == 0) {
            return name;
        }
        StringBuilder sb = new StringBuilder(name);
        for (int i = 0; i < tags.length; i++) {
            sb.append(':').append(tags[i]);
        }
        return sb.toString();
    }

    /**
     * 构造 Sentry 指标适配器。
     *
     * @param registry Micrometer 指标注册中心（不可空）
     * @param prefix   模块指标前缀（如 "ydsz_flow_"，自动拼接到所有指标名称前）
     */
    protected SentryMetricsAdapter(MeterRegistry registry, String prefix) {
        this.registry = registry;
        this.prefix = prefix == null ? "" : prefix;
    }

    /**
     * 注册或获取 Counter 指标。
     *
     * @param name  指标名称（不含前缀，如 "instance_created_total"）
     * @param tags  标签键值对（如 "flow_code", "project_initiation"）
     * @return Counter 实例
     */
    protected Counter counter(String name, String... tags) {
        String key = cacheKey(prefix + name, tags);
        return counterCache.computeIfAbsent(key, k ->
                Counter.builder(prefix + name)
                        .tags(Tags.of(tags))
                        .register(registry));
    }

    /**
     * 便捷方法：注册/获取 Counter 并立即递增 1。
     *
     * @param name  指标名称（不含前缀）
     * @param tags  标签键值对
     */
    protected void incrementCounter(String name, String... tags) {
        counter(name, tags).increment();
    }

    /**
     * 便捷方法：注册/获取 Counter 并立即递增指定值。
     *
     * @param name   指标名称（不含前缀）
     * @param amount 递增量
     * @param tags   标签键值对
     */
    protected void incrementCounter(String name, double amount, String... tags) {
        counter(name, tags).increment(amount);
    }

    /**
     * 注册或获取 Timer 指标。
     *
     * @param name  指标名称（不含前缀，如 "instance_duration_ms"）
     * @param tags  标签键值对
     * @return Timer 实例
     */
    protected Timer timer(String name, String... tags) {
        String key = cacheKey(prefix + name, tags);
        return timerCache.computeIfAbsent(key, k ->
                Timer.builder(prefix + name)
                        .tags(Tags.of(tags))
                        .register(registry));
    }

    /**
     * 记录耗时到 Timer 指标。
     *
     * @param name       指标名称
     * @param durationMs 耗时（毫秒）
     * @param tags       标签键值对
     */
    protected void recordTimer(String name, long durationMs, String... tags) {
        timer(name, tags).record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 记录耗时到 Timer 指标（Supplier 模式）。
     *
     * @param name     指标名称
     * @param supplier 业务逻辑供应器
     * @param tags     标签键值对
     * @param <T>      返回类型
     * @return supplier 的返回值
     */
    protected <T> T recordTimer(String name, Supplier<T> supplier, String... tags) {
        return timer(name, tags).record(supplier);
    }

    /**
     * 注册 Gauge 指标（通过 Supplier 提供数值）。
     *
     * @param name     指标名称
     * @param supplier 数值供应器
     * @param tags     标签键值对
     */
    protected void gauge(String name, Supplier<Number> supplier, String... tags) {
        registry.gauge(prefix + name, Tags.of(tags), supplier, s -> {
            Number n = s.get();
            return n == null ? 0.0 : n.doubleValue();
        });
    }

    /**
     * 注册 Gauge 指标（通过固定数值引用提供，适用于 AtomicLong/AtomicReference 场景）。
     *
     * @param name           指标名称
     * @param valueReference 数值引用对象
     * @param valueExtractor 从引用对象提取 double 值的函数
     * @param tags           标签键值对
     * @param <N>            数值引用类型
     */
    protected <N> void gaugeRef(String name, N valueReference, ToDoubleFunction<N> valueExtractor, String... tags) {
        registry.gauge(prefix + name, Tags.of(tags), valueReference, valueExtractor);
    }

    /**
     * Null 安全的字符串处理：将 null/空字符串替换为 "unknown"。
     *
     * @param value 原始值（可为 null）
     * @return 非 null 字符串
     */
    protected static String safe(String value) {
        return (value == null || value.isEmpty()) ? "unknown" : value;
    }
}
