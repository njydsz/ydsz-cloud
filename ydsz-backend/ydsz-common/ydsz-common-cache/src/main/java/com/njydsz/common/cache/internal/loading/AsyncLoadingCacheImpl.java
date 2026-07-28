package com.njydsz.common.cache.internal.loading;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.cache.api.AsyncCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.support.AsyncFunction;
import com.njydsz.common.cache.support.CacheLoader;

import com.njydsz.common.cache.api.CachePolicy;
import com.njydsz.common.cache.listener.RemovalListener;
import com.njydsz.common.cache.stats.CacheStats;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
/**
 * 异步加载缓存实现 — 基于 ConcurrentHashMap+CompletableFuture 防缓存击穿
 *
 * <p>核心原理：对同一个 key 的并发请求会共享同一个 {@link CompletableFuture}， 只有一个请求会实际执行加载逻辑，
 * 其他请求等待结果。这有效防止缓存击穿（热点 key 过期后大量并发请求穿透到后端）。
 *
 * <p>参考 Caffeine 的 AsyncLoadingCache 实现，使用 {@link ConcurrentHashMap#computeIfAbsent}
 * 保证只有一个线程执行加载。
 *
 * <p>特性：
 *
 * <ul>
 *   <li>防击穿：同一 key 并发请求共享同一个 Future
 *   <li>自动加载：支持 CacheLoader 自动加载
 *   <li>异步批量：getAll 支持批量异步加载
 *   <li>失败清理：加载失败时自动移除 Future，允许重试
 *   <li>同步适配：通过 synchronous() 获取同步视图
 * </ul>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author ydsz-team
 * 
 * @since 1.0.0
 */
public class AsyncLoadingCacheImpl<K, V> implements AsyncCache<K, V> {

  private static final Logger log = LoggerFactory.getLogger(AsyncLoadingCacheImpl.class);

  /** 底层同步缓存 */
  private final Cache<K, V> delegate;

  /** 异步加载器 */
  private final CacheLoader<K, V> loader;

  /** 异步任务执行器 */
  private final Executor executor;

  /** 进行中的加载 Future 映射（防击穿核心数据结构） */
  private final ConcurrentHashMap<K, CompletableFuture<V>> loadingMap = new ConcurrentHashMap<>();

  /**
   * 创建异步加载缓存
   *
   * @param delegate 底层同步缓存
   * @param loader 缓存加载器（null 表示不自动加载）
   * @param executor 异步任务执行器（null 使用 ForkJoinPool）
   */
  public AsyncLoadingCacheImpl(Cache<K, V> delegate, CacheLoader<K, V> loader, Executor executor) {
    this.delegate = delegate;
    this.loader = loader;
    this.executor = executor != null ? executor : ForkJoinPool.commonPool();
  }

