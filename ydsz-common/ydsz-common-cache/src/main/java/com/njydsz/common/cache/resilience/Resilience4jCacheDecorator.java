package com.njydsz.common.cache.resilience;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.listener.RemovalListener;
import com.njydsz.common.cache.stats.CacheStats;
import com.njydsz.common.cache.support.AsyncFunction;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * Resilience4j 熔断降级缓存装饰器
 *
 * <p>使用 Resilience4j CircuitBreaker 包装缓存操作，当后端缓存（如 Redis）不可用时， 自动降级为直接返回 null（让调用方回退到 L1
 * 本地缓存或数据源加载）。
 *
 * <p>熔断策略：
 *
 * <ul>
 *   <li>滑动窗口大小：100 次请求
 *   <li>失败率阈值：50%
 *   <li>熔断打开后等待时间：10 秒
 *   <li>半开状态允许请求数：10
 * </ul>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author ydsz-team
 *
 * @since 1.0.0
 */
public class Resilience4jCacheDecorator<K, V> implements Cache<K, V> {

  private static final Logger log = LoggerFactory.getLogger(Resilience4jCacheDecorator.class);

  private final Cache<K, V> delegate;
  private final CircuitBreaker circuitBreaker;

  /**
   * 创建熔断降级缓存装饰器（使用默认熔断配置）
   *
   * @param delegate 被装饰的缓存实例
   * @param circuitBreakerName 熔断器名称
   */
  public Resilience4jCacheDecorator(Cache<K, V> delegate, String circuitBreakerName) {
    this(delegate, createDefaultCircuitBreaker(circuitBreakerName));
  }

  /**
   * 创建熔断降级缓存装饰器（使用自定义熔断器）
   *
   * @param delegate 被装饰的缓存实例
   * @param circuitBreaker 熔断器实例
   */
  public Resilience4jCacheDecorator(Cache<K, V> delegate, CircuitBreaker circuitBreaker) {
    this.delegate = delegate;
    this.circuitBreaker = circuitBreaker;
  }

  /** 创建默认熔断器 */
  private static CircuitBreaker createDefaultCircuitBreaker(String name) {
    CircuitBreakerConfig config =
        CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(100)
            .failureRateThreshold(50.0f)
            .waitDurationInOpenState(Duration.ofSeconds(10))
            .permittedNumberOfCallsInHalfOpenState(10)
            .build();
    CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
    return registry.circuitBreaker(name, config);
  }

  @Override
  public V getIfPresent(K key) {
    return CircuitBreaker.decorateSupplier(
            circuitBreaker,
            () -> {
              try {
                return delegate.getIfPresent(key);
              } catch (Exception e) {
                log.debug("缓存读取异常（熔断器计数）, key={}", key, e);
                throw e;
              }
            })
        .get();
  }

  @Override
  public V get(K key, Function<K, V> loader) {
    return CircuitBreaker.decorateSupplier(circuitBreaker, () -> delegate.get(key, loader)).get();
  }

  @Override
  public CompletableFuture<V> getAsync(K key, AsyncFunction<K, V> loader) {
    return CircuitBreaker.decorateCompletionStage(
            circuitBreaker, () -> delegate.getAsync(key, loader))
        .get()
        .toCompletableFuture();
  }

  @Override
  public void put(K key, V value) {
    CircuitBreaker.decorateRunnable(circuitBreaker, () -> delegate.put(key, value)).run();
  }

  @Override
  public V remove(K key) {
    return CircuitBreaker.decorateSupplier(circuitBreaker, () -> delegate.remove(key)).get();
  }

  @Override
  public void clear() {
    CircuitBreaker.decorateRunnable(circuitBreaker, delegate::clear).run();
  }

  @Override
  public long estimatedSize() {
    return CircuitBreaker.decorateSupplier(circuitBreaker, delegate::estimatedSize).get();
  }

  @Override
  public boolean containsKey(K key) {
    return CircuitBreaker.decorateSupplier(circuitBreaker, () -> delegate.containsKey(key)).get();
  }

  @Override
  public Set<K> keySet() {
    return CircuitBreaker.decorateSupplier(circuitBreaker, delegate::keySet).get();
  }

  @Override
  public Collection<V> values() {
    return CircuitBreaker.decorateSupplier(circuitBreaker, delegate::values).get();
  }

  @Override
  public Map<K, V> getAll(Collection<K> keys) {
    return CircuitBreaker.decorateSupplier(circuitBreaker, () -> delegate.getAll(keys)).get();
  }

  @Override
  public void putAll(Map<K, V> map) {
    CircuitBreaker.decorateRunnable(circuitBreaker, () -> delegate.putAll(map)).run();
  }

  @Override
  public void removeAll(Collection<K> keys) {
    CircuitBreaker.decorateRunnable(circuitBreaker, () -> delegate.removeAll(keys)).run();
  }

  @Override
  public void invalidate(K key) {
    CircuitBreaker.decorateRunnable(circuitBreaker, () -> delegate.invalidate(key)).run();
  }

  @Override
  public void invalidateAll(Collection<K> keys) {
    CircuitBreaker.decorateRunnable(circuitBreaker, () -> delegate.invalidateAll(keys)).run();
  }

  @Override
  public void invalidateAll() {
    CircuitBreaker.decorateRunnable(circuitBreaker, delegate::invalidateAll).run();
  }

  @Override
  public V computeIfAbsent(K key, Function<K, V> mappingFunction) {
    return CircuitBreaker.decorateSupplier(
            circuitBreaker, () -> delegate.computeIfAbsent(key, mappingFunction))
        .get();
  }

  @Override
  public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    return CircuitBreaker.decorateSupplier(
            circuitBreaker, () -> delegate.compute(key, remappingFunction))
        .get();
  }

  @Override
  public void forEach(BiConsumer<? super K, ? super V> action) {
    CircuitBreaker.decorateRunnable(circuitBreaker, () -> delegate.forEach(action)).run();
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
  public void addListener(RemovalListener<? super K, ? super V> listener) {
    delegate.addListener(listener);
  }

  @Override
  public void cleanUp() {
    CircuitBreaker.decorateRunnable(circuitBreaker, delegate::cleanUp).run();
  }

  /** 获取熔断器状态 */
  public CircuitBreaker.State getCircuitBreakerState() {
    return circuitBreaker.getState();
  }

  /** 获取被装饰的原始缓存 */
  public Cache<K, V> getDelegate() {
    return delegate;
  }
}
