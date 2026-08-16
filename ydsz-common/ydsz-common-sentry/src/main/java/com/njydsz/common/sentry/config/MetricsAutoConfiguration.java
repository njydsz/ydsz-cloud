package com.njydsz.common.sentry.config;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.ObjectProvider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.common.sentry.metrics.InMemoryMetricsCollector;
import com.njydsz.common.sentry.metrics.MicrometerMetricsCollector;
import com.njydsz.common.sentry.metrics.SystemMetricsCollector;
import com.njydsz.common.sentry.resilience.CircuitBreaker;
import com.njydsz.common.sentry.spi.MetricsCollector;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 指标采集与熔断器自动配置。
 *
 * <p>装配以下组件：
 * <ul>
 *   <li>{@link MicrometerMetricsCollector}：Micrometer 指标采集（优先）</li>
 *   <li>{@link InMemoryMetricsCollector}：内存指标采集（降级）</li>
 *   <li>{@link SystemMetricsCollector}：系统资源指标（CPU/内存/磁盘/GC）</li>
 *   <li>{@link CircuitBreaker}：ELK/Loki 通道独立熔断器</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SentryProperties.class)
public class MetricsAutoConfiguration {

    private ScheduledExecutorService systemMetricsScheduler;

    /**
     * 装配基于 Micrometer 的指标采集器，作为默认（首选）实现。
     *
     * <p>仅在 classpath 存在 {@link MeterRegistry} 且未显式配置其他 primary 时生效。
     *
     * @param meterRegistry Micrometer 注册中心，由 Spring Boot Actuator 提供
     * @return 指标采集器实现
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    public static class MicrometerMetricsConfiguration {

        @Bean
        @ConditionalOnMissingBean(MetricsCollector.class)
        @ConditionalOnProperty(prefix = "ydsz.sentry.metrics", name = "primary",
                havingValue = "micrometer", matchIfMissing = true)
        public MicrometerMetricsCollector micrometerMetricsCollector(MeterRegistry meterRegistry) {
            return new MicrometerMetricsCollector(meterRegistry);
        }
    }

    /**
     * 装配纯内存指标采集器，作为无 Micrometer 环境下的降级实现。
     *
     * @return 内存指标采集器实现
     */
    @Bean
    @ConditionalOnMissingBean(MetricsCollector.class)
    @ConditionalOnProperty(prefix = "ydsz.sentry.metrics", name = "primary", havingValue = "memory")
    public InMemoryMetricsCollector inMemoryMetricsCollector() {
        return new InMemoryMetricsCollector();
    }

    /**
     * 装配系统资源指标采集器，并启动独立守护线程周期性采集。
     *
     * @param metricsCollector 指标写出目标
     * @param properties       监控配置
     * @return 系统指标采集器
     */
    @Bean
    @ConditionalOnMissingBean(SystemMetricsCollector.class)
    @ConditionalOnProperty(prefix = "ydsz.sentry.metrics", name = "enable-system-metrics",
            havingValue = "true", matchIfMissing = true)
    public SystemMetricsCollector systemMetricsCollector(
            MetricsCollector metricsCollector,
            SentryProperties properties) {
        SystemMetricsCollector collector = new SystemMetricsCollector(metricsCollector);
        int interval = properties.getMetrics().getSystemMetricsIntervalSeconds();
        systemMetricsScheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "sentry-system-metrics");
            t.setDaemon(true);
            return t;
        }, new ThreadPoolExecutor.CallerRunsPolicy());
        systemMetricsScheduler.scheduleAtFixedRate(collector::collect, 5, interval, TimeUnit.SECONDS);
        log.info("[Sentry] 系统资源指标定时采集已启动, interval={}s", interval);
        return collector;
    }

    /**
     * 为 ELK（Logstash）日志通道装配独立熔断器。
     *
     * @param properties 监控配置
     * @return ELK 通道专用熔断器
     */
    @Bean("elkCircuitBreaker")
    @ConditionalOnMissingBean(name = "elkCircuitBreaker")
    @ConditionalOnProperty(prefix = "ydsz.sentry.logging.elk", name = "enabled", havingValue = "true")
    public CircuitBreaker elkCircuitBreaker(SentryProperties properties) {
        SentryProperties.CircuitBreakerConfig cb = properties.getMetrics().getCircuitBreaker();
        return new CircuitBreaker("elk-logstash",
                cb.getFailureRateThreshold(), cb.getSlidingWindowSize(),
                cb.getHalfOpenAfterSeconds() * 1000L);
    }

    /**
     * 为 Loki 日志通道装配独立熔断器。
     *
     * @param properties 监控配置
     * @return Loki 通道专用熔断器
     */
    @Bean("lokiCircuitBreaker")
    @ConditionalOnMissingBean(name = "lokiCircuitBreaker")
    @ConditionalOnProperty(prefix = "ydsz.sentry.logging.loki", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public CircuitBreaker lokiCircuitBreaker(SentryProperties properties) {
        SentryProperties.CircuitBreakerConfig cb = properties.getMetrics().getCircuitBreaker();
        return new CircuitBreaker("loki",
                cb.getFailureRateThreshold(), cb.getSlidingWindowSize(),
                cb.getHalfOpenAfterSeconds() * 1000L);
    }

    /**
     * 把容器内所有熔断器的状态与失败计数绑定为 Micrometer Gauge。
     *
     * @param circuitBreakers       容器内全部熔断器
     * @param meterRegistryProvider Micrometer 注册中心提供者
     */
    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    public void circuitBreakerMetricsBinder(
            ObjectProvider<CircuitBreaker> circuitBreakers,
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

    /**
     * 容器关闭时停止系统指标采集线程。
     */
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
