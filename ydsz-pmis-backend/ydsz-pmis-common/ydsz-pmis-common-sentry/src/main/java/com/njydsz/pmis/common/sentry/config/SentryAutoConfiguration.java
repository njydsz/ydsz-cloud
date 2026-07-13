package com.njydsz.pmis.common.sentry.config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.pmis.common.sentry.alerting.AlertConverger;
import com.njydsz.pmis.common.sentry.alerting.DefaultAlertPublisher;
import com.njydsz.pmis.common.sentry.health.SentryHealthIndicator;
import com.njydsz.pmis.common.sentry.health.SystemResourceHealthIndicator;
import com.njydsz.pmis.common.sentry.logging.DualLogPublisher;
import com.njydsz.pmis.common.sentry.logging.ElkLogPublisher;
import com.njydsz.pmis.common.sentry.logging.LokiLogPublisher;
import com.njydsz.pmis.common.sentry.metrics.InMemoryMetricsCollector;
import com.njydsz.pmis.common.sentry.metrics.MicrometerMetricsCollector;
import com.njydsz.pmis.common.sentry.metrics.SystemMetricsCollector;
import com.njydsz.pmis.common.sentry.sla.DefaultSlaCollector;
import com.njydsz.pmis.common.sentry.sla.SlaMetricAspect;
import com.njydsz.pmis.common.sentry.spi.AlertPublisher;
import com.njydsz.pmis.common.sentry.spi.LogPublisher;
import com.njydsz.pmis.common.sentry.spi.MetricsCollector;
import com.njydsz.pmis.common.sentry.spi.SlaCollector;
import com.njydsz.pmis.common.sentry.spi.TraceContext;
import com.njydsz.pmis.common.sentry.tracing.DefaultTraceContext;
import com.njydsz.pmis.common.sentry.tracing.SlowTraceDetector;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * Sentry 自动配置
 *
 * <p>自动装配指标采集、日志发布、链路追踪、告警收敛、SLA 框架等组件。
 * 支持通过配置快速切换 ELK / Loki 双方案。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(SentryProperties.class)
