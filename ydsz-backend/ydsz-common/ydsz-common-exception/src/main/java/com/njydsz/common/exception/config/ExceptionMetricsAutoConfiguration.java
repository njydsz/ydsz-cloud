package com.njydsz.common.exception.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.exception.metrics.ExceptionMetrics;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * 异常指标自动配置
 *
 * <p>当项目中存在 Micrometer MeterRegistry 时，自动注册 {@link ExceptionMetrics} Bean，
 * 提供异常计数和耗时指标记录能力。
 *
 * <p>通过 {@code ydsz.exception.metrics-enabled=true}（默认启用）控制是否注册。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ExceptionMetrics
 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnBean(MeterRegistry.class)
@ConditionalOnProperty(prefix = "ydsz.exception", name = "metrics-enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ExceptionProperties.class)
public class ExceptionMetricsAutoConfiguration {

    /**
     * 注册异常指标统计器
     *
     * @param meterRegistry Micrometer 指标注册表
     * @return ExceptionMetrics 实例
     */
    @Bean
    @ConditionalOnMissingBean(ExceptionMetrics.class)
    public ExceptionMetrics exceptionMetrics(MeterRegistry meterRegistry) {
        return new ExceptionMetrics(meterRegistry);
    }
}
