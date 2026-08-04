package com.remisoft.common.cache.internal.decorator;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import com.remisoft.common.cache.api.Cache;
import com.remisoft.common.cache.listener.RemovalListener;
import com.remisoft.common.cache.stats.CacheStats;
import com.remisoft.common.cache.support.AsyncFunction;

import com.remisoft.common.cache.api.CachePolicy;
/**
 * 条件缓存装饰器 — 通过 Predicate 控制哪些值应该被缓存
 *
 * <p>应用场景：
 *
 * <ul>
 *   <li>只缓存非空值
 *   <li>只缓存有效状态的数据（如 status=ACTIVE）
 *   <li>只缓存小于特定大小的数据
 *   <li>基于业务规则动态决定是否缓存
 * </ul>
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * Cache<String, User> cache = new ConditionalCacheDecorator<>(
 *     delegateCache,
 *     user -> user != null && user.isActive(),
 *     user -> user != null && user.getId() != null);
 * }</pre>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author remi-team
 * 
 * @since 1.0.0
 */
public class ConditionalCacheDecorator<K, V> implements Cache<K, V> {

  private final Cache<K, V> delegate;
  private final Predicate<V> cacheValuePredicate;
  private final Predicate<V> cacheKeyPredicate;

  /**
   * 创建条件缓存装饰器
   *
   * @param delegate 底层缓存
   * @param cacheValuePredicate 值缓存条件（true 才缓存）
   * @param cacheKeyPredicate key 生成条件（可选，默认始终 true）
   */
  public ConditionalCacheDecorator(
      Cache<K, V> delegate,
      Predicate<V> cacheValuePredicate,
      Predicate<V> cacheKeyPredicate) {
    this.delegate = delegate;
    this.cacheValuePredicate = cacheValuePredicate != null ? cacheValuePredicate : v -> true;
    this.cacheKeyPredicate = cacheKeyPredicate != null ? cacheKeyPredicate : v -> true;
  }

  /**
   * 创建条件缓存装饰器（仅值条件）
   *
   * @param delegate 底层缓存
   * @param cacheValuePredicate 值缓存条件
   */
  public ConditionalCacheDecorator(Cache<K, V> delegate, Predicate<V> cacheValuePredicate) {
    this(delegate, cacheValuePredicate, null);
  }

  /**
   * 获取缓存值（不触发加载）。
   *
   * <p>与普通缓存一致，直接透传底层缓存；未命中时返回 null，不经过任何条件过滤。
   *
   * @param key 缓存键
   * @return 缓存值；未命中时返回 {@code null}
   */
  @Override
  public V getIfPresent(K key) {
    return delegate.getIfPresent(key);
  }

  /**
   * 获取缓存值，未命中时使用加载器加载。
   *
   * <p>加载结果只有通过值缓存条件 {@code cacheValuePredicate} 才会写回缓存，
   * 不满足条件的值仅返回给调用方而不落缓存，避免把"不应缓存"的数据占用内存。
   *
   * @param key    缓存键
   * @param loader 值加载器
   * @return 缓存值或加载器产生的新值
   */
  @Override
  public V get(K key, Function<K, V> loader) {
    V value = getIfPresent(key);
    if (value == null && loader != null) {
      value = loader.apply(key);
      if (value != null && cacheValuePredicate.test(value)) {
        put(key, value);
      }
    }
    return value;
  }

  /**
   * 异步获取缓存值，未命中时使用异步加载器加载。
   *
   * <p>与 {@link #get} 相同的条件过滤语义：加载结果通过值缓存条件才写回缓存。
   *
   * @param key    缓存键
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
              if (v != null && cacheValuePredicate.test(v)) {
                put(key, v);
              }
              return v;
            });
  }

  /**
   * 写入键值对，仅当值通过全部缓存条件时才真正落缓存。
   *
   * <p>值缓存条件 {@code cacheValuePredicate} 与键缓存条件 {@code cacheKeyPredicate}
   * 均需满足；任一条件不满足则静默丢弃本次写入（不抛异常、无副作用）。
   *
   * @param key   缓存键
   * @param value 缓存值
   */
  @Override
  public void put(K key, V value) {
    if (value != null && cacheValuePredicate.test(value) && cacheKeyPredicate.test(value)) {
      delegate.put(key, value);
    }
  }

