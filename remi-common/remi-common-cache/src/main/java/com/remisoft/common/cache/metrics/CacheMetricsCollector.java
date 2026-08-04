package com.remisoft.common.cache.metrics;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.remisoft.common.cache.stats.CacheStats;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

/**
 * 缓存指标收集器
 *
 * <p>将 {@link CacheStats} 指标暴露给 Micrometer，支持 Prometheus、Grafana 等监控平台。 适用于仅有 CacheStats 引用、无法直接访问
 * Cache 实例的场景。
 *
 * <p><b>注册的指标：</b>
 *
 * <ul>
 *   <li>cache.gets - 缓存查询总次数（FunctionCounter）
 *   <li>cache.misses - 缓存未命中总次数（FunctionCounter）
 *   <li>cache.puts - 缓存加载放入总次数（FunctionCounter）
 *   <li>cache.hit.rate - 缓存命中率（Gauge）
 *   <li>cache.size - 当前缓存条目数（Gauge，始终返回 0）
 *   <li>cache.evictions - 淘汰总次数（FunctionCounter）
 *   <li>cache.load.duration - 平均加载耗时（FunctionTimer）
 *   <li>cache.load.success - 加载成功总次数（FunctionCounter）
 *   <li>cache.load.error - 加载异常总次数（FunctionCounter）
 * </ul>
 *
 * <p>注意：{@code cache.size} 始终返回 0，因为 CacheStats 不包含缓存大小信息。 如需准确的缓存大小指标，请使用 {@link
 * CacheMeterBinder}。
 *
 *
 * @author remi-team
 * @since 1.0.0
 */
public class CacheMetricsCollector {

  private static final String METRIC_PREFIX = "cache";
  private static final String TAG_CACHE_NAME = "cache_name";
  private static final String TAG_CACHE_TYPE = "cache_type";

  private final AtomicReference<CacheStats> statsRef;
  private final String cacheName;

  public CacheMetricsCollector(String cacheName, AtomicReference<CacheStats> statsRef) {
    this.cacheName = cacheName;
    this.statsRef = statsRef;
  }

  /**
   * 注册到 Micrometer
   *
   * @param registry MeterRegistry 实例
   * @return 当前收集器
   */
  public CacheMetricsCollector bindTo(MeterRegistry registry) {
    Tags tags = Tags.of(TAG_CACHE_NAME, cacheName, TAG_CACHE_TYPE, "unknown");

    FunctionCounter.builder(
            METRIC_PREFIX + ".gets", statsRef, s -> (double) s.get().getTotalAccessCount())
        .tags(tags)
        .description("Total number of cache get operations (hits + misses)")
        .register(registry);

    FunctionCounter.builder(
            METRIC_PREFIX + ".misses", statsRef, s -> (double) s.get().getMissCount())
        .tags(tags)
        .description("Total number of cache misses")
        .register(registry);

    FunctionCounter.builder(METRIC_PREFIX + ".puts", statsRef, s -> (double) s.get().getLoadCount())
        .tags(tags)
        .description("Total number of cache put operations via loader")
        .register(registry);

    Gauge.builder(METRIC_PREFIX + ".hit.rate", statsRef, s -> s.get().getHitRate())
        .tags(tags)
        .description("Cache hit rate (0.0 - 1.0)")
        .register(registry);

    Gauge.builder(METRIC_PREFIX + ".size", statsRef, s -> 0.0)
        .tags(tags)
        .description("Current number of entries in the cache (unavailable, always 0)")
        .register(registry);

    FunctionCounter.builder(
            METRIC_PREFIX + ".evictions", statsRef, s -> (double) s.get().getEvictionCount())
        .tags(tags)
        .description("Total number of cache evictions")
        .register(registry);

    FunctionTimer.builder(
            METRIC_PREFIX + ".load.duration",
            statsRef,
            s -> s.get().getLoadSuccessCount(),
            s -> (double) s.get().getTotalLoadTimeNanos(),
            TimeUnit.NANOSECONDS)
        .tags(tags)
        .description("Cache load duration")
        .register(registry);

    FunctionCounter.builder(
            METRIC_PREFIX + ".load.success", statsRef, s -> (double) s.get().getLoadSuccessCount())
        .tags(tags)
        .description("Total number of successful cache loads")
        .register(registry);

    FunctionCounter.builder(
            METRIC_PREFIX + ".load.error", statsRef, s -> (double) s.get().getLoadExceptionCount())
        .tags(tags)
        .description("Total number of cache load exceptions")
        .register(registry);

    return this;
  }
}
