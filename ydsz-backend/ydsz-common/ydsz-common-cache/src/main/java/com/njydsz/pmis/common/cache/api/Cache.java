package com.njydsz.common.cache.api;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.njydsz.common.cache.listener.RemovalListener;
import com.njydsz.common.cache.stats.CacheStats;
import com.njydsz.common.cache.support.AsyncFunction;

/**
 * 缓存基础接口，定义基本缓存操作
 *
 * <p>参考 Caffeine/Guava Cache API 设计，提供完整的缓存操作能力：
 *
 * <ul>
 *   <li>查询操作：get、getIfPresent、containsKey
 *   <li>写入操作：put、putIfAbsent、compute、computeIfAbsent
 *   <li>删除操作：remove、invalidate、invalidateAll、clear
 *   <li>批量操作：getAll、putAll、removeAll
 *   <li>统计信息：getStats、getHitRate、estimatedSize
 *   <li>视图操作：asMap、keySet、values
 *   <li>维护操作：cleanUp
 * </ul>
 *
 * <p>防穿透/击穿/雪崩防护请使用 {@link CacheProtectionGuard}。 空值占位管理请使用 {@link NullValueGuard}。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * 
 */
public interface Cache<K, V> {

  // ============================================================================
  // 查询操作
  // ============================================================================

  /**
   * 获取缓存值（如果存在）
   *
   * @param key 缓存键
   * @return 缓存值，如果不存在则返回 null
   */
  V getIfPresent(K key);

  /**
   * 获取缓存值，如果不存在则使用加载器加载
   *
   * @param key 缓存键
   * @param loader 加载器
   * @return 缓存值
   */
  default V get(K key, Function<K, V> loader) {
    V value = getIfPresent(key);
    if (value == null) {
      value = loader.apply(key);
      if (value != null) {
        put(key, value);
      }
    }
    return value;
  }

  /** 异步获取缓存值 */
  CompletableFuture<V> getAsync(K key, AsyncFunction<K, V> loader);

  /** 检查是否包含指定键 */
  boolean containsKey(K key);

  // ============================================================================
  // 写入操作
  // ============================================================================

  /** 放入缓存 */
  void put(K key, V value);

  /** 如果键不存在则放入缓存 */
  default V putIfAbsent(K key, V value) {
    V existing = getIfPresent(key);
    if (existing == null) {
      put(key, value);
      return null;
    }
    return existing;
  }

  /** 如果键不存在则计算并放入缓存 */
  default V computeIfAbsent(K key, Function<K, V> mappingFunction) {
    V value = getIfPresent(key);
    if (value == null) {
      value = mappingFunction.apply(key);
      if (value != null) {
        put(key, value);
      }
    }
    return value;
  }

