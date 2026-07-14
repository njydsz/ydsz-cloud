package com.njydsz.pmis.common.cache.builder;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.cache.api.Cache;
import com.njydsz.pmis.common.cache.api.LoadingCache;
import com.njydsz.pmis.common.cache.internal.concurrent.ConcurrentCache;
import com.njydsz.pmis.common.cache.internal.concurrent.StripedConcurrentCache;
import com.njydsz.pmis.common.cache.internal.decorator.ExpirableCache;
import com.njydsz.pmis.common.cache.internal.decorator.WriteThroughCache;
import com.njydsz.pmis.common.cache.internal.lfu.LFUCache;
import com.njydsz.pmis.common.cache.internal.loading.EnhancedLoadingCache;
import com.njydsz.pmis.common.cache.internal.lru.LRUCache;
import com.njydsz.pmis.common.cache.internal.reference.SoftValueCache;
import com.njydsz.pmis.common.cache.internal.reference.WeakKeyCache;
import com.njydsz.pmis.common.cache.internal.reference.WeakValueCache;
import com.njydsz.pmis.common.cache.internal.tinylfu.WTinyLFUCache;
import com.njydsz.pmis.common.cache.internal.tinylfu.WindowTinyLFUCache;
import com.njydsz.pmis.common.cache.internal.ttl.TTLCache;
import com.njydsz.pmis.common.cache.internal.weighted.WeightedCache;
import com.njydsz.pmis.common.cache.listener.RemovalListener;
import com.njydsz.pmis.common.cache.support.CacheLoader;
import com.njydsz.pmis.common.cache.support.CacheWriter;
import com.njydsz.pmis.common.cache.support.Expiry;
import com.njydsz.pmis.common.cache.support.TTLMode;
import com.njydsz.pmis.common.cache.support.Weigher;

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
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public final class CacheBuilder<K, V> {

  private static final Logger log = LoggerFactory.getLogger(CacheBuilder.class);

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
   *   <li>叠加写穿透装饰器 WriteThroughCache（如启用）
   *   <li>添加删除监听器
   * </ol>
   *
   * @return 缓存实例
   */
  public Cache<K, V> build() {
    validate();
    Cache<K, V> cache = createBaseCache();

    // 过期策略装饰器叠加（与淘汰策略正交）
    boolean hasExpiration =
        expireAfterWriteDuration > 0 || expireAfterAccessDuration > 0 || expiry != null;
    if (hasExpiration && !(cache instanceof TTLCache) && !(cache instanceof EnhancedLoadingCache)) {
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

    // 写穿透装饰器
    if (writer != null) {
      cache = new WriteThroughCache<>(cache, writer);
    }

    if (removalListener != null) {
      cache.addListener(removalListener);
    }

    return cache;
  }

  public WTinyLFUCache<K, V> buildWTinyLFU() {
    int effectiveSize = maximumSize > 0 ? (int) maximumSize : 1000;
    WTinyLFUCache<K, V> cache = new WTinyLFUCache<>(effectiveSize);
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

    Cache<K, V> baseCache = createBaseCache();
    if (baseCache instanceof LoadingCache) {
      return (LoadingCache<K, V>) baseCache;
    }

    long effectiveRefreshDuration = refreshAfterWriteDuration > 0 ? refreshAfterWriteDuration : 0;
    TimeUnit effectiveRefreshUnit =
        effectiveRefreshDuration > 0
            ? (refreshAfterWriteUnit != null ? refreshAfterWriteUnit : TimeUnit.MILLISECONDS)
            : TimeUnit.NANOSECONDS;

    return EnhancedLoadingCache.create(
        baseCache,
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

      case TTL:
        if (expireAfterWriteDuration > 0) {
          return TTLCache.create(
              expireAfterWriteDuration,
              expireAfterWriteUnit,
              TTLMode.WRITE,
              recordStats,
              true,
              60,
              refreshAfterWriteDuration > 0
                  ? refreshAfterWriteUnit.toMillis(refreshAfterWriteDuration)
                  : 0,
              loader,
              taskExecutor != null ? taskExecutor : listenerExecutor);
        } else if (expireAfterAccessDuration > 0) {
          return TTLCache.create(
              expireAfterAccessDuration,
              expireAfterAccessUnit,
              TTLMode.ACCESS,
              recordStats,
              true,
              60,
              refreshAfterWriteDuration > 0
                  ? refreshAfterWriteUnit.toMillis(refreshAfterWriteDuration)
                  : 0,
              loader,
              taskExecutor != null ? taskExecutor : listenerExecutor);
        } else {
          return TTLCache.create(5, TimeUnit.MINUTES, TTLMode.WRITE);
        }

      case WEIGHTED:
        if (weigher == null) {
          throw new IllegalStateException("weigher must be set for weighted cache");
        }
        if (maximumWeight <= 0) {
          throw new IllegalStateException("maximumWeight must be set for weighted cache");
        }
        return new WeightedCache<>(maximumWeight, initialCapacity, weigher);

      case WEAK_KEY:
        return new WeakKeyCache<>();

      case WEAK_VALUE:
        return new WeakValueCache<>();

      case SOFT_VALUE:
        return new SoftValueCache<>();

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

      default:
        throw new IllegalStateException("Unknown cache type: " + type);
    }
  }
}
