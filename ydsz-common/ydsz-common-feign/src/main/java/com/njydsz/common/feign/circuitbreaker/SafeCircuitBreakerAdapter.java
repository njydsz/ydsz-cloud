package com.njydsz.common.feign.circuitbreaker;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.feign.config.FeignProperties;

/**
 * 平台自研熔断器适配器。
 *
 * <p>封装自研弹性引擎 {@link com.njydsz.common.safe.resilience.CircuitBreaker} 实例，
 * 实现 {@link FeignCircuitBreakerStrategy} 接口。 每个服务名称对应一个独立的熔断器实例。
 *
 * <p>历史说明：1.0.0 曾基于 Resilience4j；因内网项目不允许引入第三方弹性库竞品，
 * 现改为平台自研引擎（决策见 docs/ADR-0004-resilience-self-hosted.md），对外 API 保持兼容。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class SafeCircuitBreakerAdapter implements FeignCircuitBreakerStrategy {

  private final FeignProperties properties;
  private final CircuitBreakerStatePersistence statePersistence;
  private final FeignCircuitBreakerMetricsExporter metricsExporter;
  private final com.njydsz.common.safe.resilience.CircuitBreakerRegistry registry;

  /**
   * 构造自研熔断适配器。
   *
   * @param properties Feign 配置属性
   * @param statePersistence 状态持久化（可为 null）
   * @param metricsExporter 指标导出器（可为 null）
   */
  public SafeCircuitBreakerAdapter(
      FeignProperties properties,
      CircuitBreakerStatePersistence statePersistence,
      FeignCircuitBreakerMetricsExporter metricsExporter) {
    this.properties = properties;
    this.statePersistence = statePersistence;
    this.metricsExporter = metricsExporter;
    this.registry = new com.njydsz.common.safe.resilience.CircuitBreakerRegistry(
        com.njydsz.common.safe.resilience.CircuitBreakerConfig.ofDefaults());
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
      case FORCED_OPEN -> CircuitBreakerState.FORCED_OPEN;
      default -> CircuitBreakerState.CLOSED;
    };
  }

  @Override
  public CircuitBreakerMetrics getMetrics(String serviceName) {
    com.njydsz.common.safe.resilience.CircuitBreaker.Metrics metrics =
        getOrCreate(serviceName).getMetrics();
    return new CircuitBreakerMetrics() {
      @Override
      public float getFailureRate() {
        return metrics.getFailureRate();
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
        return metrics.getAverageDurationMs();
      }
    };
  }

  private com.njydsz.common.safe.resilience.CircuitBreaker getOrCreate(String serviceName) {
    return registry.computeIfAbsent(
        serviceName,
        () -> {
          // 熔断参数从配置读取（ydsz.feign.circuit-breaker.*），不再硬编码，支持按环境调优
          FeignProperties.CircuitBreaker cbConfig = properties.getCircuitBreaker();
          return com.njydsz.common.safe.resilience.CircuitBreakerConfig.custom()
              .failureRateThreshold(cbConfig.getFailureRateThreshold())
              .slowCallRateThreshold(cbConfig.getSlowCallRateThreshold())
              .slowCallDurationThreshold(Duration.ofMillis(cbConfig.getSlowCallDurationMs()))
              .waitDurationInOpenState(Duration.ofMillis(cbConfig.getWaitDurationMs()))
              .minimumNumberOfCalls(cbConfig.getMinimumNumberOfCalls())
              .slidingWindowSize(cbConfig.getSlidingWindowSize())
              .build();
        });
  }
}
