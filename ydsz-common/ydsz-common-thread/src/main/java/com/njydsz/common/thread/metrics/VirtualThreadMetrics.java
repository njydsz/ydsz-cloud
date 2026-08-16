package com.njydsz.common.thread.metrics;

import java.util.concurrent.atomic.LongAdder;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.lang.NonNull;

/**
 * 虚拟线程池 Micrometer 指标绑定器。
 *
 * <p>JDK 21 的虚拟线程执行器不支持 {@link java.util.concurrent.ThreadPoolExecutor} 的计数 API，
 * 因此本绑定器通过应用层计数器暴露 submitted / completed 两个指标，
 * 由 {@link MeteredVirtualExecutorService} 在任务提交/完成时回调计数。
 *
 * <p>v1.4.0 变更：移除 rejected 指标（JDK 21 的虚拟线程执行器从不拒绝任务，
 * 该计数器始终为 0，无实际意义）。
 *
 * <p>v1.3.1 变更：移除无意义的 {@code active} Gauge（固定返回 1.0），
 * 计数器改用 {@link LongAdder} 优化高并发写入性能。
 *
 * @author ydsz-team
 * @since 1.3.0
 */
public class VirtualThreadMetrics implements MeterBinder {

    public static final String DEFAULT_METRIC_PREFIX = "ydsz.virtual.executor";

    private final String poolName;
    private final String metricPrefix;
    private final LongAdder submittedCounter = new LongAdder();
    private final LongAdder completedCounter = new LongAdder();

    /**
     * 构造虚拟线程池指标绑定器。
     *
     * @param poolName     线程池名称
     * @param metricPrefix Micrometer 指标前缀
     */
    public VirtualThreadMetrics(String poolName, String metricPrefix) {
        this.poolName = poolName;
        this.metricPrefix = metricPrefix != null ? metricPrefix : DEFAULT_METRIC_PREFIX;
    }

    @Override
    public void bindTo(@NonNull MeterRegistry registry) {
        Gauge.builder(metricPrefix + ".submitted", submittedCounter, LongAdder::doubleValue)
                .tags(Tags.of("pool.name", poolName))
                .description("累计提交虚拟线程任务数")
                .register(registry);

        Gauge.builder(metricPrefix + ".completed", completedCounter, LongAdder::doubleValue)
                .tags(Tags.of("pool.name", poolName))
                .description("累计完成虚拟线程任务数")
                .register(registry);
    }

    /**
     * 增加已提交任务计数。
     *
     * <p>由 {@link MeteredVirtualExecutorService} 在任务提交时回调。
     */
    public void incrementSubmitted() {
        submittedCounter.increment();
    }

    /**
     * 增加已完成任务计数。
     *
     * <p>由 {@link MeteredVirtualExecutorService} 在任务完成时回调。
     */
    public void incrementCompleted() {
        completedCounter.increment();
    }

    /**
     * 获取累计提交任务数。
     *
     * @return 提交总数
     */
    public long getSubmittedCount() {
        return submittedCounter.sum();
    }

    /**
     * 获取累计完成任务数。
     *
     * @return 完成总数
     */
    public long getCompletedCount() {
        return completedCounter.sum();
    }
}
