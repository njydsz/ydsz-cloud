package com.njydsz.pmis.common.cache.metrics;

import com.njydsz.pmis.common.cache.api.Cache;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * YdszCache 到 Micrometer 的指标桥接器
 *
 * <p>将缓存统计信息注册为 Micrometer 指标，支持与 Prometheus、Grafana 等可观测性平台集成。
 *
 * <p>注册的指标：
 * <ul>
 *   <li>{@code cache.gets} - 缓存查询总次数（FunctionCounter）</li>
 *   <li>{@code cache.misses} - 缓存未命中总次数（FunctionCounter）</li>
 *   <li>{@code cache.puts} - 缓存加载放入总次数（FunctionCounter）</li>
 *   <li>{@code cache.hit.rate} - 缓存命中率（Gauge，0.0 ~ 1.0）</li>
 *   <li>{@code cache.size} - 当前缓存条目数（Gauge）</li>
 *   <li>{@code cache.evictions} - 淘汰总次数（FunctionCounter）</li>
 *   <li>{@code cache.load.duration} - 平均加载耗时（FunctionTimer）</li>
 * </ul>
 *
 * <p>指标标签：
 * <ul>
 *   <li>{@code cache_name} - 缓存名称</li>
 *   <li>{@code cache_type} - 缓存类型（由调用方通过 Tag 传入，如 "local", "caffeine" 等）</li>
 * </ul>
 *
 * @author Marvin Lee
 * @version 3.5.0
 */
public class CacheMeterBinder implements MeterBinder {

    private static final String METRIC_PREFIX = "cache";
    private static final String TAG_CACHE_NAME = "cache_name";
    private static final String TAG_CACHE_TYPE = "cache_type";

    private final Cache<?, ?> cache;
    private final String cacheName;
    private final String cacheType;
    private final Iterable<Tag> extraTags;

    public CacheMeterBinder(Cache<?, ?> cache, String cacheName) {
        this(cache, cacheName, "local", Collections.emptyList());
    }

    public CacheMeterBinder(Cache<?, ?> cache, String cacheName, String cacheType) {
        this(cache, cacheName, cacheType, Collections.emptyList());
    }

    public CacheMeterBinder(Cache<?, ?> cache, String cacheName, String cacheType, Iterable<Tag> extraTags) {
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

        FunctionCounter.builder(METRIC_PREFIX + ".gets", cache, c -> (double) c.getStats().getTotalAccessCount())
                .tags(extraTags)
                .tag(cacheNameTag.getKey(), cacheNameTag.getValue())
                .tag(cacheTypeTag.getKey(), cacheTypeTag.getValue())
                .description("Total number of cache get operations (hits + misses)")
                .register(registry);

        FunctionCounter.builder(METRIC_PREFIX + ".misses", cache, c -> (double) c.getStats().getMissCount())
                .tags(extraTags)
                .tag(cacheNameTag.getKey(), cacheNameTag.getValue())
                .tag(cacheTypeTag.getKey(), cacheTypeTag.getValue())
                .description("Total number of cache misses")
                .register(registry);

        FunctionCounter.builder(METRIC_PREFIX + ".puts", cache, c -> (double) c.getStats().getLoadCount())
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

        FunctionCounter.builder(METRIC_PREFIX + ".evictions", cache, c -> (double) c.getStats().getEvictionCount())
                .tags(extraTags)
                .tag(cacheNameTag.getKey(), cacheNameTag.getValue())
                .tag(cacheTypeTag.getKey(), cacheTypeTag.getValue())
                .description("Total number of cache evictions")
                .register(registry);

        FunctionTimer.builder(METRIC_PREFIX + ".load.duration", cache,
                        c -> c.getStats().getLoadSuccessCount(),
                        c -> (double) c.getStats().getTotalLoadTimeNanos(),
                        TimeUnit.NANOSECONDS)
                .tags(extraTags)
                .tag(cacheNameTag.getKey(), cacheNameTag.getValue())
                .tag(cacheTypeTag.getKey(), cacheTypeTag.getValue())
                .description("Cache load duration")
                .register(registry);
    }
}
