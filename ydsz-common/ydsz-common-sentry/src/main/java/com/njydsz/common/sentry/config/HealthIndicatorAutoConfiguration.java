package com.njydsz.common.sentry.config;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.common.sentry.health.SentryHealthIndicator;
import com.njydsz.common.sentry.health.SentryInfoContributor;
import com.njydsz.common.sentry.health.SystemResourceHealthIndicator;
import com.njydsz.common.sentry.metrics.SystemMetricsCollector;
import com.njydsz.common.sentry.spi.LogPublisher;
import com.njydsz.common.sentry.spi.MetricsCollector;
import com.njydsz.common.sentry.spi.TraceContext;

/**
 * 健康检查自动配置。
 *
 * <p>装配健康探针，聚合指标 / 日志 / 链路三条通道的可用性到 Actuator health 端点。
 *
 * <p>仅在 Actuator health 相关类存在时装配，避免非 Web 或未引入 Actuator 的模块启动失败。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter({MetricsAutoConfiguration.class, TracingAutoConfiguration.class,
        LoggingAutoConfiguration.class})
@EnableConfigurationProperties(SentryProperties.class)
public class HealthIndicatorAutoConfiguration {

    /**
     * 装配 Sentry 组件健康探针。
     *
     * @param metricsCollector 指标采集器
     * @param logPublisher     日志发布器
     * @param traceContext     链路上下文
     * @return 健康探针
     */
    @Bean
    @ConditionalOnMissingBean(SentryHealthIndicator.class)
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    public SentryHealthIndicator sentryHealthIndicator(MetricsCollector metricsCollector,
                                                        LogPublisher logPublisher,
                                                        TraceContext traceContext) {
        return new SentryHealthIndicator(metricsCollector, logPublisher, traceContext);
    }

    /**
     * 装配系统资源健康探针。
     *
     * @param systemMetricsCollector 系统指标采集器
     * @return 系统资源健康探针
     */
    @Bean
    @ConditionalOnMissingBean(SystemResourceHealthIndicator.class)
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    @ConditionalOnProperty(prefix = "ydsz.sentry.metrics", name = "enable-system-metrics",
            havingValue = "true", matchIfMissing = true)
    public SystemResourceHealthIndicator systemResourceHealthIndicator(
            SystemMetricsCollector systemMetricsCollector) {
        return new SystemResourceHealthIndicator(systemMetricsCollector);
    }

    /**
     * 装配 Sentry 运行时元数据贡献者，通过 {@code /actuator/info} 暴露当前生效的 SPI 运行时信息。
     *
     * @param metricsCollector 指标采集器
     * @param logPublisher     日志发布器
     * @param traceContext     链路上下文
     * @return 元数据贡献者
     */
    @Bean
    @ConditionalOnMissingBean(SentryInfoContributor.class)
    @ConditionalOnClass(name = "org.springframework.boot.actuate.info.InfoContributor")
    public SentryInfoContributor sentryInfoContributor(MetricsCollector metricsCollector,
                                                       LogPublisher logPublisher,
                                                       TraceContext traceContext) {
        return new SentryInfoContributor(metricsCollector, logPublisher, traceContext);
    }
}
