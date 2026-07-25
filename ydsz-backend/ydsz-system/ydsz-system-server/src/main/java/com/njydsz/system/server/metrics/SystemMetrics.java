package com.njydsz.system.server.metrics;

import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import lombok.extern.slf4j.Slf4j;

/**
 * System module Micrometer metrics.
 *
 * <p>Exposes config read / dict query / app validate counters and timers.
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
    private final Counter appValidateSuccessCounter;
    private final Counter appValidateFailCounter;

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
        this.appValidateSuccessCounter = Counter.builder("system.app.validate.success")
                .description("App validate success count")
                .register(meterRegistry);
        this.appValidateFailCounter = Counter.builder("system.app.validate.fail")
                .description("App validate fail count")
                .register(meterRegistry);
    }

    public void recordConfigRead(long durationNanos) {
        configReadCounter.increment();
        configReadTimer.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordConfigCacheHit() {
        configCacheHitCounter.increment();
    }

    public void recordConfigCacheMiss() {
        configCacheMissCounter.increment();
    }

    public void recordDictQuery(long durationNanos) {
        dictQueryCounter.increment();
        dictQueryTimer.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordAppValidateSuccess() {
        appValidateSuccessCounter.increment();
    }

    public void recordAppValidateFail() {
        appValidateFailCounter.increment();
    }
}
