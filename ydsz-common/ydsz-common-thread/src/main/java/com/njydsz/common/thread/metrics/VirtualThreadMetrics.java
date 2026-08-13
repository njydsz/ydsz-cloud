package com.njydsz.common.thread.metrics;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * 虚拟线程池 Micrometer 指标绑定器。
 *
 * <p>JDK 21 的 {@link Executors#newThreadPerTaskExecutor} 返回的虚拟线程执行器
 * （注意由 {@code ydsz-common-thread} 配置管理的虚拟线程池不受并发限制，每任务一线程）
 * 不支持 {@link java.util.concurrent.ThreadPoolExecutor} 的计数 API，
 * 因此本绑定器通过应用层计数器暴露 submitted / completed / rejected / active 四个指标。
 *
 * <p>如需对虚拟线程池进行精细化监控（活跃线程数、拒绝计数等），
 * 建议使用 {@code ydsz-common-util} 中的 {@code BoundedVirtualThreadScheduler}。
 *
 * @author ydsz-team
 * @since 1.3.0
 */
public class VirtualThreadMetrics implements MeterBinder {

    public static final String DEFAULT_METRIC_PREFIX = "ydsz.virtual.executor";

    private final ExecutorService executorService;
    private final String poolName;
    private final String metricPrefix;
    private final AtomicLong submittedCounter = new AtomicLong(0);
    private final AtomicLong completedCounter = new AtomicLong(0);
    private final AtomicLong rejectedCounter = new AtomicLong(0);

    public VirtualThreadMetrics(ExecutorService executorService, String poolName) {
        this(executorService, poolName, DEFAULT_METRIC_PREFIX);
    }

    public VirtualThreadMetrics(ExecutorService executorService, String poolName, String metricPrefix) {
        this.executorService = executorService;
        this.poolName = poolName;
        this.metricPrefix = metricPrefix != null ? metricPrefix : DEFAULT_METRIC_PREFIX;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(metricPrefix + ".active", executorService, e -> 1.0)
                .tags(Tags.of("pool.name", poolName))
                .description("虚拟线程池存活状态（1=存在）")
                .register(registry);

        Gauge.builder(metricPrefix + ".submitted", submittedCounter, AtomicLong::doubleValue)
                .tags(Tags.of("pool.name", poolName))
                .description("累计提交虚拟线程任务数")
                .register(registry);

        Gauge.builder(metricPrefix + ".completed", completedCounter, AtomicLong::doubleValue)
                .tags(Tags.of("pool.name", poolName))
                .description("累计完成虚拟线程任务数")
                .register(registry);

        Gauge.builder(metricPrefix + ".rejected", rejectedCounter, AtomicLong::doubleValue)
                .tags(Tags.of("pool.name", poolName))
                .description("累计拒绝虚拟线程任务数")
                .register(registry);
    }

    public void incrementSubmitted() {
        submittedCounter.incrementAndGet();
    }

    public void incrementCompleted() {
        completedCounter.incrementAndGet();
    }

    public void incrementRejected() {
        rejectedCounter.incrementAndGet();
    }
}
