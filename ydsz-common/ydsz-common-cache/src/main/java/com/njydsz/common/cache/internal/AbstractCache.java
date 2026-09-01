package com.njydsz.common.cache.internal;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
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
 *   <li>命中/未命中/淘汰/加载计数与完整统计（{@code getStats()}）
 *   <li>删除监听器管理与通知
 *   <li>带加载器的获取（{@code get(key, loader)}，含加载计时统计）
 *   <li>批量加载获取（{@code getAll(keys, loader)}，单批一次加载统计）
 *   <li>异步获取（{@code getAsync(key, loader)}，单飞 + 加载统计）
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

  /** 加载成功次数（loader 正常返回，含返回 null 的"确认不存在"） */
  protected final LongAdder loadSuccessCount = new LongAdder();

  /** 加载异常次数（loader 抛出异常） */
  protected final LongAdder loadExceptionCount = new LongAdder();

  /** 总加载耗时（纳秒，成功与异常加载均计入） */
  protected final LongAdder totalLoadTimeNanos = new LongAdder();

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
   * <p><b>异步派发（对标 Caffeine RemovalListeners.async）</b>：配置了 {@code
   * listenerExecutor} 时回调在执行器上异步执行——底层实现的淘汰/删除常持有写锁， 同步回调慢逻辑会拖慢整条读写路径。
   * 未配置时保持同步执行（向后兼容）。 {@link RemovalCause#SIZE} 的淘汰计数始终同步递增，不受异步影响。
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
    Executor executor = listenerExecutor;
    for (RemovalListener<? super K, ? super V> listener : listeners) {
      if (executor != null) {
        executor.execute(() -> invokeListenerSafely(listener, key, value, cause));
      } else {
        invokeListenerSafely(listener, key, value, cause);
      }
    }
  }

  /**
   * 安全调用单个删除监听器（异常仅记录警告，不影响缓存操作）。
   *
   * @param listener 监听器
   * @param key 被删除的键
   * @param value 被删除的值
   * @param cause 删除原因
   */
  private void invokeListenerSafely(
      RemovalListener<? super K, ? super V> listener, K key, V value, RemovalCause cause) {
    try {
      listener.onRemoval(key, value, cause);
    } catch (Exception e) {
      LOG.warn("缓存删除监听器执行异常, key={}, cause={}", key, cause, e);
    }
  }

  /**
   * 删除监听器回调执行器（null 时同步执行，保持向后兼容）。
   */
  private volatile Executor listenerExecutor;

  /**
   * 配置删除监听器异步回调执行器。
   *
   * @param executor 回调执行器；null 表示恢复同步执行
   */
  @Override
  public void setListenerExecutor(Executor executor) {
    this.listenerExecutor = executor;
  }

  /**
   * 实例级空值占位 TTL 下界（毫秒）。默认 0 表示未配置，使用接口默认区间 30~60 秒。
   */
  private volatile long nullValueMinExpireMs = 0;

  /**
   * 实例级空值占位 TTL 上界（毫秒）。默认 0 表示未配置，使用接口默认区间 30~60 秒。
   */
  private volatile long nullValueMaxExpireMs = 0;

  /**
   * 配置实例级空值占位 TTL 区间（由 CacheBuilder 注入，调用点无需重复传参）。
   *
   * @param minExpireMs 最小过期时间（毫秒）
   * @param maxExpireMs 最大过期时间（毫秒）
   */
  @Override
  public void setNullValueTtl(long minExpireMs, long maxExpireMs) {
    this.nullValueMinExpireMs = minExpireMs;
    this.nullValueMaxExpireMs = maxExpireMs;
  }

  /**
   * 带防护的缓存获取（使用实例级空值 TTL 配置，未配置时回退接口默认区间）。
   *
   * @param key 缓存键
   * @param loader 值加载器
   * @return 缓存值
   */
  @Override
  public V getWithProtection(K key, Function<K, V> loader) {
    long min = nullValueMinExpireMs > 0 ? nullValueMinExpireMs : DEFAULT_NULL_VALUE_TTL_MIN_MS;
    long max = nullValueMaxExpireMs > 0 ? nullValueMaxExpireMs : DEFAULT_NULL_VALUE_TTL_MAX_MS;
    return getWithProtection(key, loader, min, max);
  }

  /**
   * 获取缓存值，如果不存在则使用加载器加载并放入缓存。
   *
   * <p>加载统计（对标 Caffeine recordStats）：loader 调用计时并计入 加载成功/异常次数与总加载耗时，经 {@link #getStats()} 暴露。
   *
   * @param key 缓存键
   * @param loader 值加载器
   * @return 缓存值或加载的值
   */
  @Override
  public V get(K key, Function<K, V> loader) {
    V value = getIfPresent(key);
    if (value == null && loader != null) {
      long start = System.nanoTime();
      try {
        value = loader.apply(key);
        totalLoadTimeNanos.add(System.nanoTime() - start);
        loadSuccessCount.increment();
        if (value != null) {
          put(key, value);
        }
      } catch (Exception e) {
        totalLoadTimeNanos.add(System.nanoTime() - start);
        loadExceptionCount.increment();
        throw e;
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
   * <p>等待方完成等待后重新竞争加载权执行自身的 remap（对标 ConcurrentHashMap.compute 语义：
   * 每个调用方的重映射都必须生效，不得因排队而丢弃）。
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
    while (true) {
      CompletableFuture<Object> ourSignal = new CompletableFuture<>();
      CompletableFuture<Object> winner = atomicSignals.putIfAbsent(key, ourSignal);
      if (winner != null) {
        try {
          winner.join();
        } catch (CompletionException e) {
          throw unwrapCompletion(e);
        }
        // 排队完成，重新竞争加载权执行自身重映射（不得丢弃本次调用）
        continue;
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
  }

  /**
   * 原子的 merge（对标 ConcurrentHashMap.merge，同一 key 并发按抵达顺序串行化）。
   *
   * <p>等待方完成等待后重新竞争加载权执行自身的合并（对标 CHM.merge 语义： 每个调用方的合并都必须生效——16
   * 线程并发 merge(key, 1, sum) 结果必须为 16，排队不得导致调用丢失）。
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
    while (true) {
      CompletableFuture<Object> ourSignal = new CompletableFuture<>();
      CompletableFuture<Object> winner = atomicSignals.putIfAbsent(key, ourSignal);
      if (winner != null) {
        try {
          winner.join();
        } catch (CompletionException e) {
          throw unwrapCompletion(e);
        }
        // 排队完成，重新竞争加载权执行自身合并（不得丢弃本次调用）
        continue;
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
   * <p><b>单飞语义（对标 Caffeine AsyncCache）</b>：同一 key 的并发异步加载共享同一个
   * Future，loader 仅执行一次，结果对全部等待方可见；loader 异常传播给全部等待方。 旧实现每个并发调用方各自执行 loader，与同步路径
   * {@code getWithProtection} 的防击穿能力不一致。
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
    if (key == null || loader == null) {
      return CompletableFuture.completedFuture(null);
    }
    // 单飞：同一 key 的并发异步加载共享同一 Future（对标 Caffeine AsyncCache）
    CompletableFuture<Object> ourFuture = new CompletableFuture<>();
    CompletableFuture<Object> winner = asyncLoadingFutures.putIfAbsent(key, ourFuture);
    if (winner != null) {
      return castFuture(winner);
    }
    // 持有加载权：double-check 缓存（获取信号期间其他路径可能已写入）
    V second = getIfPresent(key);
    if (second != null) {
      asyncLoadingFutures.remove(key, ourFuture);
      ourFuture.complete(second);
      return castFuture(ourFuture);
    }
    // 持有加载权：仅本线程记录加载统计（等待方共享同一 Future，不重复计数）
    long loadStart = System.nanoTime();
    loader
        .apply(key)
        .whenComplete(
            (v, err) -> {
              try {
                totalLoadTimeNanos.add(System.nanoTime() - loadStart);
                if (err == null) {
                  loadSuccessCount.increment();
                  if (v != null) {
                    put(key, v);
                  }
                } else {
                  loadExceptionCount.increment();
                }
              } finally {
                asyncLoadingFutures.remove(key, ourFuture);
                if (err != null) {
                  ourFuture.completeExceptionally(err);
                } else {
                  ourFuture.complete(v);
                }
              }
            });
    return castFuture(ourFuture);
  }

  /**
   * 异步单飞 Future 映射（key -> 进行中的加载 Future），与同步信号 {@code atomicSignals} 分离。
   */
  private final ConcurrentHashMap<Object, CompletableFuture<Object>> asyncLoadingFutures =
      new ConcurrentHashMap<>();

  /**
   * 信号 Future 的类型安全转换（Future 实际承载的即为 V 实例）。
   *
   * @param <T> 目标值类型
   * @param future 原始 Future
   * @return 转换后的 Future
   */
  @SuppressWarnings("unchecked")
  private <T> CompletableFuture<T> castFuture(CompletableFuture<Object> future) {
    return (CompletableFuture<T>) future;
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
   * <p>完整统计（对标 Caffeine recordStats）：命中/未命中/淘汰计数之外， 包含 {@code get(key, loader)}、
   * {@code getAsync} 与 {@code getAll(keys, loader)} 路径的 加载成功/异常次数与总加载耗时。 loadCount =
   * 加载成功次数 + 加载异常次数。
   *
   * @return 缓存统计快照
   */
  @Override
  public CacheStats getStats() {
    long success = loadSuccessCount.sum();
    long failure = loadExceptionCount.sum();
    return new CacheStats(
        hitCount.sum(),
        missCount.sum(),
        evictionCount.sum(),
        success + failure,
        success,
        failure,
        totalLoadTimeNanos.sum());
  }

  /**
   * 重置命中/未命中与加载统计计数器。
   *
   * <p>清零 {@code hitCount}、{@code missCount} 与全部加载统计（次数/耗时）， 不重置淘汰计数 {@code
   * evictionCount}，也不清空缓存数据本身。 用于在监控周期切换时重新起算命中率与平均加载耗时。
   */
  @Override
  public void resetStats() {
    hitCount.reset();
    missCount.reset();
    loadSuccessCount.reset();
    loadExceptionCount.reset();
    totalLoadTimeNanos.reset();
  }

  /**
   * 批量获取，未命中的键集合由加载器一次性批量加载（记录批量加载统计）。
   *
   * <p>单次批量调用计一次加载（成功或异常）并累计整批耗时—— 对标 Caffeine 的批量加载统计口径，避免 N
   * 个缺失键膨胀为 N 次加载。
   *
   * @param keys 待查询的键集合
   * @param loader 批量加载器，入参为缺失键集合，返回值为键到值的映射
   * @return 命中与加载合并后的结果映射（不含加载不到的键）
   */
  @Override
  public Map<K, V> getAll(Collection<K> keys, Function<Set<K>, Map<K, V>> loader) {
    Map<K, V> result = getAll(keys);
    if (loader == null || keys == null || keys.isEmpty()) {
      return result;
    }
    Set<K> missing = new LinkedHashSet<>(keys);
    missing.removeAll(result.keySet());
    if (missing.isEmpty()) {
      return result;
    }
    long start = System.nanoTime();
    Map<K, V> loaded;
    try {
      loaded = loader.apply(missing);
    } catch (Exception e) {
      totalLoadTimeNanos.add(System.nanoTime() - start);
      loadExceptionCount.increment();
      throw e;
    }
    totalLoadTimeNanos.add(System.nanoTime() - start);
    loadSuccessCount.increment();
    if (loaded != null) {
      for (Map.Entry<K, V> entry : loaded.entrySet()) {
        if (entry.getKey() != null && entry.getValue() != null) {
          put(entry.getKey(), entry.getValue());
          result.put(entry.getKey(), entry.getValue());
        }
      }
    }
    return result;
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
