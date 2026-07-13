package com.njydsz.pmis.common.cache.api;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * 缓存防护守卫 — 防穿透/防击穿/防雪崩
 *
 * <p>从 {@link Cache} 接口中提取，将可变静态状态（KEY_LOCKS）和防护逻辑
 * 隔离为独立工具类，避免接口持有可变状态。
 *
 * <p>提供以下防护机制：
 * <ul>
 *   <li><b>防穿透</b>：加载器返回 null 时缓存空标记，防止恶意请求穿透到后端</li>
 *   <li><b>防击穿</b>：对同一个 key 的并发请求，只有一个会执行加载</li>
 *   <li><b>防雪崩</b>：通过过期时间抖动，避免大量缓存同时失效</li>
 * </ul>
 *
 * @author Marvin Lee
 * @version 3.5.0
 */
public final class CacheProtectionGuard {

    private static final ConcurrentHashMap<Object, Object> KEY_LOCKS = new ConcurrentHashMap<>();

    /**
     * 空值占位符的过期时间注册表（防雪崩：随机过期）
     * key -> expireTimestamp
     */
    private static final ConcurrentHashMap<NullKey, Long> NULL_KEY_EXPIRATIONS = new ConcurrentHashMap<>();

    private CacheProtectionGuard() {
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

        // 防雪崩：检查空值占位符是否已过期
        if (NullValueGuard.isNullKeyRegistered(cache, key)) {
            NullKey nullKey = new NullKey(cache, key);
            Long expiration = NULL_KEY_EXPIRATIONS.get(nullKey);
            if (expiration != null && System.currentTimeMillis() > expiration) {
                // 空值占位已过期，清除并重新加载
                NullValueGuard.unregisterNullKey(cache, key);
                NULL_KEY_EXPIRATIONS.remove(nullKey);
            } else {
                return null;
            }
        }

        V value = cache.getIfPresent(key);
        if (value == null && loader != null) {
            Object lock = KEY_LOCKS.computeIfAbsent(key, k -> new Object());
            try {
                synchronized (lock) {
                    // Double-check after acquiring lock
                    if (NullValueGuard.isNullKeyRegistered(cache, key)) {
                        NullKey nullKey = new NullKey(cache, key);
                        Long expiration = NULL_KEY_EXPIRATIONS.get(nullKey);
                        if (expiration != null && System.currentTimeMillis() > expiration) {
                            NullValueGuard.unregisterNullKey(cache, key);
                            NULL_KEY_EXPIRATIONS.remove(nullKey);
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
                                NULL_KEY_EXPIRATIONS.put(new NullKey(cache, key),
                                        System.currentTimeMillis() + jitteredExpire);
                            }
                        }
                    }
                }
            } finally {
                KEY_LOCKS.remove(key, lock);
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

    /**
     * 空值键包装类，用于 NULL_KEY_EXPIRATIONS 的键
     */
    private static final class NullKey {
        private final Cache<?, ?> cache;
        private final Object key;

        NullKey(Cache<?, ?> cache, Object key) {
            this.cache = cache;
            this.key = key;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof NullKey)) return false;
            NullKey nullKey = (NullKey) o;
            return cache == nullKey.cache && (key == null ? nullKey.key == null : key.equals(nullKey.key));
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(cache);
            result = 31 * result + (key != null ? key.hashCode() : 0);
            return result;
        }
    }
}
