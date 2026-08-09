package com.njydsz.common.thread.metrics;

import java.util.concurrent.ThreadPoolExecutor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * 线程池 Micrometer 指标绑定器。
 *
 * <p>暴露四个核心指标（均按 {@code pool.name} 打标）：</p>
 * <ul>
 *   <li>{@code executor.active} - 当前活跃线程数</li>
 *   <li>{@code executor.queue.size} - 工作队列当前长度</li>
 *   <li>{@code executor.queue.remaining} - 工作队列剩余容量</li>
 *   <li>{@code executor.completed} - 累计完成任务数</li>
 *   <li>{@code executor.rejected} - 累计拒绝任务数</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.2.0
 */
public class ThreadPoolMetrics implements MeterBinder {

    public static final String DEFAULT_METRIC_PREFIX = "executor";

    private final ThreadPoolExecutor executor;
    private final String poolName;
    private final String metricPrefix;
    private final Iterable<io.micrometer.core.instrument.Tag> tags;

    private Counter rejectedCounter;

    public ThreadPoolMetrics(ThreadPoolExecutor executor, String poolName) {
        this(executor, poolName, DEFAULT_METRIC_PREFIX, Tags.empty());
    }

    public ThreadPoolMetrics(ThreadPoolExecutor executor, String poolName,
                              String metricPrefix, Iterable<io.micrometer.core.instrument.Tag> tags) {
        this.executor = executor;
        this.poolName = poolName;
        this.metricPrefix = metricPrefix != null ? metricPrefix : DEFAULT_METRIC_PREFIX;
        this.tags = tags != null ? tags : Tags.empty();
    }

    @Override
    public void bindTo(MeterRegistry registry) {

        Gauge.builder(metricPrefix + ".active", executor, ThreadPoolExecutor::getActiveCount)
                .tags(Tags.concat(tags, "pool.name", poolName))
                .description("当前活跃线程数")
                .register(registry);

        Gauge.builder(metricPrefix + ".pool.size", executor, ThreadPoolExecutor::getPoolSize)
                .tags(Tags.concat(tags, "pool.name", poolName))
                .description("线程池当前大小")
                .register(registry);

        Gauge.builder(metricPrefix + ".pool.max", executor, e -> e.getMaximumPoolSize())
                .tags(Tags.concat(tags, "pool.name", poolName))
                .description("线程池最大容量")
                .register(registry);

        Gauge.builder(metricPrefix + ".queue.size", executor,
                        e -> e.getQueue() != null ? e.getQueue().size() : 0)
                .tags(Tags.concat(tags, "pool.name", poolName))
                .description("工作队列当前长度")
                .register(registry);

        Gauge.builder(metricPrefix + ".queue.remaining", executor,
                        e -> e.getQueue() != null ? e.getQueue().remainingCapacity() : 0)
                .tags(Tags.concat(tags, "pool.name", poolName))
                .description("工作队列剩余容量")
                .register(registry);

        Gauge.builder(metricPrefix + ".queue.usage", executor, e -> {
                    int queueSize = e.getQueue() != null ? e.getQueue().size() : 0;
                    int remaining = e.getQueue() != null ? e.getQueue().remainingCapacity() : 0;
                    int total = queueSize + remaining;
                    return total > 0 ? (double) queueSize / total : 0.0;
                })
                .tags(Tags.concat(tags, "pool.name", poolName))
                .description("工作队列使用率（0.0 - 1.0）")
                .register(registry);

        Gauge.builder(metricPrefix + ".completed", executor, ThreadPoolExecutor::getCompletedTaskCount)
                .tags(Tags.concat(tags, "pool.name", poolName))
                .description("累计完成任务数")
                .register(registry);

        this.rejectedCounter = Counter.builder(metricPrefix + ".rejected")
                .tags(Tags.concat(tags, "pool.name", poolName))
                .description("累计任务拒绝次数")
                .register(registry);
    }

    /**
     * 记录一次任务拒绝。
     *
     * <p>应在检测到拒绝策略触发时显式调用。</p>
     */
    public void incrementRejected() {
        if (rejectedCounter != null) {
            rejectedCounter.increment();
        }
    }
}
