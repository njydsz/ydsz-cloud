package com.njydsz.common.cache.multilevel;

import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheBuilder;
import com.njydsz.common.cache.builder.CacheType;

/**
 * 多级缓存构建器 — 流式 API 快速创建 L1+L2 多级缓存
 *
 * <p>核心特性：
 *
 * <ul>
 *   <li>流畅 API：链式调用，语义清晰
 *   <li>灵活配置：支持自定义 L1/L2 缓存或使用默认配置
 *   <li>一键启用：广播失效、分布式重建锁等高级功能
 *   <li>类型安全：泛型约束，编译期类型检查
 * </ul>
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * // 基本用法：L1 TINYLFU + L2 Redis
 * MultiLevelCache<String, User> cache = MultiLevelCacheBuilder.<String, User>newBuilder()
 *     .cacheName("users")
 *     .l1TinyLFU(5000)
 *     .l2Redis(redisTemplate, "users", 3600, User.class)
 *     .broadcast(redisTemplate, listenerContainer)
 *     .rebuildLock(redisTemplate)
 *     .build();
 *
 * // 简化用法：使用默认 L1 配置
 * MultiLevelCache<String, User> cache2 = MultiLevelCacheBuilder.<String, User>newBuilder()
 *     .cacheName("orders")
 *     .l1Default(10000)
 *     .l2Redis(redisTemplate, "orders", 1800, Order.class)
 *     .build();
 * }</pre>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author ydsz-team
 * @since 1.0.0
 */
public final class MultiLevelCacheBuilder<K, V> {

  private Cache<K, V> l1Cache;
  private Cache<K, V> l2Cache;
  private String cacheName;
  private CacheInvalidationBroadcaster broadcaster;
  private DistributedRebuildLock rebuildLock;
  private long l1BackfillTtlDuration = -1;
  private TimeUnit l1BackfillTtlUnit;

  private MultiLevelCacheBuilder() {}

  /**
   * 创建 MultiLevelCacheBuilder 实例
   *
   * @param <K> 键类型
   * @param <V> 值类型
   * @return MultiLevelCacheBuilder 实例
   */
  public static <K, V> MultiLevelCacheBuilder<K, V> newBuilder() {
    return new MultiLevelCacheBuilder<>();
  }

  /**
   * 设置缓存名称（用于广播消息标识）
   *
   * @param cacheName 缓存名称
   * @return this
   */
  public MultiLevelCacheBuilder<K, V> cacheName(String cacheName) {
    this.cacheName = cacheName;
    return this;
  }

  /**
   * 设置自定义 L1 本地缓存
   *
   * @param l1Cache L1 缓存实例
   * @return this
   */
  public MultiLevelCacheBuilder<K, V> l1(Cache<K, V> l1Cache) {
    this.l1Cache = l1Cache;
    return this;
  }

  /**
   * 使用 TINYLFU 作为 L1 缓存
   *
   * @param maximumSize 最大容量
   * @return this
   */
  public MultiLevelCacheBuilder<K, V> l1TinyLFU(int maximumSize) {
    this.l1Cache = CacheBuilder.<K, V>newBuilder()
        .type(CacheType.TINYLFU)
        .maximumSize(maximumSize)
        .build();
    return this;
  }

  /**
   * 使用 LRU 作为 L1 缓存
   *
   * @param maximumSize 最大容量
   * @return this
   */
  public MultiLevelCacheBuilder<K, V> l1LRU(int maximumSize) {
    this.l1Cache = CacheBuilder.<K, V>newBuilder()
        .type(CacheType.LRU)
        .maximumSize(maximumSize)
        .build();
    return this;
  }

  /**
   * 使用 STRIPED 作为 L1 缓存
   *
   * @param maximumSize 最大容量
   * @return this
   */
  public MultiLevelCacheBuilder<K, V> l1Striped(int maximumSize) {
    this.l1Cache = CacheBuilder.<K, V>newBuilder()
        .type(CacheType.STRIPED)
        .maximumSize(maximumSize)
        .build();
    return this;
  }

  /**
   * 使用默认 L1 配置（TINYLFU + 过期策略）
   *
   * @param maximumSize 最大容量
   * @param expireAfterWrite 写入后过期时间
   * @param unit 时间单位
   * @return this
   */
  public MultiLevelCacheBuilder<K, V> l1Default(
      int maximumSize, long expireAfterWrite, TimeUnit unit) {
    this.l1Cache = CacheBuilder.<K, V>newBuilder()
        .type(CacheType.TINYLFU)
        .maximumSize(maximumSize)
        .expireAfterWrite(expireAfterWrite, unit)
        .build();
    return this;
  }

  /**
   * 设置自定义 L2 分布式缓存
   *
   * @param l2Cache L2 缓存实例
   * @return this
   */
  public MultiLevelCacheBuilder<K, V> l2(Cache<K, V> l2Cache) {
    this.l2Cache = l2Cache;
    return this;
  }

