package com.njydsz.common.exception.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.exception.health.ExceptionHealthIndicator;
import com.njydsz.common.exception.metrics.ExceptionMetrics;
import com.njydsz.common.exception.registry.ResultCodeRegistry;

/**
 * 异常模块健康检查自动配置
 *
 * <p>当 spring-boot-health 在类路径上时，自动注册 {@link ExceptionHealthIndicator}。
 *
 * @author ydsz-team
 * @since 1.1.0
 * @see ExceptionHealthIndicator
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
@EnableConfigurationProperties(ExceptionProperties.class)
public class ExceptionHealthAutoConfiguration {

    /**
     * 注册异常模块健康指示器，向 Actuator 暴露异常体系的运行状态。
     *
     * <p>{@link ExceptionMetrics} 与 {@link ResultCodeRegistry} 均以
     * {@link ObjectProvider} 惰性注入：这两个组件由各自的自动配置按条件装配，
     * 缺失时健康检查降级为「仅上报配置项」而不会导致容器启动失败。
     *
     * <p>标注 {@link ConditionalOnMissingBean}，业务方可自定义同类型 Bean 覆盖默认实现。
     *
     * @param properties                异常模块配置，用于读取健康检查阈值等开关，不可为 {@code null}
     * @param metricsProvider           异常指标采集器的惰性提供者；未装配时健康详情中不包含异常计数
     * @param resultCodeRegistryProvider 错误码注册表的惰性提供者；未装配时健康详情中不包含错误码统计
     * @return 健康指示器实例，永不为 {@code null}
     */
    @Bean
    @ConditionalOnMissingBean(ExceptionHealthIndicator.class)
    public ExceptionHealthIndicator exceptionHealthIndicator(
            ExceptionProperties properties,
            ObjectProvider<ExceptionMetrics> metricsProvider,
            ObjectProvider<ResultCodeRegistry> resultCodeRegistryProvider) {
        return new ExceptionHealthIndicator(properties, metricsProvider,
                resultCodeRegistryProvider);
    }
}
