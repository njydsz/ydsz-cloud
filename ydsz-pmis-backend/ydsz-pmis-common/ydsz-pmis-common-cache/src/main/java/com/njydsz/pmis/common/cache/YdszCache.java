package com.njydsz.pmis.common.cache;

import com.njydsz.pmis.common.cache.builder.CacheBuilder;

/**
 * YdszCache - 高性能缓存框架（零依赖、企业级）
 *
 * <p>提供多种缓存实现，包括 LRU 缓存、TTL 缓存、并发缓存、权重缓存、异步加载缓存等，
 * 完全基于 JDK 原生 API，无需任何第三方依赖。功能对标并超越 Guava Cache 和 Caffeine。
 *
 * <p><b>核心特性：</b>
 * <ul>
 *   <li><b>零依赖</b>：纯 JDK 实现，无需任何第三方库</li>
 *   <li><b>Builder 模式</b>：参考 Caffeine/Guava 的流畅 API 设计</li>
 *   <li><b>11 种缓存类型</b>：TINYLFU、LRU、LFU、TTL、WEIGHTED、WEAK_KEY、WEAK_VALUE、SOFT_VALUE、CONCURRENT、STRIPED、ENHANCED_LOADING</li>
 *   <li><b>自动加载</b>：支持 CacheLoader 自动加载和自动刷新</li>
 *   <li><b>写穿透</b>：支持 CacheWriter 同步写入后端存储</li>
 *   <li><b>高性能统计</b>：使用 PaddedStatsCounter 缓存行填充优化</li>
 *   <li><b>删除通知</b>：支持 RemovalListener 回调通知</li>
 *   <li><b>定时任务</b>：支持 CacheScheduler 定时清理、刷新、健康检查</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 简单缓存（默认 TINYLFU）
 * Cache<String, User> cache = YdszCache.newBuilder()
 *     .maximumSize(1000)
 *     .build();
 *
 * // LRU 缓存
 * Cache<String, User> lruCache = YdszCache.newBuilder()
 *     .type(CacheType.LRU)
 *     .maximumSize(1000)
 *     .build();
 *
 * // 高性能并发缓存
 * Cache<String, User> stripedCache = YdszCache.newBuilder()
 *     .type(CacheType.STRIPED)
 *     .maximumSize(10000)
 *     .recordStats()
 *     .removalListener((key, value, cause) -> log.info("removed: {}", key))
 *     .build();
 *
 * // 自动加载缓存
 * LoadingCache<String, User> loadingCache = YdszCache.newBuilder()
 *     .type(CacheType.ENHANCED_LOADING)
 *     .maximumSize(10000)
 *     .refreshAfterWrite(5, TimeUnit.MINUTES)
 *     .loader(CacheLoader.from(key -> userDao.findById(key)))
 *     .buildLoadingCache();
 *
 * // 写穿透缓存
 * Cache<String, User> writeCache = YdszCache.newBuilder()
 *     .type(CacheType.STRIPED)
 *     .maximumSize(10000)
 *     .writer(userCacheWriter)
 *     .build();
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public final class YdszCache {

    private YdszCache() {
        throw new UnsupportedOperationException("YdszCache is a utility class and cannot be instantiated");
    }

    /**
     * 创建 CacheBuilder 实例（参考 Caffeine/Guava 风格）
     *
     * <p>使用示例：
     * <pre>{@code
     * // 简单缓存（默认 TINYLFU）
     * Cache<String, User> cache = YdszCache.newBuilder()
     *     .maximumSize(1000)
     *     .build();
     *
     * // 高性能并发缓存
     * Cache<String, User> stripedCache = YdszCache.newBuilder()
     *     .type(CacheType.STRIPED)
     *     .maximumSize(10000)
     *     .recordStats()
     *     .removalListener((key, value, cause) -> log.info("removed: {}", key))
     *     .build();
     *
     * // 自动加载缓存
     * LoadingCache<String, User> loadingCache = YdszCache.newBuilder()
     *     .type(CacheType.ENHANCED_LOADING)
     *     .maximumSize(10000)
     *     .refreshAfterWrite(5, TimeUnit.MINUTES)
     *     .loader(CacheLoader.from(key -> userDao.findById(key)))
     *     .buildLoadingCache();
     * }</pre>
     *
     * @param <K> 键类型
     * @param <V> 值类型
     * @return CacheBuilder 实例
     */
    public static <K, V> CacheBuilder<K, V> newBuilder() {
        return CacheBuilder.newBuilder();
    }
}
