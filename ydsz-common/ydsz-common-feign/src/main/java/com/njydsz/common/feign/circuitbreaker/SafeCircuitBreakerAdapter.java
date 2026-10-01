package com.njydsz.common.feign.circuitbreaker;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.feign.config.FeignProperties;

/**
 * 基于 Resilience4j 的 Feign 熔断器适配器。
 *
 * <p>封装 Resilience4j {@link CircuitBreaker} 实现，实现 {@link FeignCircuitBreakerStrategy} 接口。
 * 每个服务名称对应一个独立的熔断器实例，参数从 {@code ydsz.feign.circuit-breaker.*} 配置读取。
 *
 * <p>推荐使用接受 {@link CircuitBreakerRegistry} 参数的构造方法，以复用 ydzs-common-safe
 * 提供的共享注册表（Bean 名 {@code safeCircuitBreakerRegistry}），保证全平台指标统一采集。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class SafeCircuitBreakerAdapter implements FeignCircuitBreakerStrategy {

  private final FeignProperties properties;
  private final CircuitBreakerStatePersistence statePersistence;
  private final FeignCircuitBreakerMetricsExporter metricsExporter;
  private final CircuitBreakerRegistry registry;

  /**
   * 构造熔断适配器（使用共享注册表）。
   *
   * <p>使用 ydzs-common-safe 提供的共享 {@link CircuitBreakerRegistry}，
   * 保证熔断器指标统一采集到全局 Micrometer。
   *
   * @param properties Feign 配置属性
   * @param statePersistence 状态持久化（可为 null）
   * @param metricsExporter 指标导出器（可为 null）
   * @param registry 共享的熔断器注册表
   */
  public SafeCircuitBreakerAdapter(
      FeignProperties properties,
      CircuitBreakerStatePersistence statePersistence,
      FeignCircuitBreakerMetricsExporter metricsExporter,
      CircuitBreakerRegistry registry) {
    this.properties = properties;
    this.statePersistence = statePersistence;
    this.metricsExporter = metricsExporter;
    this.registry = registry;
  }

  @Override
  public boolean allowRequest(String serviceName) {
    return getOrCreate(serviceName).tryAcquirePermission();
  }

  @Override
  public void recordSuccess(String serviceName, long durationMs) {
    getOrCreate(serviceName).onSuccess(durationMs, TimeUnit.MILLISECONDS);
    if (metricsExporter != null) {
      metricsExporter.registerServiceMetrics(serviceName);
    }
  }

  @Override
  public void recordFailure(String serviceName, long durationMs, Throwable throwable) {
    getOrCreate(serviceName).onError(durationMs, TimeUnit.MILLISECONDS, throwable);
  }

  @Override
  public CircuitBreakerState getState(String serviceName) {
    return switch (getOrCreate(serviceName).getState()) {
      case OPEN -> CircuitBreakerState.OPEN;
      case HALF_OPEN -> CircuitBreakerState.HALF_OPEN;
      default -> CircuitBreakerState.CLOSED;
    };
  }

  @Override
  public CircuitBreakerMetrics getMetrics(String serviceName) {
    CircuitBreaker.Metrics metrics = getOrCreate(serviceName).getMetrics();
    return new CircuitBreakerMetrics() {
      @Override
      public BigDecimal getFailureRate() {
        return new BigDecimal(Float.toString(metrics.getFailureRate()));
      }

      @Override
      public int getTotalCalls() {
        return metrics.getNumberOfBufferedCalls();
      }

      @Override
      public int getSuccessfulCalls() {
        return metrics.getNumberOfSuccessfulCalls();
      }

      @Override
      public int getFailedCalls() {
        return metrics.getNumberOfFailedCalls();
      }

      @Override
      public int getSlowCalls() {
        return metrics.getNumberOfSlowCalls();
      }

      @Override
      public long getAverageDuration() {
        // Resilience4j circuitbreaker 2.x Metrics API 不直接暴露时长聚合，依赖 Micrometer 聚合；
        // 为兼容 FeignCircuitBreakerMetricsExporter 调用，保留返回 0（指标由 micrometer-resilience4j 采集）
        return 0L;
      }
    };
  }

  private CircuitBreaker getOrCreate(String serviceName) {
    // 使用 circuitBreaker(name, supplier) 实现懒加载：同名熔断器已存在时直接返回，
    // 不存在时通过 supplier 构建配置并创建，避免 computeIfAbsent 内部递归调用 registry
    return registry.circuitBreaker(
        serviceName,
        () -> {
          // 熔断参数从统一配置入口读取（ydsz.feign.circuit-breaker.*）
          FeignProperties.CircuitBreaker cbConfig = properties.getCircuitBreaker();
          return CircuitBreakerConfig.custom()
              .failureRateThreshold(cbConfig.getFailureRateThreshold())
              .slowCallRateThreshold(cbConfig.getSlowCallRateThreshold())
              .slowCallDurationThreshold(Duration.ofMillis(cbConfig.getSlowCallDurationMs()))
              .waitDurationInOpenState(Duration.ofMillis(cbConfig.getWaitDurationMs()))
              .permittedNumberOfCallsInHalfOpenState(cbConfig.getPermittedNumberOfCallsInHalfOpenState())
              .minimumNumberOfCalls(cbConfig.getMinimumNumberOfCalls())
              .slidingWindowSize(cbConfig.getSlidingWindowSize())
              .automaticTransitionFromOpenToHalfOpenEnabled(true)
              .build();
        });
  }
}
