package com.njydsz.common.base.metrics;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * Micrometer 实现的 {@link FunctionMetricsBridge}。
 *
 * <p>将增量和记录操作桥接到底层 {@link MeterRegistry}，线程安全，支持 SLO 百分位直方图。 首次调用时自动注册
 * Counter/Timer/Gauge，后续调用直接通过缓存引用更新，避免 Micrometer 重复注册冲突。
 *
 * <h3>缓存设计</h3>
 *
 * <ul>
 *   <li>Counter 缓存：基于 {@code name + tags} 字符串 Key
 *   <li>Timer 缓存：基于 {@code name + tags} 字符串 Key，首次注册时自动启用 SLO（50/100/250/500/1000/5000ms）
 *   <li>Gauge 缓存：基于 {@code name + tags} 字符串 Key，使用 {@link AtomicReference} 作为动态数据源
 * </ul>
 *
 * <p>当 MeterRegistry 为 null 时所有方法降级为 no-op，不影响业务主流程。
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see FunctionMetricsBridge
 * @see NoopFunctionMetricsBridge
 */
@Slf4j
public class MicrometerFunctionMetricsBridge implements FunctionMetricsBridge {

  /** Timer SLO 配置（对齐 MicrometerMetricsCollector 的一致性 SLO） */
  private static final Duration[] TIMER_SLOS = {
    Duration.ofMillis(50), Duration.ofMillis(100), Duration.ofMillis(250),
    Duration.ofMillis(500), Duration.ofMillis(1000), Duration.ofMillis(5000)
  };

  /** 底层 Micrometer 注册表 */
  private final MeterRegistry meterRegistry;

  /** Counter 缓存 */
  private final ConcurrentHashMap<String, Counter> counterCache = new ConcurrentHashMap<>();

  /** Timer 缓存 */
  private final ConcurrentHashMap<String, Timer> timerCache = new ConcurrentHashMap<>();

  /** Gauge 引用缓存 */
  private final ConcurrentHashMap<String, AtomicReference<Double>> gaugeRefCache = new ConcurrentHashMap<>();

  /**
   * 构造 Micrometer 桥接器。
   *
   * <p>如 {@code meterRegistry} 为 null，所有方法自动降级为 no-op（与 {@link
   * NoopFunctionMetricsBridge} 行为一致）。
   *
   * @param meterRegistry Micrometer MeterRegistry，可为 null（降级场景）
   */
  public MicrometerFunctionMetricsBridge(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    if (meterRegistry != null) {
      log.info(
          "[FunctionMetricsBridge] MicrometerFunctionMetricsBridge 初始化完成, registry={}",
          meterRegistry.getClass().getSimpleName());
    } else {
      log.info("[FunctionMetricsBridge] MicrometerFunctionMetricsBridge 以降级模式初始化（MeterRegistry=null）");
    }
  }

  /**
   * 从已有 MeterRegistry 创建桥接器（工厂方法）。
   *
   * @param registry Micrometer MeterRegistry，可为 null
   * @return 桥接器实例（不返回 null，即使 registry 为 null 也能安全使用）
   */
  public static MicrometerFunctionMetricsBridge from(MeterRegistry registry) {
    return new MicrometerFunctionMetricsBridge(registry);
  }

  @Override
  public void incrementCounter(String name, String... tags) {
    incrementCounter(name, 1.0, tags);
  }

  @Override
  public void incrementCounter(String name, double amount, String... tags) {
    if (!isAvailable()) {
      return;
    }
    String key = CacheKey.toKey(name, tags);
    try {
      Counter counter =
          counterCache.computeIfAbsent(
              key,
              k -> Counter.builder(name).tags(Tags.of(tags)).register(meterRegistry));
      counter.increment(amount);
    } catch (Exception e) {
      log.debug("[FunctionMetricsBridge] Counter 记录失败(name={}): {}", name, e.getMessage());
    }
  }

  @Override
  public void recordDuration(String name, long durationMs, String... tags) {
    if (!isAvailable()) {
      return;
    }
    String key = CacheKey.toKey(name, tags);
    try {
      Timer timer =
          timerCache.computeIfAbsent(
              key,
              k ->
                  Timer.builder(name)
                      .tags(Tags.of(tags))
                      .sla(TIMER_SLOS)
                      .register(meterRegistry));
      timer.record(Duration.ofMillis(durationMs));
    } catch (Exception e) {
      log.debug("[FunctionMetricsBridge] Timer 记录失败(name={}): {}", name, e.getMessage());
    }
  }

  @Override
  public <T> T recordTimed(String name, Supplier<T> supplier, String... tags) {
    if (!isAvailable()) {
      return supplier.get();
    }
    long start = System.currentTimeMillis();
    try {
      return supplier.get();
    } finally {
      long durationMs = System.currentTimeMillis() - start;
      recordDuration(name, durationMs, tags);
    }
  }

  @Override
  public void setGauge(String name, Double value, String... tags) {
    if (!isAvailable() || value == null) {
      return;
    }
    String key = CacheKey.toKey(name, tags);
    try {
      AtomicReference<Double> ref =
          gaugeRefCache.computeIfAbsent(
              key,
              k -> {
                AtomicReference<Double> newRef = new AtomicReference<>(value);
                Gauge.builder(name, newRef, AtomicReference::get)
                    .tags(Tags.of(tags))
                    .register(meterRegistry);
                return newRef;
              });
      ref.set(value);
    } catch (Exception e) {
      log.debug("[FunctionMetricsBridge] Gauge 记录失败(name={}): {}", name, e.getMessage());
    }
  }

  /** 获取底层 MeterRegistry（供高级 API 使用）。 */
  public MeterRegistry getMeterRegistry() {
    return meterRegistry;
  }

  @Override
  public boolean isAvailable() {
    return meterRegistry != null;
  }

  /**
   * 缓存 Key 工具（避免字符串拼接）。
   *
   * <p>基于指标名 + tags 组合生成唯一 key，确保不同 tags 的同命指标注册独立 Meter。
   */
  private static final class CacheKey {
    private CacheKey() {}

    static String toKey(String name, String... tags) {
      if (tags == null || tags.length == 0) {
        return name;
      }
      StringBuilder sb = new StringBuilder(name);
      for (int i = 0; i < tags.length - 1; i += 2) {
        sb.append('|').append(tags[i]).append('=').append(tags[i + 1]);
      }
      return sb.toString();
    }
  }
}
