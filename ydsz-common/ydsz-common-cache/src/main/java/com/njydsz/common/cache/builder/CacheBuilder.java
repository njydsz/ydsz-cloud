package com.njydsz.common.cache.builder;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.njydsz.common.cache.api.AsyncCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.api.LoadingCache;
import com.njydsz.common.cache.internal.AsyncCacheAdapter;
import com.njydsz.common.cache.internal.concurrent.ConcurrentCache;
import com.njydsz.common.cache.internal.concurrent.StripedConcurrentCache;
import com.njydsz.common.cache.internal.decorator.ExpirableCache;
import com.njydsz.common.cache.internal.decorator.MemoryAwareEvictionCache;
import com.njydsz.common.cache.internal.decorator.SwrCacheDecorator;
import com.njydsz.common.cache.internal.decorator.WriteBehindCache;
import com.njydsz.common.cache.internal.decorator.WriteThroughCache;
import com.njydsz.common.cache.internal.lfu.LFUCache;
import com.njydsz.common.cache.internal.loading.EnhancedLoadingCache;
import com.njydsz.common.cache.internal.lru.LRUCache;
import com.njydsz.common.cache.internal.reference.SoftValueCache;
import com.njydsz.common.cache.internal.reference.WeakKeyCache;
import com.njydsz.common.cache.internal.reference.WeakValueCache;
import com.njydsz.common.cache.internal.tinylfu.WindowTinyLFUCache;
import com.njydsz.common.cache.internal.weighted.WeightedCache;
import com.njydsz.common.cache.listener.RemovalListener;
import com.njydsz.common.cache.support.CacheLoader;
import com.njydsz.common.cache.support.CacheWriter;
import com.njydsz.common.cache.support.Expiry;
import com.njydsz.common.cache.support.Weigher;

/**
 * 缓存构建器 - 参考 Caffeine/Guava 的流畅构建器
 *
 * <p>核心特性：
 *
 * <ul>
 *   <li>流畅 API：链式调用，语义清晰
 *   <li>灵活配置：支持多种缓存类型、淘汰策略、统计开关
 *   <li>类型安全：泛型约束，编译期类型检查
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
 * // LRU 缓存
 * Cache<String, User> lruCache = YdszCache.newBuilder()
 *     .type(CacheType.LRU)
 *     .maximumSize(1000)
 *     .build();
 *
 * // 带写穿透的高性能缓存
 * Cache<String, User> writeThroughCache = YdszCache.newBuilder()
 *     .type(CacheType.STRIPED)
 *     .maximumSize(10000)
 *     .writer(userCacheWriter)
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
 * @author ydsz-team
 * @since 1.0.0
 *
 */
public final class CacheBuilder<K, V> {

  /** 缓存类型（默认 TINYLFU，命中率最优） */
  private CacheType type = CacheType.TINYLFU;

  /** 最大容量（-1 表示无限制） */
  private long maximumSize = -1;

  /** 最大权重（-1 表示无限制） */
  private long maximumWeight = -1;

  /** 权重计算器 */
  private Weigher<? super K, ? super V> weigher;

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

  /** 异步任务执行器（用于 getAsync、自动刷新等异步操作） */
  private Executor taskExecutor;

  /** 缓存加载器 */
  private CacheLoader<K, V> loader;

  /** 缓存写入器 */
  private CacheWriter<? super K, ? super V> writer;

  /** 自定义过期策略 */
  private Expiry<? super K, ? super V> expiry;

  /** SWR 新鲜期（-1 表示不启用 SWR） */
  private long swrFreshPeriod = -1;

  /** SWR 陈旧期 */
  private long swrStalePeriod = -1;

  /** SWR 时间单位 */
  private TimeUnit swrTimeUnit;

  /** SWR 数据加载器（可选，未设置则使用 loader） */
  private CacheLoader<K, V> swrLoader;

  /** 是否启用 Write-Behind 模式 */
  private boolean writeBehindEnabled = false;

  /** Write-Behind 刷新间隔（毫秒） */
  private long writeBehindFlushIntervalMs = 5000;

