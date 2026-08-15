package com.njydsz.common.json.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

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
 *   <li>{@link #computeIfAbsent} 锁外构建 + 锁内双检，构建期间不阻塞其他线程</li>
 * </ul>
 *
 * <p><b>并发说明：</b>{@code accessOrder=true} 的 LinkedHashMap 在 {@code get()} 时会重排链表
 * （afterNodeAccess 副作用），因此 {@link #get} 必须持写锁，否则并发读会损坏链表结构。</p>
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
     * <p>accessOrder 模式下 {@code get()} 会重排链表，必须持写锁执行，
     * 否则并发读会产生数据竞争导致链表损坏（P0-1 修复）。</p>
     *
     * @param key 键
     * @return 缓存值，不存在返回 null
     */
    public V get(K key) {
        lock.writeLock().lock();
        try {
            return map.get(key);
        } finally {
            lock.writeLock().unlock();
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
     * 执行"查-建-存"：键已存在直接返回；否则用 mappingFunction 创建并缓存。
     *
     * <p>构建函数在锁外执行（可能包含反射扫描全字段等重操作），避免冷启动并发时
     * 阻塞其他线程；构建完成后在写锁内双检，并发构建时先到者生效，保证全线程
     * 可见同一实例（P0-1 修复：原先在持写锁状态下执行 mappingFunction）。</p>
     *
     * @param key 键
     * @param mappingFunction 值创建函数（不应为 null）
     * @return 缓存值；mappingFunction 返回 null 时不缓存并返回 null
     */
    public V computeIfAbsent(K key, Function<K, V> mappingFunction) {
        V existing = get(key);
        if (existing != null) {
            return existing;
        }
        V created = mappingFunction.apply(key);
        if (created == null) {
            return null;
        }
        lock.writeLock().lock();
        try {
            V winner = map.putIfAbsent(key, created);
            return winner != null ? winner : created;
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
