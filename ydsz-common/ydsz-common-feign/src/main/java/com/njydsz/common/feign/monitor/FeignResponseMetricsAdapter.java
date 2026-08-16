package com.njydsz.common.feign.monitor;

import io.micrometer.core.instrument.MeterRegistry;

import com.njydsz.common.feign.interceptor.FeignResponseInterceptor;

/**
 * Feign 响应指标 Micrometer 适配器。
 *
 * <p>实现 {@link FeignResponseInterceptor.FeignResponseMetrics} 接口， 将 Feign 调用指标通过 {@link
 * FeignMicrometerCollector} 注册到 Micrometer。
 *
 * <p>注册的指标：
 *
 * <ul>
 *   <li>{@code feign.request.latency} - 请求延迟 Timer（标签: client, method, status_code）
 *   <li>{@code feign.request.errors} - 请求错误 Counter（标签: client, method, status_code）
 *   <li>{@code feign.request.slow} - 慢调用 Counter（标签: client, method）
 *   <li>{@code feign.response.body.size} - 响应体大小 DistributionSummary（标签: client, method,
 *       status_code）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class FeignResponseMetricsAdapter implements FeignResponseInterceptor.FeignResponseMetrics {

  private final FeignMicrometerCollector collector;

  /**
   * 构造 Feign 响应指标适配器。
   *
   * @param meterRegistry Micrometer 指标注册表
   */
  public FeignResponseMetricsAdapter(MeterRegistry meterRegistry) {
    this.collector = FeignMicrometerCollector.getInstance(meterRegistry);
  }

  @Override
  public void recordSuccess(String service, String method, int status, long duration) {
    collector.recordLatency(service, method, duration, String.valueOf(status));
  }

  @Override
  public void recordFailure(
      String service, String method, int status, long duration, String errorType) {
    collector.recordError(service, method, String.valueOf(status));
    collector.recordLatency(service, method, duration, String.valueOf(status));
  }

  @Override
  public void recordSlowCall(String service, String method, long duration, long threshold) {
    collector.recordSlowCall(service, method);
  }

  @Override
  public void recordResponseBodySize(
      String service, String method, int status, long bodySizeBytes) {
    collector.recordResponseBodySize(service, method, status, bodySizeBytes);
  }
}
