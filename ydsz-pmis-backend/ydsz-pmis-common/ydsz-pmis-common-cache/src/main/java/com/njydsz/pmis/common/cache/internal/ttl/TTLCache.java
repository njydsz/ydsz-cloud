package com.njydsz.pmis.common.cache.internal.ttl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.cache.internal.AbstractCache;
import com.njydsz.pmis.common.cache.listener.RemovalCause;
import com.njydsz.pmis.common.cache.support.CacheLoader;
import com.njydsz.pmis.common.cache.support.TTLMode;

/**
 * TTL（Time To Live）缓存实现 - 支持过期时间的缓存
 *
 * @deprecated 使用 {@link com.njydsz.pmis.common.cache.internal.decorator.ExpirableCache} 装饰器替代。
 *     通过 {@code builder.expireAfterWrite(duration, unit)} 配置过期策略，叠加在任意淘汰策略缓存上。
 *     <pre>{@code
 *     Cache<String, V> cache = LocalCache.newBuilder()
 *         .maximumSize(1000)
 *         .expireAfterWrite(5, TimeUnit.MINUTES)
 *         .build();
 *     }</pre>
 *
 * <p>核心特性：
 *
 * <ul>
 *   <li>过期时间支持：支持基于写入时间或访问时间的过期策略
 *   <li>自动清理：后台线程定期清理过期缓存项
 *   <li>线程安全：使用 ConcurrentHashMap 保证并发安全
 *   <li>双模式支持：WRITE 模式（写入计时）、ACCESS 模式（访问计时）
 *   <li>延迟过期检查：避免频繁 System.currentTimeMillis 调用
 * </ul>
 *
 * <p>工作原理：
 *
 * <ol>
 *   <li>每个缓存项维护一个过期时间戳
 *   <li>WRITE 模式：写入时计算过期时间
 *   <li>ACCESS 模式：每次访问时刷新过期时间
 *   <li>后台线程定期扫描并移除过期项
 * </ol>
 *
 * <p>适用场景：
 *
 * <ul>
 *   <li>会话缓存、令牌缓存
 *   <li>短期数据缓存（如天气预报、股票数据）
 *   <li>需要自动过期的业务数据
 * </ul>
 *
 * <p>注意事项：
 *
 * <ul>
 *   <li>后台清理线程会消耗少量资源
 *   <li>过期项不会立即删除，下次访问时才会被清除
 *   <li>建议设置合理的清理间隔
 * </ul>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @since 1.0.0
 * 
 */
@Deprecated
public class TTLCache<K, V> extends AbstractCache<K, V> implements AutoCloseable {

  /** 日志记录器 */
  private static final Logger log = LoggerFactory.getLogger(TTLCache.class);

  /** 底层并发存储映射 */
  private final ConcurrentMap<K, CacheEntry<V>> map;

  /** TTL 毫秒数 */
  private final long ttlMillis;

  private final TTLMode mode;

  private final long refreshAfterWriteMillis;

  private final CacheLoader<K, V> loader;

  private final Executor refreshExecutor;

  private final Set<K> refreshingKeys = ConcurrentHashMap.newKeySet();

