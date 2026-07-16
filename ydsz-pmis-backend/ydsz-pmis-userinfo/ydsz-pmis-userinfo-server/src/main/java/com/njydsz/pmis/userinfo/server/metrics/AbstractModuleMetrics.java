package com.njydsz.pmis.userinfo.server.metrics;

import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

/**
 * 模块级 Micrometer 指标基类（userinfo 模块本地版本）
 *
 * <p>原参考实现位于 ydsz-pmis-common-core.metrics 包，因 common 重构后该基类已迁移到各业务模块本地化。
 * 提供 Counter / Timer 的统一封装，自动按 {@code metricPrefix} 拼接指标名，
 * 避免各业务模块重复编写 {@code registry.counter("prefix_xxx")} 样板代码。
 *
 * <p>使用示例：
 * <pre>{@code
 * public class UserInfoMetrics extends AbstractModuleMetrics {
 *     public UserInfoMetrics(MeterRegistry registry, JdbcTemplate jdbc) {
 *         super(registry, "pmis_user_");
 *     }
 *     public void onLogin(String clientType) {
 *         incrementCounter("login_success_total", "client_type", clientType);
 *     }
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
public abstract class AbstractModuleMetrics {

    /** Micrometer 注册中心，子类通过 {@link #registry} 直接访问（如注册 Gauge） */
    protected final MeterRegistry registry;
    /** 指标名前缀（如 {@code "pmis_user_"}），子类 incrementCounter/recordTimer 自动拼接 */
    protected final String metricPrefix;

    protected AbstractModuleMetrics(MeterRegistry registry, String metricPrefix) {
        if (registry == null) {
            throw new IllegalArgumentException("MeterRegistry 不能为空");
        }
        this.registry = registry;
        this.metricPrefix = metricPrefix == null ? "" : metricPrefix;
    }

    /**
     * 注册一个无 Tag 的 Counter
     *
     * @param name 指标名（不含前缀）
     */
    protected void incrementCounter(String name) {
        Counter.builder(metricPrefix + name)
                .description(name)
                .register(registry)
                .increment();
    }

    /**
     * 注册一个带单 Tag 的 Counter
     *
     * @param name     指标名（不含前缀）
     * @param tagKey   Tag Key
     * @param tagValue Tag Value（null/blank 自动转为 {@code "unknown"}）
     */
    protected void incrementCounter(String name, String tagKey, String tagValue) {
        Counter.builder(metricPrefix + name)
                .description(name)
                .tags(Tags.of(tagKey, safe(tagValue)))
                .register(registry)
                .increment();
    }

    /**
     * 记录一次 Timer 耗时（单位：毫秒）
     *
     * @param name      指标名（不含前缀）
     * @param elapsedMs 耗时（毫秒）
     */
    protected void recordTimer(String name, long elapsedMs) {
        Timer.builder(metricPrefix + name)
                .description(name)
                .register(registry)
                .record(elapsedMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 将 null/blank 字符串规整为 {@code "unknown"}，避免 Micrometer 注册时 NPE
     *
     * @param value 原始值
     * @return 规整后的值
     */
    protected String safe(String value) {
        return (value == null || value.isBlank()) ? "unknown" : value;
    }
}
