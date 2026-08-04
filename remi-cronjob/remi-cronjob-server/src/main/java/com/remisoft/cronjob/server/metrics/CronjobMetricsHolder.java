package com.remisoft.cronjob.server.metrics;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

/**
 * 分布式调度引擎运行态 Metrics 静态持有者。
 *
 * <p>为调度引擎核心路径提供 Micrometer 指标注册与累加能力，
 * 通过静态方法方便业务代码（如 {@code JobScanner}、{@code DefaultTaskDispatcher}、
 * {@code AverageShardingStrategy}）埋点。
 *
 * <p>可测试设计：{@link #registry} 字段通过 {@link #bindTo(MeterRegistry)} 写入，
 * 单元测试中注入 {@code SimpleMeterRegistry} 即可验证计数器和计时器行为。
 *
 * <p>暴露的 Prometheus 指标：
 * <ul>
 *   <li>{@code cronjob.execution_total{job_name}} — 任务执行计数</li>
 *   <li>{@code cronjob.execution_duration{job_name}} — 任务执行耗时分布</li>
 *   <li>{@code cronjob.shard_success_total{job_name,shard_index}} — 分片成功计数</li>
 *   <li>{@code cronjob.shard_failure_total{job_name,shard_index}} — 分片失败计数</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
public final class CronjobMetricsHolder {

    private static final String METRIC_PREFIX = "cronjob.";

    /** Micrometer 注册表（由 Spring 容器或测试初始化） */
    private static volatile MeterRegistry registry;

    /** Counter 实例缓存，避免重复构建 */
    private static final Map<String, Counter> counterCache = new ConcurrentHashMap<>();

    /** Timer 实例缓存，避免重复构建 */
    private static final Map<String, Timer> timerCache = new ConcurrentHashMap<>();

    private CronjobMetricsHolder() {
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

    // ======================== 任务执行计数 ========================

    /**
     * 递增任务执行计数（{@code cronjob.execution_total}）。
     *
     * @param jobName 任务名称（job_name 标签）
     */
    public static void incrementExecution(String jobName) {
        Counter counter = counterCache.computeIfAbsent(
                cacheKey("execution_total", jobName),
                k -> Counter.builder(METRIC_PREFIX + "execution_total")
                        .tags(Tags.of("job_name", safe(jobName)))
                        .register(registry));
        counter.increment();
    }

    // ======================== 任务执行耗时 ========================

    /**
     * 记录任务执行耗时（{@code cronjob.execution_duration}）。
     *
     * @param jobName 任务名称
     * @param millis  执行耗时（毫秒）
     */
    public static void recordExecutionDuration(String jobName, long millis) {
        if (millis < 0) {
            return;
        }
        Timer timer = timerCache.computeIfAbsent(
                cacheKey("execution_duration", jobName),
                k -> Timer.builder(METRIC_PREFIX + "execution_duration")
                        .tags(Tags.of("job_name", safe(jobName)))
                        .register(registry));
        timer.record(Duration.ofMillis(millis));
    }

    // ======================== 分片成功计数 ========================

    /**
     * 递增分片成功计数（{@code cronjob.shard_success_total}）。
     *
     * @param jobName    任务名称
     * @param shardIndex 分片索引
     */
    public static void incrementShardSuccess(String jobName, int shardIndex) {
        String si = String.valueOf(shardIndex);
        Counter counter = counterCache.computeIfAbsent(
                cacheKey("shard_success_total", jobName, si),
                k -> Counter.builder(METRIC_PREFIX + "shard_success_total")
                        .tags(Tags.of("job_name", safe(jobName), "shard_index", si))
                        .register(registry));
        counter.increment();
    }

    // ======================== 分片失败计数 ========================

    /**
     * 递增分片失败计数（{@code cronjob.shard_failure_total}）。
     *
     * @param jobName    任务名称
     * @param shardIndex 分片索引
     */
    public static void incrementShardFailure(String jobName, int shardIndex) {
        String si = String.valueOf(shardIndex);
        Counter counter = counterCache.computeIfAbsent(
                cacheKey("shard_failure_total", jobName, si),
                k -> Counter.builder(METRIC_PREFIX + "shard_failure_total")
                        .tags(Tags.of("job_name", safe(jobName), "shard_index", si))
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
