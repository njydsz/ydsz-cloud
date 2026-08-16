package com.njydsz.common.base.metrics;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

/**
 * 模块 Micrometer 持有者基类（静态工具模式）。
 *
 * <p>为不方便注入 Spring Bean 的核心引擎路径提供静态注册与累加能力。
 * 通过 {@link #bindTo(MeterRegistry)} 在应用启动时将 Spring 容器注入的 MeterRegistry 绑定到持有者，
 * 单元测试中注入 {@code SimpleMeterRegistry} 即可验证计数器和计时器行为。
 *
 * <p>子类放置模块指标前缀（如 {@code "cronjob."}）和业务语义方法，
 * 调用 {@link #registerCounter} / {@link #registerTimer} / {@link #recordDuration} 系列方法，
 * 无需关心注册表绑定与缓存去重。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * public final class CronjobMetricsHolder extends AbstractMetricsHolder {
 *
 *     private static final String METRIC_PREFIX = "cronjob.";
 *
 *     private CronjobMetricsHolder() {
 *         throw new UnsupportedOperationException("utility class");
 *     }
 *
 *     public static void incrementExecution(String jobName) {
 *         registerCounter(METRIC_PREFIX, "execution_total", "job_name", safe(jobName)).increment();
 *     }
 *
 *     public static void recordExecutionDuration(String jobName, long millis) {
 *         recordDuration(METRIC_PREFIX, "execution_duration", millis, "job_name", safe(jobName));
 *     }
 * }
 * }</pre>
 *
 * <p>注意：因共享机制使用静态字段，{@link #bindTo} 仅需调用一次（传入 Spring 容器中的 MeterRegistry 即可）。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.common.sentry.adapter.SentryMetricsAdapter 若可使用 Spring Bean 注入，推荐优先选择 SentryMetricsAdapter 基类
 */
public abstract class AbstractMetricsHolder {

    /** Micrometer 注册表（由 Spring 容器或测试初始化） */
    private static volatile MeterRegistry registry;

    /** Counter 实例缓存，避免重复构建 */
    private static final Map<String, Counter> counterCache = new ConcurrentHashMap<>();

    /** Timer 实例缓存，避免重复构建 */
    private static final Map<String, Timer> timerCache = new ConcurrentHashMap<>();

    /**
     * 工具构造器（禁止外部实例化）。
     *
     * <p>因父类构造器抛出异常，子类无需额外声明私有构造器，
     * 但建议在子类显式声明以保持代码可读性和反射安全。
     */
    protected AbstractMetricsHolder() {
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

    // ======================== 模板方法（子类通过静态继承调用） ========================

    /**
     * 注册或获取 Counter 指标（子类应通过自己的前缀调用本方法）。
     *
     * @param prefix 模块指标前缀（如 "cronjob."）
     * @param name   指标名称（不含前缀，如 "execution_total"）
     * @param tags   标签键值对（如 "job_name", "my_job"）
     * @return Counter 实例
     */
    protected static Counter registerCounter(String prefix, String name, String... tags) {
        String key = cacheKey(prefix + name, tags);
        return counterCache.computeIfAbsent(
                key,
                k -> Counter.builder(prefix + name)
                        .tags(Tags.of(tags))
                        .register(registry));
    }

    /**
     * 注册或获取 Timer 指标（子类应通过自己的前缀调用本方法）。
     *
     * @param prefix 模块指标前缀（如 "workflow."）
     * @param name   指标名称（不含前缀，如 "execution_duration"）
     * @param tags   标签键值对（如 "process_def_key", "my_process"）
     * @return Timer 实例
     */
    protected static Timer registerTimer(String prefix, String name, String... tags) {
        String key = cacheKey(prefix + name, tags);
        return timerCache.computeIfAbsent(
                key,
                k -> Timer.builder(prefix + name)
                        .tags(Tags.of(tags))
                        .register(registry));
    }

    /**
     * 记录耗时到 Timer 指标（负值自动忽略）。
     *
     * @param prefix 模块指标前缀
     * @param name   指标名称（不含前缀）
     * @param millis 耗时（毫秒）
     * @param tags   标签键值对
     */
    protected static void recordDuration(String prefix, String name, long millis, String... tags) {
        if (millis < 0) {
            return;
        }
        registerTimer(prefix, name, tags).record(Duration.ofMillis(millis));
    }

    // ======================== 内部工具 ========================

    /**
     * 生成带标签的缓存 key。
     *
     * @param name 已拼接前缀的指标全名
     * @param tags 标签值数组
     * @return 缓存 key 字符串
     */
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
