package com.njydsz.common.feign.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.feign.config.FeignConfiguration;
import com.njydsz.common.feign.config.FeignProperties;

/**
 * Resilience4j 熔断器自动配置类。
 *
 * <p>当配置启用时，自动注册基于 Resilience4j 的 Feign 熔断器策略。
 *
 * <p><b>生效条件：</b>
 *
 * <ul>
 *   <li>classpath 中存在 Resilience4j {@code CircuitBreaker}
 *   <li>{@code ydsz.feign.circuit-breaker.enabled=true}
 *   <li>尚未注册其他 {@link FeignCircuitBreakerStrategy} Bean
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration(after = FeignConfiguration.class)
@ConditionalOnClass(CircuitBreaker.class)
@ConditionalOnProperty(
    prefix = "ydsz.feign.circuit-breaker",
    name = "enabled",
    havingValue = "true")
public class CircuitBreakerFeignConfiguration {

  /**
   * 注册熔断器指标导出器。
   *
   * <p>当 Micrometer MeterRegistry 在 classpath 中时自动创建。 使用单参数构造（仅 MeterRegistry），策略通过 setter
   * 注入以避免循环依赖。
   *
   * @param meterRegistry Micrometer 指标注册表
   * @return FeignCircuitBreakerMetricsExporter 实例
   */
  @Bean
  @ConditionalOnClass(MeterRegistry.class)
  @ConditionalOnBean(MeterRegistry.class)
  @ConditionalOnMissingBean
  public FeignCircuitBreakerMetricsExporter feignCircuitBreakerMetricsExporter(
      MeterRegistry meterRegistry) {
    return new FeignCircuitBreakerMetricsExporter(meterRegistry);
  }

  /**
   * 注册 Resilience4j 熔断器策略 Bean。
   *
   * <p>自动注入 {@link CircuitBreakerStatePersistence}（用于状态持久化） 和 {@link
   * FeignCircuitBreakerMetricsExporter}（用于指标自动注册）。
   *
   * @param properties Feign 配置属性
   * @param statePersistenceProvider 熔断状态持久化提供者（可选）
   * @param metricsExporterProvider 熔断指标导出器提供者（可选）
   * @return SafeCircuitBreakerAdapter 实例
   */
  @Bean
  @ConditionalOnMissingBean(FeignCircuitBreakerStrategy.class)
  public FeignCircuitBreakerStrategy safeCircuitBreakerStrategy(
      FeignProperties properties,
      ObjectProvider<CircuitBreakerStatePersistence> statePersistenceProvider,
      ObjectProvider<FeignCircuitBreakerMetricsExporter> metricsExporterProvider) {
    FeignCircuitBreakerMetricsExporter exporter = metricsExporterProvider.getIfAvailable();
    SafeCircuitBreakerAdapter adapter =
        new SafeCircuitBreakerAdapter(
            properties, statePersistenceProvider.getIfAvailable(), exporter);
    if (exporter != null) {
      exporter.setCircuitBreakerStrategy(adapter);
    }
    log.info("[Feign] 使用 Resilience4j 熔断器策略");
    return adapter;
  }
}
