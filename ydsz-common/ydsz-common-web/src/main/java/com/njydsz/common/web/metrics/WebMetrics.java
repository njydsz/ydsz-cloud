package com.njydsz.common.web.metrics;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Web 模块 Micrometer 指标采集
 *
 * <p>统一采集 Web 基座核心链路指标：
 *
 * <ul>
 *   <li>{@code web.auth.total} — 认证请求总数（tag: result=success/failure）
 *   <li>{@code web.auth.duration} — 认证耗时分布
 *   <li>{@code web.request.total} — HTTP 请求总数（tag: method, status）
 *   <li>{@code web.request.duration} — HTTP 请求耗时分布（tag: method）
 *   <li>{@code web.ratelimit.rejected} — 限流拒绝计数
 *   <li>{@code web.security.header.injected} — 安全响应头注入计数
 * </ul>
 *
 * <p>指标注册采用惰性创建模式，首次调用时注册到 MeterRegistry， 后续复用已注册的 Counter/Timer 实例，避免重复创建。
 *
 * @author ydsz-team
 * @see MeterRegistry
 * @see Counter
 * @see Timer
 * @since 26.09.01
 */
public class WebMetrics {

  private static final String METRIC_AUTH_TOTAL = "web.auth.total";
  private static final String METRIC_AUTH_DURATION = "web.auth.duration";
  private static final String METRIC_REQUEST_TOTAL = "web.request.total";
  private static final String METRIC_REQUEST_DURATION = "web.request.duration";
  private static final String METRIC_RATELIMIT_REJECTED = "web.ratelimit.rejected";
  private static final String METRIC_SECURITY_HEADER = "web.security.header.injected";

  private final MeterRegistry meterRegistry;

  private final ConcurrentMap<String, Counter> counterCache = new ConcurrentHashMap<>(16);
  private final ConcurrentMap<String, Timer> timerCache = new ConcurrentHashMap<>(16);
  private final AtomicLong totalAuthRequests = new AtomicLong(0);
  private final AtomicLong totalAuthFailures = new AtomicLong(0);
  private final AtomicLong totalRateLimitRejected = new AtomicLong(0);
  private final AtomicLong totalSecurityHeadersInjected = new AtomicLong(0);

  public WebMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  /**
   * 记录一次认证成功。
   *
   * <p>累加内部 {@link AtomicLong} 计数（无锁、线程安全），递增指标 {@code web.auth.total{result=success}}，并采样 {@code
   * web.auth.duration} 计时分布。 {@code durationNanos} 为本次认证耗时（纳秒），由调用方测量后传入。
   *
   * @param durationNanos 认证耗时（纳秒）；传 0 仅累计计数、不产生有效耗时样本
   */
  public void recordAuthSuccess(long durationNanos) {
    totalAuthRequests.incrementAndGet();
    getCounter(METRIC_AUTH_TOTAL, "result", "success").increment();
    getTimer(METRIC_AUTH_DURATION).record(Duration.ofNanos(durationNanos));
  }

  /**
   * 记录一次认证失败。
   *
   * <p>同时累加认证总次数与失败次数（两路 {@link AtomicLong}），递增指标 {@code web.auth.total{result=failure}}，并采样 {@code
   * web.auth.duration} 计时分布。 线程安全由 Atomic 计数与 Micrometer 内部并发语义保证。
   *
   * @param durationNanos 认证耗时（纳秒）；含失败路径的整体耗时
   */
  public void recordAuthFailure(long durationNanos) {
    totalAuthRequests.incrementAndGet();
    totalAuthFailures.incrementAndGet();
    getCounter(METRIC_AUTH_TOTAL, "result", "failure").increment();
    getTimer(METRIC_AUTH_DURATION).record(Duration.ofNanos(durationNanos));
  }

