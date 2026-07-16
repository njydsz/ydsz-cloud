package com.njydsz.common.sentry.config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.ObjectProvider;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.njydsz.common.sentry.alerting.AlertConverger;
import com.njydsz.common.sentry.alerting.DefaultAlertPublisher;
import com.njydsz.common.notify.core.NotifyService;
import com.njydsz.common.sentry.alerting.NotifyAlertHandler;
import com.njydsz.common.sentry.domain.AlertSeverity;
import com.njydsz.common.sentry.health.SentryHealthIndicator;
import com.njydsz.common.sentry.health.SystemResourceHealthIndicator;
import com.njydsz.common.sentry.logging.AsyncLogPublisher;
import com.njydsz.common.sentry.logging.DualLogPublisher;
import com.njydsz.common.sentry.logging.ElkLogPublisher;
import com.njydsz.common.sentry.logging.LokiLogPublisher;
import com.njydsz.common.sentry.metrics.InMemoryMetricsCollector;
import com.njydsz.common.sentry.metrics.MicrometerMetricsCollector;
import com.njydsz.common.sentry.metrics.SystemMetricsCollector;
import com.njydsz.common.sentry.resilience.CircuitBreaker;
import com.njydsz.common.sentry.sla.DefaultSlaCollector;
import com.njydsz.common.sentry.sla.SlaMetricAspect;
import com.njydsz.common.sentry.spi.AlertPublisher;
import com.njydsz.common.sentry.spi.LogPublisher;
import com.njydsz.common.sentry.spi.MetricsCollector;
import com.njydsz.common.sentry.spi.SlaCollector;
import com.njydsz.common.sentry.spi.TraceContext;
import com.njydsz.common.sentry.tracing.DefaultTraceContext;
import com.njydsz.common.sentry.tracing.OpenTelemetryTraceContext;
import com.njydsz.common.sentry.tracing.SkyWalkingTraceContext;
import com.njydsz.common.sentry.tracing.SlowTraceDetector;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * Sentry 自动配置
 *
 * <p>自动装配指标采集、日志发布、链路追踪、告警收敛、SLA 框架等组件。
 * 支持通过配置快速切换 ELK / Loki 双方案。
 *
 * @author ydsz-team
 * @since 1.5.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(SentryProperties.class)
