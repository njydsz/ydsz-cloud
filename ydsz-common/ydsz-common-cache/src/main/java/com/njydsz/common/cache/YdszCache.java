package com.njydsz.common.cache;

import com.njydsz.common.cache.builder.CacheBuilder;

/**
 * YdszCache - 高性能本地缓存框架
 *
 * <p>提供 Window-TinyLFU（默认）和 Striped（分段锁）两种缓存实现， 完全基于 JDK 原生 API，核心包零第三方依赖。
 *
 * <p><b>核心特性：</b>
 *
 * <ul>
 *   <li><b>零依赖核心</b>：纯 JDK 实现，可选 Spring/Micrometer 集成
 *   <li><b>Builder 模式</b>：参考 Caffeine 的流畅 API 设计
 *   <li><b>过期策略</b>：支持 expireAfterWrite / expireAfterAccess / 自定义 Expiry
 *   <li><b>自动加载</b>：支持 CacheLoader 自动加载和自动刷新
 *   <li><b>写穿透</b>：支持 CacheWriter 同步写入后端存储
 *   <li><b>统计监控</b>：命中率、淘汰数等内置统计
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 简单缓存（默认 TINYLFU，命中率最优）
 * Cache<String, User> cache = YdszCache.newBuilder()
 *     .maximumSize(1000)
 *     .build();
 *
 * // 高性能并发缓存（写多读少场景）
 * Cache<String, User> stripedCache = YdszCache.newBuilder()
 *     .type(CacheType.STRIPED)
 *     .maximumSize(10000)
 *     .expireAfterWrite(30, TimeUnit.MINUTES)
 *     .recordStats()
 *     .removalListener((key, value, cause) -> log.info("removed: {}", key))
 *     .build();
 *
 * // 自动加载缓存
 * LoadingCache<String, User> loadingCache = YdszCache.newBuilder()
 *     .maximumSize(10000)
 *     .refreshAfterWrite(5, TimeUnit.MINUTES)
 *     .loader(CacheLoader.from(key -> userDao.findById(key)))
 *     .buildLoadingCache();
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class YdszCache {

  private YdszCache() {
    throw new UnsupportedOperationException(
        "YdszCache is a utility class and cannot be instantiated");
  }

  /**
   * 创建 CacheBuilder 实例
   *
   * @param <K> 键类型
   * @param <V> 值类型
   * @return CacheBuilder 实例
   */
  public static <K, V> CacheBuilder<K, V> newBuilder() {
    return CacheBuilder.newBuilder();
  }
}
