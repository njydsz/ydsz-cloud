package com.njydsz.pmis.common.cache.builder;

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
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
public enum CacheType {

  /**
   * Window-TinyLFU 缓存（v3.10.0 分段式架构） 适用场景：通用场景，命中率最高，参考 Caffeine 核心算法 推荐：高命中率场景首选
   * 特性：分段锁架构，无锁读取，采样淘汰
   */
  TINYLFU,

  /** LRU（最近最少使用）缓存 适用场景：访问热点数据，淘汰最久未访问的项 */
  LRU,

  /** LFU（最不经常使用）缓存 适用场景：访问频率稳定的数据 */
  LFU,

  /** TTL（基于过期时间）缓存 适用场景：需要精确控制过期时间的数据 */
  TTL,

  /** 基于权重的缓存 适用场景：内存敏感场景，按对象大小淘汰 */
  WEIGHTED,

  /** 弱引用键缓存 适用场景：键对象可能被外部回收的场景 */
  WEAK_KEY,

  /** 弱引用值缓存 适用场景：值对象可能被 GC 回收的场景 */
  WEAK_VALUE,

  /** 软引用值缓存 适用场景：值对象在内存不足时可被回收的场景 */
  SOFT_VALUE,

  /** 并发安全的 ConcurrentHashMap 缓存 适用场景：中等并发场景 */
  CONCURRENT,

  /** 高性能分段锁并发缓存（默认） 适用场景：高并发场景，性能最优 推荐：高并发场景首选 */
  STRIPED,

  /** 增强版自动加载缓存 适用场景：需要自动加载、自动刷新的场景 推荐：数据库查询缓存首选 */
  ENHANCED_LOADING
}
