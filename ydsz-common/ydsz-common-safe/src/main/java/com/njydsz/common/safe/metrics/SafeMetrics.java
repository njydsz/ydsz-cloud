package com.njydsz.common.safe.metrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.safe.alert.SecurityEvent;
import com.njydsz.common.safe.alert.SecurityEventType;

/**
 * 安全模块 Micrometer 指标采集
 *
 * <p>采集安全相关指标，通过 Micrometer 暴露到 Prometheus，供 Grafana 监控安全态势。
 *
 * <p><b>指标列表：</b>
 *
 * <ul>
 *   <li>{@code safe_xss_attacks_total} - XSS 攻击次数
 *   <li>{@code safe_csrf_failures_total} - CSRF 验证失败次数
 *   <li>{@code safe_rate_limit_triggered_total} - 限流触发次数
 *   <li>{@code safe_illegal_access_total} - 非法访问次数
 *   <li>{@code safe_ip_blocked_total} - IP 封禁次数
 *   <li>{@code safe_filter_duration_seconds} - 安全过滤器处理耗时
 * </ul>
 *
 * <p><b>性能优化：</b>Counter 实例预注册并缓存，避免每次事件触发时的重复 {@code Counter.builder().register()} 开销。 Micrometer
 * 的 {@code Counter.register()} 内部已有幂等处理（相同 MeterId 返回已有实例），但 缓存 MeterId 维度 Counter 可进一步减少 Tag 匹配与
 * Map 查找。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class SafeMetrics {

  private static final Logger LOG = LoggerFactory.getLogger(SafeMetrics.class);

  private final MeterRegistry meterRegistry;

  private final AtomicLong xssAttacks = new AtomicLong(0);
  private final AtomicLong csrfFailures = new AtomicLong(0);
  private final AtomicLong rateLimitTriggered = new AtomicLong(0);
  private final AtomicLong illegalAccess = new AtomicLong(0);
  private final AtomicLong ipBlocked = new AtomicLong(0);

  /** Counter 缓存（预注册） */
  private final ConcurrentHashMap<String, Counter> counterCache = new ConcurrentHashMap<>();

  /**
   * 构造安全指标采集器，并在 Micrometer 不可用时降级为纯内存计数。
   *
   * <p>有 {@code MeterRegistry} 时，各计数值会同时写入 Micrometer {@code Counter}；
   * 为 {@code null} 时仅累加内存 {@code AtomicLong}，指标不外泄到监控系统，进程重启即清零。
   *
   * @param meterRegistry Micrometer MeterRegistry（可为 null，降级为内存计数）
   */
  public SafeMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    if (meterRegistry != null) {
      LOG.info("安全模块 Micrometer 指标采集已初始化");
    } else {
      LOG.info("安全模块指标采集降级为内存计数（MeterRegistry 不可用）");
    }
  }

  /**
   * 记录安全事件
   *
   * @param event 安全事件
   */
  public void recordSecurityEvent(SecurityEvent event) {
    if (event == null) {
      return;
    }

    SecurityEventType type = event.getEventType();
    String sourceIp = event.getSourceIp() != null ? event.getSourceIp() : "unknown";

    switch (type) {
      case XSS_ATTACK -> incrementCounter("safe_xss_attacks_total", sourceIp, xssAttacks);
      case CSRF_ATTACK -> incrementCounter("safe_csrf_failures_total", sourceIp, csrfFailures);
      case RATE_LIMIT_TRIGGERED ->
          incrementCounter("safe_rate_limit_triggered_total", sourceIp, rateLimitTriggered);
      case ILLEGAL_ACCESS -> incrementCounter("safe_illegal_access_total", sourceIp, illegalAccess);
      case IP_AUTO_BLOCKED -> incrementCounter("safe_ip_blocked_total", sourceIp, ipBlocked);
      default -> incrementCounter("safe_security_events_total", sourceIp, new AtomicLong(0));
    }
  }

  /**
   * 记录过滤器处理耗时
   *
   * @param filterName 过滤器名称
   * @param durationNanos 处理耗时（纳秒）
   */
  public void recordFilterDuration(String filterName, long durationNanos) {
    if (meterRegistry != null) {
      Timer.builder("safe_filter_duration_seconds")
          .tag("filter", filterName)
          .register(meterRegistry)
          .record(durationNanos, TimeUnit.NANOSECONDS);
    }
  }

  /**
   * 获取累计 XSS 攻击次数
   *
   * @return 累计次数
   */
  public long getXssAttacksCount() {
    return xssAttacks.get();
  }

  /**
   * 获取累计 CSRF 失败次数
   *
   * @return 累计次数
   */
  public long getCsrfFailuresCount() {
    return csrfFailures.get();
  }

  /**
   * 获取累计限流触发次数
   *
   * @return 累计次数
   */
  public long getRateLimitTriggeredCount() {
    return rateLimitTriggered.get();
  }

  /**
   * 增加计数。
   *
   * <p>优先从 {@link #counterCache} 获取已缓存的 Counter 实例； 首次调用时通过 {@code Counter.builder().register()}
   * 创建并缓存。 内存计数（fallback）始终递增，确保降级模式下数据不丢失。
   *
   * @param name 指标名称
   * @param sourceIp 来源 IP（Tag 值）
   * @param fallback 内存计数器（降级兜底）
   */
  private void incrementCounter(String name, String sourceIp, AtomicLong fallback) {
    fallback.incrementAndGet();
    if (meterRegistry == null) {
      return;
    }
    // 缓存 key = metricName + tagValue，避免重复 builder/register 开销
    String cacheKey = name + "|" + sourceIp;
    Counter counter =
        counterCache.computeIfAbsent(
            cacheKey,
            k -> Counter.builder(name).tag("source_ip", sourceIp).register(meterRegistry));
    counter.increment();
  }
}
