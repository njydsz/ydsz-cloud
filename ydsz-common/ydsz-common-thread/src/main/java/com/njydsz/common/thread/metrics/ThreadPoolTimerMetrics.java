package com.njydsz.common.thread.metrics;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.lang.Nullable;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

/**
 * 平台线程池耗时指标绑定器。
 *
 * <p>向 Micrometer 注册执行耗时与队列等待时长的 Timer 指标，
 * 并追踪慢任务（执行耗时超过阈值）计数。
 *
 * <p>初始化流程：
 * <ol>
 *   <li>{@link #afterSingletonsInstantiated()}：在 Spring 容器启动后，
 *       遍历所有 {@link ThreadPoolExecutor}，为每个池创建{@link io.micrometer.core.instrument.Timer}</li>
 * </ol>
 *
 * <p><b>注意</b>：{@link #record} 方法由 {@link TimedTaskDecorator} 调用，
 * 慢任务阈值通过参数传入，各池可使用不同阈值。
 *
 * @author ydsz-team
 * @since 1.4.0
 * @see TimedTaskDecorator
 */
public class ThreadPoolTimerMetrics {

    /**
     * 默认慢任务阈值（毫秒）。当池级别未指定阈值时使用此值。
     */
    public static final long DEFAULT_SLOW_TASK_THRESHOLD_MS = 5_000L;

    private static final String METRIC_EXECUTION_TIME = "ydsz.executor.execution";
    private static final String METRIC_QUEUE_WAIT_TIME = "ydsz.executor.queue.wait";
    private static final String METRIC_SLOW_TASKS = "ydsz.executor.slow.tasks";

    private final String poolName;
    private final MeterRegistry meterRegistry;
    private final Tags commonTags;

    /**
     * 构造指标绑定器。
     *
     * @param poolName     线程池名称（作为指标 tag）
     * @param meterRegistry Micrometer 注册表
     */
    public ThreadPoolTimerMetrics(String poolName, MeterRegistry meterRegistry) {
        this.poolName = poolName;
        this.meterRegistry = meterRegistry;
        this.commonTags = Tags.of("pool", poolName);
    }

    /**
     * 记录任务执行耗时与队列等待时长。
     *
     * <p>若执行耗时超过 {@code slowTaskThresholdMs}，则递增慢任务计数器。
     *
     * @param executionMs         执行耗时（毫秒）
     * @param queueWaitMs         队列等待时长（毫秒）
     * @param slowTaskThresholdMs 慢任务阈值（毫秒），≥ 100
     * @param metricPoolName      指标 pool tag 值（用于标签一致性校验）
     */
    public void record(long executionMs, long queueWaitMs, long slowTaskThresholdMs,
            String metricPoolName) {
        Timer executionTimer = Timer.builder(METRIC_EXECUTION_TIME)
                .tags(commonTags)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
        Timer queueWaitTimer = Timer.builder(METRIC_QUEUE_WAIT_TIME)
                .tags(commonTags)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        executionTimer.record(executionMs, TimeUnit.MILLISECONDS);
        queueWaitTimer.record(queueWaitMs, TimeUnit.MILLISECONDS);

        if (executionMs > slowTaskThresholdMs) {
            meterRegistry.counter(METRIC_SLOW_TASKS, commonTags).increment();
        }
    }

    /**
     * 构建指标标签。
     *
     * @return 通用标签
     */
    public Tags getCommonTags() {
        return commonTags;
    }

    /**
     * 获取线程池名称。
     *
     * @return 线程池名称
     */
    public String getPoolName() {
        return poolName;
    }

    /**
     * 根据 pool 配置构建 {@link ThreadPoolTimerMetrics} 实例。
     *
     * <p>工厂方法，供 {@code ThreadPoolRegistrar} 使用。
     *
     * @param poolName     线程池名称
     * @param meterRegistry Micrometer 注册表
     * @return 指标绑定器实例
     */
    @Nullable
    public static ThreadPoolTimerMetrics createIfMeterRegistryPresent(
            String poolName, MeterRegistry meterRegistry) {
        if (meterRegistry == null) {
            return null;
        }
        return new ThreadPoolTimerMetrics(poolName, meterRegistry);
    }
}
