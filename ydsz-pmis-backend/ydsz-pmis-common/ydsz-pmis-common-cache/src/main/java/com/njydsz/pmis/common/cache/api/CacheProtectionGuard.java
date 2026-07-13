package com.njydsz.pmis.common.cache.api;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * 缓存防护守卫 — 防穿透/防击穿/防雪崩
 *
 * <p>提供以下防护机制：
 * <ul>
 *   <li><b>防穿透</b>：加载器返回 null 时缓存空标记，防止恶意请求穿透到后端</li>
 *   <li><b>防击穿</b>：对同一个 key 的并发请求，只有一个会执行加载</li>
 *   <li><b>防雪崩</b>：通过过期时间抖动，避免大量缓存同时失效</li>
 * </ul>
 *
 * <p>优化点（P1 修复）：
 * <ul>
 *   <li>从全局静态 {@code KEY_LOCKS} 改为 per-cache 实例级锁映射，
 *       消除跨缓存实例的锁竞争</li>
 *   <li>从全局静态 {@code NULL_KEY_EXPIRATIONS} 改为 per-cache 实例级过期映射，
 *       消除内存泄漏风险</li>
 *   <li>移除 {@code NullKey} 包装类（per-cache 状态下直接使用 key 即可）</li>
 *   <li>外层 {@link WeakHashMap} 确保缓存实例 GC 后状态自动清理</li>
 * </ul>
 *
 * @author Marvin Lee
 * @version 4.0.0
 */
public final class CacheProtectionGuard {

    /**
     * Per-cache 实例注册表，使用 WeakHashMap 避免内存泄漏。
     * 当 Cache 实例不再被引用时，对应的 CacheProtectionGuard 实例会被自动 GC 清理。
     */
    private static final Map<Cache<?, ?>, CacheProtectionGuard> INSTANCES =
            Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Per-cache Key 级锁映射（防击穿）
     */
    private final ConcurrentHashMap<Object, Object> keyLocks = new ConcurrentHashMap<>();

    /**
     * Per-cache 空值占位符过期时间（防雪崩：随机过期）
     * key -> expireTimestamp
     */
    private final ConcurrentHashMap<Object, Long> nullKeyExpirations = new ConcurrentHashMap<>();

    private CacheProtectionGuard() {
    }

    /**
     * 获取或创建指定缓存实例对应的 CacheProtectionGuard
     *
     * @param cache 缓存实例
     * @return 对应的 CacheProtectionGuard 实例
     */
    private static CacheProtectionGuard forCache(Cache<?, ?> cache) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(cache, c -> new CacheProtectionGuard());
        }
    }

    /**
     * 带防护的缓存获取（防穿透/击穿/雪崩）
     *
     * @param cache       缓存实例
     * @param key         缓存键
     * @param loader      值加载器（不应返回 null，返回 null 时会缓存空标记）
     * @param minExpireMs 最小过期时间（毫秒），用于空值占位符的随机过期
     * @param maxExpireMs 最大过期时间（毫秒），用于空值占位符的随机过期
     * @param <K>         键类型
     * @param <V>         值类型
     * @return 缓存值（可能是空标记）
     * @throws IllegalArgumentException 如果 minExpireMs > maxExpireMs
     */
    public static <K, V> V getWithProtection(Cache<K, V> cache, K key,
                                              Function<K, V> loader,
                                              long minExpireMs, long maxExpireMs) {
        if (minExpireMs > maxExpireMs) {
            throw new IllegalArgumentException("minExpireMs must be <= maxExpireMs");
        }

        CacheProtectionGuard guard = forCache(cache);

        // 防雪崩：检查空值占位符是否已过期
        if (NullValueGuard.isNullKeyRegistered(cache, key)) {
            Long expiration = guard.nullKeyExpirations.get(key);
            if (expiration != null && System.currentTimeMillis() > expiration) {
                // 空值占位已过期，清除并重新加载
                NullValueGuard.unregisterNullKey(cache, key);
                guard.nullKeyExpirations.remove(key);
            } else {
                return null;
            }
        }

        V value = cache.getIfPresent(key);
        if (value == null && loader != null) {
            Object lock = guard.keyLocks.computeIfAbsent(key, k -> new Object());
            try {
                synchronized (lock) {
                    // Double-check after acquiring lock
                    if (NullValueGuard.isNullKeyRegistered(cache, key)) {
                        Long expiration = guard.nullKeyExpirations.get(key);
                        if (expiration != null && System.currentTimeMillis() > expiration) {
                            NullValueGuard.unregisterNullKey(cache, key);
                            guard.nullKeyExpirations.remove(key);
                        } else {
                            return null;
                        }
                    }
                    value = cache.getIfPresent(key);
                    if (value == null) {
                        value = loader.apply(key);
                        if (value != null) {
                            cache.put(key, value);
                        } else {
                            NullValueGuard.registerNullKey(cache, key);
                            // 防雪崩：为空值占位符设置随机过期时间
                            if (maxExpireMs > 0) {
                                long jitteredExpire = minExpireMs > 0
                                        ? minExpireMs + ThreadLocalRandom.current().nextLong(maxExpireMs - minExpireMs + 1)
                                        : maxExpireMs;
                                guard.nullKeyExpirations.put(key,
                                        System.currentTimeMillis() + jitteredExpire);
                            }
                        }
                    }
                }
            } finally {
                guard.keyLocks.remove(key, lock);
            }
        }
        return value;
    }

    /**
     * 创建空值占位符
     *
     * @param cache 缓存实例
     * @param key   缓存键
     * @param <K>   键类型
     * @param <V>   值类型
     * @return null
     */
    public static <K, V> V createNullPlaceholder(Cache<K, V> cache, K key) {
        NullValueGuard.registerNullKey(cache, key);
        return null;
    }

    /**
     * 检查指定键是否已标记为空值占位键
     *
     * @param cache 缓存实例
     * @param key   缓存键
     * @return true 如果该键已标记为空值占位
     */
    public static boolean isNullPlaceholderKey(Cache<?, ?> cache, Object key) {
        return NullValueGuard.isNullKeyRegistered(cache, key);
    }
}
