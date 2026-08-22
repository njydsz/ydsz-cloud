package com.njydsz.common.sentry.metrics;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.sentry.spi.MetricsCollector;

/**
 * Micrometer 指标采集器。
 *
 * <p>基于 Micrometer {@link MeterRegistry} 实现指标采集，自动暴露到 Prometheus。 当 MeterRegistry 不可用时自动降级为 {@link
 * InMemoryMetricsCollector}。
 *
 * <h3>支持的指标类型</h3>
 *
 * <ul>
 *   <li>Counter：单调递增计数器（如请求总量、错误总量）
 *   <li>Gauge：瞬时值指标（如队列大小、活跃连接数），使用 {@link AtomicReference} 动态更新
 *   <li>Timer：耗时指标（如请求延迟），支持 SLO（50/100/250/500/1000/5000ms）百分位
 *   <li>DistributionSummary：分布统计（如消息大小、缓存值大小），支持 SLO（1/5/10/50/100/500/1000）
 * </ul>
 *
 * <h3>缓存设计</h3>
 *
 * <p>使用 {@link ConcurrentHashMap} 缓存已注册的 Counter/Timer/DistributionSummary/Gauge， 避免重复注册（Micrometer
 * 不允许重复注册同名指标）。 缓存 Key 为 {@code name + tags.toString()}，保证相同 name+tags 复用同一实例。
 *
 * <h3>降级策略</h3>
 *
 * <p>若 MeterRegistry 为 null 或指标注册失败（如并发冲突）， 自动降级到内存采集器，不影响业务流程。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see MetricsCollector
 * @see InMemoryMetricsCollector
 * @see MeterRegistry
 */
@Slf4j
public class MicrometerMetricsCollector implements MetricsCollector {

  /** Micrometer MeterRegistry（注入的指标注册中心） */
  private final MeterRegistry meterRegistry;

  /** 内存降级采集器（MeterRegistry 不可用时的兜底方案） */
  private final InMemoryMetricsCollector fallback;

  /** Counter 缓存：name + tags → Counter */
  private final ConcurrentHashMap<String, Counter> counterCache = new ConcurrentHashMap<>();

  /** Timer 缓存：name + tags → Timer */
  private final ConcurrentHashMap<String, Timer> timerCache = new ConcurrentHashMap<>();

  /** DistributionSummary 缓存：name + tags → DistributionSummary */
  private final ConcurrentHashMap<String, DistributionSummary> histogramCache =
      new ConcurrentHashMap<>();

  /** Gauge 动态值引用缓存：name + tags → AtomicReference<Double> */
  private final ConcurrentHashMap<String, AtomicReference<Double>> gaugeRefCache =
      new ConcurrentHashMap<>();

  /** Timer SLO 配置（启用百分位和直方图，支持 Prometheus Exemplar） */
  private static final Duration[] TIMER_SLOS = {
    Duration.ofMillis(50), Duration.ofMillis(100), Duration.ofMillis(250),
    Duration.ofMillis(500), Duration.ofMillis(1000), Duration.ofMillis(5000)
  };

  /** DistributionSummary/Histogram SLO 配置 */
  private static final double[] HISTOGRAM_SLOS = {1, 5, 10, 50, 100, 500, 1000};

  /**
   * 构造 Micrometer 指标采集器。
   *
   * @param meterRegistry Micrometer MeterRegistry，可能为 null（降级场景）
   */
  public MicrometerMetricsCollector(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    this.fallback = new InMemoryMetricsCollector();
    log.info(
        "[Sentry] MicrometerMetricsCollector 初始化完成, MeterRegistry={}",
        meterRegistry != null ? meterRegistry.getClass().getSimpleName() : "null");
  }

  /**
   * 递增 Counter 指标。
   *
   * <p>先检查 MeterRegistry 是否可用，不可用时降级到内存采集器。 使用缓存避免重复注册同名 Counter。
   *
   * @param name 指标名称
   * @param description 指标描述（仅首次注册时使用）
   * @param tags 指标标签（影响缓存 Key）
   * @param amount 递增量（通常为 1.0）
   */
  @Override
  public void incrementCounter(
      String name, String description, Map<String, String> tags, double amount) {
    if (!isAvailable()) {
      fallback.incrementCounter(name, description, tags, amount);
      return;
    }
    try {
      String cacheKey = buildCacheKey(name, tags);
      Counter counter =
          counterCache.computeIfAbsent(
              cacheKey,
              k ->
                  Counter.builder(name)
                      .description(description)
                      .tags(toTags(tags))
                      .register(meterRegistry));
      counter.increment(amount);
    } catch (Exception e) {
      log.debug("[Sentry] Micrometer Counter 记录失败, 降级到内存: name={}, err={}", name, e.getMessage());
      fallback.incrementCounter(name, description, tags, amount);
    }
  }

