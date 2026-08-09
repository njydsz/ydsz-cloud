package com.njydsz.common.json.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 线程安全的有界 LRU 缓存（零外部依赖实现）。
 *
 * <p>用于替代无淘汰策略的 {@code ConcurrentHashMap} 静态缓存，防止在热部署、
 * 动态类加载、多租户等场景下缓存无限增长导致内存泄漏（OOM）。</p>
 *
 * <p><b>特性：</b></p>
 * <ul>
 *   <li>容量上限 {@code maxSize}，超出后按访问顺序淘汰最久未使用的条目（LRU）</li>
 *   <li>读写锁保证线程安全（读读并发、写写互斥、读写互斥）</li>
 *   <li>零外部依赖，仅使用 JDK 内置 {@link LinkedHashMap}</li>
 *   <li>{@link #computeIfAbsent} 原子执行"查-建-存"，避免重复创建开销</li>
 * </ul>
 *
 * <p>适用场景：类元数据、序列化器实例、格式化器等小对象缓存（建议容量 128~1024）。</p>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author ydsz-team
 * @since 1.2.1
 */
public final class BoundedLruCache<K, V> {

    private final int maxSize;
    private final LinkedHashMap<K, V> map;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 创建有界 LRU 缓存。
     *
     * @param maxSize 最大条目数，必须大于 0
     * @throws IllegalArgumentException 如果 maxSize 不大于 0
     */
    public BoundedLruCache(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be > 0, got " + maxSize);
        }
        this.maxSize = maxSize;
        this.map = new LinkedHashMap<K, V>(Math.min(maxSize * 2, 64), 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maxSize;
            }
        };
    }

    /**
     * 获取缓存值。
     *
     * @param key 键
     * @return 缓存值，不存在返回 null
     */
    public V get(K key) {
        lock.readLock().lock();
        try {
            return map.get(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 写入缓存（若已存在则覆盖，并刷新 LRU 访问顺序）。
     *
     * @param key 键
     * @param value 值
     * @return 之前的缓存值，不存在返回 null
     */
    public V put(K key, V value) {
        lock.writeLock().lock();
        try {
            return map.put(key, value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 原子执行"查-建-存"：键已存在直接返回；否则用 mappingFunction 创建并缓存。
     *
     * <p>在写锁保护下执行，避免并发场景下重复创建实例。</p>
     *
     * @param key 键
     * @param mappingFunction 值创建函数（不应为 null）
     * @return 缓存值
     */
    public V computeIfAbsent(K key, Function<K, V> mappingFunction) {
        lock.writeLock().lock();
        try {
            V existing = map.get(key);
            if (existing != null) {
                return existing;
            }
            V value = mappingFunction.apply(key);
            map.put(key, value);
            return value;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 清空所有缓存条目。
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            map.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 当前缓存条目数。
     *
     * @return 条目数
     */
    public int size() {
        lock.readLock().lock();
        try {
            return map.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 缓存容量上限。
     *
     * @return 最大条目数
     */
    public int maxSize() {
        return maxSize;
    }
}
