package com.njydsz.pmis.common.cache.api;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 空值占位符守卫 — 缓存穿透防护
 *
 * <p>当加载器返回 null 时，通过注册空值键防止缓存穿透。
 * 内部使用 {@link WeakHashMap} 跟踪每个缓存实例的空值键，
 * 避免将非 V 类型的占位符存入缓存导致的类型不安全。
 *
 * <p>使用 {@link WeakHashMap} 以缓存实例为键，当缓存实例被 GC 回收时，
 * 对应的空值键注册信息也会自动清理，避免内存泄漏。
 *
 * <p>从 {@link Cache} 接口中提取，遵循单一职责原则。
 *
 * @author Marvin Lee
 * @version 3.5.0
 */
public final class NullValueGuard {

    /**
     * 单例实例（保留用于向后兼容的 isNullPlaceholder 检测）
     */
    public static final NullValueGuard INSTANCE = new NullValueGuard();

    /**
     * 空值键注册表，使用 WeakHashMap 避免内存泄漏。
     * 当 Cache 实例不再被引用时，对应的注册信息会被自动 GC 清理。
     */
    private static final Map<Cache<?, ?>, Set<Object>> NULL_KEY_REGISTRY =
            Collections.synchronizedMap(new WeakHashMap<>());

    private NullValueGuard() {
    }

    /**
     * 注册空值占位键
     *
     * @param cache 缓存实例
     * @param key   缓存键
     */
    public static void registerNullKey(Cache<?, ?> cache, Object key) {
        NULL_KEY_REGISTRY.computeIfAbsent(cache, c -> Collections.newSetFromMap(
                Collections.synchronizedMap(new WeakHashMap<>()))).add(key);
    }

    /**
     * 检查指定键是否已注册为空值占位键
     *
     * @param cache 缓存实例
     * @param key   缓存键
     * @return true 如果已注册
     */
    public static boolean isNullKeyRegistered(Cache<?, ?> cache, Object key) {
        Set<Object> keys = NULL_KEY_REGISTRY.get(cache);
        return keys != null && keys.contains(key);
    }

    /**
     * 取消注册空值占位键（当实际值被写入时调用）
     *
     * @param cache 缓存实例
     * @param key   缓存键
     */
    public static void unregisterNullKey(Cache<?, ?> cache, Object key) {
        Set<Object> keys = NULL_KEY_REGISTRY.get(cache);
        if (keys != null) {
            keys.remove(key);
        }
    }
}
