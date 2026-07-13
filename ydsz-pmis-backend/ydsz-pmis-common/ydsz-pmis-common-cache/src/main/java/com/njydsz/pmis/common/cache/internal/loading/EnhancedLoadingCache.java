package com.njydsz.pmis.common.cache.internal.loading;

import com.njydsz.pmis.common.cache.internal.AbstractCache;

import com.njydsz.pmis.common.cache.api.Cache;
import com.njydsz.pmis.common.cache.api.LoadingCache;
import com.njydsz.pmis.common.cache.listener.RemovalCause;
import com.njydsz.pmis.common.cache.stats.CacheStats;
import com.njydsz.pmis.common.cache.support.AsyncFunction;
import com.njydsz.pmis.common.cache.support.CacheLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * 增强版异步加载缓存实现 - 支持 CacheLoader、自动刷新和完整统计
 *
 * <p>继承 {@link AbstractCache}，复用命中/未命中计数、删除监听器等通用逻辑，
 * 同时实现 {@link LoadingCache} 接口提供自动加载能力。
 *
 * <p>核心特性：
 * <ul>
 *   <li>CacheLoader 集成：支持单键/批量加载</li>
 *   <li>自动刷新：支持 refreshAfterWrite 自动刷新机制</li>
 *   <li>防击穿：同一 key 的并发加载请求共享同一个 Future</li>
 *   <li>防穿透：缓存加载失败时返回旧值或 null</li>
 *   <li>完整统计：命中/未命中/加载成功/加载失败/加载时间</li>
 *   <li>异步执行：支持自定义 Executor</li>
 *   <li>刷新策略：支持周期性自动刷新</li>
 * </ul>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author Marvin Lee
 * @version 3.5.0
 */