  /** 全局共享过期清理调度器（单线程，避免任务堆积） */
  private static final ScheduledExecutorService SHARED_CLEANER =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "TTL-Cache-Shared-Cleaner");
            t.setDaemon(true);
            return t;
          });

  /** 全局共享刷新执行器（替代每实例创建线程池） */
  private static final Executor SHARED_REFRESH_EXECUTOR =
      Executors.newCachedThreadPool(
          r -> {
            Thread t = new Thread(r, "TTL-Cache-Shared-Refresh");
            t.setDaemon(true);
            return t;
          });

  /** 过期清理调度器引用（可能为共享实例） */
  private final ScheduledExecutorService cleaner;

  /** 是否使用共享调度器 */
  private final boolean useSharedCleaner;

  /** 过期清理标记（避免频繁清理） */
  private volatile long lastCleanupTime = 0;

  /** 清理时间间隔阈值（纳秒） 避免每次访问都触发清理 */
  private static final long CLEANUP_INTERVAL_NANOS = 1_000_000L;

  /** 默认清理间隔（秒） */
  private static final long DEFAULT_CLEANUP_INTERVAL_SECONDS = 60;

  /** 时间戳缓存有效期（毫秒） */
  private static final long TIME_CACHE_TTL_MS = 50;

  /** 是否启用统计 */
  private final boolean recordStats;

  /** 当前时间戳缓存 优化：使用 volatile 读取 + 简单更新，减少写竞争 */
  private volatile long currentTimeCache = System.currentTimeMillis();

  /**
   * 创建 TTL 缓存（默认写入计时模式，使用共享调度器）
   *
   * @param duration 时长
   * @param unit 时间单位
   * @param <K> 键类型
   * @param <V> 值类型
   * @return TTL 缓存实例
   */
  public static <K, V> TTLCache<K, V> create(long duration, TimeUnit unit) {
    return new TTLCache<>(
        duration,
        unit,
        TTLMode.WRITE,
        true,
        true,
        DEFAULT_CLEANUP_INTERVAL_SECONDS,
        0,
        null,
        null,
        false);
  }

  /**
   * 创建 TTL 缓存
   *
   * @param duration 时长
   * @param unit 时间单位
   * @param mode 过期模式
   * @param <K> 键类型
   * @param <V> 值类型
   * @return TTL 缓存实例
   */
  public static <K, V> TTLCache<K, V> create(long duration, TimeUnit unit, TTLMode mode) {
    return new TTLCache<>(
        duration, unit, mode, true, true, DEFAULT_CLEANUP_INTERVAL_SECONDS, 0, null, null, false);
  }

  /**
   * 创建 TTL 缓存（完整参数）
   *
   * <p>使用静态工厂方法替代构造函数，避免 {@code this} 在构造完成前逃逸。 过期清理调度在对象完全构造后启动，确保线程安全。
   *
   * @param duration 时长
   * @param unit 时间单位
   * @param mode 过期模式
   * @param recordStats 是否启用统计
   * @param useSharedCleaner 是否使用共享清理器
   * @param cleanupInterval 清理间隔（秒）
   * @param refreshAfterWriteMillis 写入后刷新间隔（毫秒）
   * @param loader 缓存加载器
   * @param refreshExecutor 刷新执行器
   * @param <K> 键类型
   * @param <V> 值类型
   * @return TTL 缓存实例
   */
  public static <K, V> TTLCache<K, V> create(
      long duration,
      TimeUnit unit,
      TTLMode mode,
      boolean recordStats,
      boolean useSharedCleaner,
      long cleanupInterval,
      long refreshAfterWriteMillis,
      CacheLoader<K, V> loader,
      Executor refreshExecutor) {
    TTLCache<K, V> instance =
        new TTLCache<>(
            duration,
            unit,
            mode,
            recordStats,
            useSharedCleaner,
            cleanupInterval,
            refreshAfterWriteMillis,
            loader,
            refreshExecutor,
            false);
    instance.startCleanup(cleanupInterval);
    return instance;
  }

  /**
   * 创建 TTL 缓存（默认写入计时模式，使用共享调度器）
   *
   * @param duration 时长
   * @param unit 时间单位
   * @deprecated 使用 {@link #create(long, TimeUnit)} 替代
   */
  @Deprecated
    public TTLCache(long duration, TimeUnit unit) {
    this(duration, unit, TTLMode.WRITE, true, true, DEFAULT_CLEANUP_INTERVAL_SECONDS);
  }

  /**
   * @deprecated 使用 {@link #create(long, TimeUnit, TTLMode)} 替代
   */
  @Deprecated
    public TTLCache(long duration, TimeUnit unit, TTLMode mode) {
    this(duration, unit, mode, true, true, DEFAULT_CLEANUP_INTERVAL_SECONDS);
  }

  /**
   * @deprecated 使用对应的 {@code create} 工厂方法替代
   */
  @Deprecated
    public TTLCache(long duration, TimeUnit unit, TTLMode mode, boolean recordStats) {
    this(duration, unit, mode, recordStats, true, DEFAULT_CLEANUP_INTERVAL_SECONDS);
  }

  /**
   * @deprecated 使用对应的 {@code create} 工厂方法替代
   */
  @Deprecated
    public TTLCache(
      long duration, TimeUnit unit, TTLMode mode, boolean recordStats, boolean useSharedCleaner) {
    this(duration, unit, mode, recordStats, useSharedCleaner, DEFAULT_CLEANUP_INTERVAL_SECONDS);
  }

  /**
   * @deprecated 使用对应的 {@code create} 工厂方法替代
   */
  @Deprecated
    public TTLCache(
      long duration,
      TimeUnit unit,
      TTLMode mode,
      boolean recordStats,
      boolean useSharedCleaner,
      long cleanupInterval) {
    this(duration, unit, mode, recordStats, useSharedCleaner, cleanupInterval, 0, null, null);
  }

  /**
   * @deprecated 使用 {@link #create(long, TimeUnit, TTLMode, boolean, boolean, long, long,
   *     CacheLoader, Executor)} 替代
   */
  @Deprecated
    public TTLCache(
      long duration,
      TimeUnit unit,
      TTLMode mode,
      boolean recordStats,
      boolean useSharedCleaner,
      long cleanupInterval,
      long refreshAfterWriteMillis,
      CacheLoader<K, V> loader,
      Executor refreshExecutor) {
    this(
        duration,
        unit,
        mode,
        recordStats,
        useSharedCleaner,
        cleanupInterval,
        refreshAfterWriteMillis,
        loader,
        refreshExecutor,
        true);
  }

  /**
   * 内部构造函数
   *
   * @param scheduleCleanup 是否在构造时立即调度清理任务（仅 deprecated 公开构造函数传 true， 工厂方法传 false，由工厂方法在构造完成后调度）
   */
  private TTLCache(
      long duration,
      TimeUnit unit,
      TTLMode mode,
      boolean recordStats,
      boolean useSharedCleaner,
      long cleanupInterval,
      long refreshAfterWriteMillis,
      CacheLoader<K, V> loader,
      Executor refreshExecutor,
      boolean scheduleCleanup) {
    this.ttlMillis = unit.toMillis(duration);
    this.mode = mode;
    this.refreshAfterWriteMillis = refreshAfterWriteMillis;
    this.loader = loader;
    this.refreshExecutor = refreshExecutor != null ? refreshExecutor : SHARED_REFRESH_EXECUTOR;
    this.map = new ConcurrentHashMap<>();
    this.recordStats = recordStats;
    this.useSharedCleaner = useSharedCleaner;
    if (useSharedCleaner) {
      this.cleaner = SHARED_CLEANER;
    } else {
      this.cleaner =
          Executors.newSingleThreadScheduledExecutor(
              r -> {
                Thread t = new Thread(r, "TTL-Cache-Cleaner-" + System.identityHashCode(this));
                t.setDaemon(true);
                return t;
              });
    }
    if (scheduleCleanup) {
      startCleanup(cleanupInterval);
    }
    log.info(
        "TTL 缓存已创建，duration={}, unit={}, mode={}, recordStats={}, sharedCleaner={}, cleanupInterval={}s",
        duration,
        unit,
        mode,
        recordStats,
        useSharedCleaner,
        cleanupInterval);
  }

  /** 启动过期清理调度任务 */
  private void startCleanup(long cleanupInterval) {
    cleaner.scheduleAtFixedRate(this::cleanup, cleanupInterval, cleanupInterval, TimeUnit.SECONDS);
  }

  /**
   * 获取当前时间（带缓存优化）
   *
   * <p>减少频繁调用 System.currentTimeMillis，通过 CAS 更新避免写竞争
   *
   * @return 当前时间戳（毫秒）
   */
  private long currentTimeMillis() {
    long now = System.currentTimeMillis();
    long cached = currentTimeCache;
    if (now - cached > TIME_CACHE_TTL_MS) {
      currentTimeCache = now;
    }
    return currentTimeCache;
  }

  @Override
  public V getIfPresent(K key) {
    CacheEntry<V> entry = map.get(key);
    if (entry == null) {
      if (recordStats) {
        missCount.add(1);
      }
      return null;
    }

    if (entry.isExpired(currentTimeMillis())) {
      map.remove(key, entry);
      notifyRemoval(key, entry.value, RemovalCause.EXPIRED);
      if (recordStats) {
        missCount.add(1);
      }
      return null;
    }

    if (mode == TTLMode.ACCESS) {
      entry.refreshExpiration(ttlMillis, currentTimeMillis());
    }

    if (refreshAfterWriteMillis > 0 && loader != null) {
      long now = currentTimeMillis();
      if (now - entry.writeTime >= refreshAfterWriteMillis && refreshingKeys.add(key)) {
        CompletableFuture.runAsync(
            () -> {
              try {
                V newValue = loader.load(key);
                if (newValue != null) {
                  CacheEntry<V> oldEntry = map.get(key);
                  if (oldEntry != null) {
                    notifyRemoval(key, oldEntry.value, RemovalCause.REPLACED);
                  }
                  map.put(key, new CacheEntry<>(newValue, ttlMillis, now));
                }
              } catch (Exception e) {
                log.debug("TTL 缓存异步刷新失败, key={}", key, e);
              } finally {
                refreshingKeys.remove(key);
              }
            },
            refreshExecutor);
      }
    }

    if (recordStats) {
      hitCount.increment();
    }
    return entry.value;
  }

  @Override
  public void put(K key, V value) {
    CacheEntry<V> oldEntry = map.put(key, new CacheEntry<>(value, ttlMillis, currentTimeMillis()));
    if (oldEntry != null) {
      notifyRemoval(key, oldEntry.value, RemovalCause.REPLACED);
    }
  }

  @Override
  public V remove(K key) {
    CacheEntry<V> entry = map.remove(key);
    if (entry != null) {
      notifyRemoval(key, entry.value, RemovalCause.EXPLICIT);
      return entry.value;
    }
    return null;
  }

  @Override
  public void clear() {
    map.forEach((key, entry) -> notifyRemoval(key, entry.value, RemovalCause.EXPLICIT));
    map.clear();
  }

  @Override
  public long estimatedSize() {
    maybeCleanup();
    return map.size();
  }

  @Override
  public boolean containsKey(K key) {
    CacheEntry<V> entry = map.get(key);
    if (entry == null) {
      return false;
    }
    if (entry.isExpired(currentTimeMillis())) {
      map.remove(key, entry);
      notifyRemoval(key, entry.value, RemovalCause.EXPIRED);
      return false;
    }
    return true;
  }

  @Override
  public Set<K> keySet() {
    maybeCleanup();
    return map.keySet();
  }

  @Override
  public Collection<V> values() {
    maybeCleanup();
    List<V> list = new ArrayList<>();
    long now = currentTimeMillis();
    map.forEach(
        (key, entry) -> {
          if (!entry.isExpired(now)) {
            list.add(entry.value);
          }
        });
    return list;
  }

  /** 尝试清理（避免频繁清理） */
  private void maybeCleanup() {
    long now = System.nanoTime();
    if (now - lastCleanupTime > CLEANUP_INTERVAL_NANOS) {
      lastCleanupTime = now;
      cleanup();
    }
  }

  /** 清理所有过期条目 */
  private void cleanup() {
    long now = currentTimeMillis();
    int[] removed = {0};
    map.entrySet()
        .removeIf(
            entry -> {
              if (entry.getValue().expireTime < now) {
                notifyRemoval(entry.getKey(), entry.getValue().value, RemovalCause.EXPIRED);
                removed[0]++;
                return true;
              }
              return false;
            });
    if (removed[0] > 0) {
      log.debug("TTL 缓存清理完成，移除过期条目数={}", removed[0]);
    }
  }

  /**
   * 关闭缓存，释放后台清理线程
   *
   * <p>建议在应用关闭时调用，避免资源泄漏。
   *
   * <p>使用共享调度器时，仅取消当前缓存的定时任务，不关闭全局调度器。
   *
   * <p>支持 try-with-resources 模式。
   */
  @Override
  public void close() {
    shutdown();
  }

  /**
   * 关闭缓存，释放后台清理线程
   *
   * <p>建议在应用关闭时调用，避免资源泄漏。
   *
   * <p>使用共享调度器时，仅取消当前缓存的定时任务，不关闭全局调度器。
   */
  public void shutdown() {
    log.info("TTL 缓存关闭中... (sharedCleaner={})", useSharedCleaner);
    if (!useSharedCleaner && cleaner != null) {
      cleaner.shutdown();
      try {
        if (!cleaner.awaitTermination(5, TimeUnit.SECONDS)) {
          cleaner.shutdownNow();
        }
      } catch (InterruptedException e) {
        cleaner.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
    log.info("TTL 缓存已关闭");
  }

  /**
   * 注册 JVM 关闭钩子，自动关闭缓存
   *
   * <p>当 JVM 退出时自动释放后台清理线程
   */
  public void setShutdownHook() {
    Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
  }

  /**
   * TTL 缓存条目
   *
   * <p>维护值和过期时间信息
   */
  private static class CacheEntry<V> {
    final V value;
    volatile long expireTime;
    final long writeTime;

    CacheEntry(V value, long ttlMillis, long now) {
      this.value = value;
      this.expireTime = now + ttlMillis;
      this.writeTime = now;
    }

    boolean isExpired(long now) {
      return now > expireTime;
    }

    void refreshExpiration(long ttlMillis, long now) {
      this.expireTime = now + ttlMillis;
    }
  }
}
