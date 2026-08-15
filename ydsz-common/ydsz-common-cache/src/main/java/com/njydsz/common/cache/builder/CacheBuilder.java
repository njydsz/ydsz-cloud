package com.njydsz.common.cache.builder;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.api.LoadingCache;
import com.njydsz.common.cache.internal.concurrent.StripedConcurrentCache;
import com.njydsz.common.cache.internal.decorator.ExpirableCache;
import com.njydsz.common.cache.internal.decorator.WriteThroughCache;
import com.njydsz.common.cache.internal.loading.EnhancedLoadingCache;
import com.njydsz.common.cache.internal.tinylfu.WindowTinyLFUCache;
import com.njydsz.common.cache.listener.RemovalListener;
import com.njydsz.common.cache.support.CacheLoader;
import com.njydsz.common.cache.support.CacheWriter;
import com.njydsz.common.cache.support.Expiry;

/**
 * 缓存构建器 - 参考 Caffeine 的流畅构建器
 *
 * <p>核心特性：
 *
 * <ul>
 *   <li>流畅 API：链式调用，语义清晰
 *   <li>灵活配置：支持容量、过期、loader、writer 等核心参数
 *   <li>默认 TINYLFU：命中率最优的默认缓存类型
 * </ul>
 *
 * <p>使用示例：
 *
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
 *     .maximumSize(10000)
 *     .refreshAfterWrite(5, TimeUnit.MINUTES)
 *     .loader(CacheLoader.from(key -> userDao.findById(key)))
 *     .buildLoadingCache();
 * }</pre>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author ydsz-team
 * @since 1.0.0
 */
public final class CacheBuilder<K, V> {

  /** 缓存类型（默认 TINYLFU，命中率最优） */
  private CacheType type = CacheType.TINYLFU;

  /** 最大容量（-1 表示无限制） */
  private long maximumSize = -1;

  /** 写入后过期时间（-1 表示不过期） */
  private long expireAfterWriteDuration = -1;

  /** 写入后过期时间单位 */
  private TimeUnit expireAfterWriteUnit;

  /** 访问后过期时间（-1 表示不过期） */
  private long expireAfterAccessDuration = -1;

  /** 访问后过期时间单位 */
  private TimeUnit expireAfterAccessUnit;

  /** 刷新间隔（-1 表示不刷新） */
  private long refreshAfterWriteDuration = -1;

  /** 刷新间隔单位 */
  private TimeUnit refreshAfterWriteUnit;

  /** 初始容量 */
  private int initialCapacity = 16;

  /** 锁段数（仅 STRIPED 类型） */
  private int stripes = 32;

  /** 是否启用统计 */
  private boolean recordStats = true;

  /** 删除监听器 */
  private RemovalListener<? super K, ? super V> removalListener;

  /** 监听器执行器（异步模式） */
  private Executor listenerExecutor;

  /** 异步任务执行器（用于自动刷新等异步操作） */
  private Executor taskExecutor;

  /** 缓存加载器 */
  private CacheLoader<K, V> loader;

  /** 缓存写入器 */
  private CacheWriter<? super K, ? super V> writer;

  /** 自定义过期策略 */
  private Expiry<? super K, ? super V> expiry;

  /** 缓存名称（可选，用于监控标识） */
  private String cacheName;

  /** 是否启用健康检查注册（默认 true） */
  private boolean healthCheckEnabled = true;

  /** 健康检查指示器 */
  private com.njydsz.common.cache.health.CacheHealthIndicator healthIndicator;

  /** 私有构造函数，通过 YdszCache.newBuilder() 创建 */
  private CacheBuilder() {
  }

  /**
   * 创建 CacheBuilder 实例
   *
   * @param <K> 键类型
   * @param <V> 值类型
   * @return CacheBuilder 实例
   */
  public static <K, V> CacheBuilder<K, V> newBuilder() {
    return new CacheBuilder<>();
  }

