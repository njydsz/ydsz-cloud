package com.njydsz.common.cache.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.cache.api.AsyncCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.support.AsyncFunction;

/**
 * AsyncCache 适配器 — 将同步 {@link Cache} 适配为 {@link AsyncCache}
 *
 * <p>使用 {@link CompletableFuture#supplyAsync} 将同步操作包装为异步操作。
 * 对同一 key 的并发异步加载请求会共享同一个 Future，实现异步防击穿。
 *
 * <p>线程池：默认使用 {@link CompletableFuture#supplyAsync} 的 ForkJoinPool.commonPool()，
 * 也可通过构造器指定自定义 {@link Executor}。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @since 1.0.0
 */
public class AsyncCacheAdapter<K, V> implements AsyncCache<K, V> {

  private static final Logger log = LoggerFactory.getLogger(AsyncCacheAdapter.class);

  private final Cache<K, V> delegate;
  private final Executor executor;

  /** 正在加载的 Future 映射（key -> 进行中的 CompletableFuture），用于异步防击穿 */
  private final Map<K, CompletableFuture<V>> loadingFutures = new ConcurrentHashMap<>();

  /** 正在刷新的 Future 映射（key -> 进行中的 CompletableFuture），用于刷新防击穿 */
  private final Map<K, CompletableFuture<V>> refreshingFutures = new ConcurrentHashMap<>();

  /**
   * 创建 AsyncCache 适配器（使用默认线程池）
   *
   * @param delegate 底层同步缓存
   */
  public AsyncCacheAdapter(Cache<K, V> delegate) {
    this(delegate, null);
  }

  /**
   * 创建 AsyncCache 适配器
   *
   * @param delegate 底层同步缓存
   * @param executor 自定义线程池（null 表示使用默认 ForkJoinPool）
   */
  public AsyncCacheAdapter(Cache<K, V> delegate, Executor executor) {
    this.delegate = delegate;
    this.executor = executor;
  }

  @Override
  public CompletableFuture<V> getIfPresent(K key) {
    return CompletableFuture.supplyAsync(() -> delegate.getIfPresent(key), actualExecutor());
  }

  @Override
  public CompletableFuture<V> get(K key, AsyncFunction<K, V> loader) {
    // 异步防击穿：同一 key 的并发请求共享同一个 Future
    return loadingFutures.computeIfAbsent(
        key,
        k -> {
          CompletableFuture<V> future =
              CompletableFuture.supplyAsync(() -> delegate.getIfPresent(k), actualExecutor());
          return future.thenCompose(
              existing -> {
                if (existing != null) {
                  return CompletableFuture.completedFuture(existing);
                }
                // 缓存未命中，异步加载
                CompletableFuture<V> loadFuture = loader.apply(k);
                return loadFuture.thenApply(
                    v -> {
                      if (v != null) {
                        delegate.put(k, v);
                      }
                      return v;
                    });
              });
        });
  }

  @Override
  public CompletableFuture<Map<K, V>> getAll(
      Collection<K> keys, AsyncFunction<Collection<K>, Map<K, V>> loader) {
    return CompletableFuture.supplyAsync(
        () -> {
          // 先批量查缓存
          Map<K, V> result = new HashMap<>(keys.size());
          List<K> missedKeys = new ArrayList<>(keys.size());

          for (K key : keys) {
            V value = delegate.getIfPresent(key);
            if (value != null) {
              result.put(key, value);
            } else {
              missedKeys.add(key);
            }
          }

          if (missedKeys.isEmpty()) {
            return result;
          }

          // 异步加载未命中的 key
          try {
            Map<K, V> loaded = loader.apply(missedKeys).join();
            if (loaded != null) {
              for (Map.Entry<K, V> entry : loaded.entrySet()) {
                if (entry.getValue() != null) {
                  delegate.put(entry.getKey(), entry.getValue());
                }
                result.put(entry.getKey(), entry.getValue());
              }
            }
          } catch (Exception e) {
            log.warn("异步批量加载失败, keys={}", missedKeys, e);
          }

          return result;
        },
        actualExecutor());
  }

  @Override
  public CompletableFuture<Void> put(K key, V value) {
    return CompletableFuture.runAsync(() -> delegate.put(key, value), actualExecutor());
  }

  @Override
  public CompletableFuture<V> refresh(K key, AsyncFunction<K, V> loader) {
    if (key == null) {
      return CompletableFuture.failedFuture(new NullPointerException("缓存键不能为 null"));
    }
    if (loader == null) {
      return CompletableFuture.failedFuture(new NullPointerException("加载器不能为 null"));
    }
    // 刷新防击穿：同一 key 的并发刷新请求共享同一个 Future
    // 注意：whenComplete 必须在 computeIfAbsent 之外附加，避免在 mapping function
    // 内部触发 refreshingFutures.remove 导致 ConcurrentHashMap 抛 Recursive update
    CompletableFuture<V> future =
        refreshingFutures.computeIfAbsent(
            key,
            k ->
                CompletableFuture.supplyAsync(() -> loader.apply(k), actualExecutor())
                    .thenCompose(f -> f));
    future.whenComplete(
        (v, ex) -> {
          // 加载完成后从刷新中映射移除（用 value 相等性检查避免误删其他线程的 future）
          refreshingFutures.remove(key, future);
          if (ex == null) {
            if (v != null) {
              // 加载成功且非 null，更新缓存
              delegate.put(key, v);
            } else {
              // 加载返回 null，从缓存中移除该键
              delegate.remove(key);
            }
          }
          // 加载失败时（ex != null）保留缓存旧值，不清空
        });
    return future;
  }

  @Override
  public CompletableFuture<Map<K, V>> refreshAll(
      Collection<K> keys, AsyncFunction<Collection<K>, Map<K, V>> loader) {
    if (keys == null || keys.isEmpty()) {
      return CompletableFuture.completedFuture(Collections.emptyMap());
    }
    if (loader == null) {
      return CompletableFuture.failedFuture(new NullPointerException("加载器不能为 null"));
    }
    return CompletableFuture.supplyAsync(
            () -> {
              try {
                Map<K, V> loaded = loader.apply(keys).join();
                if (loaded == null) {
                  return Collections.<K, V>emptyMap();
                }
                Map<K, V> result = new HashMap<>(loaded.size());
                for (Map.Entry<K, V> entry : loaded.entrySet()) {
                  V value = entry.getValue();
                  if (value != null) {
                    // 加载成功且非 null，更新缓存
                    delegate.put(entry.getKey(), value);
                    result.put(entry.getKey(), value);
                  } else {
                    // 加载返回 null，从缓存中移除该键
                    delegate.remove(entry.getKey());
                  }
                }
                return result;
              } catch (Exception e) {
                log.warn("批量刷新失败, keys={}", keys, e);
                // 批量加载失败时保留所有键的旧值，返回空 Map
                return Collections.<K, V>emptyMap();
              }
            },
            actualExecutor());
  }

  @Override
  public Cache<K, V> synchronous() {
    return delegate;
  }

  private Executor actualExecutor() {
    return executor != null ? executor : Runnable::run;
  }
}
