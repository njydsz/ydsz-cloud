package com.njydsz.common.cache.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 缓存防护守卫 — 防穿透/防击穿/防雪崩
 *
 * <p>提供以下防护机制：
 *
 * <ul>
 *   <li><b>防穿透</b>：加载器返回 null 时缓存空标记，防止恶意请求穿透到后端
 *   <li><b>防击穿</b>：对同一个 key 的并发请求，只有一个会执行加载
 *   <li><b>防雪崩</b>：通过过期时间抖动，避免大量缓存同时失效
 * </ul>
 *
 * <p>优化点（P1 修复）：
 *
 * <ul>
 *   <li>从全局静态 {@code KEY_LOCKS} 改为 per-cache 实例级锁映射， 消除跨缓存实例的锁竞争
 *   <li>从全局静态 {@code NULL_KEY_EXPIRATIONS} 改为 per-cache 实例级过期映射， 消除内存泄漏风险
 *   <li>移除 {@code NullKey} 包装类（per-cache 状态下直接使用 key 即可）
 *   <li>外层 {@link WeakHashMap} 确保缓存实例 GC 后状态自动清理
 * </ul>
 *
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class CacheProtectionGuard {

  private static final Logger log = LoggerFactory.getLogger(CacheProtectionGuard.class);

  /**
   * Per-cache 实例注册表，使用 WeakHashMap 避免内存泄漏。 当 Cache 实例不再被引用时，对应的 CacheProtectionGuard 实例会被自动 GC 清理。
   */
  private static final Map<Cache<?, ?>, CacheProtectionGuard> INSTANCES =
      Collections.synchronizedMap(new WeakHashMap<>());

  /**
   * Per-cache Key 级加载信号映射（防击穿）
   *
   * <p>Future 仅用作完成信号（存储 null），不携带值。 等待线程通过 cache.getIfPresent(key) 读取结果，避免 unchecked cast。
   */
  private final ConcurrentHashMap<Object, CompletableFuture<Object>> loadingFutures =
      new ConcurrentHashMap<>();

  /** Per-cache 空值占位符过期时间（防雪崩：随机过期） key -> expireTimestamp */
  private final ConcurrentHashMap<Object, Long> nullKeyExpirations = new ConcurrentHashMap<>();

  private CacheProtectionGuard() {}

  /**
   * 获取或创建指定缓存实例对应的 CacheProtectionGuard
   *
   * @param cache 缓存实例
   * @return 对应的 CacheProtectionGuard 实例
   */
  private static CacheProtectionGuard forCache(Cache<?, ?> cache) {
    // computeIfAbsent on synchronized WeakHashMap is thread-safe
    return INSTANCES.computeIfAbsent(cache, c -> new CacheProtectionGuard());
  }

  /**
   * 带防护的缓存获取（防穿透/击穿/雪崩）
   *
   * <p>防击穿实现：使用 putIfAbsent + 信号 Future 模式。 同一 key 的并发请求中，只有一个线程执行加载，其余线程等待完成后从缓存读取。
   * Future 仅用作完成信号，不携带值，避免 unchecked cast。
   *
   * @param cache 缓存实例
   * @param key 缓存键
   * @param loader 值加载器（不应返回 null，返回 null 时会缓存空标记）
   * @param minExpireMs 最小过期时间（毫秒），用于空值占位符的随机过期
   * @param maxExpireMs 最大过期时间（毫秒），用于空值占位符的随机过期
   * @param <K> 键类型
   * @param <V> 值类型
   * @return 缓存值（可能是空标记）
   * @throws IllegalArgumentException 如果 minExpireMs > maxExpireMs
   */
  public static <K, V> V getWithProtection(
      Cache<K, V> cache, K key, Function<K, V> loader, long minExpireMs, long maxExpireMs) {
    if (minExpireMs > maxExpireMs) {
      throw new IllegalArgumentException("minExpireMs must be <= maxExpireMs");
    }

    CacheProtectionGuard guard = forCache(cache);

    // 防雪崩：检查空值占位符是否已过期
    if (NullValueGuard.isNullKeyRegistered(cache, key)) {
      Long expiration = guard.nullKeyExpirations.get(key);
      if (expiration != null && System.currentTimeMillis() > expiration) {
        NullValueGuard.unregisterNullKey(cache, key);
        guard.nullKeyExpirations.remove(key);
      } else {
        return null;
      }
    }

    V value = cache.getIfPresent(key);
    if (value == null && loader != null) {
      // 防击穿：putIfAbsent + 信号 Future 模式
      // Future 仅用作完成信号，不携带值，等待线程从缓存读取结果
      CompletableFuture<Object> ourSignal = new CompletableFuture<>();
      CompletableFuture<Object> existing = guard.loadingFutures.putIfAbsent(key, ourSignal);

      if (existing == null) {
        // 当前线程获得加载权
        try {
          // Double-check：防止在获取加载权前其他线程已完成加载
          if (NullValueGuard.isNullKeyRegistered(cache, key)) {
            Long exp = guard.nullKeyExpirations.get(key);
            if (exp != null && System.currentTimeMillis() > exp) {
              NullValueGuard.unregisterNullKey(cache, key);
              guard.nullKeyExpirations.remove(key);
            } else {
              return null;
            }
          }
          V loaded = cache.getIfPresent(key);
          if (loaded == null) {
            loaded = loader.apply(key);
            if (loaded != null) {
              cache.put(key, loaded);
            } else {
              NullValueGuard.registerNullKey(cache, key);
              if (maxExpireMs > 0) {
                long jitteredExpire =
                    minExpireMs > 0
                        ? minExpireMs
                            + ThreadLocalRandom.current()
                                .nextLong(maxExpireMs - minExpireMs + 1)
                        : maxExpireMs;
                guard.nullKeyExpirations.put(key, System.currentTimeMillis() + jitteredExpire);
              }
            }
          }
          return loaded;
        } catch (Exception e) {
          log.warn("Cache loading failed for key={}", key, e);
          return null;
        } finally {
          guard.loadingFutures.remove(key, ourSignal);
          ourSignal.complete(null);
        }
      }

      // 等待其他线程完成加载
      try {
        existing.join();
      } catch (CompletionException e) {
 // 加载失败，递归重试
        return getWithProtection(cache, key, loader, minExpireMs, maxExpireMs);
      }

      // 从缓存读取结果（类型安全，无需 unchecked cast）
      V loaded = cache.getIfPresent(key);
      if (loaded != null) {
        return loaded;
      }
      // 检查是否为空值占位符
      if (NullValueGuard.isNullKeyRegistered(cache, key)) {
        return null;
      }
      // 边界情况：加载完成后缓存条目被淘汰，递归重试
      return getWithProtection(cache, key, loader, minExpireMs, maxExpireMs);
    }
    return value;
  }

  /**
   * 创建空值占位符
   *
   * @param cache 缓存实例
   * @param key 缓存键
   * @param <K> 键类型
   * @param <V> 值类型
   * @return null
   */
  public static <K, V> V createNullPlaceholder(Cache<K, V> cache, K key) {
    NullValueGuard.registerNullKey(cache, key);
    return null;
  }

  /**
   * 批量带防护的缓存获取（防穿透/击穿/雪崩）
   *
   * <p>对每个 key 独立应用防护逻辑，已缓存的 key 直接返回，未命中的 key 通过 loader 批量加载。
   *
   * @param cache 缓存实例
   * @param keys 缓存键集合
   * @param loader 批量加载器
   * @param minExpireMs 空值占位符最小过期时间（毫秒）
   * @param maxExpireMs 空值占位符最大过期时间（毫秒）
   * @param <K> 键类型
   * @param <V> 值类型
   * @return 缓存值映射
   */
  public static <K, V> Map<K, V> getAllWithProtection(
      Cache<K, V> cache,
      Collection<K> keys,
      Function<Collection<K>, Map<K, V>> loader,
      long minExpireMs,
      long maxExpireMs) {
    if (keys == null || keys.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<K, V> result = new HashMap<>(keys.size());
    List<K> missingKeys = new ArrayList<>();

    // 先从缓存获取
    for (K key : keys) {
      V value = cache.getIfPresent(key);
      if (value != null) {
        result.put(key, value);
      } else if (!NullValueGuard.isNullKeyRegistered(cache, key)) {
        missingKeys.add(key);
      }
    }

    // 批量加载缺失的 key
    if (!missingKeys.isEmpty()) {
      Map<K, V> loaded = loader.apply(missingKeys);
      for (K key : missingKeys) {
        V value = loaded != null ? loaded.get(key) : null;
        if (value != null) {
          cache.put(key, value);
          result.put(key, value);
        } else {
          NullValueGuard.registerNullKey(cache, key);
          if (maxExpireMs > 0) {
            CacheProtectionGuard guard = forCache(cache);
            long jitteredExpire =
                minExpireMs > 0
                    ? minExpireMs + ThreadLocalRandom.current().nextLong(maxExpireMs - minExpireMs + 1)
                    : maxExpireMs;
            guard.nullKeyExpirations.put(key, System.currentTimeMillis() + jitteredExpire);
          }
        }
      }
    }
    return result;
  }

  /**
   * 检查指定键是否已标记为空值占位键
   *
   * @param cache 缓存实例
   * @param key 缓存键
   * @return true 如果该键已标记为空值占位
   */
  public static boolean isNullPlaceholderKey(Cache<?, ?> cache, Object key) {
    return NullValueGuard.isNullKeyRegistered(cache, key);
  }
}