  /**
   * 设置 Gauge 指标值。
   *
   * <p>Gauge 使用 {@link AtomicReference<Double>} 作为动态数据源， 每次调用更新引用值，无需重复注册 Gauge。 若首次调用则注册 Gauge
   * 并缓存引用。
   *
   * @param name 指标名称
   * @param description 指标描述（仅首次注册时使用）
   * @param tags 指标标签（影响缓存 Key）
   * @param value Gauge 当前值
   */
  @Override
  public void setGauge(String name, String description, Map<String, String> tags, double value) {
    if (!isAvailable()) {
      fallback.setGauge(name, description, tags, value);
      return;
    }
    try {
      String cacheKey = buildCacheKey(name, tags);
      AtomicReference<Double> ref =
          gaugeRefCache.computeIfAbsent(
              cacheKey,
              k -> {
                AtomicReference<Double> newRef = new AtomicReference<>(value);
                Gauge.builder(name, newRef, AtomicReference::get)
                    .description(description)
                    .tags(toTags(tags))
                    .register(meterRegistry);
                return newRef;
              });
      ref.set(value);
    } catch (Exception e) {
      log.debug("[Sentry] Micrometer Gauge 记录失败, 降级到内存: name={}, err={}", name, e.getMessage());
      fallback.setGauge(name, description, tags, value);
    }
  }

  /**
   * 记录 Timer 耗时指标。
   *
   * <p>自动记录 P50/P90/P95/P99 百分位，并按 SLO 区间统计分布。 使用缓存避免重复注册同名 Timer。
   *
   * @param name 指标名称
   * @param description 指标描述（仅首次注册时使用）
   * @param tags 指标标签（影响缓存 Key）
   * @param duration 耗时值
   */
  @Override
  public void recordTimer(
      String name, String description, Map<String, String> tags, Duration duration) {
    if (!isAvailable()) {
      fallback.recordTimer(name, description, tags, duration);
      return;
    }
    try {
      String cacheKey = buildCacheKey(name, tags);
      Timer timer =
          timerCache.computeIfAbsent(
              cacheKey,
              k ->
                  Timer.builder(name)
                      .description(description)
                      .tags(toTags(tags))
                      .sla(TIMER_SLOS)
                      .register(meterRegistry));
      timer.record(duration);
    } catch (Exception e) {
      log.debug("[Sentry] Micrometer Timer 记录失败, 降级到内存: name={}, err={}", name, e.getMessage());
      fallback.recordTimer(name, description, tags, duration);
    }
  }

  /**
   * 记录 DistributionSummary 分布指标。
   *
   * <p>适用于记录数值型分布数据（如消息大小、缓存值大小、队列长度）。 自动按 SLO 区间统计分布。
   *
   * @param name 指标名称
   * @param description 指标描述（仅首次注册时使用）
   * @param tags 指标标签（影响缓存 Key）
   * @param value 分布值
   */
  @Override
  public void recordHistogram(
      String name, String description, Map<String, String> tags, double value) {
    if (!isAvailable()) {
      fallback.recordHistogram(name, description, tags, value);
      return;
    }
    try {
      String cacheKey = buildCacheKey(name, tags);
      DistributionSummary summary =
          histogramCache.computeIfAbsent(
              cacheKey,
              k ->
                  DistributionSummary.builder(name)
                      .description(description)
                      .tags(toTags(tags))
                      .sla(HISTOGRAM_SLOS)
                      .register(meterRegistry));
      summary.record(value);
    } catch (Exception e) {
      log.debug("[Sentry] Micrometer Histogram 记录失败, 降级到内存: name={}, err={}", name, e.getMessage());
      fallback.recordHistogram(name, description, tags, value);
    }
  }

  /**
   * 检查 MeterRegistry 是否可用。
   *
   * @return 若 MeterRegistry 不为 null 则返回 true
   */
  @Override
  public boolean isAvailable() {
    return meterRegistry != null;
  }

  /**
   * 获取采集器类型名称。
   *
   * @return 固定返回 {@code "micrometer"}
   */
  @Override
  public String getName() {
    return "micrometer";
  }

  /**
   * 获取 Micrometer MeterRegistry。
   *
   * <p>仅供 SentryMetricsAdapter 等内部组件在需要注册 Gauge 回调时使用， 业务代码不应直接操作此 MeterRegistry。
   *
   * @return Micrometer MeterRegistry 实例
   * @since 1.0.0
   */
  public MeterRegistry getMeterRegistry() {
    return meterRegistry;
  }

  /**
   * 获取内存降级采集器。
   *
   * <p>可用于查询降级期间的指标数据，或手动触发降级采集。
   *
   * @return 内存降级采集器实例
   */
  public InMemoryMetricsCollector getFallback() {
    return fallback;
  }

  /**
   * 将 Map 转换为 Micrometer Tags。
   *
   * @param tags 标签 Map
   * @return Micrometer Tags 对象，输入为空或 null 时返回空 Tags
   */
  private Tags toTags(Map<String, String> tags) {
    if (tags == null || tags.isEmpty()) {
      return Tags.empty();
    }
    return Tags.of(tags.entrySet().stream().map(e -> Tag.of(e.getKey(), e.getValue())).toList());
  }

  /**
   * 构建指标缓存 Key。
   *
   * <p>Key 格式为 {@code name + "|" + tags.toString()}， 保证相同 name + tags 组合复用同一指标实例。
   *
   * @param name 指标名称
   * @param tags 指标标签
   * @return 缓存 Key 字符串
   */
  private String buildCacheKey(String name, Map<String, String> tags) {
    if (tags == null || tags.isEmpty()) {
      return name;
    }
    return name + "|" + tags.toString();
  }
}
