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
 *   <li>Weighted：基于权重的缓存
 *   <li>Concurrent：并发安全的 ConcurrentHashMap 缓存
 *   <li>Striped：高性能分段锁并发缓存（默认）
 *   <li>EnhancedLoading：增强版自动加载缓存
 * </ul>
 *
 * <p><b>引用缓存</b>（通过 CacheBuilder 的 weakKeys/weakValues/softValues 配置，不再作为独立 CacheType）：
 * <ul>
 *   <li>WeakKey：弱引用键缓存 → 使用 {@code builder.weakKeys()}</li>
 *   <li>WeakValue：弱引用值缓存 → 使用 {@code builder.weakValues()}</li>
 *   <li>SoftValue：软引用值缓存 → 使用 {@code builder.softValues()}</li>
 * </ul>
 *
 * <p><b>TTL 缓存</b>：通过 {@code builder.expireAfterWrite()} 或 {@code builder.expireAfterAccess()} 配置，
 * 不再作为独立 CacheType。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum CacheType {
  /** LRU 最近最少使用淘汰策略 适用场景：热点数据缓存 */
  LRU,

  /** LFU 最不经常使用淘汰策略 适用场景：访问频率差异大的场景 */
  LFU,

  /** Window-TinyLFU 算法（参考 Caffeine） 适用场景：通用场景，命中率最优（默认） */
  TINYLFU,

  /** 基于权重的缓存 适用场景：内存敏感场景，按对象大小淘汰 */
  WEIGHTED,

  /** 并发安全的 ConcurrentHashMap 缓存 适用场景：中等并发场景 */
  CONCURRENT,

  /** 高性能分段锁并发缓存（默认） 适用场景：高并发场景，性能最优 推荐：高并发场景首选 */
  STRIPED,

  /** 增强版自动加载缓存 适用场景：需要自动加载、自动刷新的场景 推荐：数据库查询缓存首选 */
  ENHANCED_LOADING,

  /**
   * 异步缓存 — 所有操作返回 CompletableFuture
   *
   * <p>适用场景：响应式编程、异步 IO 场景。底层淘汰策略使用 TINYLFU。
   * 通过 {@code CacheBuilder.buildAsync()} 构建。
   *
   * @since 1.0.0
   */
  ASYNC
}
