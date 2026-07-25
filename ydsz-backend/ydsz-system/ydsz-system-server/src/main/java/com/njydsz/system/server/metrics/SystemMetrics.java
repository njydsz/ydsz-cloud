package com.njydsz.system.server.metrics;

import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import lombok.extern.slf4j.Slf4j;

/**
 * 系统模块 Micrometer 指标。
 *
 * <p>暴露以下指标：
 * <ul>
 *   <li>{@code system.config.read.total/duration} — 配置读取次数/耗时</li>
 *   <li>{@code system.config.cache.hit/miss} — 配置缓存命中/未命中</li>
 *   <li>{@code system.dict.query.total/duration} — 字典查询次数/耗时</li>
 *   <li>{@code system.dict.cache.hit/miss} — 字典缓存命中/未命中</li>
 *   <li>{@code system.app.validate.success/fail} — 应用校验成功/失败</li>
 * </ul>
 *
 * @author ydsz-team
 */
@Slf4j
@Component
@ConditionalOnClass(MeterRegistry.class)
public class SystemMetrics {

    private final MeterRegistry meterRegistry;
    private final Counter configReadCounter;
    private final Counter configCacheHitCounter;
    private final Counter configCacheMissCounter;
    private final Timer configReadTimer;
    private final Counter dictQueryCounter;
    private final Timer dictQueryTimer;
    private final Counter dictCacheHitCounter;
    private final Counter dictCacheMissCounter;
    private final Counter appValidateSuccessCounter;
    private final Counter appValidateFailCounter;

    /**
     * 构造函数，注册所有指标。
     *
     * @param meterRegistry Micrometer 指标注册中心
     */
    public SystemMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.configReadCounter = Counter.builder("system.config.read.total")
                .description("Config read total count")
                .register(meterRegistry);
        this.configCacheHitCounter = Counter.builder("system.config.cache.hit")
                .description("Config cache hit count")
                .register(meterRegistry);
        this.configCacheMissCounter = Counter.builder("system.config.cache.miss")
                .description("Config cache miss count")
                .register(meterRegistry);
        this.configReadTimer = Timer.builder("system.config.read.duration")
                .description("Config read duration")
                .register(meterRegistry);
        this.dictQueryCounter = Counter.builder("system.dict.query.total")
                .description("Dict query total count")
                .register(meterRegistry);
        this.dictQueryTimer = Timer.builder("system.dict.query.duration")
                .description("Dict query duration")
                .register(meterRegistry);
        this.dictCacheHitCounter = Counter.builder("system.dict.cache.hit")
                .description("Dict cache hit count")
                .register(meterRegistry);
        this.dictCacheMissCounter = Counter.builder("system.dict.cache.miss")
                .description("Dict cache miss count")
                .register(meterRegistry);
        this.appValidateSuccessCounter = Counter.builder("system.app.validate.success")
                .description("App validate success count")
                .register(meterRegistry);
        this.appValidateFailCounter = Counter.builder("system.app.validate.fail")
                .description("App validate fail count")
                .register(meterRegistry);
    }

    /**
     * 记录配置读取。
     *
     * @param durationNanos 读取耗时（纳秒）
     */
    public void recordConfigRead(long durationNanos) {
        configReadCounter.increment();
        configReadTimer.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * 记录配置缓存命中。
     */
    public void recordConfigCacheHit() {
        configCacheHitCounter.increment();
    }

    /**
     * 记录配置缓存未命中。
     */
    public void recordConfigCacheMiss() {
        configCacheMissCounter.increment();
    }

    /**
     * 记录字典查询。
     *
     * @param durationNanos 查询耗时（纳秒）
     */
    public void recordDictQuery(long durationNanos) {
        dictQueryCounter.increment();
        dictQueryTimer.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * 记录字典缓存命中。
     */
    public void recordDictCacheHit() {
        dictCacheHitCounter.increment();
    }

    /**
     * 记录字典缓存未命中。
     */
    public void recordDictCacheMiss() {
        dictCacheMissCounter.increment();
    }

    /**
     * 记录应用校验成功。
     */
    public void recordAppValidateSuccess() {
        appValidateSuccessCounter.increment();
    }

    /**
     * 记录应用校验失败。
     */
    public void recordAppValidateFail() {
        appValidateFailCounter.increment();
    }
}