  /**
   * 仅当键不存在且值通过缓存条件时写入并返回旧值。
   *
   * <p>值不满足缓存条件时放弃写入，仅返回当前已存在的值（可能为 null）。
   *
   * @param key   缓存键
   * @param value 缓存值
   * @return 已存在的旧值；键原本不存在时返回 {@code null}
   */
  @Override
  public V putIfAbsent(K key, V value) {
    if (value != null && cacheValuePredicate.test(value) && cacheKeyPredicate.test(value)) {
      return delegate.putIfAbsent(key, value);
    }
    return delegate.getIfPresent(key);
  }

  // === 以下方法直接委托 ===

  /**
   * 计算并写入缓存（直接委托，不经过条件过滤）。
   *
   * <p>注意：本方法绕过了条件过滤，映射结果会无条件写回底层缓存， 需要条件语义时请改用 {@link #get}。
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
   * 基于旧值重新计算映射并写回缓存（直接委托，不经过条件过滤）。
   *
   * @param key                缓存键
   * @param remappingFunction  重映射函数
   * @return 重映射后的值
   */
  @Override
  public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    return delegate.compute(key, remappingFunction);
  }

  /**
   * 合并值与现有值（直接委托，不经过条件过滤）。
   *
   * @param key               缓存键
   * @param value             待合并的值
   * @param remappingFunction 合并函数
   * @return 合并后的值
   */
  @Override
  public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
    return delegate.merge(key, value, remappingFunction);
  }

  /**
   * 移除指定键并返回被移除的值。
   *
   * @param key 缓存键
   * @return 被移除的值；键不存在时返回 {@code null}
   */
  @Override
  public V remove(K key) {
    return delegate.remove(key);
  }

  /**
   * 使单个键失效（等价于 {@link #remove}）。
   *
   * @param key 缓存键
   */
  @Override
  public void invalidate(K key) {
    delegate.invalidate(key);
  }

  /**
   * 批量使指定键集合失效。
   *
   * @param keys 待失效的键集合
   */
  @Override
  public void invalidateAll(Collection<K> keys) {
    delegate.invalidateAll(keys);
  }

  /**
   * 使全部键失效（等价于 {@link #clear}）。
   */
  @Override
  public void invalidateAll() {
    delegate.invalidateAll();
  }

  /**
   * 清空缓存。
   */
  @Override
  public void clear() {
    delegate.clear();
  }

  /**
   * 批量写入，逐条应用条件过滤后再落缓存。
   *
   * <p>通过 {@code map.forEach(this::put)} 委托，因此不满足缓存条件的条目会被静默丢弃。
   *
   * @param map 待写入的映射
   */
  @Override
  public void putAll(Map<K, V> map) {
    if (map == null || map.isEmpty()) return;
    map.forEach(this::put);
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
   * 批量移除指定键。
   *
   * @param keys 待移除的键集合
   */
  @Override
  public void removeAll(Collection<K> keys) {
    delegate.removeAll(keys);
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
   * 判断缓存是否为空。
   *
   * @return 底层缓存无条目时返回 {@code true}
   */
  @Override
  public boolean isEmpty() {
    return delegate.isEmpty();
  }

  /**
   * 获取缓存命中率。
   *
   * @return 底层缓存的命中率
   */
  @Override
  public double getHitRate() {
    return delegate.getHitRate();
  }

  /**
   * 获取缓存统计快照。
   *
   * @return 底层缓存的统计对象
   */
  @Override
  public CacheStats getStats() {
    return delegate.getStats();
  }

  /**
   * 重置统计计数器。
   */
  @Override
  public void resetStats() {
    delegate.resetStats();
  }

  /**
   * 获取缓存策略查询接口。
   *
   * @return 底层缓存的策略接口
   */
  @Override
  public CachePolicy policy() {
    return delegate.policy();
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
   * 执行缓存维护操作（清理过期条目等）。
   */
  @Override
  public void cleanUp() {
    delegate.cleanUp();
  }

  /**
   * 添加删除监听器。
   *
   * @param listener 删除监听器
   */
  @Override
  public void addListener(RemovalListener<? super K, ? super V> listener) {
    delegate.addListener(listener);
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
}
