package com.njydsz.common.lock.metrics;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * 分布式锁 Micrometer 指标自动配置
 *
 * <p>当 classpath 中存在 MeterRegistry 时，自动绑定到 LockMetrics，
 * 启用 Prometheus 指标采集。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnBean(MeterRegistry.class)
public class LockMetricsConfiguration {

    /**
     * 将 MeterRegistry 绑定到 LockMetrics，启用 Prometheus 指标采集
     */
    public LockMetricsConfiguration(LockMetrics lockMetrics, MeterRegistry meterRegistry) {
        lockMetrics.bindMeterRegistry(meterRegistry);
    }
}