  /**
   * 设置缓存类型
   *
   * @param type 缓存类型（TINYLFU 或 STRIPED）
   * @return this
   */
  public CacheBuilder<K, V> type(CacheType type) {
    this.type = type;
    return this;
  }

  /**
   * 设置最大容量
   *
   * @param maximumSize 最大容量（-1 表示无限制）
   * @return this
   */
  public CacheBuilder<K, V> maximumSize(long maximumSize) {
    this.maximumSize = maximumSize;
    return this;
  }

  /**
   * 设置写入后过期时间
   *
   * @param duration 过期时间
   * @param unit 时间单位
   * @return this
   */
  public CacheBuilder<K, V> expireAfterWrite(long duration, TimeUnit unit) {
    this.expireAfterWriteDuration = duration;
    this.expireAfterWriteUnit = unit;
    return this;
  }

  /**
   * 设置访问后过期时间
   *
   * @param duration 过期时间
   * @param unit 时间单位
   * @return this
   */
  public CacheBuilder<K, V> expireAfterAccess(long duration, TimeUnit unit) {
    this.expireAfterAccessDuration = duration;
    this.expireAfterAccessUnit = unit;
    return this;
  }

  /**
   * 设置自定义过期策略
   *
   * <p>允许为每个缓存条目动态计算过期时间。如果设置了此策略，将覆盖 expireAfterWrite 和 expireAfterAccess 的配置。
   *
   * @param expiry 自定义过期策略
   * @return this
   */
  public CacheBuilder<K, V> expireAfter(Expiry<? super K, ? super V> expiry) {
    this.expiry = expiry;
    return this;
  }

  /**
   * 设置缓存名称
   *
   * @param cacheName 缓存名称，非空字符串
   * @return this
   */
  public CacheBuilder<K, V> name(String cacheName) {
    this.cacheName = cacheName;
    return this;
  }

  /**
   * 设置是否启用健康检查注册
   *
   * @param healthCheckEnabled true 表示启用（默认），false 表示禁用
   * @return this
   */
  public CacheBuilder<K, V> healthCheckEnabled(boolean healthCheckEnabled) {
    this.healthCheckEnabled = healthCheckEnabled;
    return this;
  }

  /**
   * 设置健康检查指示器
   *
   * @param healthIndicator 健康检查指示器
   * @return this
   */
  public CacheBuilder<K, V> healthIndicator(
      com.njydsz.common.cache.health.CacheHealthIndicator healthIndicator) {
    this.healthIndicator = healthIndicator;
    return this;
  }

  /**
   * 设置刷新间隔
   *
   * @param duration 刷新间隔
   * @param unit 时间单位
   * @return this
   */
  public CacheBuilder<K, V> refreshAfterWrite(long duration, TimeUnit unit) {
    this.refreshAfterWriteDuration = duration;
    this.refreshAfterWriteUnit = unit;
    return this;
  }

  /**
   * 设置初始容量
   *
   * @param initialCapacity 初始容量
   * @return this
   */
  public CacheBuilder<K, V> initialCapacity(int initialCapacity) {
    this.initialCapacity = initialCapacity;
    return this;
  }

  /**
   * 设置锁段数（仅 STRIPED 类型有效）
   *
   * @param stripes 锁段数
   * @return this
   */
  public CacheBuilder<K, V> stripes(int stripes) {
    this.stripes = stripes;
    return this;
  }

  /**
   * 启用统计
   *
   * @return this
   */
  public CacheBuilder<K, V> recordStats() {
    this.recordStats = true;
    return this;
  }

  /**
   * 设置是否启用统计
   *
   * @param recordStats 是否启用统计
   * @return this
   */
  public CacheBuilder<K, V> recordStats(boolean recordStats) {
    this.recordStats = recordStats;
    return this;
  }

  /**
   * 设置删除监听器
   *
   * @param listener 删除监听器
   * @return this
   */
  public CacheBuilder<K, V> removalListener(RemovalListener<? super K, ? super V> listener) {
    this.removalListener = listener;
    return this;
  }

