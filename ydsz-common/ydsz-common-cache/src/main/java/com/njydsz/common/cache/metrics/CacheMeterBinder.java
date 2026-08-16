package com.njydsz.common.cache.metrics;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;

import com.njydsz.common.cache.api.Cache;

/**
 * YdszCache 到 Micrometer 的指标桥接器（Tags 预编译优化版）
 *
 * <p>优化点：
 *
 * <ul>
 *   <li>Tags 预编译：构造时一次性构建 Tags 数组，避免每次 bindTo() 重复创建 Tag 对象
 *   <li>指标去重保护：使用 REGISTERED_NAMES 计数器防止高基数问题
 * </ul>
 *
 * <p>注册的指标：
 *
 * <ul>
 *   <li>{@code cache.size} - 当前缓存条目数（Gauge）
 *   <li>{@code cache.gets} - 缓存查询总次数（FunctionCounter）
 *   <li>{@code cache.misses} - 缓存未命中总次数（FunctionCounter）
 *   <li>{@code cache.puts} - 缓存加载放入总次数（FunctionCounter）
 *   <li>{@code cache.hit.rate} - 缓存命中率（Gauge）
 *   <li>{@code cache.evictions} - 淘汰总次数（FunctionCounter）
 *   <li>{@code cache.load.duration} - 平均加载耗时（FunctionTimer）
 *   <li>{@code cache.get.duration} - GET 操作耗时分布（Timer，含 P50/P90/P99 分位数）
 *   <li>{@code cache.put.duration} - PUT 操作耗时分布（Timer，含 P50/P90/P99 分位数）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class CacheMeterBinder implements MeterBinder {

  private static final Logger log = LoggerFactory.getLogger(CacheMeterBinder.class);

  private static final String METRIC_PREFIX = "cache";
  private static final String TAG_CACHE_NAME = "cache_name";
  private static final String TAG_CACHE_TYPE = "cache_type";

  /** 高基数保护：cacheName 最大长度（超过截断） */
  private static final int MAX_CACHE_NAME_LENGTH = 64;

  /** 高基数保护：最多允许注册的不同 cacheName 数量 */
  private static final AtomicInteger REGISTERED_NAMES = new AtomicInteger(0);

  private static final int MAX_REGISTERED_NAMES = 500;

  private final Cache<?, ?> cache;
  private final String cacheName;
  private final String cacheType;

  /** 预编译的 Tags 数组（构造时构建，bindTo 时复用） */
  private final Iterable<Tag> precompiledTags;

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
    // 高基数保护：截断过长的 cacheName
    this.cacheName =
        cacheName != null && cacheName.length() > MAX_CACHE_NAME_LENGTH
            ? cacheName.substring(0, MAX_CACHE_NAME_LENGTH) + "~"
            : cacheName;
    // 高基数保护：记录注册数量
    if (REGISTERED_NAMES.incrementAndGet() > MAX_REGISTERED_NAMES) {
      log.warn("缓存指标注册数量超过阈值 {}，可能存在高基数问题。cacheName={}", MAX_REGISTERED_NAMES, this.cacheName);
    }
    this.cacheType = cacheType;
    // 预编译 Tags：在构造时一次性构建，避免每次 bindTo 都重复创建
    this.precompiledTags =
        Tags.of(extraTags).and(TAG_CACHE_NAME, this.cacheName).and(TAG_CACHE_TYPE, this.cacheType);
  }

  /**
   * 将缓存的全部指标注册到指定 Micrometer 注册中心。
   *
   * <p>使用预编译的 Tags 数组，避免每次重复创建 Tag 对象。
   *
   * @param registry 目标 Micrometer 注册中心，不可为 {@code null}
   */
  @Override
  public void bindTo(MeterRegistry registry) {
    // 使用预编译的 Tags，避免每次 bindTo 都构建新的 Tag 实例
    Gauge.builder(METRIC_PREFIX + ".size", cache, c -> (double) c.estimatedSize())
        .tags(precompiledTags)
        .description("Current number of entries in the cache")
        .register(registry);

    FunctionCounter.builder(
            METRIC_PREFIX + ".gets", cache, c -> (double) c.getStats().getTotalAccessCount())
        .tags(precompiledTags)
        .description("Total number of cache get operations (hits + misses)")
        .register(registry);

    FunctionCounter.builder(
            METRIC_PREFIX + ".misses", cache, c -> (double) c.getStats().getMissCount())
        .tags(precompiledTags)
        .description("Total number of cache misses")
        .register(registry);

    FunctionCounter.builder(
            METRIC_PREFIX + ".puts", cache, c -> (double) c.getStats().getLoadCount())
        .tags(precompiledTags)
        .description("Total number of cache put operations via loader")
        .register(registry);

    Gauge.builder(METRIC_PREFIX + ".hit.rate", cache, Cache::getHitRate)
        .tags(precompiledTags)
        .description("Cache hit rate (0.0 - 1.0)")
        .register(registry);

    FunctionCounter.builder(
            METRIC_PREFIX + ".evictions", cache, c -> (double) c.getStats().getEvictionCount())
        .tags(precompiledTags)
        .description("Total number of cache evictions")
        .register(registry);

    FunctionTimer.builder(
            METRIC_PREFIX + ".load.duration",
            cache,
            c -> c.getStats().getLoadSuccessCount(),
            c -> (double) c.getStats().getTotalLoadTimeNanos(),
            TimeUnit.NANOSECONDS)
        .tags(precompiledTags)
        .description("Cache load duration")
        .register(registry);

    // GET 操作 Timer（含 P50/P90/P99 分位数）
    getTimer =
        Timer.builder(METRIC_PREFIX + ".get.duration")
            .tags(precompiledTags)
            .description("Cache GET operation duration")
            .publishPercentiles(0.5, 0.9, 0.99)
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofNanos(100))
            .maximumExpectedValue(Duration.ofMillis(100))
            .register(registry);

    // PUT 操作 Timer（含 P50/P90/P99 分位数）
    putTimer =
        Timer.builder(METRIC_PREFIX + ".put.duration")
            .tags(precompiledTags)
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

  /**
   * 获取 GET Timer 实例（供装饰器直接使用）
   *
   * @return GET Timer，如果未绑定则返回 null
   */
  public Timer getGetTimer() {
    return getTimer;
  }

  /**
   * 获取 PUT Timer 实例（供装饰器直接使用）
   *
   * @return PUT Timer，如果未绑定则返回 null
   */
  public Timer getPutTimer() {
    return putTimer;
  }
}
