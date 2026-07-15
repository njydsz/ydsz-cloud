package com.njydsz.pmis.common.cache.internal.decorator;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import com.njydsz.pmis.common.cache.api.Cache;
import com.njydsz.pmis.common.cache.listener.RemovalListener;
import com.njydsz.pmis.common.cache.stats.CacheStats;
import com.njydsz.pmis.common.cache.support.AsyncFunction;

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
 * 
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

  @Override
  public V getIfPresent(K key) {
    return delegate.getIfPresent(key);
  }

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

  @Override
  public void put(K key, V value) {
    if (value != null && cacheValuePredicate.test(value) && cacheKeyPredicate.test(value)) {
      delegate.put(key, value);
    }
  }

  @Override
  public V putIfAbsent(K key, V value) {
    if (value != null && cacheValuePredicate.test(value) && cacheKeyPredicate.test(value)) {
      return delegate.putIfAbsent(key, value);
    }
    return delegate.getIfPresent(key);
  }

  // === 以下方法直接委托 ===

  @Override
  public V computeIfAbsent(K key, Function<K, V> mappingFunction) {
    return delegate.computeIfAbsent(key, mappingFunction);
  }

  @Override
  public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    return delegate.compute(key, remappingFunction);
  }

  @Override
  public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
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
    if (map == null || map.isEmpty()) return;
    map.forEach(this::put);
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
  public void resetStats() {
    delegate.resetStats();
  }

  @Override
  public com.njydsz.pmis.common.cache.api.CachePolicy policy() {
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
  public void addListener(RemovalListener<? super K, ? super V> listener) {
    delegate.addListener(listener);
  }

  @Override
  public void forEach(BiConsumer<? super K, ? super V> action) {
    delegate.forEach(action);
  }
}
