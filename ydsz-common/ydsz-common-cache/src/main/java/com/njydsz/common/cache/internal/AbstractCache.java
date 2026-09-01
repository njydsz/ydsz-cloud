package com.njydsz.common.cache.internal;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.listener.RemovalCause;
import com.njydsz.common.cache.listener.RemovalListener;
import com.njydsz.common.cache.stats.CacheStats;
import com.njydsz.common.cache.support.AsyncFunction;

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
 * @author ydsz-team
 * @since 1.0.0
 */
public abstract class AbstractCache<K, V> implements Cache<K, V> {

  /** 日志记录器 */
  private static final Logger LOG = LoggerFactory.getLogger(AbstractCache.class);

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
   * Per-key 原子操作信号映射（putIfAbsent 与 compute 系列方法的单飞实现）。
   *
   * <p>同一 key 的并发原子操作中仅信号持有者执行用户函数，其余线程等待完成信号后读取结果—— 对标 Caffeine
   * 的 computeIfAbsent 语义（mapping 仅执行一次，结果对全部等待者可见）。 信号仅作完成通知不携带值，等待方通过 {@code
   * getIfPresent} 读取，避免 unchecked cast。 失败语义：用户函数异常时信号异常完成，异常传播给全部等待者（守卫不做递归重试）。
   */
  private final ConcurrentHashMap<Object, CompletableFuture<Object>> atomicSignals =
      new ConcurrentHashMap<>();

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
        LOG.warn("缓存删除监听器执行异常, key={}, cause={}", key, cause, e);
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
   * 原子的 putIfAbsent（对标 ConcurrentHashMap.putIfAbsent）。
   *
   * <p>per-key 单飞：并发调用下仅一个线程完成写入，竞争方等待后读取既有值。 double-check 消除信号获取与缓存读取之间的竞态窗口。
   *
   * @param key 缓存键（null 时直接返回现有值，与 put 的宽松 null 语义一致）
   * @param value 待写入值
   * @return 写入前已存在的值；写入成功（此前无值）返回 null
   */
  @Override
  public V putIfAbsent(K key, V value) {
    if (key == null || value == null) {
      return getIfPresent(key);
    }
    V existing = getIfPresent(key);
    if (existing != null) {
      return existing;
    }
    CompletableFuture<Object> ourSignal = new CompletableFuture<>();
    CompletableFuture<Object> winner = atomicSignals.putIfAbsent(key, ourSignal);
    if (winner != null) {
      winner.join();
      return getIfPresent(key);
    }
    try {
      V second = getIfPresent(key);
      if (second != null) {
        return second;
      }
      put(key, value);
      return null; // 写入成功：此前无值
    } finally {
      atomicSignals.remove(key, ourSignal);
      ourSignal.complete(null);
    }
  }

  /**
   * 原子的 computeIfAbsent（对标 Caffeine：mapping 仅执行一次，结果对全部等待者可见）。
   *
   * <p>失败语义：mappingFunction 抛出异常时，异常传播给所有等待者（等待方不重复执行 mapping， 防止并发重试风暴）。
   * mappingFunction 返回 null 时写入占位信号正常完成，等待方读取缓存未果后返回 null。
   *
   * @param key 缓存键
   * @param mappingFunction 映射函数
   * @return 缓存值或映射值；映射为 null 时返回 null 且不写入
   */
  @Override
  public V computeIfAbsent(K key, Function<K, V> mappingFunction) {
    if (key == null || mappingFunction == null) {
      return getIfPresent(key);
    }
    V value = getIfPresent(key);
    if (value != null) {
      return value;
    }
    CompletableFuture<Object> ourSignal = new CompletableFuture<>();
    CompletableFuture<Object> winner = atomicSignals.putIfAbsent(key, ourSignal);
    if (winner != null) {
      try {
        winner.join();
      } catch (CompletionException e) {
        throw unwrapCompletion(e);
      }
      V theirs = getIfPresent(key);
      return theirs;
    }
    try {
      V second = getIfPresent(key);
      if (second != null) {
        return second;
      }
      V mapped = mappingFunction.apply(key);
      if (mapped != null) {
        put(key, mapped);
      }
      return mapped;
    } catch (Exception e) {
      ourSignal.completeExceptionally(e);
      throw asRuntime(e);
    } finally {
      atomicSignals.remove(key, ourSignal);
      ourSignal.complete(null);
    }
  }