@EnableScheduling
@ConditionalOnProperty(prefix = "ydsz.sentry", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SentryAutoConfiguration {

    private ScheduledExecutorService systemMetricsScheduler;
    private AsyncLogPublisher asyncLogPublisher;

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

    // ==================== 熔断器 ====================

    @Bean("elkCircuitBreaker")
    @ConditionalOnMissingBean(name = "elkCircuitBreaker")
    @ConditionalOnProperty(prefix = "ydsz.sentry.logging.elk", name = "enabled", havingValue = "true")
    public CircuitBreaker elkCircuitBreaker(SentryProperties properties) {
        SentryProperties.CircuitBreakerConfig cb = properties.getMetrics().getCircuitBreaker();
        return new CircuitBreaker("elk-logstash",
                cb.getFailureRateThreshold(), cb.getSlidingWindowSize(),
                cb.getHalfOpenAfterSeconds() * 1000L);
    }

    @Bean("lokiCircuitBreaker")
    @ConditionalOnMissingBean(name = "lokiCircuitBreaker")
    @ConditionalOnProperty(prefix = "ydsz.sentry.logging.loki", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CircuitBreaker lokiCircuitBreaker(SentryProperties properties) {
        SentryProperties.CircuitBreakerConfig cb = properties.getMetrics().getCircuitBreaker();
        return new CircuitBreaker("loki",
                cb.getFailureRateThreshold(), cb.getSlidingWindowSize(),
                cb.getHalfOpenAfterSeconds() * 1000L);
    }

    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    public void circuitBreakerMetricsBinder(ObjectProvider<CircuitBreaker> circuitBreakers,
                                             ObjectProvider<MeterRegistry> meterRegistryProvider) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            circuitBreakers.stream().forEach(cb -> {
                String name = cb.getName();
                Gauge.builder("ydsz.sentry.circuitbreaker.state", cb,
                        st -> st.getState().ordinal())
                        .description("熔断器状态 (0=CLOSED,1=OPEN,2=HALF_OPEN)")
                        .tag("name", name)
                        .register(registry);
                Gauge.builder("ydsz.sentry.circuitbreaker.failures", cb,
                        CircuitBreaker::getFailureCount)
                        .description("熔断器失败计数")
                        .tag("name", name)
                        .register(registry);
            });
        }
    }


    // ==================== 日志发布 ====================

    @Bean
    @ConditionalOnMissingBean(LogPublisher.class)
    public LogPublisher logPublisher(SentryProperties properties,
                                     ObjectProvider<CircuitBreaker> circuitBreakers) {
        List<LogPublisher> publishers = new ArrayList<>();

        SentryProperties.ElkConfig elkConfig = properties.getLogging().getElk();
        if (elkConfig.isEnabled()) {
            CircuitBreaker elkCb = circuitBreakers.stream()
                    .filter(cb -> "elk-logstash".equals(cb.getName()))
                    .findFirst()
                    .orElse(null);
            publishers.add(new ElkLogPublisher(
                    elkConfig.getHost(), elkConfig.getPort(), elkConfig.getProtocol(),
                    elkConfig.getConnectTimeoutMillis(), elkConfig.getReadTimeoutMillis(),
                    elkConfig.getMaxRetryAttempts(), elkCb));
        }

        SentryProperties.LokiConfig lokiConfig = properties.getLogging().getLoki();
        if (lokiConfig.isEnabled()) {
            CircuitBreaker lokiCb = circuitBreakers.stream()
                    .filter(cb -> "loki".equals(cb.getName()))
                    .findFirst()
                    .orElse(null);
            publishers.add(new LokiLogPublisher(
                    lokiConfig.getUrl(), lokiConfig.getConnectTimeoutSeconds(),
                    lokiConfig.getMaxRetryAttempts(), lokiCb));
        }

        if (publishers.isEmpty()) {
            log.warn("[Sentry] 未启用任何日志发布器, 使用 Loki 默认配置");
            CircuitBreaker lokiCb = circuitBreakers.stream()
                    .filter(cb -> "loki".equals(cb.getName()))
                    .findFirst()
                    .orElse(null);
            publishers.add(new LokiLogPublisher(
                    lokiConfig.getUrl(), lokiConfig.getConnectTimeoutSeconds(),
                    lokiConfig.getMaxRetryAttempts(), lokiCb));
        }

        LogPublisher delegate;
        if (publishers.size() == 1) {
            delegate = publishers.get(0);
        } else {
            delegate = new DualLogPublisher(publishers, properties.getLogging().getDual().isFailOnAllError());
        }

        // 异步包装
        SentryProperties.AsyncConfig asyncConfig = properties.getLogging().getAsync();
        if (asyncConfig.isEnabled()) {
            asyncLogPublisher = new AsyncLogPublisher(delegate,
                    asyncConfig.getQueueCapacity(),
                    asyncConfig.getBatchSize(),
                    asyncConfig.getFlushIntervalMillis(),
                    asyncConfig.getMaxRatePerSecond());
            return asyncLogPublisher;
        }
        return delegate;
    }

    // ==================== 链路追踪 ====================

    @Bean
    @ConditionalOnMissingBean(TraceContext.class)
    public TraceContext traceContext(SentryProperties properties) {
        String primary = properties.getTracing().getPrimary();
        if ("skywalking".equals(primary)) {
            try {
                Class.forName("org.apache.skywalking.apm.toolkit.trace.TraceContext");
                return new SkyWalkingTraceContext();
            } catch (ClassNotFoundException e) {
                log.info("[Sentry] SkyWalking agent 未检测到, 尝试 OpenTelemetry");
            }
        }
        if ("opentelemetry".equals(primary) || "skywalking".equals(primary)) {
            try {
                if (OpenTelemetryTraceContext.isAvailable()) {
                    return new OpenTelemetryTraceContext();
                }
            } catch (Exception e) {
                log.info("[Sentry] OpenTelemetry SDK 不可用, 降级到 DefaultTraceContext");
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
    public AlertPublisher alertPublisher(SentryProperties properties,
                                         ObjectProvider<NotifyService> notifyServiceProvider) {
        DefaultAlertPublisher publisher = new DefaultAlertPublisher(
                properties.getAlerting().isLogAlerts());

        // 当 NotifyService 可用时注册通知处理器
        NotifyService notifyService = notifyServiceProvider.getIfAvailable();
        if (notifyService != null) {
            NotifyAlertHandler handler = new NotifyAlertHandler(
                    notifyService,
                    properties.getAlerting().getDingtalkReceiver(),
                    properties.getAlerting().getEmailReceiver());
            publisher.registerHandler(AlertSeverity.P0, handler);
            publisher.registerHandler(AlertSeverity.P1, handler);
            publisher.registerHandler(AlertSeverity.P2, handler);
            log.info("[Sentry] NotifyAlertHandler 已注册, 告警将通过 common-notify 发送");
        } else {
            log.info("[Sentry] NotifyService 不可用, 告警仅记录日志");
        }

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
    public SlaMetricAspect slaMetricAspect(SlaCollector slaCollector) {
        return new SlaMetricAspect(slaCollector);
    }

    // ==================== 自监控指标 ====================

    @Bean
    @ConditionalOnMissingBean
    public SentrySelfMonitor sentrySelfMonitor(MetricsCollector metricsCollector,
                                                 LogPublisher logPublisher,
                                                 AlertPublisher alertPublisher,
                                                 SentryProperties properties) {
        return new SentrySelfMonitor(metricsCollector, logPublisher, alertPublisher);
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
        if (asyncLogPublisher != null) {
            asyncLogPublisher.close();
        }
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

    /**
     * 自监控指标上报器
     *
     * <p>定时上报 Sentry 各组件的可用性指标到 MetricsCollector，
     * 供 Prometheus 告警规则使用。
     */
    @Slf4j
    public static class SentrySelfMonitor {

        private final MetricsCollector metricsCollector;
        private final LogPublisher logPublisher;
        private final AlertPublisher alertPublisher;

        public SentrySelfMonitor(MetricsCollector metricsCollector,
                                  LogPublisher logPublisher,
                                  AlertPublisher alertPublisher) {
            this.metricsCollector = metricsCollector;
            this.logPublisher = logPublisher;
            this.alertPublisher = alertPublisher;
            log.info("[Sentry] SentrySelfMonitor 初始化完成");
        }

        @Scheduled(fixedRate = 15000)
        public void reportSelfMetrics() {
            try {
                if (metricsCollector != null) {
                    metricsCollector.setGauge("ydsz.sentry.metrics.available",
                            "指标采集器可用性", null, metricsCollector.isAvailable() ? 1.0 : 0.0);
                }
                if (logPublisher != null) {
                    metricsCollector.setGauge("ydsz.sentry.logging.available",
                            "日志发布器可用性", null, logPublisher.isAvailable() ? 1.0 : 0.0);
                    if (logPublisher instanceof AsyncLogPublisher async) {
                        metricsCollector.setGauge("ydsz.sentry.logging.queue_size",
                                "异步日志队列积压数", null, async.getQueueSize());
                        metricsCollector.setGauge("ydsz.sentry.logging.dropped_total",
                                "异步日志丢弃总数", null, async.getDroppedCount());
                        metricsCollector.setGauge("ydsz.sentry.logging.published_total",
                                "异步日志已发布总数", null, async.getTotalPublished());
                    }
                }
                if (alertPublisher != null) {
                    metricsCollector.setGauge("ydsz.sentry.alerting.available",
                            "告警发布器可用性", null, alertPublisher.isAvailable() ? 1.0 : 0.0);
                }
                if (alertPublisher instanceof AlertConverger converger) {
                    metricsCollector.setGauge("ydsz.sentry.alert.suppression_rate",
                            "告警抑制率", null, converger.getSuppressionRate());
                    metricsCollector.setGauge("ydsz.sentry.alert.total",
                            "告警总数", null, converger.getTotalAlerts());
                    metricsCollector.setGauge("ydsz.sentry.alert.suppressed",
                            "被抑制告警数", null, converger.getSuppressedAlerts());
                }
            } catch (Exception e) {
                log.debug("[Sentry] 自监控指标上报异常: {}", e.getMessage());
            }
        }
    }
}
