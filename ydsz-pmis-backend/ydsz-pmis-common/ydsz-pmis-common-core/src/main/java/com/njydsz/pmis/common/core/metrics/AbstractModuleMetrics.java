package com.njydsz.pmis.common.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Tags;

import java.time.Duration;

/**
 * 模块级 Prometheus 指标抽象基类。
 *
 * <p>封装 Micrometer MeterRegistry，提供统一的指标前缀和便捷的 Counter/Timer 创建方法。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public abstract class AbstractModuleMetrics {

    /** Micrometer 指标注册表 */
    protected final MeterRegistry registry;

    /** 指标名前缀（如 "pmis_cronjob_"） */
    private final String prefix;

    /**
     * @param registry Micrometer 指标注册表
     * @param prefix   指标名前缀
     */
    protected AbstractModuleMetrics(MeterRegistry registry, String prefix) {
        this.registry = registry;
        this.prefix = prefix;
    }

    /**
     * 创建或获取 Counter。
     *
     * @param name 指标名（不含前缀）
     * @param tags 标签键值对（key1, value1, key2, value2, ...）
     * @return Counter 实例
     */
    protected Counter counter(String name, String... tags) {
        return Counter.builder(prefix + name)
                .tags(Tags.of(tags))
                .register(registry);
    }

    /**
     * 创建或获取 Timer。
     *
     * @param name 指标名（不含前缀）
     * @param tags 标签键值对（key1, value1, key2, value2, ...）
     * @return Timer 实例
     */
    protected Timer timer(String name, String... tags) {
        return Timer.builder(prefix + name)
                .tags(Tags.of(tags))
                .register(registry);
    }

    /**
     * 递增 Counter 指标（便捷方法）。
     *
     * <p>自动拼接前缀，并通过 tags 传递标签键值对。
     *
     * @param name 指标名（不含前缀）
     * @param tags 标签键值对（key1, value1, key2, value2, ...）
     */
    protected void incrementCounter(String name, String... tags) {
        Counter.builder(prefix + name)
                .tags(Tags.of(tags))
                .register(registry)
                .increment();
    }

    /**
     * 记录 Timer 指标（便捷方法）。
     *
     * <p>自动拼接前缀，并将毫秒值转换为 Duration。
     *
     * @param name      指标名（不含前缀）
     * @param elapsedMs 耗时（毫秒）
     * @param tags      标签键值对（key1, value1, key2, value2, ...）
     */
    protected void recordTimer(String name, long elapsedMs, String... tags) {
        Timer.builder(prefix + name)
                .tags(Tags.of(tags))
                .register(registry)
                .record(Duration.ofMillis(elapsedMs));
    }

    /**
     * 安全处理 null 或空字符串，返回 "unknown"。
     *
     * @param value 原始值
     * @return 安全值
     */
    protected String safe(String value) {
        return (value == null || value.isEmpty()) ? "unknown" : value;
    }

    /**
     * 获取指标名前缀。
     *
     * @return 前缀字符串
     */
    public String getPrefix() {
        return prefix;
    }
}