  /**
   * 使用 Redis 作为 L2 缓存
   *
   * @param redisTemplate Redis 模板
   * @param keyPrefix key 前缀
   * @param ttlSeconds TTL 过期时间（秒）
   * @param valueClass 值类型
   * @return this
   */
  public MultiLevelCacheBuilder<K, V> l2Redis(
      RedisTemplate<String, Object> redisTemplate,
      String keyPrefix,
      long ttlSeconds,
      Class<V> valueClass) {
    this.l2Cache = new RedisCacheAdapter<>(redisTemplate, keyPrefix, ttlSeconds, valueClass);
    return this;
  }

  /**
   * 启用跨节点 L1 失效广播（Redis Pub/Sub）
   *
   * @param redisTemplate Redis 模板
   * @param listenerContainer Redis 消息监听容器
   * @return this
   */
  public MultiLevelCacheBuilder<K, V> broadcast(
      RedisTemplate<String, Object> redisTemplate,
      RedisMessageListenerContainer listenerContainer) {
    this.broadcaster =
        new RedisCacheInvalidationBroadcaster(redisTemplate, listenerContainer, null);
    return this;
  }

  /**
   * 启用跨节点 L1 失效广播（Redis Pub/Sub，自定义频道前缀）
   *
   * @param redisTemplate Redis 模板
   * @param listenerContainer Redis 消息监听容器
   * @param channelPrefix 频道前缀
   * @return this
   */
  public MultiLevelCacheBuilder<K, V> broadcast(
      RedisTemplate<String, Object> redisTemplate,
      RedisMessageListenerContainer listenerContainer,
      String channelPrefix) {
    this.broadcaster =
        new RedisCacheInvalidationBroadcaster(redisTemplate, listenerContainer, channelPrefix);
    return this;
  }

  /**
   * 设置自定义广播器
   *
   * @param broadcaster 广播器实例
   * @return this
   */
  public MultiLevelCacheBuilder<K, V> broadcaster(CacheInvalidationBroadcaster broadcaster) {
    this.broadcaster = broadcaster;
    return this;
  }

  /**
   * 启用分布式重建锁（防止缓存击穿）
   *
   * @param redisTemplate Redis 模板
   * @return this
   */
  public MultiLevelCacheBuilder<K, V> rebuildLock(RedisTemplate<String, Object> redisTemplate) {
    this.rebuildLock = new DistributedRebuildLock(redisTemplate);
    return this;
  }

  /**
   * 启用分布式重建锁（自定义锁 TTL）
   *
   * @param redisTemplate Redis 模板
   * @param lockTtlSeconds 锁 TTL（秒）
   * @return this
   */
  public MultiLevelCacheBuilder<K, V> rebuildLock(
      RedisTemplate<String, Object> redisTemplate, long lockTtlSeconds) {
    this.rebuildLock = new DistributedRebuildLock(redisTemplate, lockTtlSeconds);
    return this;
  }

  /**
   * 设置自定义重建锁
   *
   * @param rebuildLock 重建锁实例
   * @return this
   */
  public MultiLevelCacheBuilder<K, V> rebuildLock(DistributedRebuildLock rebuildLock) {
    this.rebuildLock = rebuildLock;
    return this;
  }

  /**
   * 设置 L1 回填独立 TTL（与 L2 TTL 正交）
   *
   * <p>默认情况下，从 L2 回填到 L1 的条目使用 L1 缓存自身的过期策略（如果有）。
   * 启用此配置后，回填条目将被包装为独立的短 TTL 控制，确保 L1 数据比 L2 更快失效。
   *
   * <p>典型用途：L2 Redis 缓存 1 小时，L1 本地缓存回填后 5 分钟即过期，
   * 减少 L1 脏数据风险，同时享受本地缓存的高速读取优势。
   *
   * @param duration 回填 TTL 时长
   * @param unit 时间单位
   * @return this
   */
  public MultiLevelCacheBuilder<K, V> l1BackfillTTL(long duration, TimeUnit unit) {
    this.l1BackfillTtlDuration = duration;
    this.l1BackfillTtlUnit = unit;
    return this;
  }

  /**
   * 构建多级缓存实例
   *
   * @return 多级缓存实例
   * @throws IllegalStateException 如果 L1 或 L2 缓存未设置
   */
  public MultiLevelCache<K, V> build() {
    if (l1Cache == null) {
      throw new IllegalStateException("L1 cache must be set. Use l1(), l1TinyLFU(), l1LRU(), etc.");
    }
    if (l2Cache == null) {
      throw new IllegalStateException("L2 cache must be set. Use l2() or l2Redis().");
    }
    long backfillTtlNanos = l1BackfillTtlDuration > 0 && l1BackfillTtlUnit != null
        ? l1BackfillTtlUnit.toNanos(l1BackfillTtlDuration)
        : -1;
    return new MultiLevelCache<>(l1Cache, l2Cache, cacheName, broadcaster, rebuildLock, backfillTtlNanos);
  }
}
