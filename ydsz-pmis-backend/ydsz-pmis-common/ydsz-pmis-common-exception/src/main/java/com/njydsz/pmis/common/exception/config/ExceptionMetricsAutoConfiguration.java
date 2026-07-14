package com.njydsz.pmis.common.exception.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.njydsz.pmis.common.exception.metrics.ExceptionMetrics;
import com.njydsz.pmis.common.exception.metrics.ExceptionMetricsRecorder;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * 异常指标自动配置
 *
 * <p>当项目中存在 Micrometer MeterRegistry 时，自动注册以下 Bean：
 * <ul>
 *   <li>{@link ExceptionMetrics} — 基础异常计数和耗时指标</li>
 *   <li>{@link ExceptionMetricsRecorder} — 增强装饰器（Timer + 结构化日志 + 路径归一化）</li>
 * </ul>
 *
 * <p>通过 {@code ydsz.exception.metrics-enabled=true}（默认启用）控制是否注册。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @see ExceptionMetrics
 * @see ExceptionMetricsRecorder
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

    /**
     * 注册异常指标记录装饰器
     *
     * <p>提供 Timer 计时、结构化日志输出、路径归一化、MDC traceId 读取等增强能力。
     * 可在业务代码中直接注入使用，替代手动 try-catch + log 模式。
     *
     * @param meterRegistry Micrometer 指标注册表
     * @param properties    异常模块配置
     * @return ExceptionMetricsRecorder 实例
     */
    @Bean
    @ConditionalOnMissingBean(ExceptionMetricsRecorder.class)
    public ExceptionMetricsRecorder exceptionMetricsRecorder(MeterRegistry meterRegistry,
                                                              ExceptionProperties properties) {
        return new ExceptionMetricsRecorder(meterRegistry, true, properties.isIncludeStackTrace());
    }
}