  /** Write-Behind 批量大小 */
  private int writeBehindBatchSize = 100;

  /** Write-Behind 最大队列长度 */
  private int writeBehindMaxQueueSize = 10000;

  /** 是否启用内存感知淘汰 */
  private boolean memoryAwareEnabled = false;

  /** 内存告警阈值（0-1） */
  private double memoryWarnThreshold = 0.75;

  /** 内存淘汰阈值（0-1） */
  private double memoryEvictThreshold = 0.85;

  /** 内存临界清除阈值（0-1） */
  private double memoryCriticalThreshold = 0.95;

  /** 内存检查间隔（秒） */
  private long memoryCheckIntervalSeconds = 10;

  /** 弱引用键标志（与 type 正交，不覆盖 type） */
  private boolean weakKeysFlag = false;

  /** 弱引用值标志（与 type 正交，不覆盖 type） */
  private boolean weakValuesFlag = false;

  /** 软引用值标志（与 type 正交，不覆盖 type） */
  private boolean softValuesFlag = false;

  /** 私有构造函数，通过 YdszCache.newBuilder() 创建 */
  private CacheBuilder() {}

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
   * 创建 String-String 类型的 CacheBuilder 实例（快捷方式）
   *
   * @return CacheBuilder 实例
   */
  public static CacheBuilder<String, String> stringBuilder() {
    return new CacheBuilder<>();
  }