  /**
   * 设置监听器执行器（异步模式）
   *
   * @param executor 监听器执行器
   * @return this
   */
  public CacheBuilder<K, V> listenerExecutor(Executor executor) {
    this.listenerExecutor = executor;
    return this;
  }

  /**
   * 设置异步任务执行器
   *
   * <p>用于自动刷新任务。如果未设置，将使用默认线程池。
   *
   * @param executor 异步任务执行器
   * @return this
   */
  public CacheBuilder<K, V> executor(Executor executor) {
    this.taskExecutor = executor;
    return this;
  }

  /**
   * 设置缓存加载器
   *
   * @param loader 缓存加载器
   * @return this
   */
  public CacheBuilder<K, V> loader(CacheLoader<K, V> loader) {
    this.loader = loader;
    return this;
  }

  /**
   * 设置缓存加载器（Lambda 快捷方式）
   *
   * @param loader 缓存加载器函数
   * @return this
   */
  public CacheBuilder<K, V> loaderFrom(java.util.function.Function<? super K, ? extends V> loader) {
    this.loader = CacheLoader.from(loader::apply);
    return this;
  }

  /**
   * 设置缓存写入器
   *
   * @param writer 缓存写入器
   * @return this
   */
  public CacheBuilder<K, V> writer(CacheWriter<? super K, ? super V> writer) {
    this.writer = writer;
    return this;
  }

  /**
   * 构建缓存实例
   *
   * <p>构建顺序：
   *
   * <ol>
   *   <li>创建基础淘汰缓存（TINYLFU 或 STRIPED）
   *   <li>叠加过期装饰器 ExpirableCache（如启用了过期配置）
   *   <li>叠加写策略装饰器 WriteThroughCache（如设置了 writer）
   *   <li>添加删除监听器
   * </ol>
   *
   * @return 缓存实例
   */
  public Cache<K, V> build() {
    validate();
    Cache<K, V> cache = createBaseCache();
    cache = applyDecorators(cache);
    // 健康检查自动注册
    if (healthCheckEnabled && healthIndicator != null && cacheName != null && !cacheName.isEmpty()) {
      healthIndicator.registerCache(cacheName, cache);
    }
    return cache;
  }

  /**
   * 应用装饰器栈到基础缓存实例
   *
   * @param cache 基础缓存实例
   * @return 装饰后的缓存实例
   */
  private Cache<K, V> applyDecorators(Cache<K, V> cache) {
    // 过期策略装饰器叠加
    boolean hasExpiration =
        expireAfterWriteDuration > 0 || expireAfterAccessDuration > 0 || expiry != null;
    if (hasExpiration && !(cache instanceof EnhancedLoadingCache)) {
      long writeNanos =
          expireAfterWriteDuration > 0 && expireAfterWriteUnit != null
              ? expireAfterWriteUnit.toNanos(expireAfterWriteDuration)
              : 0;
      long accessNanos =
          expireAfterAccessDuration > 0 && expireAfterAccessUnit != null
              ? expireAfterAccessUnit.toNanos(expireAfterAccessDuration)
              : 0;
      cache = new ExpirableCache<>(cache, writeNanos, accessNanos, expiry, 1);
    }

    // 写穿透装饰器
    if (writer != null) {
      cache = new WriteThroughCache<>(cache, writer);
    }

    if (removalListener != null) {
      cache.addListener(removalListener);
    }

    return cache;
  }

