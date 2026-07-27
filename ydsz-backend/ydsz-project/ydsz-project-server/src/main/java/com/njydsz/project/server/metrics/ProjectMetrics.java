package com.njydsz.project.server.metrics;

import java.util.concurrent.atomic.AtomicBoolean;

import com.njydsz.common.core.metrics.AbstractModuleMetrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;

/**
 * 项目模块 Micrometer 指标采集。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class ProjectMetrics extends AbstractModuleMetrics {

    private static final String MODULE = "project";

    private final Counter initiationCreated;
    private final Counter initiationUpdated;
    private final Counter initiationDeleted;
    private final Timer initiationQueryTimer;
    private final AtomicBoolean registered = new AtomicBoolean(false);

    public ProjectMetrics(MeterRegistry meterRegistry) {
        super(meterRegistry, MODULE);
        this.initiationCreated = Counter.builder("project.initiation.created")
                .description("项目立项创建计数")
                .register(meterRegistry);
        this.initiationUpdated = Counter.builder("project.initiation.updated")
                .description("项目立项更新计数")
                .register(meterRegistry);
        this.initiationDeleted = Counter.builder("project.initiation.deleted")
                .description("项目立项删除计数")
                .register(meterRegistry);
        this.initiationQueryTimer = Timer.builder("project.initiation.query.duration")
                .description("项目立项查询耗时")
                .register(meterRegistry);
        this.registered.set(true);
    }

    public void incInitiationCreated() {
        initiationCreated.increment();
    }

    public void incInitiationUpdated() {
        initiationUpdated.increment();
    }

    public void incInitiationDeleted() {
        initiationDeleted.increment();
    }

    public void recordQueryDuration(long millis) {
        initiationQueryTimer.record(java.time.Duration.ofMillis(millis));
    }

    public boolean isRegistered() {
        return registered.get();
    }
}
