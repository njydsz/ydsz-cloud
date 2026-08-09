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

  /**
   * 获取指定 key 的缓存值，不做加载。
   *
   * <p>命中与未命中均返回已完成的 Future，未命中时 Future 值为 {@code null}，
   * 不会触发任何加载逻辑。
   *
   * @param key 查询的键，不可为 {@code null}
   * @return 携带缓存值的已完成 Future；未命中时值为 {@code null}
   */
  @Override
  public CompletableFuture<V> getIfPresent(K key) {
    V value = delegate.getIfPresent(key);
    if (value != null) {
      return CompletableFuture.completedFuture(value);
    }
    return CompletableFuture.completedFuture(null);
  }

  /**
   * 获取缓存值，未命中时通过异步加载函数加载。
   *
   * <p>防击穿语义：对同一 key 的并发请求共享同一个 {@link CompletableFuture}，
   * 仅首个请求真正执行加载。加载成功且值非空时写入底层缓存。
   *
   * @param key         查询的键，不可为 {@code null}
   * @param asyncLoader 异步加载函数；为 {@code null} 时回退到构造器注入的
   *                    {@link CacheLoader}，两者皆无则返回值为 {@code null} 的 Future
   * @return 携带缓存或加载值的 Future
   */
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

  /**
   * 批量获取多个 key，未命中的 key 通过异步加载函数批量加载。
   *
   * <p>先逐 key 查询缓存，汇总缺失 key 后一次性异步加载，加载结果仅写入
   * 非空值的条目（空值条目不写缓存、不出现在结果中），最终返回命中与加载结果的并集。
   *
   * @param keys        待查询的键集合，空集合或 {@code null} 返回空 map 的已完成 Future
   * @param asyncLoader 异步批量加载函数；为 {@code null} 时回退到构造器
   *                    {@link CacheLoader} 逐 key 加载，两者皆无则缺失 key 不加载
   * @return 携带 key 到值映射的 Future，缺失且加载失败/为空的 key 不会出现在结果中
   */
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

  /**
   * 异步写入缓存。
   *
   * <p>写入操作在独立执行器上异步执行，返回的 Future 在写入完成后完成，
   * 异常会传播给调用方。
   *
   * @param key   写入的键，不可为 {@code null}
   * @param value 写入的值，允许为 {@code null}
   * @return 表示写入完成的 Future
   */
  @Override
  public CompletableFuture<Void> put(K key, V value) {
    return CompletableFuture.runAsync(
        () -> delegate.put(key, value), executor);
  }

  /**
   * 强制刷新指定 key 的缓存值。
   *
   * <p>与 {@link #get(Object, AsyncFunction)} 的差异：不做缓存命中检查，直接重新加载；
   * 通过 loadingMap 与并发加载/刷新共享 Future，防止同一 key 的加载与刷新互相穿透。
   * 加载成功且非空则覆盖写入缓存，加载成功但为空则移除旧值，异常不写缓存。
   *
   * @param key         待刷新的键，为 {@code null} 时返回以 {@link NullPointerException}
   *                    失败的 Future
   * @param asyncLoader 异步加载函数，为 {@code null} 时返回失败 Future
   * @return 携带刷新结果的 Future
   */
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

  /**
   * 批量强制刷新多个 key 的缓存值。
   *
   * <p>不查缓存，直接批量异步加载并覆盖写入；加载结果中值为 {@code null} 的 key
   * 会被从缓存移除。批量加载异常时返回空 map 的已完成 Future，不抛出。
   *
   * @param keys        待刷新的键集合，空集合或 {@code null} 返回空 map 的已完成 Future
   * @param asyncLoader 异步批量加载函数，为 {@code null} 时返回失败 Future
   * @return 携带非空刷新结果的映射的 Future；整体失败时为空 map
   */
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

  /**
   * 返回本异步缓存对应的同步视图。
   *
   * <p>视图将全部异步接口委托为同步调用，并复用同一底层缓存存储，
   * 适合以同步 API 消费 {@link AsyncCache} 的场景。
   *
   * @return 与当前异步缓存共享底层存储的同步 {@link Cache}
   */
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

    /**
     * 获取指定 key 的缓存值，不做加载。
     *
     * @param key 查询的键，不可为 {@code null}
     * @return 已缓存的值；未命中返回 {@code null}
     */
    @Override
    public V getIfPresent(K key) {
      return delegate.getIfPresent(key);
    }

    /**
     * 获取缓存值，未命中时同步调用加载函数并回填缓存。
     *
     * @param key    查询的键，不可为 {@code null}
     * @param loader 未命中时使用的加载函数；为 {@code null} 时不加载直接返回
     *               {@code null}。加载结果非空才写入缓存
     * @return 缓存值或加载结果；未命中且加载为空时返回 {@code null}
     */
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

    /**
     * 异步获取缓存值，委托给异步加载缓存的防击穿逻辑。
     *
     * @param key    查询的键，不可为 {@code null}
     * @param loader 未命中时的异步加载函数
     * @return 携带缓存或加载值的 Future
     */
    @Override
    public CompletableFuture<V> getAsync(
        K key, AsyncFunction<K, V> loader) {
      return asyncCache.get(key, loader);
    }

    /**
     * 同步写入缓存。
     *
     * @param key   写入的键，不可为 {@code null}
     * @param value 写入的值
     */
    @Override
    public void put(K key, V value) {
      delegate.put(key, value);
    }

    /**
     * 仅当 key 不存在时写入，返回被覆盖的旧值。
     *
     * @param key   写入的键，不可为 {@code null}
     * @param value 待写入的值
     * @return 已存在的旧值；key 原本不存在时返回 {@code null}
     */
    @Override
    public V putIfAbsent(K key, V value) {
      return delegate.putIfAbsent(key, value);
    }

    /**
     * key 不存在时原子执行加载函数并写入，返回最终值。
     *
     * <p>并发调用同一 key 时仅一个线程执行映射函数（原子语义）。
     *
     * @param key            查询的键，不可为 {@code null}
     * @param mappingFunction 未命中时执行的映射函数，不可为 {@code null}
     * @return 已存在的值或映射函数的计算结果
     */
    @Override
    public V computeIfAbsent(K key, Function<K, V> mappingFunction) {
      return delegate.computeIfAbsent(key, mappingFunction);
    }

    /**
     * 基于当前值计算新值并写回缓存。
     *
     * @param key              操作的键，不可为 {@code null}
     * @param remappingFunction 重映射函数，入参为当前值（可为 {@code null}），
     *                          返回 {@code null} 时移除该 key
     * @return 重映射后的值；key 被移除时返回 {@code null}
     */
    @Override
    public V compute(
        K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
      return delegate.compute(key, remappingFunction);
    }

    /**
     * 将新值与当前值合并后写回缓存。
     *
     * @param key               操作的键，不可为 {@code null}
     * @param value             待合并的新值
     * @param remappingFunction  合并函数；返回 {@code null} 时移除该 key
     * @return 合并后的值；key 被移除时返回 {@code null}
     */
    @Override
    public V merge(
        K key,
        V value,
        BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
      return delegate.merge(key, value, remappingFunction);
    }

    /**
     * 移除指定 key 并返回旧值。
     *
     * @param key 待移除的键，不可为 {@code null}
     * @return 被移除的旧值；key 不存在时返回 {@code null}
     */
    @Override
    public V remove(K key) {
      return delegate.remove(key);
    }

    /**
     * 使指定 key 失效（与 remove 等价）。
     *
     * @param key 待失效的键，不可为 {@code null}
     */
    @Override
    public void invalidate(K key) {
      delegate.invalidate(key);
    }

    /**
     * 批量使多个 key 失效。
     *
     * @param keys 待失效的键集合
     */
    @Override
    public void invalidateAll(Collection<K> keys) {
      delegate.invalidateAll(keys);
    }

    /**
     * 使全部缓存项失效。
     */
    @Override
    public void invalidateAll() {
      delegate.invalidateAll();
    }

    /**
     * 清空全部缓存项。
     */
    @Override
    public void clear() {
      delegate.clear();
    }

    /**
     * 批量写入缓存。
     *
     * @param map 待写入的键值映射
     */
    @Override
    public void putAll(Map<K, V> map) {
      delegate.putAll(map);
    }

    /**
     * 批量获取多个 key，缺失的 key 不加载、不出现在结果中。
     *
     * @param keys 待查询的键集合
     * @return key 到已缓存值的映射
     */
    @Override
    public Map<K, V> getAll(Collection<K> keys) {
      return delegate.getAll(keys);
    }

    /**
     * 批量移除多个 key。
     *
     * @param keys 待移除的键集合
     */
    @Override
    public void removeAll(Collection<K> keys) {
      delegate.removeAll(keys);
    }

    /**
     * 返回当前缓存中的条目数。
     *
     * @return 已缓存条目的近似数量
     */
    @Override
    public long estimatedSize() {
      return delegate.estimatedSize();
    }

    /**
     * 判断缓存是否为空。
     *
     * @return true 表示当前无任何缓存条目
     */
    @Override
    public boolean isEmpty() {
      return delegate.isEmpty();
    }

    /**
     * 返回缓存命中率。
     *
     * @return 命中率，范围 [0, 1]
     */
    @Override
    public double getHitRate() {
      return delegate.getHitRate();
    }

    /**
     * 返回缓存统计信息。
     *
     * @return 底层缓存的 {@link CacheStats}
     */
    @Override
    public CacheStats getStats() {
      return delegate.getStats();
    }

    /**
     * 返回缓存策略视图（淘汰/过期等）。
     *
     * @return 底层缓存的 {@link CachePolicy}
     */
    @Override
    public CachePolicy policy() {
      return delegate.policy();
    }

    /**
     * 判断指定 key 是否已缓存。
     *
     * @param key 查询的键，不可为 {@code null}
     * @return true 表示该 key 存在于缓存中
     */
    @Override
    public boolean containsKey(K key) {
      return delegate.containsKey(key);
    }

    /**
     * 返回当前缓存键的快照视图。
     *
     * @return 缓存键的 {@link Set}
     */
    @Override
    public Set<K> keySet() {
      return delegate.keySet();
    }

    /**
     * 返回当前缓存值的集合视图。
     *
     * @return 缓存值的 {@link Collection}
     */
    @Override
    public Collection<V> values() {
      return delegate.values();
    }

    /**
     * 执行缓存清理（如过期条目的惰性回收）。
     */
    @Override
    public void cleanUp() {
      delegate.cleanUp();
    }

    /**
     * 注册删除监听器。
     *
     * @param listener 删除监听器，不可为 {@code null}
     */
    @Override
    public void addListener(
        RemovalListener<? super K, ? super V> listener) {
      delegate.addListener(listener);
    }

    /**
     * 遍历全部缓存条目。
     *
     * @param action 遍历动作，不可为 {@code null}
     */
    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
      delegate.forEach(action);
    }
  }
}
