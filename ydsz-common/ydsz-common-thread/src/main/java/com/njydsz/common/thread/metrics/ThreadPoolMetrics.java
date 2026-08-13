package com.njydsz.common.thread.metrics;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * 线程池 Micrometer 指标绑定器。
 *
 * <p>接受 {@link ThreadPoolTaskExecutor}，在 {@link #bindTo} 阶段延迟提取底层
 * {@link ThreadPoolExecutor}，避免 Bean 未初始化完成时调用 {@code getThreadPoolExecutor()} 失败。
 *
 * <p>暴露八个核心指标（均按 {@code pool.name} 打标）：
 * <ul>
 *   <li>{@code ydsz.executor.active} - 当前活跃线程数</li>
 *   <li>{@code ydsz.executor.pool.size} - 线程池当前大小</li>
 *   <li>{@code ydsz.executor.pool.max} - 线程池最大容量</li>
 *   <li>{@code ydsz.executor.queue.size} - 工作队列当前长度</li>
 *   <li>{@code ydsz.executor.queue.remaining} - 工作队列剩余容量</li>
 *   <li>{@code ydsz.executor.queue.usage} - 工作队列使用率（0.0 - 1.0）</li>
 *   <li>{@code ydsz.executor.completed} - 累计完成任务数</li>
 *   <li>{@code ydsz.executor.rejected} - 累计拒绝任务数</li>
 * </ul>
 *
 * <p>默认指标前缀使用 {@code ydsz.executor} 而非 {@code executor}，
 * 避免与 Spring Boot Actuator 内置的线程池指标命名空间冲突。
 *
 * <p>v1.3.0 变更：构造器改为接受 {@link ThreadPoolTaskExecutor}，
 * 拒绝计数由 {@link MeteredRejectedHandler} 自动回调 {@link #incrementRejected()}，
 * 无需业务方手动调用。
 *
 * @author ydsz-team
 * @since 1.2.0
 * @see MeteredRejectedHandler
 */
public class ThreadPoolMetrics implements MeterBinder {

    public static final String DEFAULT_METRIC_PREFIX = "ydsz.executor";

    private final ThreadPoolTaskExecutor taskExecutor;
    private final String poolName;
    private final String metricPrefix;
    private final Iterable<io.micrometer.core.instrument.Tag> tags;

    private final AtomicReference<Counter> rejectedCounterRef = new AtomicReference<>();

    public ThreadPoolMetrics(ThreadPoolTaskExecutor taskExecutor, String poolName) {
        this(taskExecutor, poolName, DEFAULT_METRIC_PREFIX, Tags.empty());
    }

    public ThreadPoolMetrics(ThreadPoolTaskExecutor taskExecutor, String poolName,
                              String metricPrefix, Iterable<io.micrometer.core.instrument.Tag> tags) {
        this.taskExecutor = taskExecutor;
        this.poolName = poolName;
        this.metricPrefix = metricPrefix != null ? metricPrefix : DEFAULT_METRIC_PREFIX;
        this.tags = tags != null ? tags : Tags.empty();
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        ThreadPoolExecutor executor;
        try {
            executor = taskExecutor.getThreadPoolExecutor();
        } catch (Exception e) {
            // 底层 executor 未就绪时跳过注册，避免启动失败
            return;
        }

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

        Counter rejectedCounter = Counter.builder(metricPrefix + ".rejected")
                .tags(Tags.concat(tags, "pool.name", poolName))
                .description("累计任务拒绝次数")
                .register(registry);
        rejectedCounterRef.set(rejectedCounter);
    }

    /**
     * 记录一次任务拒绝。
     *
     * <p>由 {@link MeteredRejectedHandler} 在拒绝策略触发时自动回调，业务方无需手动调用。</p>
     */
    public void incrementRejected() {
        Counter counter = rejectedCounterRef.get();
        if (counter != null) {
            counter.increment();
        }
    }
}
