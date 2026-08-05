package com.remisoft.common.cache;

import com.remisoft.common.cache.builder.CacheBuilder;

/**
 * RemiCache - 高性能缓存框架（零依赖、企业级）
 *
 * <p>提供多种缓存实现，包括 LRU 缓存、TTL 缓存、并发缓存、权重缓存、异步加载缓存等， 完全基于 JDK 原生 API，无需任何第三方依赖。功能对标并超越 Guava Cache 和
 * Caffeine。
 *
 * <p><b>核心特性：</b>
 *
 * <ul>
 *   <li><b>零依赖</b>：纯 JDK 实现，无需任何第三方库
 *   <li><b>Builder 模式</b>：参考 Caffeine/Guava 的流畅 API 设计
 *   <li><b>11
 *       种缓存类型</b>：TINYLFU、LRU、LFU、WEIGHTED、CONCURRENT、STRIPED、ENHANCED_LOADING
 *   <li><b>自动加载</b>：支持 CacheLoader 自动加载和自动刷新
 *   <li><b>写穿透</b>：支持 CacheWriter 同步写入后端存储
 *   <li><b>高性能统计</b>：使用 PaddedStatsCounter 缓存行填充优化
 *   <li><b>删除通知</b>：支持 RemovalListener 回调通知
 *   <li><b>定时任务</b>：支持 CacheScheduler 定时清理、刷新、健康检查
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 简单缓存（默认 TINYLFU）
 * Cache<String, User> cache = RemiCache.newBuilder()
 *     .maximumSize(1000)
 *     .build();
 *
 * // LRU 缓存
 * Cache<String, User> lruCache = RemiCache.newBuilder()
 *     .type(CacheType.LRU)
 *     .maximumSize(1000)
 *     .build();
 *
 * // 高性能并发缓存
 * Cache<String, User> stripedCache = RemiCache.newBuilder()
 *     .type(CacheType.STRIPED)
 *     .maximumSize(10000)
 *     .recordStats()
 *     .removalListener((key, value, cause) -> log.info("removed: {}", key))
 *     .build();
 *
 * // 自动加载缓存
 * LoadingCache<String, User> loadingCache = RemiCache.newBuilder()
 *     .type(CacheType.ENHANCED_LOADING)
 *     .maximumSize(10000)
 *     .refreshAfterWrite(5, TimeUnit.MINUTES)
 *     .loader(CacheLoader.from(key -> userDao.findById(key)))
 *     .buildLoadingCache();
 *
 * // 写穿透缓存
 * Cache<String, User> writeCache = RemiCache.newBuilder()
 *     .type(CacheType.STRIPED)
 *     .maximumSize(10000)
 *     .writer(userCacheWriter)
 *     .build();
 * }</pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
public final class RemiCache {

  private RemiCache() {
    throw new UnsupportedOperationException(
        "RemiCache is a utility class and cannot be instantiated");
  }

  /**
   * 创建 CacheBuilder 实例（参考 Caffeine/Guava 风格）
   *
   * <p>使用示例：
   *
   * <pre>{@code
   * // 简单缓存（默认 TINYLFU）
   * Cache<String, User> cache = RemiCache.newBuilder()
   *     .maximumSize(1000)
   *     .build();
   *
   * // 高性能并发缓存
   * Cache<String, User> stripedCache = RemiCache.newBuilder()
   *     .type(CacheType.STRIPED)
   *     .maximumSize(10000)
   *     .recordStats()
   *     .removalListener((key, value, cause) -> log.info("removed: {}", key))
   *     .build();
   *
   * // 自动加载缓存
   * LoadingCache<String, User> loadingCache = RemiCache.newBuilder()
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
