package com.njydsz.common.cache.internal.decorator;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.api.CachePolicy;
import com.njydsz.common.cache.listener.RemovalListener;
import com.njydsz.common.cache.stats.CacheStats;
import com.njydsz.common.cache.support.AsyncFunction;

/**
 * 缓存装饰器抽象基类 — 提供统一的底层缓存委托能力
 *
 * <p>所有装饰器应继承此类而非直接实现 {@link Cache} 接口，以避免大量重复的委托样板代码。
 * 子类仅需覆盖需要自定义行为的方法，其余方法自动委托给 {@link #delegate}。
 *
 * <p>注意：本类的所有方法默认直接委托，不做任何额外处理。如需组合多个装饰器，
 * 请在构造时将内层装饰器作为 {@code delegate} 传入即可形成装饰链。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author ydsz-team
 * @since 1.0.0
 */
public abstract class AbstractCacheDecorator<K, V> implements Cache<K, V> {

  /** 被装饰的底层缓存 */
  protected final Cache<K, V> delegate;

  /**
   * 创建缓存装饰器
   *
   * @param delegate 被装饰的底层缓存
   */
  protected AbstractCacheDecorator(Cache<K, V> delegate) {
    this.delegate = delegate;
  }

  /**
   * 获取被装饰的底层缓存
   *
   * @return 底层缓存实例
   */
  public Cache<K, V> getDelegate() {
    return delegate;
  }

  @Override
  public V getIfPresent(K key) {
    return delegate.getIfPresent(key);
  }

  @Override
  public V get(K key, Function<K, V> loader) {
    return delegate.get(key, loader);
  }

  @Override
  public CompletableFuture<V> getAsync(K key, AsyncFunction<K, V> loader) {
    return delegate.getAsync(key, loader);
  }

  @Override
  public boolean containsKey(K key) {
    return delegate.containsKey(key);
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
  public void resetStats() {
    delegate.resetStats();
  }

  @Override
  public CachePolicy policy() {
    return delegate.policy();
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
