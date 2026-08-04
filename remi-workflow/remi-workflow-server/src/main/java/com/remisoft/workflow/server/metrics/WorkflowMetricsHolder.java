package com.remisoft.workflow.server.metrics;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

/**
 * 流程引擎运行态 Metrics 静态持有者。
 *
 * <p>为流程引擎核心路径提供 Micrometer 指标注册与累加能力，
 * 通过静态方法方便业务代码（如 {@code DefaultFlowAdvancer}、{@code FlowTaskService}）埋点。
 *
 * <p>可测试设计：{@link #registry} 字段通过 {@link #bindTo(MeterRegistry)} 写入，
 * 单元测试中注入 {@code SimpleMeterRegistry} 即可验证计数器和计时器行为。
 *
 * <p>暴露的 Prometheus 指标：
 * <ul>
 *   <li>{@code workflow.start_total{process_def_key}} — 流程启动计数</li>
 *   <li>{@code workflow.task_complete_total{process_def_key}} — 任务完成计数</li>
 *   <li>{@code workflow.execution_duration{process_def_key}} — 流程平均执行耗时分布</li>
 *   <li>{@code workflow.task_timeout_total{process_def_key}} — 流程卡住/超时计数</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
public final class WorkflowMetricsHolder {

    private static final String METRIC_PREFIX = "workflow.";

    /** Micrometer 注册表（由 Spring 容器或测试初始化） */
    private static volatile MeterRegistry registry;

    /** Counter 实例缓存，避免重复构建 */
    private static final Map<String, Counter> counterCache = new ConcurrentHashMap<>();

    /** Timer 实例缓存，避免重复构建 */
    private static final Map<String, Timer> timerCache = new ConcurrentHashMap<>();

    private WorkflowMetricsHolder() {
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

    // ======================== 流程启动计数 ========================

    /**
     * 递增流程启动计数（{@code workflow.start_total}）。
     *
     * @param processDefKey 流程定义 KEY
     */
    public static void incrementStart(String processDefKey) {
        Counter counter = counterCache.computeIfAbsent(
                cacheKey("start_total", processDefKey),
                k -> Counter.builder(METRIC_PREFIX + "start_total")
                        .tags(Tags.of("process_def_key", safe(processDefKey)))
                        .register(registry));
        counter.increment();
    }

    // ======================== 任务完成计数 ========================

    /**
     * 递增任务完成计数（{@code workflow.task_complete_total}）。
     *
     * @param processDefKey 流程定义 KEY
     */
    public static void incrementTaskComplete(String processDefKey) {
        Counter counter = counterCache.computeIfAbsent(
                cacheKey("task_complete_total", processDefKey),
                k -> Counter.builder(METRIC_PREFIX + "task_complete_total")
                        .tags(Tags.of("process_def_key", safe(processDefKey)))
                        .register(registry));
        counter.increment();
    }

    // ======================== 流程平均执行耗时 ========================

    /**
     * 记录流程执行耗时（{@code workflow.execution_duration}）。
     *
     * @param processDefKey 流程定义 KEY
     * @param millis        执行耗时（毫秒）
     */
    public static void recordExecutionDuration(String processDefKey, long millis) {
        if (millis < 0) {
            return;
        }
        Timer timer = timerCache.computeIfAbsent(
                cacheKey("execution_duration", processDefKey),
                k -> Timer.builder(METRIC_PREFIX + "execution_duration")
                        .tags(Tags.of("process_def_key", safe(processDefKey)))
                        .register(registry));
        timer.record(Duration.ofMillis(millis));
    }

    // ======================== 流程卡住/超时计数 ========================

    /**
     * 递增流程卡住/超时计数（{@code workflow.task_timeout_total}）。
     *
     * @param processDefKey 流程定义 KEY
     */
    public static void incrementTaskTimeout(String processDefKey) {
        Counter counter = counterCache.computeIfAbsent(
                cacheKey("task_timeout_total", processDefKey),
                k -> Counter.builder(METRIC_PREFIX + "task_timeout_total")
                        .tags(Tags.of("process_def_key", safe(processDefKey)))
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
