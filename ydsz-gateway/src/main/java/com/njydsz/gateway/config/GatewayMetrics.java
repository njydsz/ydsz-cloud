package com.njydsz.gateway.config;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import com.njydsz.common.sentry.SentryService;
import com.njydsz.common.sentry.metrics.MicrometerMetricsCollector;
import com.njydsz.common.sentry.spi.MetricsCollector;

/**
 * 网关自定义 Prometheus 指标。
 *
 * <p>使用组合模式注入 {@link SentryService}，通过 {@link MetricsCollector} SPI 注册指标，
 * 解除与 {@code SentryMetricsAdapter} 的继承耦合。
 *
 * <h3>指标清单（Prometheus 指标名 = 前缀 + 名称）</h3>
 *
 * <ul>
 *   <li>{@code ydsz_gateway_request_duration_seconds} — 按路由分桶的请求延迟
 *   <li>{@code ydsz_gateway_request_total} — 请求总数计数器（route/method/status 标签）
 *   <li>{@code ydsz_gateway_ratelimit_triggered_total} — 限流触发计数器（dimension/route 标签）
 *   <li>{@code ydsz_gateway_jwt_validation_duration_seconds} — JWT 校验耗时（cached 标签）
 *   <li>{@code ydsz_gateway_circuit_breaker_state} — 熔断器状态（0=closed, 1=open, 2=half-open）
 * </ul>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
@ConditionalOnClass(MeterRegistry.class)
public class GatewayMetrics {

  /** 指标前缀 */
  private static final String PREFIX = "ydsz_gateway_";

  /** 按 routeId 维护的熔断器状态引用 */
  private final ConcurrentMap<String, AtomicInteger> breakerStates = new ConcurrentHashMap<>();

  /** 本地兜底限流配额引用 */
  private final AtomicInteger fallbackQuotaRef = new AtomicInteger(0);

  /** SentryService 提供者（可选，Sentry 模块未装配时降级为空操作） */
  private final ObjectProvider<SentryService> sentryServiceProvider;

  /**
   * 构造网关指标组件。
   *
   * @param sentryServiceProvider SentryService 提供者（可选）
   */
  public GatewayMetrics(ObjectProvider<SentryService> sentryServiceProvider) {
    this.sentryServiceProvider = sentryServiceProvider;
    log.info("[GatewayMetrics] 自定义 Prometheus 指标初始化完成");
  }

  /**
   * 获取 MetricsCollector 实例。
   *
   * @return MetricsCollector 实例，可能为 null
   */
  private MetricsCollector getMetricsCollector() {
    SentryService service = sentryServiceProvider.getIfAvailable();
    if (service == null) {
      return null;
    }
    return service.getMetricsCollector();
  }

  /**
   * 获取 Micrometer MeterRegistry。
   *
   * @return MeterRegistry 或 null
   */
  private MeterRegistry getMicrometerRegistry() {
    MetricsCollector collector = getMetricsCollector();
    if (collector instanceof MicrometerMetricsCollector micrometer) {
      return micrometer.getMeterRegistry();
    }
    return null;
  }

  /**
   * 记录请求延迟。
   *
   * @param routeId 路由 ID
   * @param method 请求方法
   * @param status 响应状态码
   * @param durationMs 请求耗时（毫秒）
   */
  public void recordRequestDuration(String routeId, String method, int status, long durationMs) {
    MetricsCollector collector = getMetricsCollector();
    if (collector != null) {
      collector.recordTimer(PREFIX + "request_duration_seconds", null,
          toMap("route", safe(routeId), "method", safe(method), "status", String.valueOf(status)),
          Duration.ofMillis(durationMs));
    }
  }

  /**
   * 增加请求计数。
   *
   * @param routeId 路由 ID
   * @param method 请求方法
   * @param status 响应状态码
   */
  public void incrementRequestTotal(String routeId, String method, int status) {
    MetricsCollector collector = getMetricsCollector();
    if (collector != null) {
      collector.incrementCounter(PREFIX + "request_total", null,
          toMap("route", safe(routeId), "method", safe(method), "status", String.valueOf(status)), 1.0);
    }
  }

  /**
   * 增加灰度路由命中计数。。
   * @param hitGray 增加灰度路由命中计数。
   */
  public void incrementGrayHit(boolean hitGray) {
    MetricsCollector collector = getMetricsCollector();
    if (collector != null) {
      collector.incrementCounter(PREFIX + "gray_hit_total", null,
          toMap("gray", String.valueOf(hitGray)), 1.0);
    }
  }

  /**
   * 增加限流触发计数。
   *
   * @param dimension 限流维度
   * @param routeId 路由 ID
   */
  public void incrementRatelimitTriggered(String dimension, String routeId) {
    MetricsCollector collector = getMetricsCollector();
    if (collector != null) {
      collector.incrementCounter(PREFIX + "ratelimit_triggered_total", null,
          toMap("dimension", safe(dimension), "route", safe(routeId)), 1.0);
    }
  }

  /**
   * 增加限流本地兜底计数。。
   */
  public void incrementRatelimitFallback() {
    MetricsCollector collector = getMetricsCollector();
    if (collector != null) {
      collector.incrementCounter(PREFIX + "ratelimit_fallback_total", null, null, 1.0);
    }
  }

  /**
   * 上报本地兜底令牌桶的自适应配额。。
   * @param quota 上报本地兜底令牌桶的自适应配额。
   */
  public void setRatelimitFallbackQuota(int quota) {
    if (fallbackQuotaRef.getAndSet(quota) == 0) {
      MeterRegistry registry = getMicrometerRegistry();
      if (registry != null) {
        registry.gauge(PREFIX + "ratelimit_fallback_quota", fallbackQuotaRef, AtomicInteger::doubleValue);
      }
    }
  }

  /**
   * 记录 JWT 校验耗时。
   *
   * @param durationMs 校验耗时（毫秒）
   * @param cached 是否命中缓存
   */
  public void recordJwtValidationDuration(long durationMs, boolean cached) {
    MetricsCollector collector = getMetricsCollector();
    if (collector != null) {
      collector.recordTimer(PREFIX + "jwt_validation_duration_seconds", null,
          toMap("cached", String.valueOf(cached)), Duration.ofMillis(durationMs));
    }
  }

  /**
   * 设置熔断器状态。
   *
   * @param routeId 路由 ID
   * @param state 熔断状态值
   */
  public void setCircuitBreakerState(String routeId, int state) {
    AtomicInteger ref = breakerStates.computeIfAbsent(routeId, k -> {
      AtomicInteger holder = new AtomicInteger(state);
      MeterRegistry registry = getMicrometerRegistry();
      if (registry != null) {
        registry.gauge(PREFIX + "circuit_breaker_state", Tags.of("route", safe(k)),
            holder, AtomicInteger::doubleValue);
      }
      return holder;
    });
    ref.set(state);
  }

  /**
   * 注册 JWT 缓存命中/未命中计数器到 Prometheus。
   *
   * @param hitCounter 命中计数器引用
   * @param missCounter 未命中计数器引用
   */
  public void registerJwtCacheCounters(AtomicLong hitCounter, AtomicLong missCounter) {
    MeterRegistry registry = getMicrometerRegistry();
    if (registry != null) {
      registry.gauge(PREFIX + "jwt_cache_hit_rate", hitCounter, AtomicLong::doubleValue);
      registry.gauge(PREFIX + "jwt_cache_miss_total", missCounter, AtomicLong::doubleValue);
      log.info("[GatewayMetrics] JWT 缓存命中/未命中 Prometheus 指标已注册");
    }
  }

  /**
   * 计算 JWT 缓存命中率。
   *
   * @param hitCounter 命中计数器引用
   * @param missCounter 未命中计数器引用
   * @return 缓存命中率（0.0 ~ 1.0），无请求时返回 -1.0
   */
  public static double calculateJwtCacheHitRate(AtomicLong hitCounter, AtomicLong missCounter) {
    long hits = hitCounter.get();
    long total = hits + missCounter.get();
    return total > 0 ? (double) hits / total : -1.0;
  }

  /**
   * Null 安全的字符串处理。
   *
   * @param value 原始值（可为 null）
   * @return 非 null 字符串
   */
  private static String safe(String value) {
    return (value == null || value.isEmpty()) ? "unknown" : value;
  }

  /**
   * 将标签键值对转换为 Map。
   *
   * @param keyValuePairs 标签键值对（k1, v1, k2, v2...）
   * @return 标签 Map
   */
  private static Map<String, String> toMap(String... keyValuePairs) {
    if (keyValuePairs == null || keyValuePairs.length == 0) {
      return Collections.emptyMap();
    }
    Map<String, String> map = new HashMap<>();
    for (int i = 0; i < keyValuePairs.length - 1; i += 2) {
      map.put(keyValuePairs[i], keyValuePairs[i + 1]);
    }
    return map;
  }

}
