package com.njydsz.gateway.config;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import com.njydsz.common.sentry.adapter.SentryMetricsAdapter;

/**
 * 网关自定义 Prometheus 指标。
 *
 * <p>继承 {@link SentryMetricsAdapter}，通过 {@code MetricsCollector} SPI 注册指标，
 * 解除与 {@code MeterRegistry} 的直接耦合，符合《云顶编码规范》第 27.2.1 节
 * 「禁止直接操作 MeterRegistry」的强制要求。
 *
 * <h3>指标清单（Prometheus 指标名 = 前缀 + 名称）</h3>
 *
 * <ul>
 *   <li>{@code ydsz_gateway_request_duration_seconds} — 按路由分桶的请求延迟
 *   <li>{@code ydsz_gateway_request_total} — 请求总数计数器（route/method/status 标签）
 *   <li>{@code ydsz_gateway_ratelimit_triggered_total} — 限流触发计数器（dimension/route 标签）
 *   <li>{@code ydsz_gateway_ratelimit_fallback_quota} — 本地兜底令牌桶自适应配额（Gauge）
 *   <li>{@code ydsz_gateway_jwt_validation_duration_seconds} — JWT 校验耗时（cached 标签）
 *   <li>{@code ydsz_gateway_circuit_breaker_state} — 熔断器状态（0=closed, 1=open, 2=half-open; 按 route 标签区分）
 *   <li>{@code ydsz_gateway_jwt_cache_hit_total} — JWT 缓存命中数（Gauge）
 *   <li>{@code ydsz_gateway_jwt_cache_miss_total} — JWT 缓存未命中数（Gauge）
 * </ul>
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Slf4j
@Component
@ConditionalOnClass(MeterRegistry.class)
public class GatewayMetrics extends SentryMetricsAdapter {

  /** 指标前缀 */
  private static final String PREFIX = "ydsz_gateway_";

  /** 熔断器状态：关闭 */
  public static final int STATE_CLOSED = 0;

  /** 熔断器状态：打开 */
  public static final int STATE_OPEN = 1;

  /** 熔断器状态：半开 */
  public static final int STATE_HALF_OPEN = 2;

  /** 按 routeId 维护的熔断器状态引用 */
  private final ConcurrentMap<String, AtomicInteger> breakerStates = new ConcurrentHashMap<>();

  /** 本地兜底限流配额引用 */
  private final AtomicInteger fallbackQuotaRef = new AtomicInteger(0);

  /** 配额 Gauge 注册标志（确保仅注册一次） */
  private final AtomicBoolean quotaGaugeRegistered = new AtomicBoolean(false);

  /** JWT 缓存 Gauge 注册标志 */
  private final AtomicBoolean jwtGaugeRegistered = new AtomicBoolean(false);

  /**
   * 构造网关指标组件。
   *
   * <p>通过 {@link SentryMetricsAdapter} 静态桥接自动获取 {@code SentryService}，
   * 业务模块不再需要显式注入 {@code SentryService} 或 {@code MeterRegistry}。
   */
  public GatewayMetrics() {
    super(PREFIX);
    log.info("[GatewayMetrics] 自定义 Prometheus 指标初始化完成（通过 SentryMetricsAdapter 桥接）");
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
    recordTimer("request_duration_seconds", durationMs,
        "route", safe(routeId), "method", safe(method), "status", String.valueOf(status));
  }

  /**
   * 增加请求计数。
   *
   * @param routeId 路由 ID
   * @param method 请求方法
   * @param status 响应状态码
   */
  public void incrementRequestTotal(String routeId, String method, int status) {
    incrementCounter("request_total",
        "route", safe(routeId), "method", safe(method), "status", String.valueOf(status));
  }

  /**
   * 增加灰度路由命中计数。
   *
   * @param hitGray 是否命中灰度
   */
  public void incrementGrayHit(boolean hitGray) {
    incrementCounter("gray_hit_total", "gray", String.valueOf(hitGray));
  }

  /**
   * 增加限流触发计数。
   *
   * @param dimension 限流维度
   * @param routeId 路由 ID
   */
  public void incrementRatelimitTriggered(String dimension, String routeId) {
    incrementCounter("ratelimit_triggered_total",
        "dimension", safe(dimension), "route", safe(routeId));
  }

  /**
   * 增加限流本地兜底计数。
   */
  public void incrementRatelimitFallback() {
    incrementCounter("ratelimit_fallback_total");
  }

  /**
   * 上报本地兜底令牌桶的自适应配额。
   *
   * <p>首次调用时注册 Gauge（通过 {@link SentryMetricsAdapter#gaugeRef}），后续调用仅更新值。
   *
   * @param quota 当前配额值
   */
  public void setRatelimitFallbackQuota(int quota) {
    fallbackQuotaRef.set(quota);
    // 首次写入时确保 Gauge 已注册
    if (quotaGaugeRegistered.compareAndSet(false, true)) {
      gaugeRef("ratelimit_fallback_quota", fallbackQuotaRef, AtomicInteger::doubleValue);
    }
  }

  /**
   * 记录 JWT 校验耗时。
   *
   * @param durationMs 校验耗时（毫秒）
   * @param cached 是否命中缓存
   */
  public void recordJwtValidationDuration(long durationMs, boolean cached) {
    recordTimer("jwt_validation_duration_seconds", durationMs,
        "cached", String.valueOf(cached));
  }

  /**
   * 设置熔断器状态。
   *
   * <p>每个 routeId 首次调用时注册 Gauge（通过 {@link SentryMetricsAdapter#gaugeRef}），
   * 后续调用仅更新 AtomicInteger 值。
   *
   * @param routeId 路由 ID
   * @param state 熔断状态值（0=closed, 1=open, 2=half-open）
   */
  public void setCircuitBreakerState(String routeId, int state) {
    AtomicInteger ref = breakerStates.computeIfAbsent(routeId, k -> {
      AtomicInteger holder = new AtomicInteger(state);
      // 首次注册 Gauge（由 SentryMetricsAdapter 桥接到 MetricsCollector）
      gaugeRef("circuit_breaker_state", holder, AtomicInteger::doubleValue,
          "route", safe(k));
      return holder;
    });
    ref.set(state);
  }

  /**
   * 注册 JWT 缓存命中/未命中 Gauge 到 Micrometer。
   *
   * <p>使用 {@link SentryMetricsAdapter#gaugeRef} 注册引用型 Gauge，无需直接操作 {@link MeterRegistry}。
   *
   * @param hitCounter 命中计数器引用
   * @param missCounter 未命中计数器引用
   */
  public void registerJwtCacheCounters(AtomicLong hitCounter, AtomicLong missCounter) {
    if (jwtGaugeRegistered.compareAndSet(false, true)) {
      gaugeRef("jwt_cache_hit_total", hitCounter, AtomicLong::doubleValue);
      gaugeRef("jwt_cache_miss_total", missCounter, AtomicLong::doubleValue);
      log.info("[GatewayMetrics] JWT 缓存命中/未命中 Prometheus 指标已注册");
    }
  }

  /**
   * Null 安全的字符串处理：将 null/空字符串替换为 "unknown"。
   *
   * @param value 原始值（可为 null）
   * @return 非 null 字符串
   */
  private static String safe(String value) {
    return (value == null || value.isEmpty()) ? "unknown" : value;
  }

}
