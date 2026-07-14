package com.njydsz.pmis.common.cache.metrics;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import com.njydsz.pmis.common.cache.api.Cache;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * YdszCache 到 Micrometer 的指标桥接器
 *
 * <p>将缓存统计信息注册为 Micrometer 指标，支持与 Prometheus、Grafana 等可观测性平台集成。
 *
 * <p>注册的指标：
 *
 * <ul>
 *   <li>{@code cache.gets} - 缓存查询总次数（FunctionCounter）
 *   <li>{@code cache.misses} - 缓存未命中总次数（FunctionCounter）
 *   <li>{@code cache.puts} - 缓存加载放入总次数（FunctionCounter）
 *   <li>{@code cache.hit.rate} - 缓存命中率（Gauge，0.0 ~ 1.0）
 *   <li>{@code cache.size} - 当前缓存条目数（Gauge）
 *   <li>{@code cache.evictions} - 淘汰总次数（FunctionCounter）
 *   <li>{@code cache.load.duration} - 平均加载耗时（FunctionTimer）
 *   <li>{@code cache.get.duration} - GET 操作耗时分布（Timer，含 P50/P90/P99 分位数）
 *   <li>{@code cache.put.duration} - PUT 操作耗时分布（Timer，含 P50/P90/P99 分位数）
 * </ul>
 *
 * <p>指标标签：
 *
 * <ul>
 *   <li>{@code cache_name} - 缓存名称
 *   <li>{@code cache_type} - 缓存类型
 * </ul>
 *
 * @author Marvin Lee
 * @version 4.0.0
 */
public class CacheMeterBinder implements MeterBinder {

  private static final String METRIC_PREFIX = "cache";
  private static final String TAG_CACHE_NAME = "cache_name";
  private static final String TAG_CACHE_TYPE = "cache_type";

  private final Cache<?, ?> cache;
  private final String cacheName;
  private final String cacheType;
  private final Iterable<Tag> extraTags;

  /** GET 操作 Timer（含 P50/P90/P99 分位数） */
  private Timer getTimer;

  /** PUT 操作 Timer（含 P50/P90/P99 分位数） */
  private Timer putTimer;

  public CacheMeterBinder(Cache<?, ?> cache, String cacheName) {
    this(cache, cacheName, "local", Collections.emptyList());
  }

  public CacheMeterBinder(Cache<?, ?> cache, String cacheName, String cacheType) {
    this(cache, cacheName, cacheType, Collections.emptyList());
  }

  public CacheMeterBinder(
      Cache<?, ?> cache, String cacheName, String cacheType, Iterable<Tag> extraTags) {
    this.cache = cache;
    this.cacheName = cacheName;
    this.cacheType = cacheType;
    this.extraTags = extraTags;
  }

  @Override
    public void bindTo(MeterRegistry registry) {
    Tag cacheNameTag = Tag.of(TAG_CACHE_NAME, cacheName);
    Tag cacheTypeTag = Tag.of(TAG_CACHE_TYPE, cacheType);

    Gauge.builder(METRIC_PREFIX + ".size", cache, c -> (double) c.estimatedSize())
        .tags(extraTags)
        .tag(cacheNameTag.getKey(), cacheNameTag.getValue())
        .tag(cacheTypeTag.getKey(), cacheTypeTag.getValue())
        .description("Current number of entries in the cache")
        .register(registry);

    FunctionCounter.builder(
            METRIC_PREFIX + ".gets", cache, c -> (double) c.getStats().getTotalAccessCount())
        .tags(extraTags)
        .tag(cacheNameTag.getKey(), cacheNameTag.getValue())
        .tag(cacheTypeTag.getKey(), cacheTypeTag.getValue())
        .description("Total number of cache get operations (hits + misses)")
        .register(registry);

    FunctionCounter.builder(
            METRIC_PREFIX + ".misses", cache, c -> (double) c.getStats().getMissCount())
        .tags(extraTags)
        .tag(cacheNameTag.getKey(), cacheNameTag.getValue())
        .tag(cacheTypeTag.getKey(), cacheTypeTag.getValue())
        .description("Total number of cache misses")
        .register(registry);

    FunctionCounter.builder(
            METRIC_PREFIX + ".puts", cache, c -> (double) c.getStats().getLoadCount())
        .tags(extraTags)
        .tag(cacheNameTag.getKey(), cacheNameTag.getValue())
        .tag(cacheTypeTag.getKey(), cacheTypeTag.getValue())
        .description("Total number of cache put operations via loader")
        .register(registry);

    Gauge.builder(METRIC_PREFIX + ".hit.rate", cache, Cache::getHitRate)
        .tags(extraTags)
        .tag(cacheNameTag.getKey(), cacheNameTag.getValue())
        .tag(cacheTypeTag.getKey(), cacheTypeTag.getValue())
        .description("Cache hit rate (0.0 - 1.0)")
        .register(registry);

    FunctionCounter.builder(
            METRIC_PREFIX + ".evictions", cache, c -> (double) c.getStats().getEvictionCount())
        .tags(extraTags)
        .tag(cacheNameTag.getKey(), cacheNameTag.getValue())
        .tag(cacheTypeTag.getKey(), cacheTypeTag.getValue())
        .description("Total number of cache evictions")
        .register(registry);

    FunctionTimer.builder(
            METRIC_PREFIX + ".load.duration",
            cache,
            c -> c.getStats().getLoadSuccessCount(),
            c -> (double) c.getStats().getTotalLoadTimeNanos(),
            TimeUnit.NANOSECONDS)
        .tags(extraTags)
        .tag(cacheNameTag.getKey(), cacheNameTag.getValue())
        .tag(cacheTypeTag.getKey(), cacheTypeTag.getValue())
        .description("Cache load duration")
        .register(registry);

    // GET 操作 Timer（含 P50/P90/P99 分位数）
    getTimer =
        Timer.builder(METRIC_PREFIX + ".get.duration")
            .tags(extraTags)
            .tag(cacheNameTag.getKey(), cacheNameTag.getValue())
            .tag(cacheTypeTag.getKey(), cacheTypeTag.getValue())
            .description("Cache GET operation duration")
            .publishPercentiles(0.5, 0.9, 0.99)
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofNanos(100))
            .maximumExpectedValue(Duration.ofMillis(100))
            .register(registry);

    // PUT 操作 Timer（含 P50/P90/P99 分位数）
    putTimer =
        Timer.builder(METRIC_PREFIX + ".put.duration")
            .tags(extraTags)
            .tag(cacheNameTag.getKey(), cacheNameTag.getValue())
            .tag(cacheTypeTag.getKey(), cacheTypeTag.getValue())
            .description("Cache PUT operation duration")
            .publishPercentiles(0.5, 0.9, 0.99)
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofNanos(100))
            .maximumExpectedValue(Duration.ofMillis(100))
            .register(registry);
  }

  /**
   * 记录 GET 操作耗时
   *
   * @param nanos 耗时（纳秒）
   */
  public void recordGetDuration(long nanos) {
    if (getTimer != null) {
      getTimer.record(nanos, TimeUnit.NANOSECONDS);
    }
  }

  /**
   * 记录 PUT 操作耗时
   *
   * @param nanos 耗时（纳秒）
   */
  public void recordPutDuration(long nanos) {
    if (putTimer != null) {
      putTimer.record(nanos, TimeUnit.NANOSECONDS);
    }
  }
}
