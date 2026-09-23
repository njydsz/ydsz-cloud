package com.njydsz.gateway.config;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import com.njydsz.common.sentry.adapter.SentryMetricsAdapter;
import com.njydsz.common.sentry.metrics.MicrometerMetricsCollector;

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
 *   <li>{@code ydsz_gateway_ws_rejected_total} — WebSocket 连接被拒绝计数器（dimension 标签：user/ip）
 *   <li>{@code ydsz_gateway_request_latency_seconds} — 请求延迟直方图（支持 Prometheus 的 {@code histogram_quantile()} 查询 P99/P95）
 * </ul>
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Slf4j
@Component
@ConditionalOnClass(MeterRegistry.class)
public class GatewayMetrics extends SentryMetricsAdapter {

  /** 所有 Prometheus 指标的统一前缀。 */
  private static final String PREFIX = "ydsz_gateway_";

  /** 熔断器指标值：{@link CircuitBreaker.State#CLOSED}，正常放行请求。 */
  public static final int STATE_CLOSED = 0;

  /** 熔断器指标值：{@link CircuitBreaker.State#OPEN}，快速失败所有请求。 */
  public static final int STATE_OPEN = 1;

  /** 熔断器指标值：{@link CircuitBreaker.State#HALF_OPEN}，放行限流探测流量。 */
  public static final int STATE_HALF_OPEN = 2;

  /** 按 routeId 维护的熔断器状态引用（映射为 0=CLOSED, 1=OPEN, 2=HALF_OPEN）。 */
  private final ConcurrentMap<String, AtomicInteger> breakerStates = new ConcurrentHashMap<>();

  /**
   * P99 延迟直方图（DistributionSummary + 百分位直方图）。
   *
   * <p>支持 Prometheus {@code histogram_quantile(0.99, rate(ydsz_gateway_request_latency_seconds_bucket[5m]))} 查询。
   * 分桶覆盖典型网关延迟范围（1ms-30s），满足 P95/P99 监控需求。
   */
  private volatile DistributionSummary requestLatencyHistogram;

  /** 延迟直方图注册标志。 */
  private final AtomicBoolean latencyHistogramRegistered = new AtomicBoolean(false);

  /** 延迟直方图注册的 route 标签值（延迟直方图按 route 维度拆分）。 */
  private static final String LATENCY_HISTOGRAM_NAME = "request_latency_seconds";

  /** 本地兜底限流配额引用（Gauge 上报用）。 */
  private final AtomicInteger fallbackQuotaRef = new AtomicInteger(0);

  /** 配额 Gauge 注册标志（确保仅注册一次）。 */
  private final AtomicBoolean quotaGaugeRegistered = new AtomicBoolean(false);

  /** JWT 缓存 Gauge 注册标志（确保命中/未命中仅注册一次）。 */
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
   * 增加 WebSocket 连接被拒绝计数。
   *
   * <p>当用户维度或 IP 维度的 WebSocket 连接数超限时调用，用于监控 WebSocket 限流触发频率。
   *
   * @param dimension 限流维度（"user" 或 "ip"）
   */
  public void incrementWsRejected(String dimension) {
    incrementCounter("ws_rejected_total", "dimension", safe(dimension));
  }

  /**
   * 记录请求延迟直方图（用于 Prometheus P99/P95 查询）。
   *
   * <p>分桶覆盖 1ms-30s 范围，首次调用时注册 DistributionSummary。
   * 通过 {@code publishPercentileHistogram()} 输出到 Prometheus 的 {@code _bucket} / {@code _count} / {@code _sum} 子指标，
   * 支持 {@code histogram_quantile()} 查询。
   *
   * <p><b>与 {@link #recordRequestDuration} 的区别：</b>
   * 后者使用 Micrometer Timer（统计 total/max/mean），本方法为直方图专用，
   * 输出结构更适合 SLI/SLO 计费与百分位监控。
   *
   * @param durationMs 请求耗时（毫秒）
   * @param routeId 路由 ID
   */
  public void recordRequestLatency(long durationMs, String routeId) {
    registerLatencyHistogramIfAbsent(routeId);
    if (requestLatencyHistogram != null) {
      requestLatencyHistogram.record(Math.max(durationMs, 0));
    }
  }

  /**
   * 注册请求延迟直方图（首次请求时懒加载 + CAS 防 NPE）。
   *
   * <p>通过 {@link DistributionSummary.Builder} 配置百分位直方图和 SLO 分桶。
   * 注册失败时静默降级（requestLatencyHistogram 保持 null，后续 record 调用为 no-op）。
   *
   * @param routeId 路由 ID（作为标签区分不同路由的延迟分布）
   */
  private void registerLatencyHistogramIfAbsent(String routeId) {
    if (latencyHistogramRegistered.compareAndSet(false, true)) {
      MeterRegistry registry = resolveMicrometerRegistry();
      if (registry != null) {
        requestLatencyHistogram = DistributionSummary.builder(prefix + LATENCY_HISTOGRAM_NAME)
            .description("网关请求延迟直方图（用于 Prometheus P99/P95 查询）")
            .tags("route", safe(routeId))
            .publishPercentileHistogram()
            .minimumExpectedValue(1.0)
            .maximumExpectedValue(30_000.0)
            .serviceLevelObjectives(
                5.0, 10.0, 25.0, 50.0, 100.0, 250.0, 500.0,
                1_000.0, 2_500.0, 5_000.0, 10_000.0)
            .register(registry);
        if (requestLatencyHistogram != null) {
          log.info("[GatewayMetrics] 请求延迟直方图已注册：{}", LATENCY_HISTOGRAM_NAME);
        }
      } else {
        log.warn("[GatewayMetrics] MetricsCollector 不可用，P99 延迟直方图未注册（降级模式）");
      }
    }
  }

  /**
   * 从 MetricsCollector 获取原生 Micrometer MeterRegistry。
   *
   * <p>SentryMetricsAdapter 未暴露 getMicrometerRegistry()，此处通过 protected getMetricsCollector()
   * 获取后做 instanceof 判断，避免直接依赖 sentry 实现细节。
   *
   * @return MicrometerRegistry 实例，未配置时返回 null
   */
  private MeterRegistry resolveMicrometerRegistry() {
    return getMetricsCollector() instanceof MicrometerMetricsCollector micrometer
        ? micrometer.getMeterRegistry()
        : null;
  }

}
