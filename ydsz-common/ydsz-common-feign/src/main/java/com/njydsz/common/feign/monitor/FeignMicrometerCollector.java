package com.njydsz.common.feign.monitor;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Feign 调用 Micrometer 指标收集器。
 *
 * <p>为 Feign 调用提供 Micrometer 指标采集，使用 {@link Timer.Sample} 模式进行低开销测量。 通过 {@link
 * #getInstance(MeterRegistry)} 获取全局单例，避免重复创建。
 *
 * <p>注册的指标：
 *
 * <ul>
 *   <li>{@code feign.request.latency} - Feign 请求延迟 Timer（标签: client, method, status_code）
 *   <li>{@code feign.request.errors} - Feign 请求错误 Counter（标签: client, method, status_code）
 *   <li>{@code feign.request.slow} - Feign 慢调用 Counter（标签: client, method）
 *   <li>{@code feign.response.body.size} - 响应体大小分布（DistributionSummary，标签: client, method, status_code）
 * </ul>
 *
 * <p><b>缓存优化（自 26.09.19）：</b>为减少 {@link Counter.Builder} 重复构建和 {@link DistributionSummary.Builder}
 * 的对象分配开销，使用 {@link ConcurrentHashMap} 缓存已创建的 Counter 和 DistributionSummary 实例，避免每次调用重新构建 Builder。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class FeignMicrometerCollector {

  private static final String METRIC_REQUEST_LATENCY = "feign.request.latency";
  private static final String METRIC_REQUEST_ERRORS = "feign.request.errors";
  private static final String METRIC_REQUEST_SLOW = "feign.request.slow";
  private static final String METRIC_RESPONSE_BODY_SIZE = "feign.response.body.size";
  private static final String TAG_CLIENT = "client";
  private static final String TAG_METHOD = "method";
  private static final String TAG_STATUS_CODE = "status_code";

  /** 单例实例（按 MeterRegistry 维度缓存） */
  private static final ConcurrentHashMap<MeterRegistry, FeignMicrometerCollector> INSTANCES =
      new ConcurrentHashMap<>();

  /** Timer 缓存，Key = "client|method|status" */
  private final ConcurrentHashMap<String, Timer> timerCache = new ConcurrentHashMap<>();

  /** Counter 缓存（errors），Key = "client|method|status" */
  private final ConcurrentHashMap<String, Counter> errorCounterCache = new ConcurrentHashMap<>();

  /** Counter 缓存（slow），Key = "client|method" */
  private final ConcurrentHashMap<String, Counter> slowCounterCache = new ConcurrentHashMap<>();

  /** DistributionSummary 缓存（body size），Key = "client|method|status" */
  private final ConcurrentHashMap<String, DistributionSummary> bodySizeSummaryCache =
      new ConcurrentHashMap<>();

  private final MeterRegistry registry;

  private FeignMicrometerCollector(MeterRegistry registry) {
    this.registry = registry;
  }

  /**
   * 获取或创建 Feign 指标收集器单例。
   *
   * @param registry MeterRegistry 实例
   * @return 全局唯一的 FeignMicrometerCollector 实例
   */
  public static FeignMicrometerCollector getInstance(MeterRegistry registry) {
    return INSTANCES.computeIfAbsent(registry, FeignMicrometerCollector::new);
  }

  /**
   * 获取或创建 Timer 实例（带缓存）。
   *
   * @param clientName Feign 客户端名称
   * @param method HTTP 方法
   * @param status HTTP 状态码分类（如 "200"、"4xx"、"5xx"、"error"）
   * @return 缓存的 Timer 实例
   */
  private Timer getOrCreateTimer(String clientName, String method, String status) {
    String cacheKey = clientName + "|" + method + "|" + status;
    return timerCache.computeIfAbsent(
        cacheKey,
        k ->
            Timer.builder(METRIC_REQUEST_LATENCY)
                .tag(TAG_CLIENT, clientName)
                .tag(TAG_METHOD, method)
                .tag(TAG_STATUS_CODE, status)
                .description("Feign request latency")
                .register(registry));
  }

  /**
   * 获取或创建错误计数 Counter（带缓存）。
   *
   * @param clientName 客户端名称
   * @param method HTTP 方法
   * @param status HTTP 状态码
   * @return 缓存的 Counter 实例
   */
  private Counter getOrCreateErrorCounter(String clientName, String method, String status) {
    String cacheKey = clientName + "|" + method + "|" + status;
    return errorCounterCache.computeIfAbsent(
        cacheKey,
        k ->
            Counter.builder(METRIC_REQUEST_ERRORS)
                .tag(TAG_CLIENT, clientName)
                .tag(TAG_METHOD, method)
                .tag(TAG_STATUS_CODE, status)
                .description("Feign request errors")
                .register(registry));
  }

  /**
   * 获取或创建慢调用计数 Counter（带缓存）。
   *
   * @param clientName 客户端名称
   * @param method HTTP 方法
   * @return 缓存的 Counter 实例
   */
  private Counter getOrCreateSlowCounter(String clientName, String method) {
    String cacheKey = clientName + "|" + method;
    return slowCounterCache.computeIfAbsent(
        cacheKey,
        k ->
            Counter.builder(METRIC_REQUEST_SLOW)
                .tag(TAG_CLIENT, clientName)
                .tag(TAG_METHOD, method)
                .description("Feign slow call count (exceeds configured threshold)")
                .register(registry));
  }

  /**
   * 获取或创建响应体大小 DistributionSummary（带缓存）。
   *
   * @param clientName 客户端名称
   * @param method HTTP 方法
   * @param status HTTP 状态码
   * @return 缓存的 DistributionSummary 实例
   */
  private DistributionSummary getOrCreateBodySizeSummary(
      String clientName, String method, String status) {
    String cacheKey = clientName + "|" + method + "|" + status;
    return bodySizeSummaryCache.computeIfAbsent(
        cacheKey,
        k ->
            DistributionSummary.builder(METRIC_RESPONSE_BODY_SIZE)
                .tag(TAG_CLIENT, clientName)
                .tag(TAG_METHOD, method)
                .tag(TAG_STATUS_CODE, status)
                .description("Feign response body size in bytes")
                .register(registry));
  }

  /**
   * 记录 Feign 请求延迟（带状态码标签）。
   *
   * @param clientName Feign 客户端名称
   * @param method HTTP 方法（如 GET, POST）
   * @param durationMs 耗时（毫秒）
   * @param statusCode HTTP 状态码（如 "200"、"500"），用于区分成功/失败的延迟分布
   */
  public void recordLatency(String clientName, String method, long durationMs, String statusCode) {
    getOrCreateTimer(clientName, method, statusCode).record(Duration.ofMillis(durationMs));
  }

  /**
   * 记录 Feign 请求延迟（向后兼容，不含状态码标签）。
   *
   * @param clientName Feign 客户端名称
   * @param method HTTP 方法（如 GET, POST）
   * @param durationMs 耗时（毫秒）
   */
  public void recordLatency(String clientName, String method, long durationMs) {
    recordLatency(clientName, method, durationMs, "unknown");
  }

  /**
   * 记录 Feign 请求延迟（通过 Supplier 包装调用）。
   *
   * <p>注意：此方法无法感知调用结果状态，Timer 将打上 {@code status_code=unknown} 标签。 若需区分成功/失败延迟，建议直接调用 {@link
   * #recordLatency(String, String, long, String)}。
   *
   * @param clientName Feign 客户端名称
   * @param method HTTP 方法
   * @param supplier 要执行的 Feign 调用
   * @param <T> 返回值类型
   * @return 调用返回值
   */
  public <T> T recordLatency(String clientName, String method, Supplier<T> supplier) {
    Timer.Sample sample = Timer.start(registry);
    try {
      return supplier.get();
    } finally {
      sample.stop(getOrCreateTimer(clientName, method, "unknown"));
    }
  }

  /**
   * 记录 Feign 请求错误（使用缓存的 Counter）。
   *
   * @param clientName Feign 客户端名称
   * @param method HTTP 方法
   * @param statusCode HTTP 状态码
   */
  public void recordError(String clientName, String method, String statusCode) {
    getOrCreateErrorCounter(clientName, method, statusCode).increment();
  }

  /**
   * 记录 Feign 请求错误（从异常推断状态码，使用缓存的 Counter）。
   *
   * @param clientName Feign 客户端名称
   * @param method HTTP 方法
   * @param exception 异常实例
   */
  public void recordError(String clientName, String method, Throwable exception) {
    String statusCode = exception != null ? exception.getClass().getSimpleName() : "Unknown";
    recordError(clientName, method, statusCode);
  }

  /**
   * 记录响应体大小（使用缓存的 DistributionSummary）。
   *
   * <p>注册指标 {@code feign.response.body.size}（DistributionSummary）， 使用标签 client / method /
   * status_code 区分维度，便于监控响应体分布、 识别异常大响应或持续空响应。
   *
   * @param clientName Feign 客户端名称
   * @param method HTTP 方法
   * @param statusCode HTTP 状态码
   * @param bodySizeBytes 响应体大小（字节），若未知可传 -1（将不记录）
   */
  public void recordResponseBodySize(
      String clientName, String method, int statusCode, long bodySizeBytes) {
    if (bodySizeBytes < 0) {
      return;
    }
    getOrCreateBodySizeSummary(clientName, method, String.valueOf(statusCode))
        .record(bodySizeBytes);
  }

  /**
   * 记录 Feign 慢调用（使用缓存的 Counter）。
   *
   * @param clientName Feign 客户端名称
   * @param method HTTP 方法
   */
  public void recordSlowCall(String clientName, String method) {
    getOrCreateSlowCounter(clientName, method).increment();
  }
}
