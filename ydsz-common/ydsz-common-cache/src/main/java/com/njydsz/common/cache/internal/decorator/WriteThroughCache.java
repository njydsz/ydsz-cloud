package com.njydsz.common.cache.internal.decorator;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.listener.RemovalCause;
import com.njydsz.common.cache.listener.RemovalListener;
import com.njydsz.common.cache.stats.CacheStats;
import com.njydsz.common.cache.support.AsyncFunction;
import com.njydsz.common.cache.support.CacheWriter;

/**
 * 写穿透缓存装饰器 - 数据同时写入缓存和后端存储
 *
 * <p>核心特性：
 *
 * <ul>
 *   <li>写穿透：put 操作同步写入后端存储，保证数据一致性
 *   <li>删除传播：remove 操作同步从后端存储删除
 *   <li>批量写入：putAll 批量写入后端存储，减少网络开销
 *   <li>异常传播：后端存储写入失败时抛出异常，避免数据不一致
 * </ul>
 *
 * <p>工作原理：
 *
 * <ol>
 *   <li>put 操作：先写入后端存储，成功后更新缓存
 *   <li>remove 操作：先从缓存获取值，然后同步删除后端存储和缓存
 *   <li>clear 操作：遍历所有缓存项，逐个从后端存储删除
 * </ol>
 *
 * <p>适用场景：
 *
 * <ul>
 *   <li>数据库缓存：写穿透保证缓存与数据库一致性
 *   <li>配置中心：配置更新需要同步持久化
 *   <li>用户会话：会话数据需要持久化存储
 * </ul>
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * Cache<String, User> cache = YdszCache.createLRUCache(1000);
 * CacheWriter<String, User> writer = new UserCacheWriter(userDao);
 * Cache<String, User> writeThrough = YdszCache.createWriteThroughCache(cache, writer);
 *
 * writeThrough.put("user:1", newUser); // 同步写入数据库
 * }</pre>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author ydsz-team
 * @since 1.0.0
 *
 */
public class WriteThroughCache<K, V> implements Cache<K, V> {

  /** 底层缓存 */
  private final Cache<K, V> delegate;

  /** 缓存写入器 */
  private final CacheWriter<? super K, ? super V> writer;

  /** 统计计数器 */
  private final LongAdder hitCount = new LongAdder();

  private final LongAdder missCount = new LongAdder();
  private final LongAdder writeCount = new LongAdder();
  private final LongAdder deleteCount = new LongAdder();

  /** 删除监听器 */
  private final List<RemovalListener<? super K, ? super V>> listeners =
      new CopyOnWriteArrayList<>();

  /**
   * 创建写穿透缓存
   *
   * @param delegate 底层缓存
   * @param writer 缓存写入器
   */
  public WriteThroughCache(Cache<K, V> delegate, CacheWriter<? super K, ? super V> writer) {
    this.delegate = delegate;
    this.writer = writer;
  }

  /**
   * 获取缓存值（不触发加载），并更新命中统计。
   *
   * @param key 缓存键
   * @return 缓存值；未命中时返回 {@code null}
   */
  @Override
  public V getIfPresent(K key) {
    V value = delegate.getIfPresent(key);
    if (value != null) {
      hitCount.increment();
    } else {
      missCount.increment();
    }
    return value;
  }

  /**
   * 获取缓存值，未命中时使用加载器加载，并更新命中统计。
   *
   * @param key    缓存键
   * @param loader 值加载器
   * @return 缓存值或加载的新值
   */
  @Override
  public V get(K key, Function<K, V> loader) {
    V value = delegate.get(key, loader);
    if (value != null) {
      hitCount.increment();
    } else {
      missCount.increment();
    }
    return value;
  }

  /**
   * 异步获取缓存值（直接委托，不参与命中统计）。
   *
   * @param key    缓存键
   * @param loader 异步值加载器
   * @return 异步完成的缓存值
   */
  @Override
  public CompletableFuture<V> getAsync(K key, AsyncFunction<K, V> loader) {
    return delegate.getAsync(key, loader);
  }