  /** 重新计算映射值 */
  default V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
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
  }

  /** 合并值 */
  default V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
    V oldValue = getIfPresent(key);
    V newValue = (oldValue == null) ? value : remappingFunction.apply(oldValue, value);
    if (newValue == null) {
      remove(key);
    } else {
      put(key, newValue);
    }
    return newValue;
  }

  // ============================================================================
  // 删除操作
  // ============================================================================

  /** 从缓存中移除指定键 */
  V remove(K key);

  /** 使键失效（等同于 remove） */
  default void invalidate(K key) {
    remove(key);
  }

  /** 使多个键失效 */
  default void invalidateAll(Collection<K> keys) {
    removeAll(keys);
  }

  /** 使所有键失效（等同于 clear） */
  default void invalidateAll() {
    clear();
  }

  /** 清空缓存 */
  void clear();

  // ============================================================================
  // 批量操作
  // ============================================================================

  /** 批量放入 */
  default void putAll(Map<K, V> map) {
    if (map == null || map.isEmpty()) {
      return;
    }
    map.forEach(this::put);
  }

  /** 批量获取 */
  default Map<K, V> getAll(Collection<K> keys) {
    if (keys == null || keys.isEmpty()) {
      return Collections.emptyMap();
    }

    Map<K, V> result = new HashMap<>(keys.size());
    for (K key : keys) {
      V value = getIfPresent(key);
      if (value != null) {
        result.put(key, value);
      }
    }
    return result;
  }

  /** 批量删除 */
  default void removeAll(Collection<K> keys) {
    if (keys == null || keys.isEmpty()) {
      return;
    }
    for (K key : keys) {
      remove(key);
    }
  }

  // ============================================================================
  // 元信息 / 统计
  // ============================================================================

  /** 获取缓存大小（估计值） */
  long estimatedSize();

  /** 缓存是否为空 */
  default boolean isEmpty() {
    return estimatedSize() == 0;
  }

  /** 获取命中率 */
  double getHitRate();

  /** 获取统计信息 */
  CacheStats getStats();

  /** 重置统计计数器（命中/未命中归零） */
  default void resetStats() {
    // 默认空实现，由支持的缓存覆写
  }

  /**
   * 获取缓存策略查询接口
   *
   * <p>允许在运行时查询和调整缓存策略（淘汰、过期等）。 不是所有缓存类型都支持策略查询，不支持时返回 Optional.empty()。
   *
   * @return 缓存策略，如果不支持则各子接口返回 Optional.empty()
   */
  default CachePolicy policy() {
    // 默认实现：不支持任何策略查询
    return new CachePolicy() {
      @Override
      public Optional<EvictionPolicy> eviction() {
        return Optional.empty();
      }

      @Override
      public Optional<ExpirationPolicy> expiration() {
        return Optional.empty();
      }
    };
  }

  // ============================================================================
  // 视图操作
  // ============================================================================

  /** 获取所有键 */
  Set<K> keySet();

  /** 获取所有值 */
  Collection<V> values();

  /** 获取缓存的 Map 视图 */
  default Map<K, V> asMap() {
    return new CacheAsMapView<>(this);
  }

  // ============================================================================
  // 维护操作
  // ============================================================================

  /** 执行缓存维护操作 */
  default void cleanUp() {}

  // ============================================================================
  // 监听器
  // ============================================================================

  /** 添加删除监听器 */
  default void addListener(RemovalListener<? super K, ? super V> listener) {}

  // ============================================================================
  // 遍历
  // ============================================================================

  /** 遍历缓存 */
  default void forEach(BiConsumer<? super K, ? super V> action) {
    for (K key : keySet()) {
      V value = getIfPresent(key);
      if (value != null) {
        action.accept(key, value);
      }
    }
  }

  // ============================================================================
  // 缓存防护（穿透/雪崩/击穿）— 委托给 CacheProtectionGuard
  // ============================================================================

  /**
   * 带防护的缓存获取（防穿透/雪崩/击穿）
   *
   * <p>委托给 {@link CacheProtectionGuard#getWithProtection} 实现。
   *
   * @param key 缓存键
   * @param loader 值加载器
   * @param minExpireMs 最小过期时间（毫秒）
   * @param maxExpireMs 最大过期时间（毫秒）
   * @return 缓存值
   * @see CacheProtectionGuard#getWithProtection
   */
  default V getWithProtection(K key, Function<K, V> loader, long minExpireMs, long maxExpireMs) {
    return CacheProtectionGuard.getWithProtection(this, key, loader, minExpireMs, maxExpireMs);
  }

  /**
   * 创建空值占位符
   *
   * @see NullValueGuard#registerNullKey
   */
  default V createNullPlaceholder(K key) {
    return CacheProtectionGuard.createNullPlaceholder(this, key);
  }

  /**
   * 检查指定键是否已标记为空值占位键
   *
   * @see NullValueGuard#isNullKeyRegistered
   */
  default boolean isNullPlaceholderKey(K key) {
    return CacheProtectionGuard.isNullPlaceholderKey(this, key);
  }
}
