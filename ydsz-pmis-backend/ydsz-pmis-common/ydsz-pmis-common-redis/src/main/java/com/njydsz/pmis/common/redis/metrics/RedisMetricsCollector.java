package com.njydsz.pmis.common.redis.metrics;

import java.util.function.Supplier;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Redis 操作指标收集器
 *
 * <p>为 Redis 操作提供可选的 Micrometer 指标采集，仅当 Micrometer 在 classpath 中时启用。
 * 使用 {@link Timer.Sample} 模式进行低开销测量。
 *
 * <p>注册的指标：
 * <ul>
 *   <li>{@code redis.operation.latency} - Redis 操作延迟 Timer</li>
 *   <li>{@code redis.operation.errors} - Redis 操作错误 Counter</li>
 *   <li>{@code redis.operation.slow} - Redis 慢操作 Counter（P2 可观测性增强）</li>
 * </ul>
 *
 * <p>指标标签：
 * <ul>
 *   <li>{@code operation_type} - 操作类型（如 get, set, del 等）</li>
 *   <li>{@code error_type} - 错误类型（仅 errors 指标，如 ConnectionException, TimeoutException 等）</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * RedisMetricsCollector collector = RedisMetricsCollector.getOrCreate(registry);
 *
 * // 延迟指标
 * collector.recordOperation("get", () -> redisTemplate.opsForValue().get(key));
 *
 * // 错误指标
 * collector.recordError("set", "TimeoutException");
 *
 * // 带慢操作阈值的收集器
 * RedisMetricsCollector slowCollector = RedisMetricsCollector.getOrCreate(registry, 100);
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @since 1.0.0
 */
public class RedisMetricsCollector {

    private static final String METRIC_OPERATION_LATENCY = "redis.operation.latency";
    private static final String METRIC_OPERATION_ERRORS = "redis.operation.errors";
    private static final String METRIC_OPERATION_SLOW = "redis.operation.slow";
    private static final String TAG_OPERATION_TYPE = "operation_type";
    private static final String TAG_ERROR_TYPE = "error_type";

    /** Micrometer 指标注册表 */
    private final MeterRegistry registry;
    /** 慢操作阈值（毫秒），0 表示禁用 */
    private final long slowOperationThresholdMillis;

    private RedisMetricsCollector(MeterRegistry registry) {
        this(registry, 0);
    }

    /**
     * 构造 Redis 指标收集器（带慢操作阈值）
     *
     * @param registry                     MeterRegistry 实例
     * @param slowOperationThresholdMillis 慢操作阈值（毫秒），0 表示禁用慢操作检测
     */
    private RedisMetricsCollector(MeterRegistry registry, long slowOperationThresholdMillis) {
        this.registry = registry;
        this.slowOperationThresholdMillis = slowOperationThresholdMillis;
    }

    /**
     * 创建或获取 Redis 指标收集器
     *
     * @param registry MeterRegistry 实例
     * @return RedisMetricsCollector 实例
     */
    public static RedisMetricsCollector getOrCreate(MeterRegistry registry) {
        return new RedisMetricsCollector(registry);
    }

    /**
     * 创建或获取 Redis 指标收集器（带慢操作阈值）
     *
     * @param registry                     MeterRegistry 实例
     * @param slowOperationThresholdMillis 慢操作阈值（毫秒），0 表示禁用
     * @return RedisMetricsCollector 实例
     */
    public static RedisMetricsCollector getOrCreate(MeterRegistry registry, long slowOperationThresholdMillis) {
        return new RedisMetricsCollector(registry, slowOperationThresholdMillis);
    }

    /**
     * 记录 Redis 操作的延迟
     *
     * <p>使用 Timer.Sample 模式，开销极低。
     * 当配置了慢操作阈值时，超阈值的操作会递增 {@code redis.operation.slow} Counter。
     *
     * @param operationType 操作类型（如 get, set, del）
     * @param supplier      要执行的操作
     * @param <T>           操作返回值类型
     * @return 操作返回值
     */
    public <T> T recordOperation(String operationType, Supplier<T> supplier) {
        Timer.Sample sample = Timer.start(registry);
        long startTime = System.currentTimeMillis();
        try {
            return supplier.get();
        } finally {
            long elapsed = System.currentTimeMillis() - startTime;
            sample.stop(Timer.builder(METRIC_OPERATION_LATENCY)
                    .tag(TAG_OPERATION_TYPE, operationType)
                    .description("Redis operation latency")
                    .register(registry));
            // P2: 慢操作检测
            if (slowOperationThresholdMillis > 0 && elapsed >= slowOperationThresholdMillis) {
                recordSlowOperation(operationType, elapsed);
            }
        }
    }

    /**
     * 记录无返回值的 Redis 操作延迟
     *
     * <p>当配置了慢操作阈值时，超阈值的操作会递增 {@code redis.operation.slow} Counter。
     *
     * @param operationType 操作类型
     * @param runnable      要执行的操作
     */
    public void recordOperation(String operationType, Runnable runnable) {
        Timer.Sample sample = Timer.start(registry);
        long startTime = System.currentTimeMillis();
        try {
            runnable.run();
        } finally {
            long elapsed = System.currentTimeMillis() - startTime;
            sample.stop(Timer.builder(METRIC_OPERATION_LATENCY)
                    .tag(TAG_OPERATION_TYPE, operationType)
                    .description("Redis operation latency")
                    .register(registry));
            // P2: 慢操作检测
            if (slowOperationThresholdMillis > 0 && elapsed >= slowOperationThresholdMillis) {
                recordSlowOperation(operationType, elapsed);
            }
        }
    }

    /**
     * 记录 Redis 操作错误
     *
     * @param operationType 操作类型
     * @param errorType     错误类型（如异常类名）
     */
    public void recordError(String operationType, String errorType) {
        Counter.builder(METRIC_OPERATION_ERRORS)
                .tag(TAG_OPERATION_TYPE, operationType)
                .tag(TAG_ERROR_TYPE, errorType)
                .description("Redis operation errors")
                .register(registry)
                .increment();
    }

    /**
     * 记录 Redis 操作错误（从异常推断错误类型）
     *
     * @param operationType 操作类型
     * @param exception     异常实例
     */
    public void recordError(String operationType, Throwable exception) {
        String errorType = exception != null ? exception.getClass().getSimpleName() : "Unknown";
        recordError(operationType, errorType);
    }

    /**
     * 记录 Redis 慢操作（P2 可观测性增强）
     *
     * <p>当 Redis 操作耗时超过配置阈值时递增此计数器，
     * 标签包含操作类型，便于按操作维度告警。
     *
     * @param operationType 操作类型
     * @param duration      耗时（毫秒）
     */
    public void recordSlowOperation(String operationType, long duration) {
        Counter.builder(METRIC_OPERATION_SLOW)
                .tag(TAG_OPERATION_TYPE, operationType)
                .description("Redis slow operation count (exceeds configured threshold)")
                .register(registry)
                .increment();
    }

    /**
     * 获取慢操作阈值（毫秒）
     *
     * @return 慢操作阈值，0 表示禁用
     */
    public long getSlowOperationThresholdMillis() {
        return slowOperationThresholdMillis;
    }
}