@ConditionalOnProperty(prefix = "ydsz.sentry", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SentryAutoConfiguration {

    private ScheduledExecutorService systemMetricsScheduler;

    // ==================== 指标采集 ====================

    @Configuration
    @ConditionalOnClass(MeterRegistry.class)
    static class MicrometerMetricsConfiguration {

        @Bean
        @ConditionalOnMissingBean(MetricsCollector.class)
        @ConditionalOnProperty(prefix = "ydsz.sentry.metrics", name = "primary",
                havingValue = "micrometer", matchIfMissing = true)
        public MetricsCollector micrometerMetricsCollector(MeterRegistry meterRegistry) {
            return new MicrometerMetricsCollector(meterRegistry);
        }
    }

    @Bean
    @ConditionalOnMissingBean(MetricsCollector.class)
    @ConditionalOnProperty(prefix = "ydsz.sentry.metrics", name = "primary", havingValue = "memory")
    public MetricsCollector inMemoryMetricsCollector() {
        return new InMemoryMetricsCollector();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ydsz.sentry.metrics", name = "enable-system-metrics",
            havingValue = "true", matchIfMissing = true)
    public SystemMetricsCollector systemMetricsCollector(MetricsCollector metricsCollector,
                                                          SentryProperties properties) {
        SystemMetricsCollector collector = new SystemMetricsCollector(metricsCollector);
        int interval = properties.getMetrics().getSystemMetricsIntervalSeconds();
        systemMetricsScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sentry-system-metrics");
            t.setDaemon(true);
            return t;
        });
        systemMetricsScheduler.scheduleAtFixedRate(collector::collect, 5, interval, TimeUnit.SECONDS);
        log.info("[Sentry] 系统资源指标定时采集已启动, interval={}s", interval);
        return collector;
    }

    // ==================== 日志发布 ====================

    @Bean
    @ConditionalOnMissingBean(LogPublisher.class)
    public LogPublisher logPublisher(SentryProperties properties) {
        List<LogPublisher> publishers = new ArrayList<>();

        SentryProperties.ElkConfig elkConfig = properties.getLogging().getElk();
        if (elkConfig.isEnabled()) {
            publishers.add(new ElkLogPublisher(
                    elkConfig.getHost(), elkConfig.getPort(), elkConfig.getProtocol(),
                    elkConfig.getConnectTimeoutMillis(), elkConfig.getReadTimeoutMillis(),
                    elkConfig.getMaxRetryAttempts(), elkConfig.getCircuitBreakerThreshold()));
        }

        SentryProperties.LokiConfig lokiConfig = properties.getLogging().getLoki();
        if (lokiConfig.isEnabled()) {
            publishers.add(new LokiLogPublisher(
                    lokiConfig.getUrl(), lokiConfig.getConnectTimeoutSeconds(),
                    lokiConfig.getMaxRetryAttempts(), lokiConfig.getCircuitBreakerThreshold()));
        }

        if (publishers.isEmpty()) {
            log.warn("[Sentry] 未启用任何日志发布器, 使用 Loki 默认配置");
            publishers.add(new LokiLogPublisher(
                    lokiConfig.getUrl(), lokiConfig.getConnectTimeoutSeconds(),
                    lokiConfig.getMaxRetryAttempts(), lokiConfig.getCircuitBreakerThreshold()));
        }

        if (publishers.size() == 1) {
            return publishers.get(0);
        }

        return new DualLogPublisher(publishers, properties.getLogging().getDual().isFailOnAllError());
    }

    // ==================== 链路追踪 ====================

    @Bean
    @ConditionalOnMissingBean(TraceContext.class)
    public TraceContext traceContext(SentryProperties properties) {
        String primary = properties.getTracing().getPrimary();
        if ("skywalking".equals(primary)) {
            try {
                Class.forName("org.apache.skywalking.apm.toolkit.trace.TraceContext");
                return new com.njydsz.pmis.common.sentry.tracing.SkyWalkingTraceContext();
            } catch (ClassNotFoundException e) {
                log.info("[Sentry] SkyWalking agent 未检测到, 降级到 DefaultTraceContext");
            }
        }
        return new DefaultTraceContext();
    }

    @Bean
    @ConditionalOnMissingBean
    public SlowTraceDetector slowTraceDetector(MetricsCollector metricsCollector,
                                                TraceContext traceContext,
                                                SentryProperties properties) {
        return new SlowTraceDetector(metricsCollector, traceContext,
                properties.getTracing().getSlowTraceThresholdMillis());
    }

    // ==================== 告警 ====================

    @Bean
    @ConditionalOnMissingBean(AlertPublisher.class)
    @ConditionalOnProperty(prefix = "ydsz.sentry.alerting", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public AlertPublisher alertPublisher(SentryProperties properties) {
        DefaultAlertPublisher publisher = new DefaultAlertPublisher(
                properties.getAlerting().isLogAlerts());
        return new AlertConverger(publisher, properties.getAlerting().getSilencePeriodMillis());
    }

    // ==================== SLA ====================

    @Bean
    @ConditionalOnMissingBean(SlaCollector.class)
    @ConditionalOnProperty(prefix = "ydsz.sentry.sla", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public SlaCollector slaCollector(MetricsCollector metricsCollector) {
        return new DefaultSlaCollector(metricsCollector);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ydsz.sentry.sla", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public SlaMetricAspect slaMetricAspect(DefaultSlaCollector slaCollector) {
        return new SlaMetricAspect(slaCollector);
    }

    // ==================== 健康检查 ====================

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    public SentryHealthIndicator sentryHealthIndicator(MetricsCollector metricsCollector,
                                                        LogPublisher logPublisher,
                                                        TraceContext traceContext) {
        return new SentryHealthIndicator(metricsCollector, logPublisher, traceContext);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    @ConditionalOnProperty(prefix = "ydsz.sentry.metrics", name = "enable-system-metrics",
            havingValue = "true", matchIfMissing = true)
    public SystemResourceHealthIndicator systemResourceHealthIndicator(
            SystemMetricsCollector systemMetricsCollector) {
        return new SystemResourceHealthIndicator(systemMetricsCollector);
    }

    // ==================== 生命周期 ====================

    @PreDestroy
    public void destroy() {
        if (systemMetricsScheduler != null) {
            systemMetricsScheduler.shutdown();
            try {
                if (!systemMetricsScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    systemMetricsScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                systemMetricsScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("[Sentry] 系统资源指标定时采集已停止");
        }
    }
}
