package com.njydsz.system.server.metrics;

import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.metrics.AbstractModuleMetrics;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 系统模块 Micrometer 指标。
 *
 * <p>P0-2 架构优化：继承 {@link AbstractModuleMetrics}，统一指标前缀 {@code ydsz_system_}，
 * 消除 15 个手动 Counter/Timer 字段和构造器样板代码。
 *
 * <p>暴露以下指标（通过 Spring Boot Actuator /actuator/prometheus）：
 * <ul>
 *   <li>{@code ydsz_system_config_read_total / config_read_duration_ms} — 配置读取次数/耗时</li>
 *   <li>{@code ydsz_system_config_cache_hit_total / cache_miss_total} — 配置缓存命中/未命中</li>
 *   <li>{@code ydsz_system_dict_query_total / dict_query_duration_ms} — 字典查询次数/耗时</li>
 *   <li>{@code ydsz_system_dict_cache_hit_total / cache_miss_total} — 字典缓存命中/未命中</li>
 *   <li>{@code ydsz_system_variable_read_total / variable_read_duration_ms} — 系统变量读取次数/耗时</li>
 *   <li>{@code ydsz_system_variable_cache_hit_total / cache_miss_total} — 系统变量缓存命中/未命中</li>
 *   <li>{@code ydsz_system_app_validate_success_total / fail_total} — 应用校验成功/失败</li>
 * </ul>
 *
 * @author ydsz-team
 */
@Slf4j
@Component
@ConditionalOnClass(MeterRegistry.class)
public class SystemMetrics extends AbstractModuleMetrics {

    public SystemMetrics(MeterRegistry meterRegistry) {
        super(meterRegistry, "ydsz_system_");
    }

    /**
     * 记录配置读取。
     *
     * @param durationNanos 读取耗时（纳秒）
     */
    public void recordConfigRead(long durationNanos) {
        incrementCounter("config_read_total");
        timer("config_read_duration_ms").record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordConfigCacheHit() {
        incrementCounter("config_cache_hit_total");
    }

    public void recordConfigCacheMiss() {
        incrementCounter("config_cache_miss_total");
    }

    /**
     * 记录字典查询。
     *
     * @param durationNanos 查询耗时（纳秒）
     */
    public void recordDictQuery(long durationNanos) {
        incrementCounter("dict_query_total");
        timer("dict_query_duration_ms").record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordDictCacheHit() {
        incrementCounter("dict_cache_hit_total");
    }

    public void recordDictCacheMiss() {
        incrementCounter("dict_cache_miss_total");
    }

    /**
     * 记录系统变量读取。
     *
     * @param durationNanos 读取耗时（纳秒）
     */
    public void recordVariableRead(long durationNanos) {
        incrementCounter("variable_read_total");
        timer("variable_read_duration_ms").record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordVariableCacheHit() {
        incrementCounter("variable_cache_hit_total");
    }

    public void recordVariableCacheMiss() {
        incrementCounter("variable_cache_miss_total");
    }

    public void recordAppValidateSuccess() {
        incrementCounter("app_validate_success_total");
    }

    public void recordAppValidateFail() {
        incrementCounter("app_validate_fail_total");
    }
}