  /**
   * 原子的 compute（同一 key 的并发 compute 按抵达顺序串行化）。
   *
   * @param key 缓存键
   * @param remappingFunction 重映射函数
   * @return 新值；重映射为 null 时移除条目并返回 null
   */
  @Override
  public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    if (key == null || remappingFunction == null) {
      return null;
    }
    CompletableFuture<Object> ourSignal = new CompletableFuture<>();
    CompletableFuture<Object> winner = atomicSignals.putIfAbsent(key, ourSignal);
    if (winner != null) {
      try {
        winner.join();
      } catch (CompletionException e) {
        throw unwrapCompletion(e);
      }
      return getIfPresent(key);
    }
    try {
      V oldValue = getIfPresent(key);
      V newValue = remappingFunction.apply(key, oldValue);
      if (newValue == null) {
        if (oldValue != null) {
          remove(key);
        }
      } else {
        put(key, newValue);
      }
      return newValue;
    } catch (Exception e) {
      ourSignal.completeExceptionally(e);
      throw asRuntime(e);
    } finally {
      atomicSignals.remove(key, ourSignal);
      ourSignal.complete(null);
    }
  }

  /**
   * 原子的 merge（对标 ConcurrentHashMap.merge，同一 key 并发按抵达顺序串行化）。
   *
   * @param key 缓存键
   * @param value 待合并值
   * @param remappingFunction 合并函数
   * @return 合并后的值；合并结果为 null 时移除条目并返回 null
   */
  @Override
  public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
    if (key == null || value == null || remappingFunction == null) {
      return getIfPresent(key);
    }
    CompletableFuture<Object> ourSignal = new CompletableFuture<>();
    CompletableFuture<Object> winner = atomicSignals.putIfAbsent(key, ourSignal);
    if (winner != null) {
      try {
        winner.join();
      } catch (CompletionException e) {
        throw unwrapCompletion(e);
      }
      return getIfPresent(key);
    }
    try {
      V oldValue = getIfPresent(key);
      V newValue = (oldValue == null) ? value : remappingFunction.apply(oldValue, value);
      if (newValue == null) {
        remove(key);
      } else {
        put(key, newValue);
      }
      return newValue;
    } catch (Exception e) {
      ourSignal.completeExceptionally(e);
      throw asRuntime(e);
    } finally {
      atomicSignals.remove(key, ourSignal);
      ourSignal.complete(null);
    }
  }

  /**
   * 将异常转换为可抛出的 RuntimeException（保留原始运行时异常类型，检查异常包装为 CompletionException）。
   *
   * @param e 原始异常
   * @return 可抛出的运行时异常
   */
  private static RuntimeException asRuntime(Exception e) {
    return (e instanceof RuntimeException) ? (RuntimeException) e : new CompletionException(e);
  }

  /**
   * 解包等待方捕获的 CompletionException，还原信号发送方的原始异常类型。
   *
   * @param e 等待方捕获的 CompletionException
   * @return 还原后的可抛出运行时异常
   */
  private static RuntimeException unwrapCompletion(CompletionException e) {
    Throwable cause = e.getCause();
    return (cause instanceof RuntimeException) ? (RuntimeException) cause : e;
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

  /**
   * 重置命中与未命中计数器。
   *
   * <p>仅清零 {@code hitCount} 与 {@code missCount}，不重置淘汰计数 {@code evictionCount}，
   * 也不清空缓存数据本身。用于在监控周期切换时重新起算命中率。
   */
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
