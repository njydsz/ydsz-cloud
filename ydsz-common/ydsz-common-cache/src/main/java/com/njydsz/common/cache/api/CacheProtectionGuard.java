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
 * <p>失败语义（对标 Caffeine）：加载异常传播给所有等待者，守卫不做递归重试， 由调用方决定重试策略；真实缓存值优先于空值占位符。
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
 * @author ydsz-team
 * @since 1.0.0
 */
public final class CacheProtectionGuard {

  private static final Logger LOG = LoggerFactory.getLogger(CacheProtectionGuard.class);

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
   * <p>失败语义（对标 Caffeine）：加载器抛出异常时，异常通过 {@link CompletionException}（或原始
   * RuntimeException）传播给<strong>所有</strong>等待者，由调用方决定重试策略——守卫自身不做递归重试， 避免等待者集体回源形成重试风暴。
   *
   * <p>读取顺序：先查真实缓存值，命中即返回；未命中才检查空值占位符—— 空值占位符不得屏蔽已通过其他路径写入的真实值。
   *
   * @param cache 缓存实例
   * @param key 缓存键
   * @param loader 值加载器（返回 null 时缓存空标记并返回 null；抛异常时异常传播给所有等待者）
   * @param minExpireMs 最小过期时间（毫秒），用于空值占位符的随机过期
   * @param maxExpireMs 最大过期时间（毫秒），用于空值占位符的随机过期
   * @param <K> 键类型
   * @param <V> 值类型
   * @return 缓存值（可能是空标记）
   * @throws IllegalArgumentException 如果 minExpireMs > maxExpireMs
   * @throws RuntimeException 加载失败时异常传播（等待者收到的也是同一异常）
   */
  public static <K, V> V getWithProtection(
      Cache<K, V> cache, K key, Function<K, V> loader, long minExpireMs, long maxExpireMs) {
    if (minExpireMs > maxExpireMs) {
      throw new IllegalArgumentException("minExpireMs must be <= maxExpireMs");
    }

    CacheProtectionGuard guard = forCache(cache);

    // 先查真实值（P1 修复）：命中即返回，空值占位符不得屏蔽其他路径写入的真实值
    V value = cache.getIfPresent(key);
    if (value != null) {
      return value;
    }

    // 防雪崩：空值占位符未过期时直接返回 null（过期时间带随机抖动）
    if (NullValueGuard.isNullKeyRegistered(cache, key)) {
      Long expiration = guard.nullKeyExpirations.get(key);
      if (expiration == null || System.currentTimeMillis() > expiration) {
        // 无过期时间或已过期：清理占位符，允许重新加载
        NullValueGuard.unregisterNullKey(cache, key);
        guard.nullKeyExpirations.remove(key);
      } else {
        return null;
      }
    }

    if (loader == null) {
      return null;
    }

    // 防击穿：putIfAbsent + 信号 Future 模式，同一 key 仅一个线程执行加载
    CompletableFuture<Object> ourSignal = new CompletableFuture<>();
    CompletableFuture<Object> existing = guard.loadingFutures.putIfAbsent(key, ourSignal);

    if (existing == null) {
      // 当前线程获得加载权
      try {
        // Double-check：获取加载权期间其他路径可能已写入真实值或空值占位符
        V loaded = cache.getIfPresent(key);
        if (loaded == null) {
          if (NullValueGuard.isNullKeyRegistered(cache, key)) {
            Long exp = guard.nullKeyExpirations.get(key);
            if (exp != null && System.currentTimeMillis() <= exp) {
              return null;
            }
            NullValueGuard.unregisterNullKey(cache, key);
            guard.nullKeyExpirations.remove(key);
          }
          loaded = loader.apply(key);
          if (loaded != null) {
            // 写入真实值时同步清理陈旧空值标记（防止残留占位符屏蔽后续读取）
            NullValueGuard.unregisterNullKey(cache, key);
            guard.nullKeyExpirations.remove(key);
            cache.put(key, loaded);
          } else {
            registerNullPlaceholder(guard, cache, key, minExpireMs, maxExpireMs);
          }
        }
        return loaded;
      } catch (Exception e) {
        LOG.warn("Cache loading failed for key={}", key, e);
        // P1 修复：异常完成信号，让全部等待者感知失败。
        // 旧实现吞异常后正常完成信号，等待者读缓存为 null 后各自递归重试回源，
        // 形成无上限、无退避的重试风暴（catch CompletionException 分支实为死代码）。
        ourSignal.completeExceptionally(e);
        if (e instanceof RuntimeException) {
          throw (RuntimeException) e;
        }
        throw new CompletionException(e);
      } finally {
        guard.loadingFutures.remove(key, ourSignal);
        ourSignal.complete(null); // 幂等：失败路径已异常完成，此处无效果
      }
    }

    // 等待其他线程完成加载
    existing.join();
    // join 失败抛 CompletionException：失败传播给等待者（对标 Caffeine），守卫不递归重试

    // 从缓存读取结果（类型安全，无需 unchecked cast）
    V loaded = cache.getIfPresent(key);
    if (loaded != null) {
      return loaded;
    }
    // 加载成功但值为 null：空值占位符已注册，返回 null 防穿透
    if (NullValueGuard.isNullKeyRegistered(cache, key)) {
      return null;
    }
    // 边界情况：加载完成后缓存条目被淘汰（容量驱逐/主动失效），返回 null 由调用方决定重试
    return null;
  }

  /**
   * 注册空值占位符并设置带随机抖动的过期时间（防雪崩）
   *
   * @param guard 对应缓存的守卫实例
   * @param cache 缓存实例
   * @param key 缓存键
   * @param minExpireMs 最小过期时间（毫秒）
   * @param maxExpireMs 最大过期时间（毫秒）
   */
  private static void registerNullPlaceholder(
      CacheProtectionGuard guard, Cache<?, ?> cache, Object key, long minExpireMs, long maxExpireMs) {
    NullValueGuard.registerNullKey(cache, key);
    if (maxExpireMs > 0) {
      long jitteredExpire =
          minExpireMs > 0
              ? minExpireMs + ThreadLocalRandom.current().nextLong(maxExpireMs - minExpireMs + 1)
              : maxExpireMs;
      guard.nullKeyExpirations.put(key, System.currentTimeMillis() + jitteredExpire);
    }
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
    List<K> missingKeys = new ArrayList<>(keys.size());

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
          // 写入真实值时同步清理陈旧空值标记（与单 key 路径语义一致）
          NullValueGuard.unregisterNullKey(cache, key);
          cache.put(key, value);
          result.put(key, value);
        } else {
          CacheProtectionGuard guard = forCache(cache);
          registerNullPlaceholder(guard, cache, key, minExpireMs, maxExpireMs);
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