  /**
   * 写入键值对：先同步写后端存储，成功后写缓存。
   *
   * <p>采用"先持久层、后缓存"的顺序，后端写入抛异常时缓存保持原值不变，
   * 避免缓存与数据库不一致。写成功时递增写入计数。
   *
   * @param key   缓存键
   * @param value 缓存值
   * @throws RuntimeException 当后端写入失败时抛出（由 {@link CacheWriter#write} 决定具体异常类型）
   */
  @Override
  public void put(K key, V value) {
    // Write-Through: 先写持久层，再写缓存
    // 如果持久层写入失败，缓存不更新，保证一致性
    writer.write(key, value);
    delegate.put(key, value);
    writeCount.increment();
  }

  /**
   * 移除指定键：先同步删除后端存储，成功后删除缓存。
   *
   * <p>缓存中不存在该键时仍尝试从后端删除（携带 null 值），保证后端数据被清理；
   * 删除成功后向监听器发出 {@link RemovalCause#EXPLICIT} 通知并递增删除计数。
   *
   * @param key 缓存键
   * @return 被移除的缓存值；键不存在时返回 {@code null}
   * @throws RuntimeException 当后端删除失败时抛出（由 {@link CacheWriter#delete} 决定具体异常类型）
   */
  @Override
  public V remove(K key) {
    V value = delegate.getIfPresent(key);
    // 先删除持久层，再删除缓存
    // 如果持久层删除失败，缓存不删除，保证一致性
    if (value != null) {
      writer.delete(key, value);
      delegate.remove(key);
      deleteCount.increment();
      notifyRemoval(key, value, RemovalCause.EXPLICIT);
    } else {
      // 缓存中不存在，仍尝试从持久层删除
      writer.delete(key, null);
      delegate.remove(key);
    }
    return value;
  }

  /**
   * 清空缓存：先逐个从后端存储删除全部条目，再清空缓存。
   *
   * <p>逐条调用 {@link CacheWriter#delete} 同步删除后端数据， 并向监听器发出
   * {@link RemovalCause#EXPLICIT} 通知。任一后端删除失败都会中断清空流程。
   */
  @Override
  public void clear() {
    delegate.forEach(
        (key, value) -> {
          writer.delete(key, value);
          notifyRemoval(key, value, RemovalCause.EXPLICIT);
        });
    delegate.clear();
  }

  /**
   * 返回缓存条目数（近似值）。
   *
   * @return 底层缓存条目数
   */
  @Override
  public long estimatedSize() {
    return delegate.estimatedSize();
  }

  /**
   * 判断缓存中是否存在指定键。
   *
   * @param key 缓存键
   * @return 底层缓存存在该键时返回 {@code true}
   */
  @Override
  public boolean containsKey(K key) {
    return delegate.containsKey(key);
  }

  /**
   * 返回缓存键集合视图。
   *
   * @return 底层缓存的键集合视图
   */
  @Override
  public Set<K> keySet() {
    return delegate.keySet();
  }

  /**
   * 返回缓存值集合视图。
   *
   * @return 底层缓存的值集合视图
   */
  @Override
  public Collection<V> values() {
    return delegate.values();
  }

  /**
   * 获取缓存命中率。
   *
   * <p>仅统计经 {@link #getIfPresent} / {@link #get} 路径的访问；异步获取不计入。
   *
   * @return 命中率，范围 [0.0, 1.0]
   */
  @Override
  public double getHitRate() {
    long total = hitCount.sum() + missCount.sum();
    return total == 0 ? 0.0 : (double) hitCount.sum() / total;
  }

  /**
   * 获取缓存统计快照。
   *
   * @return 包含命中数与未命中数的统计对象
   */
  @Override
  public CacheStats getStats() {
    return new CacheStats(hitCount.sum(), missCount.sum());
  }