  /**
   * 设置缓存类型
   *
   * @param type 缓存类型
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
   * 设置最大权重
   *
   * @param maximumWeight 最大权重
   * @param weigher 权重计算器
   * @return this
   */
  public CacheBuilder<K, V> maximumWeight(
      long maximumWeight, Weigher<? super K, ? super V> weigher) {
    this.maximumWeight = maximumWeight;
    this.weigher = weigher;
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
   * <p>允许为每个缓存条目动态计算过期时间，而非使用全局固定的过期策略。 如果设置了此策略，将覆盖 expireAfterWrite 和 expireAfterAccess 的配置。
   *
   * @param expiry 自定义过期策略
   * @return this
   */
  public CacheBuilder<K, V> expireAfter(Expiry<? super K, ? super V> expiry) {
    this.expiry = expiry;
    return this;
  }

  /**
   * 启用 SWR (Stale-While-Revalidate) 模式
   *
   * <p>在新鲜期内直接返回缓存值；超过新鲜期但在陈旧期内返回旧值并异步刷新； 超过陈旧期则同步加载。需要同时设置 loader。
   *
   * @param freshPeriod 新鲜期
   * @param stalePeriod 陈旧期
   * @param unit 时间单位
   * @return this
   */
  public CacheBuilder<K, V> staleWhileRevalidate(
      long freshPeriod, long stalePeriod, TimeUnit unit) {
    this.swrFreshPeriod = freshPeriod;
    this.swrStalePeriod = stalePeriod;
    this.swrTimeUnit = unit;
    return this;
  }

  /**
   * 设置 SWR 数据加载器（可选，默认使用 loader）
   *
   * @param swrLoader SWR 数据加载器
   * @return this
   */
  public CacheBuilder<K, V> swrLoader(CacheLoader<K, V> swrLoader) {
    this.swrLoader = swrLoader;
    return this;
  }

  /**
   * 启用 Write-Behind 模式
   *
   * <p>写入操作先更新缓存，然后异步批量写入后端存储。 需要同时设置 writer。
   *
   * @param flushIntervalMs 批量刷新间隔（毫秒）
   * @param batchSize 每批最大写入数量
   * @param maxQueueSize 最大队列长度
   * @return this
   */
  public CacheBuilder<K, V> writeBehind(
      long flushIntervalMs, int batchSize, int maxQueueSize) {
    this.writeBehindEnabled = true;
    this.writeBehindFlushIntervalMs = flushIntervalMs;
    this.writeBehindBatchSize = batchSize;
    this.writeBehindMaxQueueSize = maxQueueSize;
    return this;
  }

  /**
   * 启用 Write-Behind 模式（默认参数）
   *
   * @return this
   */
  public CacheBuilder<K, V> writeBehind() {
    return writeBehind(5000, 100, 10000);
  }

  /**
   * 启用内存感知淘汰
   *
   * <p>当 JVM 堆内存使用率超过阈值时自动淘汰缓存条目。
   *
   * @param warnThreshold 告警阈值（0-1）
   * @param evictThreshold 淘汰阈值（0-1）
   * @param criticalThreshold 临界清除阈值（0-1）
   * @param checkIntervalSeconds 检查间隔（秒）
   * @return this
   */
  public CacheBuilder<K, V> memoryAware(
      double warnThreshold,
      double evictThreshold,
      double criticalThreshold,
      long checkIntervalSeconds) {
    this.memoryAwareEnabled = true;
    this.memoryWarnThreshold = warnThreshold;
    this.memoryEvictThreshold = evictThreshold;
    this.memoryCriticalThreshold = criticalThreshold;
    this.memoryCheckIntervalSeconds = checkIntervalSeconds;
    return this;
  }

  /**
   * 启用内存感知淘汰（默认阈值）
   *
   * @return this
   */
  public CacheBuilder<K, V> memoryAware() {
    return memoryAware(0.75, 0.85, 0.95, 10);
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
   * <p>用于异步加载缓存值（getAsync 方法）和自动刷新任务。 如果未设置，将使用默认线程池。
   *
   * @param executor 异步任务执行器
   * @return this
   */
  public CacheBuilder<K, V> executor(Executor executor) {
    this.taskExecutor = executor;
    return this;
  }

  /**
   * 设置并发级别
   *
   * <p>估算并发修改的线程数，用于优化锁段数量。 默认值为 32，如果设置为 1，则不使用分段锁。
   *
   * @param concurrencyLevel 并发级别
   * @return this
   */
  public CacheBuilder<K, V> concurrencyLevel(int concurrencyLevel) {
    this.stripes = Math.max(1, concurrencyLevel);
    return this;
  }

  /**
   * 设置缓存加载器（仅 LOADING 类型）
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
   * <p>将 Function 包装为 CacheLoader，等价于 {@code loader(CacheLoader.from(function))}。
   * 如果需要批量加载或异步加载能力，请直接使用 {@link #loader(CacheLoader)}。
   *
   * @param loader 缓存加载器函数
   * @return this
   */
  public CacheBuilder<K, V> loaderFrom(Function<? super K, ? extends V> loader) {
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
   * 使用弱引用键存储缓存键
   *
   * <p>键如果没有被其他地方引用，可以被 GC 回收。 过期策略通过 ExpirableCache 装饰器与引用缓存正交叠加。
   *
   * @return this
   */
  public CacheBuilder<K, V> weakKeys() {
    this.weakKeysFlag = true;
    return this;
  }

  /**
   * 使用弱引用值存储缓存值
   *
   * <p>值如果没有被其他地方引用，可以被 GC 回收。 过期策略通过 ExpirableCache 装饰器与引用缓存正交叠加。
   *
   * @return this
   */
  public CacheBuilder<K, V> weakValues() {
    this.weakValuesFlag = true;
    return this;
  }

  /**
   * 使用软引用值存储缓存值
   *
   * <p>值在 JVM 内存不足时可以被 GC 回收。 过期策略通过 ExpirableCache 装饰器与引用缓存正交叠加。
   *
   * @return this
   */
  public CacheBuilder<K, V> softValues() {
    this.softValuesFlag = true;
    return this;
  }

  /**
   * 构建缓存实例
   *
   * <p>构建顺序（装饰器叠加，正交组合）：
   *
   * <ol>
   *   <li>创建基础淘汰缓存（LRU/TINYLFU/STRIPED 等）或引用缓存（WEAK/SOFT）
   *   <li>叠加过期装饰器 ExpirableCache（如启用了 expireAfterWrite/Access 或 Expiry）
   *   <li>叠加内存感知淘汰装饰器 MemoryAwareEvictionCache（如启用）
   *   <li>叠加 SWR 装饰器 SwrCacheDecorator（如启用）
   *   <li>叠加写策略装饰器 WriteThroughCache 或 WriteBehindCache（如启用）
   *   <li>添加删除监听器
   * </ol>
   *
   * @return 缓存实例
   */
  public Cache<K, V> build() {
    validate();
    Cache<K, V> cache = createBaseCache();
    return applyDecorators(cache);
  }

  /**
   * 应用装饰器栈到基础缓存实例
   *
   * <p>装饰器叠加顺序：
   *
   * <ol>
   *   <li>过期策略装饰器 ExpirableCache
   *   <li>内存感知淘汰装饰器 MemoryAwareEvictionCache
   *   <li>SWR 装饰器 SwrCacheDecorator
   *   <li>写策略装饰器 WriteThroughCache / WriteBehindCache
   *   <li>删除监听器
   * </ol>
   *
   * @param cache 基础缓存实例
   * @return 装饰后的缓存实例
   */
  private Cache<K, V> applyDecorators(Cache<K, V> cache) {
    // 过期策略装饰器叠加（与淘汰策略正交）
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
      cache = new ExpirableCache<>(cache, writeNanos, accessNanos, expiry, 60);
    }

    // 内存感知淘汰装饰器
    if (memoryAwareEnabled) {
      cache =
          new MemoryAwareEvictionCache<>(
              cache,
              memoryWarnThreshold,
              memoryEvictThreshold,
              memoryCriticalThreshold,
              memoryCheckIntervalSeconds);
    }

    // SWR 装饰器
    if (swrFreshPeriod > 0 && swrStalePeriod > 0 && swrTimeUnit != null) {
      CacheLoader<K, V> effectiveLoader = swrLoader != null ? swrLoader : loader;
      if (effectiveLoader == null) {
        throw new IllegalStateException("loader or swrLoader must be set for SWR mode");
      }
      cache =
          new SwrCacheDecorator<>(
              cache,
              effectiveLoader,
              swrFreshPeriod,
              swrStalePeriod,
              swrTimeUnit,
              taskExecutor != null ? taskExecutor : listenerExecutor);
    }

    // 写策略装饰器：WriteBehind 优先于 WriteThrough
    if (writeBehindEnabled) {
      if (writer == null) {
        throw new IllegalStateException("writer must be set for Write-Behind mode");
      }
      cache =
          new WriteBehindCache<>(
              cache,
              writer,
              writeBehindFlushIntervalMs,
              writeBehindBatchSize,
              writeBehindMaxQueueSize);
    } else if (writer != null) {
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
    if (maximumWeight == 0) {
      throw new IllegalArgumentException(
          "maximumWeight must be greater than 0, or -1 for unlimited");
    }
    if (type == CacheType.WEIGHTED && weigher == null) {
      throw new IllegalStateException("weigher must be set for WEIGHTED cache type");
    }
    if (stripes < 1) {
      throw new IllegalArgumentException("stripes must be at least 1");
    }
    if (initialCapacity < 0) {
      throw new IllegalArgumentException("initialCapacity must be >= 0");
    }
    // 不允许同时设置 expireAfterWrite 和 expireAfterAccess（避免混淆）
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

    // ENHANCED_LOADING 类型已在 createBaseCache 中创建 EnhancedLoadingCache
    if (baseCache instanceof LoadingCache) {
      // 仍然需要应用装饰器（过期、内存感知等）
      // 但 LoadingCache 装饰后的返回类型为 Cache，无法直接返回
      // 因此对已包含 LoadingCache 的情况，直接返回（装饰器由用户手动叠加）
      return (LoadingCache<K, V>) baseCache;
    }

    // 先应用装饰器（过期、内存感知、SWR、写策略等），再包装为 LoadingCache
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
   * 构建异步缓存实例
   *
   * <p>所有操作（get/put/remove 等）均返回 {@link java.util.concurrent.CompletableFuture}，
   * 适合在响应式编程和异步 IO 场景中使用。底层默认使用 TINYLFU 淘汰策略。
   *
   * <p>使用示例：
   *
   * <pre>{@code
   * AsyncCache<String, User> cache = YdszCache.newBuilder()
   *     .maximumSize(10000)
   *     .executor(executor)
   *     .buildAsync();
   *
   * CompletableFuture<User> user = cache.get("1", key -> loadAsync(key));
   * }</pre>
   *
   * @return 异步缓存实例
   */
  public AsyncCache<K, V> buildAsync() {
    validate();
    Cache<K, V> cache = createBaseCache();
    cache = applyDecorators(cache);
    return new AsyncCacheAdapter<>(cache, taskExecutor != null ? taskExecutor : listenerExecutor);
  }

  /**
   * 创建基础缓存实例
   *
   * <p>如果设置了弱/软引用标志，优先创建引用缓存。 否则按 type 创建对应淘汰策略缓存。
   * 过期策略不在本方法处理，由 build() 中的 ExpirableCache 装饰器叠加。
   *
   * @return 基础缓存实例
   */
  private Cache<K, V> createBaseCache() {
    int effectiveSize = maximumSize > 0 ? (int) maximumSize : 1000;

    // 引用缓存优先（如果设置了引用标志）
    if (weakKeysFlag) {
      return new WeakKeyCache<>();
    }
    if (weakValuesFlag) {
      return new WeakValueCache<>();
    }
    if (softValuesFlag) {
      return new SoftValueCache<>();
    }

    switch (type) {
      case LRU:
        return new LRUCache<>(effectiveSize, initialCapacity);

      case LFU:
        return new LFUCache<>(effectiveSize, 16);

      case TINYLFU:
        if (maximumSize > 0) {
          return new WindowTinyLFUCache<K, V>((int) maximumSize, stripes);
        } else {
          return new WindowTinyLFUCache<K, V>(initialCapacity, stripes);
        }

      case WEIGHTED:
        if (weigher == null) {
          throw new IllegalStateException("weigher must be set for weighted cache");
        }
        if (maximumWeight <= 0) {
          throw new IllegalStateException("maximumWeight must be set for weighted cache");
        }
        return new WeightedCache<>(maximumWeight, initialCapacity, weigher);

      case CONCURRENT:
        return new ConcurrentCache<>(initialCapacity);

      case STRIPED:
        if (maximumSize > 0) {
          return new StripedConcurrentCache<K, V>((int) maximumSize, stripes);
        } else {
          return new StripedConcurrentCache<K, V>(initialCapacity, stripes);
        }

      case ENHANCED_LOADING:
        if (loader == null) {
          throw new IllegalStateException("loader must be set for ENHANCED_LOADING cache");
        }
        if (maximumSize <= 0) {
          throw new IllegalStateException("maximumSize must be set for ENHANCED_LOADING cache");
        }
        long elcRefreshDuration = refreshAfterWriteDuration > 0 ? refreshAfterWriteDuration : 0;
        TimeUnit elcRefreshUnit =
            elcRefreshDuration > 0
                ? (refreshAfterWriteUnit != null ? refreshAfterWriteUnit : TimeUnit.MILLISECONDS)
                : TimeUnit.NANOSECONDS;
        return EnhancedLoadingCache.create(
            new StripedConcurrentCache<K, V>((int) maximumSize, stripes),
            loader,
            taskExecutor != null ? taskExecutor : listenerExecutor,
            elcRefreshDuration,
            elcRefreshUnit,
            null,
            recordStats);

      case ASYNC:
        // ASYNC 类型作为 buildAsync() 的基础缓存，使用 TINYLFU 底层策略
        if (maximumSize > 0) {
          return new WindowTinyLFUCache<K, V>((int) maximumSize, stripes);
        } else {
          return new WindowTinyLFUCache<K, V>(initialCapacity, stripes);
        }

      default:
        throw new IllegalStateException("Unknown cache type: " + type);
    }
  }
}