  /**
   * 记录一次 HTTP 请求完成。
   *
   * <p>递增指标 {@code web.request.total{method,status}}（按 HTTP 方法与会话状态码分桶）， 并采样 {@code
   * web.request.duration{method}} 计时分布。 {@code method} 为请求方法（如 GET/POST），{@code status} 为响应状态码（如
   * 200/401/404）， 用于后续错误率与耗时多维分析。
   *
   * @param method HTTP 请求方法
   * @param status HTTP 响应状态码
   * @param durationNanos 请求处理耗时（纳秒）
   */
  public void recordRequest(String method, int status, long durationNanos) {
    getCounter(METRIC_REQUEST_TOTAL, "method", method, "status", String.valueOf(status))
        .increment();
    getTimer(METRIC_REQUEST_DURATION, "method", method).record(Duration.ofNanos(durationNanos));
  }

  /**
   * 记录一次被限流拒绝的请求。
   *
   * <p>累加内部 {@link AtomicLong} 计数并递增指标 {@code web.ratelimit.rejected}（无标签）。 用于监控限流触发频率；该方法无参数，调用方在
   * {@code @RateLimit} 拦截或网关拒绝时调用。
   */
  public void recordRateLimitRejected() {
    totalRateLimitRejected.incrementAndGet();
    getCounter(METRIC_RATELIMIT_REJECTED).increment();
  }

  /**
   * 记录一次安全响应头注入。
   *
   * <p>累加内部 {@link AtomicLong} 计数并递增指标 {@code web.security.header.injected}（无标签）。
   * 由安全响应头过滤器在成功写入防护头（如 X-Content-Type-Options、X-Frame-Options）时调用， 用于统计安全头覆盖情况。
   */
  public void recordSecurityHeaderInjected() {
    totalSecurityHeadersInjected.incrementAndGet();
    getCounter(METRIC_SECURITY_HEADER).increment();
  }

  /**
   * 获取认证请求累计总数。
   *
   * <p>与指标 {@code web.auth.total} 的 success/failure 分桶不同，该值为 进程内 {@link AtomicLong}
   * 的无锁计数快照，供监控/日志展示或测试断言使用。
   *
   * @return 认证请求累计总数（含成功与失败）
   */
  public long getTotalAuthRequests() {
    return totalAuthRequests.get();
  }

  /**
   * 获取认证失败累计总数。
   *
   * <p>统计认证失败次数，供错误率计算与告警阈值判定使用； 仅反映当前进程实例的计数，多实例场景需按实例聚合。
   *
   * @return 认证失败累计总数
   */
  public long getTotalAuthFailures() {
    return totalAuthFailures.get();
  }

  /**
   * 获取被限流拒绝的请求累计总数。
   *
   * <p>统计 {@code @RateLimit} 拦截或网关限流拒绝的次数，用于评估 限流策略的触发频率与误伤情况。
   *
   * @return 被限流拒绝的累计总数
   */
  public long getTotalRateLimitRejected() {
    return totalRateLimitRejected.get();
  }

  /**
   * 获取安全响应头注入累计总数。
   *
   * <p>统计安全过滤器成功写入防护头（如 X-Content-Type-Options、 X-Frame-Options）的次数，用于衡量安全头覆盖率。
   *
   * @return 安全响应头注入累计总数
   */
  public long getTotalSecurityHeadersInjected() {
    return totalSecurityHeadersInjected.get();
  }

  private Counter getCounter(String name, String... tags) {
    String key = buildCacheKey(name, tags);
    return counterCache.computeIfAbsent(
        key,
        k -> {
          if (tags.length == 0) {
            return meterRegistry.counter(name);
          }
          return Counter.builder(name).tags(tags).register(meterRegistry);
        });
  }

  private Timer getTimer(String name, String... tags) {
    String key = buildCacheKey(name, tags);
    return timerCache.computeIfAbsent(
        key,
        k -> {
          if (tags.length == 0) {
            return Timer.builder(name).register(meterRegistry);
          }
          return Timer.builder(name).tags(tags).register(meterRegistry);
        });
  }

  private String buildCacheKey(String name, String... tags) {
    StringBuilder sb = new StringBuilder(name);
    for (int i = 0; i < tags.length; i++) {
      sb.append(':').append(tags[i]);
    }
    return sb.toString();
  }
}