  @Override
  public CompletableFuture<V> getIfPresent(K key) {
    V value = delegate.getIfPresent(key);
    if (value != null) {
      return CompletableFuture.completedFuture(value);
    }
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<V> get(K key, AsyncFunction<K, V> asyncLoader) {
    // 先检查缓存
    V cached = delegate.getIfPresent(key);
    if (cached != null) {
      return CompletableFuture.completedFuture(cached);
    }

    // 防击穿：使用 computeIfAbsent 保证同一 key 只有一个加载 Future
    AsyncFunction<K, V> effectiveLoader =
        asyncLoader != null ? asyncLoader : k -> defaultAsyncLoader(k);
    return loadingMap.computeIfAbsent(
        key,
        k -> {
          CompletableFuture<V> future =
              effectiveLoader
                  .apply(k)
                  .whenComplete(
                      (v, ex) -> {
                        // 加载完成后从 loadingMap 移除
                        loadingMap.remove(k);
                        if (v != null && ex == null) {
                          delegate.put(k, v);
                        }
                      });
          return future;
        });
  }

  /** 默认异步加载器（使用同步 loader 包装为异步） */
  private CompletableFuture<V> defaultAsyncLoader(K key) {
    if (loader == null) {
      return CompletableFuture.completedFuture(null);
    }
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            return loader.load(key);
          } catch (Exception e) {
            log.warn("异步加载缓存失败: key={}", key, e);
            return null;
          }
        },
        executor);
  }

  @Override
  public CompletableFuture<Map<K, V>> getAll(
      Collection<K> keys, AsyncFunction<Collection<K>, Map<K, V>> asyncLoader) {
    if (keys == null || keys.isEmpty()) {
      return CompletableFuture.completedFuture(new HashMap<>());
    }

    // 先从缓存获取
    Map<K, V> result = new HashMap<>();
    Set<K> missingKeys = new HashSet<>();

    for (K key : keys) {
      V value = delegate.getIfPresent(key);
      if (value != null) {
        result.put(key, value);
      } else {
        missingKeys.add(key);
      }
    }

    if (missingKeys.isEmpty()) {
      return CompletableFuture.completedFuture(result);
    }

    // 异步加载缺失的 key
    AsyncFunction<Collection<K>, Map<K, V>> effectiveLoader =
        asyncLoader != null ? asyncLoader : this::defaultBatchLoader;
    return effectiveLoader
        .apply(missingKeys)
        .thenApply(
            loaded -> {
              // 写入缓存并合并结果
              if (loaded != null) {
                for (Map.Entry<K, V> entry : loaded.entrySet()) {
                  if (entry.getValue() != null) {
                    delegate.put(entry.getKey(), entry.getValue());
                    result.put(entry.getKey(), entry.getValue());
                  }
                }
              }
              return result;
            });
  }

  /** 默认批量加载器 */
  private CompletableFuture<Map<K, V>> defaultBatchLoader(Collection<K> keys) {
    if (loader == null) {
      return CompletableFuture.completedFuture(new HashMap<>());
    }
    return CompletableFuture.supplyAsync(
        () -> {
          Map<K, V> loaded = new HashMap<>();
          for (K key : keys) {
            try {
              V value = loader.load(key);
              if (value != null) {
                loaded.put(key, value);
              }
            } catch (Exception e) {
              log.warn("批量异步加载缓存失败: key={}", key, e);
            }
          }
          return loaded;
        },
        executor);
  }

  @Override
  public CompletableFuture<Void> put(K key, V value) {
    return CompletableFuture.runAsync(
        () -> delegate.put(key, value), executor);
  }

  @Override
  public CompletableFuture<V> refresh(K key, AsyncFunction<K, V> asyncLoader) {
    if (key == null) {
      return CompletableFuture.failedFuture(new NullPointerException("缓存键不能为 null"));
    }
    if (asyncLoader == null) {
      return CompletableFuture.failedFuture(new NullPointerException("加载器不能为 null"));
    }
    // 刷新防击穿：复用 loadingMap 共享 Future（避免同一 key 同时刷新 + 加载冲突）
    // 注意：whenComplete 必须在 computeIfAbsent 之外附加，避免在 mapping function
    // 内部触发 loadingMap.remove 导致 ConcurrentHashMap 抛 Recursive update
    CompletableFuture<V> future =
        loadingMap.computeIfAbsent(
            key,
            k -> asyncLoader.apply(k));
    future.whenComplete(
        (v, ex) -> {
          loadingMap.remove(key, future);
          if (ex == null) {
            if (v != null) {
              delegate.put(key, v);
            } else {
              delegate.remove(key);
            }
          }
        });
    return future;
  }

  @Override
  public CompletableFuture<Map<K, V>> refreshAll(
      Collection<K> keys, AsyncFunction<Collection<K>, Map<K, V>> asyncLoader) {
    if (keys == null || keys.isEmpty()) {
      return CompletableFuture.completedFuture(Collections.emptyMap());
    }
    if (asyncLoader == null) {
      return CompletableFuture.failedFuture(new NullPointerException("加载器不能为 null"));
    }
    return asyncLoader
        .apply(keys)
        .thenApply(
            loaded -> {
              Map<K, V> result = new HashMap<>();
              if (loaded != null) {
                for (Map.Entry<K, V> entry : loaded.entrySet()) {
                  V value = entry.getValue();
                  if (value != null) {
                    delegate.put(entry.getKey(), value);
                    result.put(entry.getKey(), value);
                  } else {
                    delegate.remove(entry.getKey());
                  }
                }
              }
              return result;
            })
        .exceptionally(
            ex -> {
              log.warn("批量刷新失败, keys={}", keys, ex);
              return Collections.emptyMap();
            });
  }

  @Override
  public Cache<K, V> synchronous() {
    return new SynchronousCacheView<>(this, delegate);
  }

  /** 获取进行中的加载数量 */
  public int getPendingLoadCount() {
    return loadingMap.size();
  }

  /** 获取底层缓存实例 */
  public Cache<K, V> getDelegate() {
    return delegate;
  }

  /** 同步缓存视图 — 将 AsyncCache 适配为同步 Cache */
  private static class SynchronousCacheView<K, V> implements Cache<K, V> {

    private final AsyncLoadingCacheImpl<K, V> asyncCache;
    private final Cache<K, V> delegate;

    SynchronousCacheView(AsyncLoadingCacheImpl<K, V> asyncCache, Cache<K, V> delegate) {
      this.asyncCache = asyncCache;
      this.delegate = delegate;
    }

    @Override
    public V getIfPresent(K key) {
      return delegate.getIfPresent(key);
    }

    @Override
    public V get(K key, Function<K, V> loader) {
      V value = getIfPresent(key);
      if (value == null && loader != null) {
        value = loader.apply(key);
        if (value != null) {
          put(key, value);
        }
      }
      return value;
    }

    @Override
    public CompletableFuture<V> getAsync(
        K key, AsyncFunction<K, V> loader) {
      return asyncCache.get(key, loader);
    }

    @Override
    public void put(K key, V value) {
      delegate.put(key, value);
    }

    @Override
    public V putIfAbsent(K key, V value) {
      return delegate.putIfAbsent(key, value);
    }

    @Override
    public V computeIfAbsent(K key, Function<K, V> mappingFunction) {
      return delegate.computeIfAbsent(key, mappingFunction);
    }

    @Override
    public V compute(
        K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
      return delegate.compute(key, remappingFunction);
    }

    @Override
    public V merge(
        K key,
        V value,
        BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
      return delegate.merge(key, value, remappingFunction);
    }

    @Override
    public V remove(K key) {
      return delegate.remove(key);
    }

    @Override
    public void invalidate(K key) {
      delegate.invalidate(key);
    }

    @Override
    public void invalidateAll(Collection<K> keys) {
      delegate.invalidateAll(keys);
    }

    @Override
    public void invalidateAll() {
      delegate.invalidateAll();
    }

    @Override
    public void clear() {
      delegate.clear();
    }

    @Override
    public void putAll(Map<K, V> map) {
      delegate.putAll(map);
    }

    @Override
    public Map<K, V> getAll(Collection<K> keys) {
      return delegate.getAll(keys);
    }

    @Override
    public void removeAll(Collection<K> keys) {
      delegate.removeAll(keys);
    }

    @Override
    public long estimatedSize() {
      return delegate.estimatedSize();
    }

    @Override
    public boolean isEmpty() {
      return delegate.isEmpty();
    }

    @Override
    public double getHitRate() {
      return delegate.getHitRate();
    }

    @Override
    public CacheStats getStats() {
      return delegate.getStats();
    }

    @Override
    public CachePolicy policy() {
      return delegate.policy();
    }

    @Override
    public boolean containsKey(K key) {
      return delegate.containsKey(key);
    }

    @Override
    public Set<K> keySet() {
      return delegate.keySet();
    }

    @Override
    public Collection<V> values() {
      return delegate.values();
    }

    @Override
    public void cleanUp() {
      delegate.cleanUp();
    }

    @Override
    public void addListener(
        RemovalListener<? super K, ? super V> listener) {
      delegate.addListener(listener);
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
      delegate.forEach(action);
    }
  }
}
