package com.njydsz.pmis.common.cache.internal;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.cache.api.Cache;
import com.njydsz.pmis.common.cache.listener.RemovalCause;
import com.njydsz.pmis.common.cache.listener.RemovalListener;
import com.njydsz.pmis.common.cache.stats.CacheStats;
import com.njydsz.pmis.common.cache.support.AsyncFunction;

/**
 * 缓存抽象基类，提供缓存实现的公共逻辑。
 *
 * <p>封装了以下通用功能，消除子类重复代码：
 *
 * <ul>
 *   <li>命中/未命中计数与命中率统计
 *   <li>删除监听器管理与通知
 *   <li>带加载器的获取（{@code get(key, loader)}）
 *   <li>异步获取（{@code getAsync(key, loader)}）
 * </ul>
 *
 * <p>子类只需实现 {@link #getIfPresent(Object)}、{@link #put(Object, Object)}、 {@link
 * #remove(Object)}、{@link #clear()}、{@link #estimatedSize()}、 {@link #containsKey(Object)}、{@link
 * #keySet()}、{@link #values()} 等核心方法。
 *
 * <p>线程安全说明：本类使用 {@link LongAdder} 和 {@link CopyOnWriteArrayList}， 统计和监听器操作均为线程安全。子类需自行保证核心方法的线程安全。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author ydsz-pmis-team
 * 
 */
public abstract class AbstractCache<K, V> implements Cache<K, V> {

  /** 日志记录器 */
  private static final Logger log = LoggerFactory.getLogger(AbstractCache.class);

  /** 命中计数器 */
  protected final LongAdder hitCount = new LongAdder();

  /** 未命中计数器 */
  protected final LongAdder missCount = new LongAdder();

  /** 淘汰计数器（因容量限制被驱逐的条目数） */
  protected final LongAdder evictionCount = new LongAdder();

  /** 删除监听器列表 */
  protected final List<RemovalListener<? super K, ? super V>> listeners =
      new CopyOnWriteArrayList<>();

  /**
   * 添加删除监听器。
   *
   * @param listener 删除监听器
   */
  @Override
  public void addListener(RemovalListener<? super K, ? super V> listener) {
    if (listener != null) {
      listeners.add(listener);
    }
  }

  /**
   * 通知所有删除监听器。
   *
   * <p>监听器异常不会影响缓存正常操作，仅记录警告日志。
   *
   * <p>当删除原因为 {@link RemovalCause#SIZE} 时，同时递增淘汰计数器。
   *
   * @param key 被删除的键
   * @param value 被删除的值
   * @param cause 删除原因
   */
  protected void notifyRemoval(K key, V value, RemovalCause cause) {
    if (cause == RemovalCause.SIZE) {
      evictionCount.increment();
    }
    if (listeners.isEmpty()) {
      return;
    }
    for (RemovalListener<? super K, ? super V> listener : listeners) {
      try {
        listener.onRemoval(key, value, cause);
      } catch (Exception e) {
        log.warn("缓存删除监听器执行异常, key={}, cause={}", key, cause, e);
      }
    }
  }

  /**
   * 获取缓存值，如果不存在则使用加载器加载并放入缓存。
   *
   * @param key 缓存键
   * @param loader 值加载器
   * @return 缓存值或加载的值
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
   * 异步获取缓存值，如果不存在则使用异步加载器加载并放入缓存。
   *
   * @param key 缓存键
   * @param loader 异步值加载器
   * @return 异步完成的缓存值
   */
  @Override
  public CompletableFuture<V> getAsync(K key, AsyncFunction<K, V> loader) {
    V value = getIfPresent(key);
    if (value != null) {
      return CompletableFuture.completedFuture(value);
    }
    return loader
        .apply(key)
        .thenApply(
            v -> {
              if (v != null) {
                put(key, v);
              }
              return v;
            });
  }

  /**
   * 获取缓存命中率。
   *
   * <p>命中率 = 命中次数 / (命中次数 + 未命中次数)。 当总访问次数为 0 时，返回 0.0。
   *
   * @return 命中率，范围 [0.0, 1.0]
   */
  @Override
  public double getHitRate() {
    long total = hitCount.sum() + missCount.sum();
    return total == 0 ? 0.0 : (double) hitCount.sum() / total;
  }

  /**
   * 获取缓存统计信息。
   *
   * @return 缓存统计快照
   */
  @Override
  public CacheStats getStats() {
    return new CacheStats(hitCount.sum(), missCount.sum(), evictionCount.sum(), 0, 0, 0, 0);
  }

  @Override
  public void resetStats() {
    hitCount.reset();
    missCount.reset();
  }

  /**
   * 获取缓存大小（估计值）。
   *
   * <p>子类必须实现此方法以提供缓存的实际大小。
   *
   * @return 缓存大小（估计值）
   */
  @Override
  public abstract long estimatedSize();
}
