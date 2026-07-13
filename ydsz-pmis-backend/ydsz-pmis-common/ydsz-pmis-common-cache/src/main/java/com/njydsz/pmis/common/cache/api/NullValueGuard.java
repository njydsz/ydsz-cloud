package com.njydsz.pmis.common.cache.api;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 空值占位符守卫 — 缓存穿透防护
 *
 * <p>当加载器返回 null 时，通过注册空值键防止缓存穿透。
 * 内部使用 per-cache 实例级状态（通过 {@link WeakHashMap} 关联缓存实例），
 * 当缓存实例被 GC 回收时，对应的空值键注册信息也会自动清理，避免内存泄漏。
 *
 * <p>优化点（P1 修复）：
 * <ul>
 *   <li>从全局静态 {@code Map<Cache, Set>} 改为 per-cache {@code Set<Object>}，
 *       消除跨缓存实例的状态共享</li>
 *   <li>使用 {@link ConcurrentHashMap} 支持高并发读写</li>
 *   <li>外层 {@link WeakHashMap} 确保缓存实例 GC 后状态自动清理</li>
 * </ul>
 *
 * @author Marvin Lee
 * @version 4.0.0
 */
public final class NullValueGuard {

    /**
     * 单例实例（保留用于向后兼容）
     */
    public static final NullValueGuard INSTANCE = new NullValueGuard();

    /**
     * Per-cache 实例注册表，使用 WeakHashMap 避免内存泄漏。
     * 当 Cache 实例不再被引用时，对应的 NullValueGuard 实例会被自动 GC 清理。
     */
    private static final Map<Cache<?, ?>, NullValueGuard> INSTANCES =
            Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Per-cache 空值键集合（线程安全）
     */
    private final Set<Object> nullKeys = ConcurrentHashMap.newKeySet();

    private NullValueGuard() {
    }

    /**
     * 获取或创建指定缓存实例对应的 NullValueGuard
     *
     * @param cache 缓存实例
     * @return 对应的 NullValueGuard 实例
     */
    private static NullValueGuard forCache(Cache<?, ?> cache) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(cache, c -> new NullValueGuard());
        }
    }

    /**
     * 注册空值占位键
     *
     * @param cache 缓存实例
     * @param key   缓存键
     */
    public static void registerNullKey(Cache<?, ?> cache, Object key) {
        forCache(cache).nullKeys.add(key);
    }

    /**
     * 检查指定键是否已注册为空值占位键
     *
     * @param cache 缓存实例
     * @param key   缓存键
     * @return true 如果已注册
     */
    public static boolean isNullKeyRegistered(Cache<?, ?> cache, Object key) {
        NullValueGuard guard = forCache(cache);
        return guard.nullKeys.contains(key);
    }

    /**
     * 取消注册空值占位键（当实际值被写入时调用）
     *
     * @param cache 缓存实例
     * @param key   缓存键
     */
    public static void unregisterNullKey(Cache<?, ?> cache, Object key) {
        NullValueGuard guard = forCache(cache);
        guard.nullKeys.remove(key);
    }
}
