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
 * @author ydsz-team
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

  /**
   * 异步获取缓存值（不触发加载）。
   *
   * <p>在异步线程上执行底层同步缓存查询，未命中时 Future 结果值为 null。
   *
   * @param key 缓存键
   * @return 异步完成的缓存值；未命中时完成值为 null
   */
  @Override
  public CompletableFuture<V> getIfPresent(K key) {
    return CompletableFuture.supplyAsync(() -> delegate.getIfPresent(key), actualExecutor());
  }

  /**
   * 异步获取缓存值，未命中时使用加载器加载。
   *
   * <p>同一 key 的并发请求共享同一个进行中的 Future（{@code loadingFutures} 防击穿），
   * 只有首个请求真正执行加载；加载成功后写回底层缓存。若线程池为同步执行
   * （默认 {@link Runnable#run}），此方法退化为同步语义但仍保持防击穿。
   *
   * @param key    缓存键
   * @param loader 异步加载器，加载结果非 null 时写回缓存
   * @return 异步完成的缓存值；加载结果为 null 时完成值为 null
   */
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

  /**
   * 异步批量获取缓存值，未命中部分使用批量加载器加载。
   *
   * <p>先在异步线程上批量查询缓存，将未命中的 key 交给 loader 一次性加载并写回；
   * 批量加载失败仅记录警告日志并返回已命中的部分，不向调用方抛出异常。
   *
   * @param keys   待获取的键集合
   * @param loader 批量异步加载器，仅对未命中的键生效
   * @return 异步完成的键值映射；未命中且加载失败的键不会出现在结果中
   */
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

  /**
   * 异步写入键值对。
   *
   * <p>在异步线程上执行底层缓存写入，Future 完成表示写入已提交（同步执行的默认线程池下等价于直接写入）。
   *
   * @param key   缓存键
   * @param value 缓存值
   * @return 表示写入完成的 CompletableFuture
   */
  @Override
  public CompletableFuture<Void> put(K key, V value) {
    return CompletableFuture.runAsync(() -> delegate.put(key, value), actualExecutor());
  }

  /**
   * 主动刷新单个键，强制重新加载并覆盖缓存。
   *
   * <p>与 {@link #get} 不同，本方法总是调用 loader，不先检查缓存是否命中。
   * 同一 key 的并发刷新共享同一 Future（{@code refreshingFutures} 防击穿）；
   * 加载成功（非 null）时覆盖缓存，加载返回 null 时移除该键，
   * 加载抛异常时返回异常完成的 Future 且<b>保留缓存旧值</b>，避免刷新失败清空缓存。
   *
   * @param key    缓存键，不可为 null
   * @param loader 异步加载器，不可为 null
   * @return 异步完成的新值；key 或 loader 为 null 时返回异常完成的 Future
   */
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

  /**
   * 主动批量刷新多个键。
   *
   * <p>一次性调用批量加载器加载全部键并写回：加载成功（非 null）的键用新值覆盖，
   * 加载返回 null 的键从缓存移除，加载抛异常时保留所有旧值并返回空 Map（仅记录警告日志）。
   * 空 keys 集合直接返回空 Map，不调用 loader。
   *
   * @param keys   待刷新的键集合，可为空
   * @param loader 批量异步加载器，不可为 null
   * @return 异步完成的键值映射，仅包含加载成功且非 null 的条目
   */
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

  /**
   * 返回底层同步缓存。
   *
   * @return 适配器包装的同步 {@link Cache} 实例
   */
  @Override
  public Cache<K, V> synchronous() {
    return delegate;
  }

  private Executor actualExecutor() {
    return executor != null ? executor : Runnable::run;
  }
}
