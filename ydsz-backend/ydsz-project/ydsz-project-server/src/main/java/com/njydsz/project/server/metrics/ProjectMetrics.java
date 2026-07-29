package com.njydsz.project.server.metrics;

import java.util.concurrent.atomic.AtomicBoolean;

import com.njydsz.common.metrics.AbstractModuleMetrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;

/**
 * 项目模块 Micrometer 指标采集器
 *
 * <p>{@code ydsz-project} 微服务的 Prometheus 指标出口，继承 {@link AbstractModuleMetrics} 实现指标统一管理。
 * 通过 Spring Boot Actuator 在 {@code /actuator/prometheus} 端点暴露，供 Grafana / Prometheus 抓取。
 *
 * <p><b>暴露指标清单：</b>
 * <ul>
 *   <li>{@code project.initiation.created} — 项目立项创建计数（Counter）</li>
 *   <li>{@code project.initiation.updated} — 项目立项更新计数（Counter）</li>
 *   <li>{@code project.initiation.deleted} — 项目立项删除计数（Counter）</li>
 *   <li>{@code project.initiation.query.duration} — 项目立项查询耗时（Timer，单位毫秒）</li>
 * </ul>
 *
 * <p><b>使用方式：</b>由 {@code ProjectInitiationServiceImpl} 在 CRUD 操作后调用对应方法，
 * 框架自动注册到 {@link MeterRegistry}，无需手动管理 Counter / Timer 生命周期。
 *
 * <p><b>启用条件：</b>由 {@link com.njydsz.project.server.config.ProjectAutoConfiguration} 通过
 * {@code @Bean @ConditionalOnMissingBean} 注册，依赖 {@code MeterRegistry} Bean 存在。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see AbstractModuleMetrics 通用指标基类（封装 Counter / Timer 样板代码）
 * @see com.njydsz.project.server.config.ProjectAutoConfiguration 自动配置注册
 */
public class ProjectMetrics extends AbstractModuleMetrics {

    private static final String MODULE = "project";

    private final Counter initiationCreated;
    private final Counter initiationUpdated;
    private final Counter initiationDeleted;
    private final Timer initiationQueryTimer;
    private final AtomicBoolean registered = new AtomicBoolean(false);

    /**
     * 构造器：初始化 Micrometer 注册中心 + 注册 4 个指标
     *
     * @param meterRegistry Spring 注入的 Micrometer 注册中心
     */
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

    /**
     * 记录项目立项创建
     *
     * <p>累加 {@code project.initiation.created} 计数。
     * 由 {@code ProjectInitiationServiceImpl.save} 调用。
     */
    public void incInitiationCreated() {
        initiationCreated.increment();
    }

    /**
     * 记录项目立项更新
     *
     * <p>累加 {@code project.initiation.updated} 计数。
     * 由 {@code ProjectInitiationServiceImpl.updateById} 调用。
     */
    public void incInitiationUpdated() {
        initiationUpdated.increment();
    }

    /**
     * 记录项目立项删除
     *
     * <p>累加 {@code project.initiation.deleted} 计数。
     * 由 {@code ProjectInitiationServiceImpl.removeById} 调用。
     */
    public void incInitiationDeleted() {
        initiationDeleted.increment();
    }

    /**
     * 记录项目立项查询耗时
     *
     * <p>记录 {@code project.initiation.query.duration} 耗时。
     * 由 {@code ProjectInitiationServiceImpl.page / getById / getByCode} 等查询方法调用。
     *
     * @param millis 查询耗时（毫秒）
     */
    public void recordQueryDuration(long millis) {
        initiationQueryTimer.record(Duration.ofMillis(millis));
    }

    /**
     * 检查指标是否已注册
     *
     * <p>用于健康检查（{@link com.njydsz.project.server.health.ProjectHealthIndicator}），
     * 确认 Micrometer 指标 Bean 已完成初始化。
     *
     * @return {@code true} 表示指标已注册到 {@link MeterRegistry}
     */
    public boolean isRegistered() {
        return registered.get();
    }
}
