package com.njydsz.pmis.common.exception.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import com.njydsz.pmis.common.exception.metrics.ExceptionMetrics;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * 异常指标自动配置
 *
 * <p>当项目中存在 Micrometer MeterRegistry 时，自动注册 {@link ExceptionMetrics} Bean。
 * 通过 {@code ydsz.exception.metrics-enabled=true}（默认启用）控制是否注册。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.0.0
 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnBean(MeterRegistry.class)
@ConditionalOnProperty(prefix = "ydsz.exception", name = "metrics-enabled", havingValue = "true", matchIfMissing = true)
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
