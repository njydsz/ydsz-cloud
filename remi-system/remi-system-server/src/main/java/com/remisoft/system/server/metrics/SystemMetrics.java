package com.remisoft.system.server.metrics;

import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import com.remisoft.common.base.metrics.AbstractModuleMetrics;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 系统模块 Micrometer 指标采集器
 *
 * <p>{@code remi-system} 微服务的 Prometheus 指标出口，继承 {@link AbstractModuleMetrics} 实现指标统一管理。
 * 通过 Spring Boot Actuator 在 {@code /actuator/prometheus} 端点暴露，供 Grafana / Prometheus 抓取。
 *
 * <p><b>架构优化（P0-2）：</b>继承 {@link AbstractModuleMetrics}，统一指标前缀 {@code remi_system_}，
 * 消除了 15 个手动 Counter / Timer 字段和构造器样板代码，<b>仅保留业务方法</b>。
 *
 * <p><b>暴露指标清单：</b>
 * <ul>
 *   <li>{@code remi_system_config_read_total} / {@code config_read_duration_ms} —
 *       配置读取次数 / 耗时（Counter / Timer）</li>
 *   <li>{@code remi_system_config_cache_hit_total} / {@code config_cache_miss_total} —
 *       配置缓存命中 / 未命中（Counter）</li>
 *   <li>{@code remi_system_dict_query_total} / {@code dict_query_duration_ms} —
 *       字典查询次数 / 耗时</li>
 *   <li>{@code remi_system_dict_cache_hit_total} / {@code dict_cache_miss_total} —
 *       字典缓存命中 / 未命中</li>
 *   <li>{@code remi_system_variable_read_total} / {@code variable_read_duration_ms} —
 *       系统变量读取次数 / 耗时</li>
 *   <li>{@code remi_system_variable_cache_hit_total} / {@code variable_cache_miss_total} —
 *       系统变量缓存命中 / 未命中</li>
 *   <li>{@code remi_system_app_validate_success_total} / {@code app_validate_fail_total} —
 *       应用密钥校验成功 / 失败</li>
 * </ul>
 *
 * <p><b>使用方式：</b>由 Service 层（如 {@code ConfigServiceImpl}）调用对应方法，
 * 框架自动注册到 {@link MeterRegistry}，无需手动管理 Counter / Timer 生命周期。
 *
 * <p><b>启用条件：</b>{@code @ConditionalOnClass(MeterRegistry.class)} — Micrometer 存在时启用
 *
 * @author remi-team
 * @since 1.0.0
 * @see AbstractModuleMetrics 通用指标基类（封装 Counter / Timer 样板代码）
 * @see io.micrometer.core.instrument.MeterRegistry Micrometer 指标注册中心
 */
@Slf4j
@Component
@ConditionalOnClass(MeterRegistry.class)
public class SystemMetrics extends AbstractModuleMetrics {

    /**
     * 构造器：初始化 Micrometer 注册中心 + 指标前缀
     *
     * @param meterRegistry Spring 注入的 Micrometer 注册中心
     */
    public SystemMetrics(MeterRegistry meterRegistry) {
        super(meterRegistry, "remi_system_");
    }

    /**
     * 记录配置读取
     *
     * <p>同时累加 {@code config_read_total} 计数 + 记录 {@code config_read_duration_ms} 耗时。
     * <p>由 {@code ConfigServiceImpl.getConfigValue} 等高频读取方法调用。
     *
     * @param durationNanos 读取耗时（纳秒，建议使用 {@code System.nanoTime()} 计算）
     */
    public void recordConfigRead(long durationNanos) {
        incrementCounter("config_read_total");
        timer("config_read_duration_ms").record(durationNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * 记录配置缓存命中
     *
     * <p>累加 {@code config_cache_hit_total} 计数。
     */
    public void recordConfigCacheHit() {
        incrementCounter("config_cache_hit_total");
    }

    /**
     * 记录配置缓存未命中
     *
     * <p>累加 {@code config_cache_miss_total} 计数。
     * 命中率计算公式：{@code hit_rate = hit / (hit + miss)}，可通过 Grafana 配置。
     */
    public void recordConfigCacheMiss() {
        incrementCounter("config_cache_miss_total");
    }

    /**
     * 记录字典查询
     *
     * <p>同时累加 {@code dict_query_total} 计数 + 记录 {@code dict_query_duration_ms} 耗时。
     * <p>由 {@code DictServiceImpl.page / DictItemServiceImpl.getByTypeAndCode} 等方法调用。
     *
     * @param durationNanos 查询耗时（纳秒）
     */
    public void recordDictQuery(long durationNanos) {
        incrementCounter("dict_query_total");
        timer("dict_query_duration_ms").record(durationNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * 记录字典缓存命中
     */
    public void recordDictCacheHit() {
        incrementCounter("dict_cache_hit_total");
    }

    /**
     * 记录字典缓存未命中
     */
    public void recordDictCacheMiss() {
        incrementCounter("dict_cache_miss_total");
    }

    /**
     * 记录系统变量读取
     *
     * <p>同时累加 {@code variable_read_total} 计数 + 记录 {@code variable_read_duration_ms} 耗时。
     * <p>由 {@code VariableServiceImpl.getVariableValue} 高频读取方法调用。
     *
     * @param durationNanos 读取耗时（纳秒）
     */
    public void recordVariableRead(long durationNanos) {
        incrementCounter("variable_read_total");
        timer("variable_read_duration_ms").record(durationNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * 记录系统变量缓存命中
     */
    public void recordVariableCacheHit() {
        incrementCounter("variable_cache_hit_total");
    }

    /**
     * 记录系统变量缓存未命中
     */
    public void recordVariableCacheMiss() {
        incrementCounter("variable_cache_miss_total");
    }

    /**
     * 记录应用密钥校验成功
     *
     * <p>由 {@code AppInfoServiceImpl.validateClient} 校验通过时调用。
     */
    public void recordAppValidateSuccess() {
        incrementCounter("app_validate_success_total");
    }

    /**
     * 记录应用密钥校验失败
     *
     * <p>由 {@code AppInfoServiceImpl.validateClient} 校验失败时调用（含应用不存在 / 未启用 / 密钥不匹配）。
     * 失败率突增通常是密钥泄露 / 暴力破解的早期信号，建议接入告警。
     */
    public void recordAppValidateFail() {
        incrementCounter("app_validate_fail_total");
    }
}
