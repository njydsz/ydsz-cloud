package com.njydsz.pmis.common.util.json;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

/**
 * JSON 处理 Micrometer 指标自动配置
 *
 * <p>当 classpath 中存在 MeterRegistry 时，自动绑定到 JsonMetrics，
 * 启用 Prometheus 指标采集。
 *
 * @author Marvin Lee
 * @version 4.0.0
 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnBean(MeterRegistry.class)
@AutoConfigureAfter(name = "org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration")
public class JsonMetricsConfiguration {

    /**
     * 将 MeterRegistry 绑定到 JsonMetrics，启用 Prometheus 指标采集
     */
    public JsonMetricsConfiguration(JsonMetrics jsonMetrics, MeterRegistry meterRegistry) {
        jsonMetrics.bindMeterRegistry(meterRegistry);
    }
}
