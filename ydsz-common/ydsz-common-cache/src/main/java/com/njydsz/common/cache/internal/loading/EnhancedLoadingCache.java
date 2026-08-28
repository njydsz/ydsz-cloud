package com.njydsz.common.cache.internal.loading;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.api.LoadingCache;
import com.njydsz.common.cache.internal.AbstractCache;
import com.njydsz.common.cache.listener.RemovalCause;
import com.njydsz.common.cache.stats.CacheStats;
import com.njydsz.common.cache.support.AsyncFunction;
import com.njydsz.common.cache.support.CacheLoader;
import com.njydsz.common.cache.support.CacheThreadPoolManager;

/**
 * 增强版异步加载缓存实现 - 支持 CacheLoader、自动刷新和完整统计
 *
 * <p>继承 {@link AbstractCache}，复用命中/未命中计数、删除监听器等通用逻辑， 同时实现 {@link LoadingCache} 接口提供自动加载能力。
 *
 * <p>核心特性：
 *
 * <ul>
 *   <li>CacheLoader 集成：支持单键/批量加载
 *   <li>自动刷新：支持 refreshAfterWrite 自动刷新机制
 *   <li>防击穿：同一 key 的并发加载请求共享同一个 Future
 *   <li>防穿透：缓存加载失败时返回旧值或 null
 *   <li>完整统计：命中/未命中/加载成功/加载失败/加载时间
 *   <li>异步执行：支持自定义 Executor
 *   <li>刷新策略：支持周期性自动刷新
 * </ul>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author ydsz-team
 * @since 1.0.0
 */