  /**
   * 添加删除监听器。
   *
   * <p>监听器由本类维护（非透传底层），仅在写穿透删除路径（remove/clear/removeAll）触发。
   *
   * @param listener 删除监听器
   */
  @Override
  public void addListener(RemovalListener<? super K, ? super V> listener) {
    listeners.add(listener);
  }

  /**
   * 计算并写入缓存（直接委托，不写后端）。
   *
   * <p>注意：此路径不会同步后端存储，需要持久化时请使用 {@link #put}。
   *
   * @param key             缓存键
   * @param mappingFunction 映射函数
   * @return 计算后的值
   */
  @Override
  public V computeIfAbsent(K key, Function<K, V> mappingFunction) {
    return delegate.computeIfAbsent(key, mappingFunction);
  }

  /**
   * 基于旧值重新计算映射并写回缓存（直接委托，不写后端）。
   *
   * @param key               缓存键
   * @param remappingFunction 重映射函数
   * @return 重映射后的值
   */
  @Override
  public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    return delegate.compute(key, remappingFunction);
  }

  /**
   * 遍历缓存键值对。
   *
   * @param action 作用于每个键值对的消费动作
   */
  @Override
  public void forEach(BiConsumer<? super K, ? super V> action) {
    delegate.forEach(action);
  }

  /**
   * 批量获取指定键的缓存值（不触发加载）。
   *
   * @param keys 待获取的键集合
   * @return 命中键值映射；未命中的键不会出现在结果中
   */
  @Override
  public Map<K, V> getAll(Collection<K> keys) {
    return delegate.getAll(keys);
  }

  /**
   * 批量写入：先全部同步写后端存储，成功后写缓存。
   *
   * <p>任一后端写入失败都会中断并抛异常，此时缓存保持未更新状态。
   *
   * @param map 待写入的映射
   * @throws RuntimeException 当后端批量写入失败时抛出
   */
  @Override
  public void putAll(Map<K, V> map) {
    for (Map.Entry<K, V> entry : map.entrySet()) {
      writer.write(entry.getKey(), entry.getValue());
    }
    delegate.putAll(map);
    writeCount.add(map.size());
  }

  /**
   * 批量移除指定键：逐个同步删除后端存储（仅对缓存中存在的键），再删除缓存。
   *
   * @param keys 待移除的键集合
   * @throws RuntimeException 当后端删除失败时抛出
   */
  @Override
  public void removeAll(Collection<K> keys) {
    for (K key : keys) {
      V value = delegate.getIfPresent(key);
      if (value != null) {
        writer.delete(key, value);
        deleteCount.increment();
        notifyRemoval(key, value, RemovalCause.EXPLICIT);
      }
    }
    delegate.removeAll(keys);
  }

  /**
   * 使单个键失效（等价于 {@link #remove}，会同步删除后端数据）。
   *
   * @param key 缓存键
   */
  @Override
  public void invalidate(K key) {
    remove(key);
  }

  /**
   * 批量使指定键集合失效（等价于 {@link #removeAll}）。
   *
   * @param keys 待失效的键集合
   */
  @Override
  public void invalidateAll(Collection<K> keys) {
    removeAll(keys);
  }

  /**
   * 使全部键失效（等价于 {@link #clear}）。
   */
  @Override
  public void invalidateAll() {
    clear();
  }

  /**
   * 通知删除监听器
   *
   * @param key 缓存键
   * @param value 缓存值
   * @param cause 删除原因
   */
  protected void notifyRemoval(K key, V value, RemovalCause cause) {
    for (RemovalListener<? super K, ? super V> listener : listeners) {
      try {
        listener.onRemoval(key, value, cause);
      } catch (Exception e) {
        // 忽略监听器异常
      }
    }
  }

  /**
   * 获取写入次数
   *
   * @return 写入次数
   */
  public long getWriteCount() {
    return writeCount.sum();
  }

  /**
   * 获取删除次数
   *
   * @return 删除次数
   */
  public long getDeleteCount() {
    return deleteCount.sum();
  }
}
