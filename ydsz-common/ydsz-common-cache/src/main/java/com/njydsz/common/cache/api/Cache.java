package com.njydsz.common.cache.api;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
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
 * @author ydsz-team
 * @since 26.09.01
 */
public interface Cache<K, V> {

  // ============================================================================
  // 查询操作
  // ============================================================================

  /**
   * 获取缓存值（如果存在）
   *
   * <p>null 键统一契约：返回 null 且不计入 hit/miss 统计（防御性查询不污染命中率），全部实现保持一致。
   *
   * @param key 缓存键，为 null 时返回 null
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

  /**
   * 异步获取缓存值
   *
   * @param key 键
   * @param loader loader 参数
   * @return 返回值说明
   */
  CompletableFuture<V> getAsync(K key, AsyncFunction<K, V> loader);

  /**
   * 检查是否包含指定键
   *
   * @param key 键
   * @return 返回值说明
   */
  boolean containsKey(K key);

  // ============================================================================
  // 写入操作
  // ============================================================================

  /**
   * 放入缓存
   *
   * @param key 键
   * @param value 值
   */
  void put(K key, V value);

  /**
   * 如果键不存在则放入缓存。
   *
   * <p><b>原子性契约（对标 Caffeine/ConcurrentHashMap）</b>：同一 key 的并发调用仅一次写入成功。 继承 {@code
   * AbstractCache} 的实现通过 per-key 单飞信号保证原子；此 default 实现仅为 直接实现本接口的类提供兼容存根（check-then-act，非原子）。
   *
   * @param key 键
   * @param value 值
   * @return 写入前已存在的值；写入成功（此前无值）返回 null
   */
  default V putIfAbsent(K key, V value) {
    V existing = getIfPresent(key);
    if (existing == null) {
      put(key, value);
      return null;
    }
    return existing;
  }

  /**
   * 如果键不存在则计算并放入缓存。
   *
   * <p><b>原子性契约（对标 Caffeine）</b>：同一 key 并发调用时 mappingFunction 仅执行一次（单飞）， 映射结果对全部等待者可见。继承
   * {@code AbstractCache} 的实现已保证；此 default 实现为非原子兼容存根。 mappingFunction 抛出异常时异常传播给所有等待者。
   *
   * @param key 键
   * @param mappingFunction mappingFunction 参数
   * @return 映射值；映射为 null 时返回 null 且不写入
   */
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

  /**
   * 重新计算映射值。
   *
   * <p><b>原子性契约</b>：同一 key 的并发 compute 按抵达顺序串行化（per-key 单飞）。 继承 {@code AbstractCache}
   * 的实现已保证；此 default 实现为非原子兼容存根。 remappingFunction 抛出异常时异常传播且不改变缓存状态。
   *
   * @param key 键
   * @param remappingFunction remappingFunction 参数
   * @return 新值；重映射为 null 时移除条目并返回 null
   */
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

  /**
   * 合并值。
   *
   * <p><b>原子性契约（对标 ConcurrentHashMap.merge）</b>：同一 key 的并发 merge 按抵达顺序串行化。 继承 {@code
   * AbstractCache} 的实现已保证；此 default 实现为非原子兼容存根。
   *
   * @param key 键
   * @param value 值
   * @param remappingFunction remappingFunction 参数
   * @return 合并后的值；合并结果为 null 时移除条目并返回 null
   */
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

  /**
   * 从缓存中移除指定键
   *
   * @param key 键
   * @return 返回值说明
   */
  V remove(K key);

  /**
   * 使键失效（等同于 remove）
   *
   * @param key 键
   */
  default void invalidate(K key) {
    remove(key);
  }

  /**
   * 使多个键失效
   *
   * @param keys keys 参数
   */
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

  /**
   * 批量放入
   *
   * @param map 映射
   */
  default void putAll(Map<K, V> map) {
    if (map == null || map.isEmpty()) {
      return;
    }
    map.forEach(this::put);
  }

  /**
   * 批量获取
   *
   * @param keys keys 参数
   * @return 返回值说明
   */
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