public class EnhancedLoadingCache<K, V> extends AbstractCache<K, V>
    implements LoadingCache<K, V>, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(EnhancedLoadingCache.class);

  /**
   * 全局共享异步执行器（守护线程，不阻止 JVM 退出）
   *
   * <p>使用 CacheThreadPoolManager 统一管理线程池，避免 ForkJoinPool.commonPool() 污染。
   */
  private static final AtomicReference<ExecutorService> SHARED_EXECUTOR = new AtomicReference<>();

  /** 全局共享刷新调度器（守护线程，不阻止 JVM 退出） */
  private static final AtomicReference<ScheduledExecutorService> SHARED_REFRESH_SCHEDULER =
      new AtomicReference<>();

  /** 共享资源是否已关闭 */
  private static volatile boolean sharedResourcesShutdown = false;

  /** 获取共享异步执行器（懒加载，线程安全） */
  private static Executor getSharedExecutor() {
    if (sharedResourcesShutdown) {
      return Runnable::run;
    }
    ExecutorService executor = SHARED_EXECUTOR.get();
    if (executor != null) {
      return executor;
    }
    ExecutorService created =
        CacheThreadPoolManager.getInstance()
            .getOrCreatePool(
                "enhanced-loading-async",
                Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().availableProcessors() * 2);
    return SHARED_EXECUTOR.compareAndSet(null, created) ? created : SHARED_EXECUTOR.get();
  }

  /** 获取共享刷新调度器（懒加载，线程安全） */
  private static ScheduledExecutorService getSharedRefreshScheduler() {
    if (sharedResourcesShutdown) {
      return null;
    }
    ScheduledExecutorService scheduler = SHARED_REFRESH_SCHEDULER.get();
    if (scheduler != null) {
      return scheduler;
    }
    // CHECKSTYLE.OFF: RegexpSinglelineJava - 缓存刷新共享调度器，单线程固定，守护线程
    ScheduledThreadPoolExecutor exec =
        new ScheduledThreadPoolExecutor(
            1,
            r -> {
              Thread t = new Thread(r, "ydsz-cache-shared-refresher");
              t.setDaemon(true);
              t.setPriority(Thread.NORM_PRIORITY - 1);
              return t;
            });
    // CHECKSTYLE.ON: RegexpSinglelineJava
    exec.setRemoveOnCancelPolicy(true);
    return SHARED_REFRESH_SCHEDULER.compareAndSet(null, exec) ? exec : SHARED_REFRESH_SCHEDULER.get();
  }

  /**
   * 关闭所有共享资源（由 Spring 生命周期管理调用）
   *
   * <p>调用后所有使用共享执行器的 EnhancedLoadingCache 实例将无法再使用自动刷新功能。 建议在应用关闭阶段调用。
   */
  public static void shutdownSharedResources() {
    sharedResourcesShutdown = true;
    ExecutorService exec = SHARED_EXECUTOR.getAndSet(null);
    if (exec != null) {
      exec.shutdown();
      try {
        if (!exec.awaitTermination(5, TimeUnit.SECONDS)) {
          exec.shutdownNow();
        }
      } catch (InterruptedException e) {
        exec.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
    ScheduledExecutorService scheduler = SHARED_REFRESH_SCHEDULER.getAndSet(null);
    if (scheduler != null) {
      scheduler.shutdown();
      try {
        if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
          scheduler.shutdownNow();
        }
      } catch (InterruptedException e) {
        scheduler.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
    LOG.info("EnhancedLoadingCache 共享资源已关闭");
  }

  /** 底层缓存 */
  private final Cache<K, V> cache;

  /** 缓存加载器 */
  private final CacheLoader<K, V> loader;

  /** 异步执行器 */
  private final Executor executor;

  /** 自动刷新调度器 */
  private final ScheduledExecutorService refreshScheduler;

  /** 自动刷新定时任务 Future（用于取消当前实例的定时任务） */
  private volatile ScheduledFuture<?> refreshFuture;

  /** 自动刷新间隔（纳秒），0 表示不自动刷新 */
  private final long refreshIntervalNanos;

  /** 是否启用统计 */
  private final boolean recordStats;

  /** 加载计数器 */
  private final LongAdder loadCount = new LongAdder();

  /** 加载成功计数器 */
  private final LongAdder loadSuccessCount = new LongAdder();

  /** 加载异常计数器 */
  private final LongAdder loadExceptionCount = new LongAdder();

  /** 总加载时间计数器（纳秒） */
  private final LongAdder totalLoadTimeNanos = new LongAdder();

  /** 待加载的 Future 缓存（防击穿） */
  private final Map<K, CompletableFuture<V>> pendingLoads = new ConcurrentHashMap<>();

  /** 最后刷新时间缓存（用于自动刷新） */
  private final ConcurrentHashMap<K, Long> lastRefreshTimes = new ConcurrentHashMap<>();

  /**
   * 创建增强版加载缓存（无自动刷新）
   *
   * @param cache 底层缓存
   * @param loader 缓存加载器
   * @param <K> 键类型
   * @param <V> 值类型
   * @return 缓存实例
   */
  public static <K, V> EnhancedLoadingCache<K, V> create(
      Cache<K, V> cache, CacheLoader<K, V> loader) {
    return new EnhancedLoadingCache<>(cache, loader, null, 0, TimeUnit.NANOSECONDS, true);
  }

  /**
   * 创建增强版加载缓存（完整参数）
   *
   * <p>使用静态工厂方法替代构造函数，避免 {@code this} 在构造完成前逃逸。 自动刷新调度在对象完全构造后启动，确保线程安全。
   *
   * @param cache 底层缓存
   * @param loader 缓存加载器
   * @param executor 异步执行器（可选）
   * @param refreshInterval 自动刷新间隔
   * @param refreshUnit 刷新间隔单位
   * @param recordStats 是否启用统计
   * @param <K> 键类型
   * @param <V> 值类型
   * @return 缓存实例
   */
  public static <K, V> EnhancedLoadingCache<K, V> create(
      Cache<K, V> cache,
      CacheLoader<K, V> loader,
      Executor executor,
      long refreshInterval,
      TimeUnit refreshUnit,
      boolean recordStats) {
    EnhancedLoadingCache<K, V> instance =
        new EnhancedLoadingCache<>(
            cache, loader, executor, refreshInterval, refreshUnit, recordStats);
    instance.scheduleAutoRefresh();
    return instance;
  }

  /** 内部构造函数 */
  private EnhancedLoadingCache(
      Cache<K, V> cache,
      CacheLoader<K, V> loader,
      Executor executor,
      long refreshInterval,
      TimeUnit refreshUnit,
      boolean recordStats) {
    this.cache = cache;
    this.loader = loader;
    this.executor = executor != null ? executor : getSharedExecutor();
    this.recordStats = recordStats;

    if (refreshInterval > 0 && refreshUnit != null) {
      this.refreshIntervalNanos = refreshUnit.toNanos(refreshInterval);
      this.refreshScheduler = getSharedRefreshScheduler();
    } else {
      this.refreshIntervalNanos = 0;
      this.refreshScheduler = null;
    }

    LOG.info(
        "增强版加载缓存已创建，cache={}, loader={}, refreshInterval={}, recordStats={}",
        cache.getClass().getSimpleName(),
        loader.getClass().getSimpleName(),
        refreshInterval > 0 ? refreshInterval + " " + refreshUnit : "禁用",
        recordStats);
  }

  /** 调度自动刷新任务 */
  private void scheduleAutoRefresh() {
    if (refreshScheduler == null || refreshIntervalNanos <= 0) {
      return;
    }

    long refreshIntervalMillis = TimeUnit.NANOSECONDS.toMillis(refreshIntervalNanos);
    refreshFuture =
        refreshScheduler.scheduleAtFixedRate(
            this::refreshAll, refreshIntervalMillis, refreshIntervalMillis, TimeUnit.MILLISECONDS);
    LOG.info("自动刷新已启用，间隔={}ms", refreshIntervalMillis);
  }

  /** 刷新所有缓存项 */
  private void refreshAll() {
    long now = System.nanoTime();
    Set<K> keys = cache.keySet();

    for (K key : keys) {
      Long lastRefresh = lastRefreshTimes.get(key);
      if (lastRefresh != null && (now - lastRefresh) >= refreshIntervalNanos) {
        refresh(key);
      }
    }
  }

  /**
   * 获取指定 key 的缓存值，不做加载。
   *
   * <p>命中时若开启了自动刷新且该 key 距上次刷新已超间隔，会异步触发一次刷新 但立即返回旧值（stale-while-revalidate 语义）；未命中返回 {@code null}，
   * 不触发任何加载。命中/未命中均计入统计。
   *
   * @param key 查询的键，不可为 {@code null}
   * @return 已缓存的值；未命中时返回 {@code null}
   */
  @Override
  public V getIfPresent(K key) {
    V value = cache.getIfPresent(key);
    if (value != null) {
      if (recordStats) {
        hitCount.increment();
      }

      if (refreshIntervalNanos > 0) {
        Long lastRefresh = lastRefreshTimes.get(key);
        if (lastRefresh == null || (System.nanoTime() - lastRefresh) >= refreshIntervalNanos) {
          refresh(key);
        }
      }

      return value;
    }

    if (recordStats) {
      missCount.increment();
    }

    return null;
  }

  /**
   * 获取缓存值，未命中时同步调用 {@link CacheLoader#load} 加载。
   *
   * <p>并发请求同一未命中 key 时，后续线程会等待首个线程的加载结果（防击穿）。 加载失败时返回缓存中的旧值（若存在），否则返回 {@code null}，不抛出异常。
   *
   * @param key 查询的键，不可为 {@code null}
   * @return 缓存值或同步加载后的值；加载失败且无旧值时返回 {@code null}
   */
  @Override
  public V get(K key) {
    V value = getIfPresent(key);
    if (value != null) {
      return value;
    }
    return loadSync(key);
  }

  /** 同步加载缓存项（带防击穿） */
  private V loadSync(K key) {
    CompletableFuture<V> future = new CompletableFuture<>();
    CompletableFuture<V> existing = pendingLoads.putIfAbsent(key, future);

    if (existing == null) {
      long startTime = System.nanoTime();
      try {
        if (recordStats) {
          loadCount.increment();
        }

        V value = loader.load(key);
        if (value != null) {
          cache.put(key, value);
          lastRefreshTimes.put(key, System.nanoTime());
          if (recordStats) {
            loadSuccessCount.increment();
          }
        }

        long elapsed = System.nanoTime() - startTime;
        if (recordStats) {
          totalLoadTimeNanos.add(elapsed);
        }

        future.complete(value);
        return value;
      } catch (Exception e) {
        if (recordStats) {
          loadExceptionCount.increment();
          long elapsed = System.nanoTime() - startTime;
          totalLoadTimeNanos.add(elapsed);
        }
        // 返回缓存中的旧值（如果存在），而非 null
        V oldValue = cache.getIfPresent(key);
        future.complete(oldValue);
        LOG.warn("缓存加载失败, key={}, 返回旧值={}", key, oldValue != null, e);
        return oldValue;
      } finally {
        pendingLoads.remove(key, future);
      }
    }

    try {
      return existing.get();
    } catch (Exception e) {
      if (recordStats) {
        loadExceptionCount.increment();
      }
      // 等待其他线程的加载结果失败，尝试返回缓存中的值
      return cache.getIfPresent(key);
    }
  }

  /**
   * 获取缓存值且不抛出任何异常。
   *
   * <p>与 {@link #get(Object)} 的区别：内部捕获并记录全部加载异常， 失败时返回 {@code null}，适合对失败容忍度高的调用场景。
   *
   * @param key 查询的键，不可为 {@code null}
   * @return 缓存值；加载失败时返回 {@code null}
   */
  @Override
  public V getUnchecked(K key) {
    try {
      return get(key);
    } catch (Exception e) {
      LOG.error("缓存加载异常, key={}", key, e);
      return null;
    }
  }

  /**
   * 异步获取缓存值，未命中时通过 {@link CacheLoader#loadAsync} 加载。
   *
   * <p>命中时返回已完成的 Future；未命中时返回加载 Future，加载成功后写入 底层缓存并更新刷新时间戳。加载失败时 Future 以 {@code null} 完成， 不传播异常。
   *
   * @param key 查询的键，不可为 {@code null}
   * @return 携带加载结果的 Future；加载失败时结果为 {@code null}
   */
  @Override
  public CompletableFuture<V> getAsync(K key) {
    V value = cache.getIfPresent(key);
    if (value != null) {
      if (recordStats) {
        hitCount.increment();
      }
      return CompletableFuture.completedFuture(value);
    }

    if (recordStats) {
      missCount.increment();
      loadCount.increment();
    }

    CompletableFuture<V> future = loader.loadAsync(key);
    return future
        .thenApply(
            v -> {
              if (v != null) {
                cache.put(key, v);
                lastRefreshTimes.put(key, System.nanoTime());
                if (recordStats) {
                  loadSuccessCount.increment();
                }
              }
              return v;
            })
        .exceptionally(
            e -> {
              if (recordStats) {
                loadExceptionCount.increment();
              }
              LOG.warn("异步缓存加载失败, key={}", key, e);
              return null;
            });
  }

  /**
   * 使用调用方提供的加载函数异步获取缓存值。
   *
   * <p>该重载不使用默认 {@link CacheLoader}，加载逻辑完全由入参 {@code loader} 决定；加载成功后同样写入底层缓存并更新刷新时间戳。
   *
   * @param key 查询的键，不可为 {@code null}
   * @param loader 本次调用的异步加载函数，不可为 {@code null}
   * @return 携带加载结果的 Future；加载失败时以 {@code null} 完成
   */
  @Override
  public CompletableFuture<V> getAsync(K key, AsyncFunction<K, V> loader) {
    V value = cache.getIfPresent(key);
    if (value != null) {
      if (recordStats) {
        hitCount.increment();
      }
      return CompletableFuture.completedFuture(value);
    }

    if (recordStats) {
      missCount.increment();
    }

    return loader
        .apply(key)
        .thenApply(
            v -> {
              if (v != null) {
                cache.put(key, v);
                lastRefreshTimes.put(key, System.nanoTime());
              }
              return v;
            });
  }

  /**
   * 批量获取多个 key 的缓存值，未命中的 key 通过 {@link CacheLoader#loadAll} 一次性加载。
   *
   * <p>先逐 key 查询命中项，汇总未命中的 key 集合后调用一次批量加载， 加载结果整体写入底层缓存。批量加载失败仅记录日志，不影响已命中的结果返回。
   *
   * @param keys 待查询的键集合，空集合返回空 map；为 {@code null} 时按空集合处理
   * @return key 到值的映射；批量加载失败时缺失的 key 不会出现在结果中
   */
  @Override
  public Map<K, V> getAll(Collection<K> keys) {
    if (keys == null || keys.isEmpty()) {
      return new HashMap<>(0);
    }

    Map<K, V> result = new HashMap<>(keys.size());
    List<K> missedKeys = new ArrayList<>(keys.size());

    for (K key : keys) {
      V value = cache.getIfPresent(key);
      if (value != null) {
        if (recordStats) {
          hitCount.increment();
        }
        result.put(key, value);
      } else {
        if (recordStats) {
          missCount.increment();
        }
        missedKeys.add(key);
      }
    }

    if (!missedKeys.isEmpty()) {
      try {
        long startTime = System.nanoTime();
        if (recordStats) {
          loadCount.increment();
        }

        Map<K, V> loaded = loader.loadAll(missedKeys);
        cache.putAll(loaded);
        result.putAll(loaded);

        loaded.keySet().forEach(k -> lastRefreshTimes.put(k, System.nanoTime()));

        long elapsed = System.nanoTime() - startTime;
        if (recordStats) {
          loadSuccessCount.add(loaded.size());
          totalLoadTimeNanos.add(elapsed);
        }
      } catch (Exception e) {
        if (recordStats) {
          loadExceptionCount.increment();
        }
        LOG.warn("批量缓存加载失败", e);
      }
    }

    return result;
  }

  /**
   * 异步批量加载多个 key，并整体写入底层缓存。
   *
   * <p>不做命中/未命中拆分，直接委托 {@link CacheLoader#loadAllAsync} 全量加载， 因此即使 key 已存在也会被重新加载覆盖。
   *
   * @param keys 待加载的键集合，空集合返回已完成的空 map Future
   * @return 携带加载结果映射的 Future
   */
  @Override
  public CompletableFuture<Map<K, V>> getAllAsync(Collection<K> keys) {
    if (keys == null || keys.isEmpty()) {
      return CompletableFuture.completedFuture(new HashMap<>(0));
    }

    return loader
        .loadAllAsync(keys)
        .thenApply(
            loaded -> {
              cache.putAll(loaded);
              loaded.keySet().forEach(k -> lastRefreshTimes.put(k, System.nanoTime()));
              if (recordStats) {
                hitCount.add(loaded.size());
                loadSuccessCount.add(loaded.size());
              }
              return loaded;
            });
  }

  /**
   * 异步刷新指定 key 的缓存值。
   *
   * <p>刷新在共享执行器上异步进行，不阻塞调用线程；刷新失败仅记录日志， 保留旧值。刷新成功后更新最后刷新时间戳，供自动刷新周期判定使用。
   *
   * @param key 待刷新的键，不可为 {@code null}
   */
  @Override
  public void refresh(K key) {
    CompletableFuture.runAsync(
        () -> {
          long startTime = System.nanoTime();
          try {
            if (recordStats) {
              loadCount.increment();
            }

            V value = loader.load(key);
            if (value != null) {
              cache.put(key, value);
              lastRefreshTimes.put(key, System.nanoTime());
              if (recordStats) {
                loadSuccessCount.increment();
                totalLoadTimeNanos.add(System.nanoTime() - startTime);
              }
            }
          } catch (Exception e) {
            if (recordStats) {
              loadExceptionCount.increment();
            }
            LOG.warn("刷新缓存失败, key={}", key, e);
          }
        },
        executor);
  }

  /**
   * 写入缓存并更新该 key 的最后刷新时间戳。
   *
   * <p>写入后自动刷新机制将以本次写入时刻作为刷新起点。
   *
   * @param key 写入的键，不可为 {@code null}
   * @param value 写入的值，允许为 {@code null}（按底层缓存契约处理）
   */
  @Override
  public void put(K key, V value) {
    cache.put(key, value);
    lastRefreshTimes.put(key, System.nanoTime());
  }

  /**
   * 移除指定 key，并触发删除监听器通知。
   *
   * <p>与纯缓存移除不同，本实现额外清理刷新时间戳记录， 且仅在原值非空时通知监听器。
   *
   * @param key 待移除的键，不可为 {@code null}
   * @return 被移除的旧值；key 原本不存在时返回 {@code null}
   */
  @Override
  public V remove(K key) {
    V value = cache.remove(key);
    lastRefreshTimes.remove(key);
    if (value != null) {
      notifyRemoval(key, value, RemovalCause.EXPLICIT);
    }
    return value;
  }

  /**
   * 清空全部缓存项与刷新时间戳记录。
   *
   * <p>清理刷新时间戳是为了避免旧 key 的时间记录残留导致后续刷新逻辑误判。
   */
  @Override
  public void clear() {
    cache.clear();
    lastRefreshTimes.clear();
  }

  /**
   * 返回当前缓存中的条目数。
   *
   * @return 已缓存条目的近似数量
   */
  @Override
  public long estimatedSize() {
    return cache.estimatedSize();
  }

  /**
   * 判断指定 key 是否已缓存。
   *
   * @param key 查询的键，不可为 {@code null}
   * @return true 表示该 key 存在于缓存中
   */
  @Override
  public boolean containsKey(K key) {
    return cache.containsKey(key);
  }

  /**
   * 返回当前缓存键的快照视图。
   *
   * @return 缓存键的 {@link Set}，可能为弱一致视图
   */
  @Override
  public Set<K> keySet() {
    return cache.keySet();
  }

  /**
   * 返回当前缓存值的集合视图。
   *
   * @return 缓存值的 {@link Collection}，可能为弱一致视图
   */
  @Override
  public Collection<V> values() {
    return cache.values();
  }

  /**
   * 计算缓存命中率。
   *
   * <p>未开启统计时固定返回 0.0；开启统计且无访问记录时返回 0.0。
   *
   * @return 命中次数 / (命中次数 + 未命中次数)，范围 [0, 1]
   */
  @Override
  public double getHitRate() {
    if (!recordStats) {
      return 0.0;
    }
    long total = hitCount.sum() + missCount.sum();
    return total == 0 ? 0.0 : (double) hitCount.sum() / total;
  }

  /**
   * 返回缓存统计信息。
   *
   * <p>统计维度包括命中/未命中、驱逐数、加载总数、加载成功/失败数及总加载耗时； 未开启统计时返回全零统计对象。驱逐数来自底层缓存。
   *
   * @return 聚合了加载统计与底层缓存统计的 {@link CacheStats}
   */
  @Override
  public CacheStats getStats() {
    if (!recordStats) {
      return new CacheStats(0, 0);
    }
    CacheStats delegateStats = cache.getStats();
    return new CacheStats(
        hitCount.sum(),
        missCount.sum(),
        delegateStats.getEvictionCount(),
        loadCount.sum(),
        loadSuccessCount.sum(),
        loadExceptionCount.sum(),
        totalLoadTimeNanos.sum());
  }

  /**
   * 关闭缓存，释放资源
   *
   * <p>支持 try-with-resources 模式。
   */
  @Override
  public void close() {
    shutdown();
  }

  /** 关闭缓存，释放资源 */
  public void shutdown() {
    if (refreshFuture != null) {
      refreshFuture.cancel(false);
      refreshFuture = null;
    }
    LOG.info("增强版加载缓存已关闭");
  }
}
