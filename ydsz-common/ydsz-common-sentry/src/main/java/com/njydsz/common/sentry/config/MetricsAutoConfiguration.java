package com.njydsz.common.sentry.config;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
 *   <li>{@link CircuitBreaker}：ELK/Loki 通道独立熔断器（基于平台自研弹性引擎）
 * </ul>
 *
 * <h3>1.0.0 变更</h3>
 *
 * <ul>
 *   <li>熔断底层改为平台自研弹性引擎（common-safe resilience 包），移除第三方弹性库依赖
 *   <li>新增自研 {@code CircuitBreakerRegistry} 共享 Bean，统一管理熔断器配置
 *   <li>修复历史缺陷：SentryProperties 失败率阈值（0-1）此前直传百分比语义 API，
 *       实际生效阈值缩小 100 倍；现已正确换算
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
    /**
     * micrometer metrics collector。
     * @param meterRegistry 参数
     * @return 结果
     */
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
  /**
   * in memory metrics。
   * @return 结果
   */
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
   * 注册共享的自研 CircuitBreakerRegistry。
   *
   * <p>使用 Sentry 配置的默认熔断参数创建全局 Registry， 所有熔断器（ELK/Loki 通道）共享此
   * Registry 以便统一管理与事件订阅。指标导出请订阅各熔断器的 事件发布器并桥接 Micrometer。
   *
   * @param properties 监控配置
   * @return 共享的自研 CircuitBreakerRegistry
   */
  @Bean
  @ConditionalOnMissingBean(
      beanTypes = com.njydsz.common.safe.resilience.CircuitBreakerRegistry.class)
  public com.njydsz.common.safe.resilience.CircuitBreakerRegistry circuitBreakerRegistry(
      SentryProperties properties) {
    SentryProperties.CircuitBreakerConfig cb = properties.getMetrics().getCircuitBreaker();
    com.njydsz.common.safe.resilience.CircuitBreakerConfig config =
        com.njydsz.common.safe.resilience.CircuitBreakerConfig.custom()
            // SentryProperties 阈值为 0-1 比例，换算为引擎百分比语义
            .failureRateThreshold((float) (cb.getFailureRateThreshold() * 100))
            .slidingWindowType(
                com.njydsz.common.safe.resilience.CircuitBreakerConfig.SlidingWindowType
                    .TIME_BASED)
            .slidingWindowSize(cb.getSlidingWindowSize())
            .waitDurationInOpenState(java.time.Duration.ofSeconds(cb.getHalfOpenAfterSeconds()))
            .minimumNumberOfCalls(10)
            .permittedNumberOfCallsInHalfOpenState(1)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build();
    return new com.njydsz.common.safe.resilience.CircuitBreakerRegistry(config);
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
  /**
   * elk circuit breaker。
   * @param properties 参数
   * @return 结果
   */
  public CircuitBreaker elkCircuitBreaker(SentryProperties properties) {
    SentryProperties.CircuitBreakerConfig cb = properties.getMetrics().getCircuitBreaker();
    return new CircuitBreaker(
        "elk-logstash",
        cb.getFailureRateThreshold(),
        cb.getSlidingWindowSize(),
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
  @ConditionalOnProperty(
      prefix = "ydsz.sentry.logging.loki",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  /**
   * loki circuit breaker。
   * @param properties 参数
   * @return 结果
   */
  public CircuitBreaker lokiCircuitBreaker(SentryProperties properties) {
    SentryProperties.CircuitBreakerConfig cb = properties.getMetrics().getCircuitBreaker();
    return new CircuitBreaker(
        "loki",
        cb.getFailureRateThreshold(),
        cb.getSlidingWindowSize(),
        cb.getHalfOpenAfterSeconds() * 1000L);
  }

  /** 容器关闭时停止系统指标采集线程。 */
  @PreDestroy
  /**
   * destroy。
   */
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
