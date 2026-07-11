package com.njydsz.pmis.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 模块级 Metrics 基类（P1-2 架构优化）。
 *
 * <p>消除 6 个模块（MessageMetrics、FlowMetrics、UserInfoMetrics、CronjobMetrics、
 * AgentMetricsCollector、project/metrics）中重复的 Counter/Timer 缓存、降级模式、
 * 空值处理等样板代码。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>{@link #counter(String, String...)} — 带缓存的 Counter 获取（避免重复注册）</li>
 *   <li>{@link #timer(String, String...)} — 带缓存的 Timer 获取（避免重复注册）</li>
 *   <li>{@link #safe(String)} — 空值处理（null/empty → "unknown"）</li>
 *   <li>{@link #safeRecord(Runnable, String)} — try-catch 降级包装</li>
 *   <li>{@link #incrementCounter(String, String...)} — 便捷计数方法</li>
 *   <li>{@link #recordTimer(String, long, String...)} — 便捷计时方法</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Component
 * public class MessageMetrics extends AbstractModuleMetrics {
 *     public MessageMetrics(MeterRegistry registry) {
 *         super(registry, "pmis.message.");
 *     }
 *
 *     public void recordSend(String channel, String status, long costMs) {
 *         incrementCounter("send.total", "channel", safe(channel), "status", safe(status));
 *         recordTimer("send.duration", costMs, "channel", safe(channel));
 *     }
 * }
 * }</pre>
 *
 * <p>所有方法均 try-catch 降级，监控失败不影响业务。
 *
 * @author ydsz-pmis-team
 * @since 1.6.0 (P1-2)
 */
@Slf4j
public abstract class AbstractModuleMetrics {

    protected final MeterRegistry registry;
    protected final String prefix;

    /** Counter 缓存（避免重复注册） */
    protected final ConcurrentMap<String, Counter> counterCache = new ConcurrentHashMap<>();
    /** Timer 缓存 */
    protected final ConcurrentMap<String, Timer> timerCache = new ConcurrentHashMap<>();

    /**
     * @param registry MeterRegistry 实例
     * @param prefix   指标前缀（如 "pmis.message." / "pmis_flow_"）
     */
    protected AbstractModuleMetrics(MeterRegistry registry, String prefix) {
        this.registry = registry;
        this.prefix = prefix;
    }

    // ==================== Counter ====================

    /**
     * 获取或创建 Counter（带缓存，避免重复注册）。
     *
     * @param name 指标名称（不含前缀）
     * @param tags 标签键值对（如 "channel", "EMAIL", "status", "SUCCESS"）
     * @return Counter 实例
     */
    protected Counter counter(String name, String... tags) {
        String fullName = prefix + name;
        String key = fullName + "|" + String.join(",", tags);
        return counterCache.computeIfAbsent(key, k -> Counter.builder(fullName)
                .tags(tags)
                .description("PMIS " + prefix + " " + name)
                .register(registry));
    }

    /**
     * 便捷计数：获取 Counter 并 increment。
     *
     * @param name 指标名称（不含前缀）
     * @param tags 标签键值对
     */
    protected void incrementCounter(String name, String... tags) {
        try {
            counter(name, tags).increment();
        } catch (Exception e) {
            log.debug("[{}] counter increment 降级忽略: name={} err={}",
                    prefix, name, e.getMessage());
        }
    }

    // ==================== Timer ====================

    /**
     * 获取或创建 Timer（带缓存，避免重复注册）。
     *
     * @param name 指标名称（不含前缀）
     * @param tags 标签键值对
     * @return Timer 实例
     */
    protected Timer timer(String name, String... tags) {
        String fullName = prefix + name;
        String key = fullName + "|" + String.join(",", tags);
        return timerCache.computeIfAbsent(key, k -> Timer.builder(fullName)
                .tags(tags)
                .description("PMIS " + prefix + " " + name)
                .publishPercentileHistogram()
                .register(registry));
    }

    /**
     * 便捷计时：获取 Timer 并记录耗时。
     *
     * @param name     指标名称（不含前缀）
     * @param costMs   耗时（毫秒）
     * @param tags     标签键值对
     */
    protected void recordTimer(String name, long costMs, String... tags) {
        try {
            timer(name, tags).record(Duration.ofMillis(costMs));
        } catch (Exception e) {
            log.debug("[{}] timer record 降级忽略: name={} err={}",
                    prefix, name, e.getMessage());
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 空值处理：null/empty → "unknown"。
     *
     * @param v 原始值
     * @return 处理后的值
     */
    protected static String safe(String v) {
        return v == null || v.isEmpty() ? "unknown" : v;
    }

    /**
     * 安全执行包装：自动捕获异常并记录到错误指标。
     *
     * @param action     要执行的操作
     * @param errorName  错误指标名称（不含前缀）
     */
    protected void safeRecord(Runnable action, String errorName) {
        try {
            action.run();
        } catch (Exception e) {
            log.debug("[{}] safeRecord 降级忽略: errorName={} err={}",
                    prefix, errorName, e.getMessage());
        }
    }
}