public class EnhancedLoadingCache<K, V> extends AbstractCache<K, V> implements LoadingCache<K, V>, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(EnhancedLoadingCache.class);

    /**
     * 全局共享异步执行器（替代每实例创建线程池）
     */
    private static final Executor SHARED_EXECUTOR = Executors.newWorkStealingPool();

    /**
     * 全局共享刷新调度器（替代每实例创建调度线程）
     */
    private static final ScheduledExecutorService SHARED_REFRESH_SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ydsz-cache-shared-refresher");
        t.setDaemon(true);
        return t;
    });

    /**
     * 底层缓存
     */
    private final Cache<K, V> cache;

    /**
     * 缓存加载器
     */
    private final CacheLoader<K, V> loader;

    /**
     * 异步执行器
     */
    private final Executor executor;

    /**
     * 自动刷新调度器
     */
    private final ScheduledExecutorService refreshScheduler;

    /**
     * 自动刷新定时任务 Future（用于取消当前实例的定时任务）
     */
    private volatile ScheduledFuture<?> refreshFuture;

    /**
     * 自动刷新间隔（纳秒），0 表示不自动刷新
     */
    private final long refreshIntervalNanos;

    /**
     * 是否启用统计
     */
    private final boolean recordStats;

    /**
     * 加载计数器
     */
    private final LongAdder loadCount = new LongAdder();

    /**
     * 加载成功计数器
     */
    private final LongAdder loadSuccessCount = new LongAdder();

    /**
     * 加载异常计数器
     */
    private final LongAdder loadExceptionCount = new LongAdder();

    /**
     * 总加载时间计数器（纳秒）
     */
    private final LongAdder totalLoadTimeNanos = new LongAdder();

    /**
     * 待加载的 Future 缓存（防击穿）
     */
    private final Map<K, CompletableFuture<V>> pendingLoads = new ConcurrentHashMap<>();

    /**
     * 最后刷新时间缓存（用于自动刷新）
     */
    private final ConcurrentHashMap<K, Long> lastRefreshTimes = new ConcurrentHashMap<>();

    /**
     * 创建增强版加载缓存（无自动刷新）
     *
     * @param cache  底层缓存
     * @param loader 缓存加载器
     * @param <K>    键类型
     * @param <V>    值类型
     * @return 缓存实例
     */
    public static <K, V> EnhancedLoadingCache<K, V> create(
            Cache<K, V> cache, CacheLoader<K, V> loader) {
        return new EnhancedLoadingCache<>(cache, loader, null, 0, TimeUnit.NANOSECONDS, null, true, false);
    }

    /**
     * 创建增强版加载缓存（完整参数）
     *
     * <p>使用静态工厂方法替代构造函数，避免 {@code this} 在构造完成前逃逸。
     * 自动刷新调度在对象完全构造后启动，确保线程安全。
     *
     * @param cache              底层缓存
     * @param loader             缓存加载器
     * @param executor           异步执行器（可选）
     * @param refreshInterval    自动刷新间隔
     * @param refreshUnit        刷新间隔单位
     * @param refreshExecutor    刷新任务执行器（可选，已废弃，使用 executor）
     * @param recordStats        是否启用统计
     * @param <K>                键类型
     * @param <V>                值类型
     * @return 缓存实例
     */
    public static <K, V> EnhancedLoadingCache<K, V> create(
            Cache<K, V> cache,
            CacheLoader<K, V> loader,
            Executor executor,
            long refreshInterval,
            TimeUnit refreshUnit,
            Executor refreshExecutor,
            boolean recordStats) {
        EnhancedLoadingCache<K, V> instance = new EnhancedLoadingCache<>(
                cache, loader, executor, refreshInterval, refreshUnit, refreshExecutor, recordStats, false);
        instance.scheduleAutoRefresh();
        return instance;
    }

    /**
     * 默认构造函数（无自动刷新）
     *
     * @param cache  底层缓存
     * @param loader 缓存加载器
     * @deprecated 使用 {@link #create(Cache, CacheLoader)} 替代
     */
    @Deprecated
    @SuppressWarnings("this-escape")
    public EnhancedLoadingCache(Cache<K, V> cache, CacheLoader<K, V> loader) {
        this(cache, loader, null, 0, TimeUnit.NANOSECONDS, null, true, true);
    }

    /**
     * 完整构造函数
     *
     * @param cache              底层缓存
     * @param loader             缓存加载器
     * @param executor           异步执行器（可选）
     * @param refreshInterval    自动刷新间隔
     * @param refreshUnit        刷新间隔单位
     * @param refreshExecutor    刷新任务执行器（可选，已废弃，使用 executor）
     * @param recordStats        是否启用统计
     * @deprecated 使用 {@link #create(Cache, CacheLoader, Executor, long, TimeUnit, Executor, boolean)} 替代
     */
    @Deprecated
    @SuppressWarnings("this-escape")
    public EnhancedLoadingCache(Cache<K, V> cache,
                                CacheLoader<K, V> loader,
                                Executor executor,
                                long refreshInterval,
                                TimeUnit refreshUnit,
                                Executor refreshExecutor,
                                boolean recordStats) {
        this(cache, loader, executor, refreshInterval, refreshUnit, refreshExecutor, recordStats, true);
    }

    /**
     * 内部构造函数
     *
     * @param scheduleRefresh 是否在构造时立即调度自动刷新（仅 deprecated 公开构造函数传 true，
     *                        工厂方法传 false，由工厂方法在构造完成后调度）
     */
    private EnhancedLoadingCache(Cache<K, V> cache,
                                CacheLoader<K, V> loader,
                                Executor executor,
                                long refreshInterval,
                                TimeUnit refreshUnit,
                                Executor refreshExecutor,
                                boolean recordStats,
                                boolean scheduleRefresh) {
        this.cache = cache;
        this.loader = loader;
        this.executor = executor != null ? executor : SHARED_EXECUTOR;
        this.recordStats = recordStats;

        if (refreshInterval > 0 && refreshUnit != null) {
            this.refreshIntervalNanos = refreshUnit.toNanos(refreshInterval);
            this.refreshScheduler = SHARED_REFRESH_SCHEDULER;
            if (scheduleRefresh) {
                scheduleAutoRefresh();
            }
        } else {
            this.refreshIntervalNanos = 0;
            this.refreshScheduler = null;
        }

        log.info("增强版加载缓存已创建，cache={}, loader={}, refreshInterval={}, recordStats={}",
                cache.getClass().getSimpleName(), loader.getClass().getSimpleName(),
                refreshInterval > 0 ? refreshInterval + " " + refreshUnit : "禁用", recordStats);
    }

    /**
     * 调度自动刷新任务
     */
    private void scheduleAutoRefresh() {
        if (refreshScheduler == null || refreshIntervalNanos <= 0) {
            return;
        }

        long refreshIntervalMillis = TimeUnit.NANOSECONDS.toMillis(refreshIntervalNanos);
        refreshFuture = refreshScheduler.scheduleAtFixedRate(
                this::refreshAll,
                refreshIntervalMillis,
                refreshIntervalMillis,
                TimeUnit.MILLISECONDS
        );
        log.info("自动刷新已启用，间隔={}ms", refreshIntervalMillis);
    }

    /**
     * 刷新所有缓存项
     */
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

    @Override
    public V get(K key) {
        V value = getIfPresent(key);
        if (value != null) {
            return value;
        }
        return loadSync(key);
    }

    /**
     * 同步加载缓存项（带防击穿）
     */
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
                log.warn("缓存加载失败, key={}, 返回旧值={}", key, oldValue != null, e);
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

    @Override
    public V getUnchecked(K key) {
        try {
            return get(key);
        } catch (Exception e) {
            log.error("缓存加载异常, key={}", key, e);
            return null;
        }
    }

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
        return future.thenApply(v -> {
            if (v != null) {
                cache.put(key, v);
                lastRefreshTimes.put(key, System.nanoTime());
                if (recordStats) {
                    loadSuccessCount.increment();
                }
            }
            return v;
        }).exceptionally(e -> {
            if (recordStats) {
                loadExceptionCount.increment();
            }
            log.warn("异步缓存加载失败, key={}", key, e);
            return null;
        });
    }

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

        return loader.apply(key).thenApply(v -> {
            if (v != null) {
                cache.put(key, v);
                lastRefreshTimes.put(key, System.nanoTime());
            }
            return v;
        });
    }

    @Override
    public Map<K, V> getAll(Collection<K> keys) {
        if (keys == null || keys.isEmpty()) {
            return new HashMap<>(0);
        }

        Map<K, V> result = new HashMap<>(keys.size());
        List<K> missedKeys = new ArrayList<>();

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
                log.warn("批量缓存加载失败", e);
            }
        }

        return result;
    }

    @Override
    public CompletableFuture<Map<K, V>> getAllAsync(Collection<K> keys) {
        if (keys == null || keys.isEmpty()) {
            return CompletableFuture.completedFuture(new HashMap<>(0));
        }

        return loader.loadAllAsync(keys).thenApply(loaded -> {
            cache.putAll(loaded);
            loaded.keySet().forEach(k -> lastRefreshTimes.put(k, System.nanoTime()));
            if (recordStats) {
                hitCount.add(loaded.size());
                loadSuccessCount.add(loaded.size());
            }
            return loaded;
        });
    }

    @Override
    public void refresh(K key) {
        CompletableFuture.runAsync(() -> {
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
                log.warn("刷新缓存失败, key={}", key, e);
            }
        }, executor);
    }

    @Override
    public void put(K key, V value) {
        cache.put(key, value);
        lastRefreshTimes.put(key, System.nanoTime());
    }

    @Override
    public V remove(K key) {
        V value = cache.remove(key);
        lastRefreshTimes.remove(key);
        if (value != null) {
            notifyRemoval(key, value, RemovalCause.EXPLICIT);
        }
        return value;
    }

    @Override
    public void clear() {
        cache.clear();
        lastRefreshTimes.clear();
    }

    @Override
    public long estimatedSize() {
        return cache.estimatedSize();
    }

    @Override
    public boolean containsKey(K key) {
        return cache.containsKey(key);
    }

    @Override
    public Set<K> keySet() {
        return cache.keySet();
    }

    @Override
    public Collection<V> values() {
        return cache.values();
    }

    @Override
    public double getHitRate() {
        if (!recordStats) {
            return 0.0;
        }
        long total = hitCount.sum() + missCount.sum();
        return total == 0 ? 0.0 : (double) hitCount.sum() / total;
    }

    @Override
    public CacheStats getStats() {
        if (!recordStats) {
            return new CacheStats(0, 0);
        }
        return new CacheStats(
                hitCount.sum(),
                missCount.sum(),
                loadCount.sum(),
                loadSuccessCount.sum(),
                loadExceptionCount.sum(),
                totalLoadTimeNanos.sum()
        );
    }

    /**
     * 关闭缓存，释放资源
     * <p>支持 try-with-resources 模式。
     */
    @Override
    public void close() {
        shutdown();
    }

    /**
     * 关闭缓存，释放资源
     */
    public void shutdown() {
        if (refreshFuture != null) {
            refreshFuture.cancel(false);
            refreshFuture = null;
        }
        log.info("增强版加载缓存已关闭");
    }
}