  /**
   * 验证构建参数合法性
   *
   * @throws IllegalArgumentException 参数非法时抛出
   */
  private void validate() {
    if (maximumSize == 0) {
      throw new IllegalArgumentException("maximumSize must be greater than 0, or -1 for unlimited");
    }
    if (maximumSize > 0 && maximumSize < 2) {
      throw new IllegalArgumentException("maximumSize must be at least 2 when bounded");
    }
    if (expireAfterWriteDuration == 0) {
      throw new IllegalArgumentException(
          "expireAfterWrite duration must be greater than 0, or -1 for no expiration");
    }
    if (expireAfterAccessDuration == 0) {
      throw new IllegalArgumentException(
          "expireAfterAccess duration must be greater than 0, or -1 for no expiration");
    }
    if (expireAfterWriteDuration > 0 && expireAfterWriteUnit == null) {
      throw new IllegalArgumentException("expireAfterWriteUnit must be set when expireAfterWrite > 0");
    }
    if (expireAfterAccessDuration > 0 && expireAfterAccessUnit == null) {
      throw new IllegalArgumentException(
          "expireAfterAccessUnit must be set when expireAfterAccess > 0");
    }
    if (refreshAfterWriteDuration > 0 && refreshAfterWriteUnit == null) {
      throw new IllegalArgumentException(
          "refreshAfterWriteUnit must be set when refreshAfterWrite > 0");
    }
    if (stripes < 1) {
      throw new IllegalArgumentException("stripes must be at least 1");
    }
    if (initialCapacity < 0) {
      throw new IllegalArgumentException("initialCapacity must be >= 0");
    }
    // 不允许同时设置 expireAfterWrite 和 expireAfterAccess
    if (expireAfterWriteDuration > 0 && expireAfterAccessDuration > 0) {
      throw new IllegalArgumentException(
          "Cannot set expireAfterWrite and expireAfterAccess simultaneously. "
              + "Use only one expiration strategy, or use expireAfter(Expiry) for custom per-entry expiry.");
    }
    if (expireAfterWriteDuration > 0 && expireAfterAccessDuration > 0 && expiry != null) {
      throw new IllegalArgumentException(
          "Cannot set expireAfterWrite, expireAfterAccess, and expiry simultaneously. "
              + "Use only one expiration strategy.");
    }
  }

  /**
   * 构建自动加载缓存实例
   *
   * @return 自动加载缓存实例
   */
  public LoadingCache<K, V> buildLoadingCache() {
    if (loader == null) {
      throw new IllegalStateException("loader must be set for LoadingCache");
    }

    validate();
    Cache<K, V> baseCache = createBaseCache();

    if (baseCache instanceof LoadingCache) {
      return (LoadingCache<K, V>) baseCache;
    }

    // 应用装饰器后再包装为 LoadingCache
    Cache<K, V> decoratedCache = applyDecorators(baseCache);

    long effectiveRefreshDuration = refreshAfterWriteDuration > 0 ? refreshAfterWriteDuration : 0;
    TimeUnit effectiveRefreshUnit =
        effectiveRefreshDuration > 0
            ? (refreshAfterWriteUnit != null ? refreshAfterWriteUnit : TimeUnit.MILLISECONDS)
            : TimeUnit.NANOSECONDS;

    return EnhancedLoadingCache.create(
        decoratedCache,
        loader,
        taskExecutor != null ? taskExecutor : listenerExecutor,
        effectiveRefreshDuration,
        effectiveRefreshUnit,
        null,
        recordStats);
  }

  /**
   * 创建基础缓存实例
   *
   * @return 基础缓存实例
   */
  private Cache<K, V> createBaseCache() {
    int effectiveSize = maximumSize > 0 ? (int) maximumSize : 1024;

    switch (type) {
      case TINYLFU:
        if (maximumSize > 0) {
          return new WindowTinyLFUCache<K, V>((int) maximumSize, stripes);
        } else {
          return new WindowTinyLFUCache<K, V>(initialCapacity, stripes);
        }

      case STRIPED:
        if (maximumSize > 0) {
          return new StripedConcurrentCache<K, V>((int) maximumSize, stripes);
        } else {
          return new StripedConcurrentCache<K, V>(initialCapacity, stripes);
        }

      default:
        throw new IllegalStateException("Unknown cache type: " + type);
    }
  }
}
