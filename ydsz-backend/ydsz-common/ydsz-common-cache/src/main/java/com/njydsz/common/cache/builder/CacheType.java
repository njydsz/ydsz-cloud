package com.njydsz.common.cache.builder;

/**
 * 缓存类型枚举
 *
 * <p>支持的缓存类型：
 *
 * <ul>
 *   <li>LRU：最近最少使用淘汰策略
 *   <li>LFU：最不经常使用淘汰策略
 *   <li>TinyLFU：Window-TinyLFU 算法（参考 Caffeine）
 *   <li>TTL：基于过期时间的缓存
 *   <li>Weighted：基于权重的缓存
 *   <li>WeakKey：弱引用键缓存
 *   <li>WeakValue：弱引用值缓存
 *   <li>SoftValue：软引用值缓存
 *   <li>Concurrent：并发安全的 ConcurrentHashMap 缓存
 *   <li>Striped：高性能分段锁并发缓存（默认）
 *   <li>EnhancedLoading：增强版自动加载缓存
 * </ul>
 *
 * @since 1.0.0
 * 
 */
public enum CacheType {
  /** 基于权重的缓存 适用场景：内存敏感场景，按对象大小淘汰 */
  WEIGHTED,  /**
   * 软引用值缓存
   *
   * @deprecated 使用 {@code builder.softValues()} 替代。
   */
  @Deprecated
  SOFT_VALUE,

  /** 并发安全的 ConcurrentHashMap 缓存 适用场景：中等并发场景 */
  CONCURRENT,

  /** 高性能分段锁并发缓存（默认） 适用场景：高并发场景，性能最优 推荐：高并发场景首选 */
  STRIPED,

  /** 增强版自动加载缓存 适用场景：需要自动加载、自动刷新的场景 推荐：数据库查询缓存首选 */
  ENHANCED_LOADING
}