  /**
   * 批量获取，未命中的键集合由加载器一次性批量加载。
   *
   * <p><b>批量加载契约（对标 Caffeine LoadingCache.getAll）</b>：缺失键以集合形式交给 loader，
   * 由 loader 决定单条查询还是真正的批量查询（如 SQL IN 查询、mget）； 加载结果写回缓存并合入返回值。loader 仅在存在缺失键时调用一次。
   *
   * <p>loader 返回 null 或其映射中值为 null 的条目视为"加载不到"， 不写缓存也不出现在返回值中（与单键 get 的 null 语义一致）。
   * loader 为 null 时退化为仅查询已缓存条目。loader 抛出异常时异常直接传播， 缓存既有状态不变（本次调用不产生部分写入）。
   *
   * <p>继承 {@code AbstractCache} 的实现会记录批量加载统计（次数/耗时）； 此 default 实现为直接实现本接口的类提供兼容存根（不计统计）。
   *
   * @param keys 待查询的键集合
   * @param loader 批量加载器，入参为缺失键集合，返回值为键到值的映射
   * @return 命中与加载合并后的结果映射（不含加载不到的键）
   */
  default Map<K, V> getAll(Collection<K> keys, Function<Set<K>, Map<K, V>> loader) {
    if (keys == null || keys.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<K, V> result = getAll(keys);
    if (loader == null) {
      return result;
    }
    Set<K> missing = new LinkedHashSet<>(keys);
    missing.removeAll(result.keySet());
    if (missing.isEmpty()) {
      return result;
    }
    Map<K, V> loaded = loader.apply(missing);
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
   * 批量删除
   *
   * @param keys keys 参数
   */
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

  /**
   * 获取缓存大小（估计值）
   *
   * @return 返回值说明
   */
  long estimatedSize();

  /**
   * 缓存是否为空
   *
   * @return 返回值说明
   */
  default boolean isEmpty() {
    return estimatedSize() == 0;
  }

  /**
   * 获取命中率
   *
   * @return 返回值说明
   */
  double getHitRate();

  /**
   * 获取统计信息
   *
   * @return 返回值说明
   */
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
      /**
       * 查询淘汰策略。
       *
       * <p>默认实现为"不支持淘汰策略"占位：返回空 Optional， 而非抛出异常，保证调用方无需判空即可安全使用。
       *
       * @return 空 {@link Optional}，表示当前缓存不支持淘汰策略查询
       */
      @Override
      public Optional<EvictionPolicy> eviction() {
        return Optional.empty();
      }

      /**
       * 查询过期策略。
       *
       * <p>默认实现为"不支持过期策略"占位：返回空 Optional， 而非抛出异常，保证调用方无需判空即可安全使用。
       *
       * @return 空 {@link Optional}，表示当前缓存不支持过期策略查询
       */
      @Override
      public Optional<ExpirationPolicy> expiration() {
        return Optional.empty();
      }
    };
  }

  // ============================================================================
  // 视图操作
  // ============================================================================

  /**
   * 获取所有键
   *
   * @return 返回值说明
   */
  Set<K> keySet();

  /**
   * 获取所有值
   *
   * @return 返回值说明
   */
  Collection<V> values();

  /**
   * 获取缓存的 Map 视图
   *
   * @return 返回值说明
   */
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

  /**
   * 添加删除监听器
   *
   * @param listener listener 参数
   */
  default void addListener(RemovalListener<? super K, ? super V> listener) {}

  /**
   * 配置删除监听器回调执行器（对标 Caffeine RemovalListeners.async）。
   *
   * <p>配置后监听器回调在指定执行器上异步执行， 不阻塞缓存操作线程（底层实现的淘汰/删除常持有写锁，
   * 同步回调会拖慢读写路径）。未配置时保持同步执行（向后兼容）。 装饰器实现应透传到底层缓存。
   *
   * @param executor 回调执行器；null 表示恢复同步执行
   */
  default void setListenerExecutor(Executor executor) {}

  // ============================================================================
  // 遍历
  // ============================================================================

  /**
   * 遍历缓存
   *
   * @param action action 参数
   */
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
   * 带防护的缓存获取（防穿透/雪崩/击穿），使用实例级空值 TTL 配置。
   *
   * <p>空值占位 TTL 是缓存级策略（对标 Spring Cache 的 null TTL 配置惯例）， 应通过 builder 配置一次，
   * 而非在每次调用点重复决策（旧 API 迫使 15+ 处业务代码内联传参）。 实例未配置时使用默认区间 30~60
   * 秒（带随机抖动，防雪崩）；个别调用点需要不同 TTL 时仍可用四参版本覆盖。
   *
   * @param key 缓存键
   * @param loader 值加载器
   * @return 缓存值
   * @see CacheProtectionGuard#getWithProtection
   */
  default V getWithProtection(K key, Function<K, V> loader) {
    return getWithProtection(key, loader, DEFAULT_NULL_VALUE_TTL_MIN_MS, DEFAULT_NULL_VALUE_TTL_MAX_MS);
  }

  /**
   * 配置实例级空值占位 TTL 区间（毫秒，带随机抖动）。
   *
   * <p>装饰器实现应透传到底层缓存；由 CacheBuilder 构建时注入。
   *
   * @param minExpireMs 最小过期时间（毫秒）
   * @param maxExpireMs 最大过期时间（毫秒）
   */
  default void setNullValueTtl(long minExpireMs, long maxExpireMs) {}

  /** 实例未配置空值 TTL 时的默认下界（30 秒） */
  long DEFAULT_NULL_VALUE_TTL_MIN_MS = 30_000L;

  /** 实例未配置空值 TTL 时的默认上界（60 秒） */
  long DEFAULT_NULL_VALUE_TTL_MAX_MS = 60_000L;

  /**
   * 创建空值占位符
   * @see NullValueGuard#registerNullKey
   *
   * @param key 键
   * @return 返回值说明
   */
  default V createNullPlaceholder(K key) {
    return CacheProtectionGuard.createNullPlaceholder(this, key);
  }

  /**
   * 检查指定键是否已标记为空值占位键
   * @see NullValueGuard#isNullKeyRegistered
   *
   * @param key 键
   * @return 返回值说明
   */
  default boolean isNullPlaceholderKey(K key) {
    return CacheProtectionGuard.isNullPlaceholderKey(this, key);
  }
}
