package com.njydsz.common.sentry.config;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
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
import com.njydsz.common.thread.factory.InternalExecutorFactory;

/**
 * 指标采集与熔断器自动配置。
 *
 * <p>装配以下组件：
 *
 * <ul>
 *   <li>{@link MicrometerMetricsCollector}：Micrometer 指标采集（优先）
 *   <li>{@link InMemoryMetricsCollector}：内存指标采集（降级）
 *   <li>{@link SystemMetricsCollector}：系统资源指标（CPU/内存/磁盘/GC）
 *   <li>{@link CircuitBreaker}：ELK/Loki 通道独立熔断器（基于 Resilience4j）
 * </ul>
 *
 * <h3>1.0.0 变更（2026-09-01）</h3>
 *
 * <ul>
 *   <li>熔断底层改为 Resilience4j（{@code resilience4j-circuitbreaker}），移除自研引擎依赖
 *   <li>新增 Resilience4j {@code CircuitBreakerRegistry} 共享 Bean，统一管理熔断器配置
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
   */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(MeterRegistry.class)
  public static class MicrometerMetricsConfiguration {

    @Bean
    @ConditionalOnMissingBean(MetricsCollector.class)
    @ConditionalOnProperty(
        prefix = "ydsz.sentry.metrics",
        name = "primary",
        havingValue = "micrometer",
        matchIfMissing = true)
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
   * @param properties 监控配置
   * @return 系统指标采集器
   */
  @Bean
  @ConditionalOnMissingBean(SystemMetricsCollector.class)
  @ConditionalOnProperty(
      prefix = "ydsz.sentry.metrics",
      name = "enable-system-metrics",
      havingValue = "true",
      matchIfMissing = true)
  public SystemMetricsCollector systemMetricsCollector(
      MetricsCollector metricsCollector, SentryProperties properties) {
    SystemMetricsCollector collector = new SystemMetricsCollector(metricsCollector);
    int interval = properties.getMetrics().getSystemMetricsIntervalSeconds();
    // 系统指标采集调度器：单线程固定，守护线程，统一使用 InternalExecutorFactory 纳入线程池治理
    systemMetricsScheduler =
        InternalExecutorFactory.newSingleThreadScheduledPool("sentry-system-metrics");
    systemMetricsScheduler.scheduleAtFixedRate(collector::collect, 5, interval, TimeUnit.SECONDS);
    log.info("[Sentry] 系统资源指标定时采集已启动, interval={}s", interval);
    return collector;
  }

  /**
   * 注册 Resilience4j 共享熔断器注册中心。
   *
   * <p>使用 Sentry 配置的默认熔断参数创建全局 Registry，
   * 所有熔断器（ELK/Loki 通道）共享此 Registry 以便统一管理与事件订阅。
   * 若 Spring 容器已存在 Resilience4j {@code CircuitBreakerRegistry} Bean（由 spring-cloud-starter-circuitbreaker-resilience4j 等自动装配），
   * 则本方法不生效（@ConditionalOnMissingBean 保证唯一性）。
   *
   * @param properties 监控配置
   * @return Resilience4j 共享 CircuitBreakerRegistry
   */
  @Bean
  @ConditionalOnMissingBean(CircuitBreakerRegistry.class)
  public CircuitBreakerRegistry circuitBreakerRegistry(SentryProperties properties) {
    SentryProperties.CircuitBreakerConfig cb = properties.getMetrics().getCircuitBreaker();
    CircuitBreakerConfig config =
        CircuitBreakerConfig.custom()
            // SentryProperties 阈值为 0-1 比例，换算为 Resilience4j 百分比语义
            .failureRateThreshold((float) (cb.getFailureRateThreshold() * 100))
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
            .slidingWindowSize(cb.getSlidingWindowSize())
            .waitDurationInOpenState(Duration.ofSeconds(cb.getHalfOpenAfterSeconds()))
            .minimumNumberOfCalls(10)
            .permittedNumberOfCallsInHalfOpenState(1)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build();
    log.info("[Sentry] Resilience4j CircuitBreakerRegistry 初始化完成");
    return CircuitBreakerRegistry.of(config);
  }

  /**
   * 为 ELK（Logstash）日志通道装配独立熔断器。
   *
   * @param properties 监控配置
   * @param registry 共享注册中心
   * @return ELK 通道专用熔断器
   */
  @Bean("elkCircuitBreaker")
  @ConditionalOnMissingBean(name = "elkCircuitBreaker")
  @ConditionalOnProperty(prefix = "ydsz.sentry.logging.elk", name = "enabled", havingValue = "true")
  public CircuitBreaker elkCircuitBreaker(
      SentryProperties properties, CircuitBreakerRegistry registry) {
    SentryProperties.CircuitBreakerConfig cb = properties.getMetrics().getCircuitBreaker();
    CircuitBreakerConfig config = buildChannelConfig(cb);
    return CircuitBreaker.fromRegistry("elk-logstash", config, registry);
  }

  /**
   * 为 Loki 日志通道装配独立熔断器。
   *
   * @param properties 监控配置
   * @param registry 共享注册中心
   * @return Loki 通道专用熔断器
   */
  @Bean("lokiCircuitBreaker")
  @ConditionalOnMissingBean(name = "lokiCircuitBreaker")
  @ConditionalOnProperty(
      prefix = "ydsz.sentry.logging.loki",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public CircuitBreaker lokiCircuitBreaker(
      SentryProperties properties, CircuitBreakerRegistry registry) {
    SentryProperties.CircuitBreakerConfig cb = properties.getMetrics().getCircuitBreaker();
    CircuitBreakerConfig config = buildChannelConfig(cb);
    return CircuitBreaker.fromRegistry("loki", config, registry);
  }

  /** 构建通道熔断配置（SentryProperties 0-1 比例 → Resilience4j 百分比）。 */
  private static CircuitBreakerConfig buildChannelConfig(SentryProperties.CircuitBreakerConfig cb) {
    return CircuitBreakerConfig.custom()
        .failureRateThreshold((float) (cb.getFailureRateThreshold() * 100))
        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
        .slidingWindowSize(cb.getSlidingWindowSize())
        .waitDurationInOpenState(Duration.ofSeconds(cb.getHalfOpenAfterSeconds()))
        .minimumNumberOfCalls(10)
        .permittedNumberOfCallsInHalfOpenState(1)
        .automaticTransitionFromOpenToHalfOpenEnabled(true)
        .build();
  }

  /** 容器关闭时停止系统指标采集线程。 */
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
