package com.njydsz.common.json.util;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * 线程安全的有界 LRU 缓存（零外部依赖实现）。
 *
 * <p>用于替代无淘汰策略的 {@code ConcurrentHashMap} 静态缓存，防止在热部署、
 * 动态类加载、多租户等场景下缓存无限增长导致内存泄漏（OOM）。</p>
 *
 * <p><b>特性：</b></p>
 * <ul>
 *   <li>容量上限 {@code maxSize}，超出后按访问顺序淘汰最旧条目</li>
 *   <li><b>读路径无锁</b>：{@link #get} 走 {@link ConcurrentHashMap}，消除原先
 *       accessOrder {@code LinkedHashMap.get()} 持写锁导致的热路径互斥（P0-2 修复）</li>
 *   <li>写路径（put / computeIfAbsent）在轻量 {@link ReentrantLock} 内同步
 *       LRU 淘汰顺序，构建函数在锁外执行，构建期间不阻塞读线程</li>
 *   <li>零外部依赖，仅使用 JDK 内置容器</li>
 * </ul>
 *
 * <p><b>淘汰语义（近似 LRU）：</b>读操作不更新淘汰顺序（无锁读无法安全重排链表），
 * 淘汰依据为"最近写入/覆盖顺序"。对类元数据、序列化器等键集合基本固定的缓存，
 * 实际效果与严格 LRU 一致；键高频换入换出的场景退化为 FIFO，仅影响缓存命中率，
 * 不影响正确性。此权衡优先保证序列化热路径读性能。</p>
 *
 * <p><b>并发说明：</b>真实数据存于 {@link ConcurrentHashMap}（线程安全），
 * {@code LinkedHashMap} 仅维护淘汰顺序（全部操作持锁），两个容器在写路径内保持同步。</p>
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

    /** 真实数据存储（读路径无锁） */
    private final ConcurrentHashMap<K, V> map;

    /** 淘汰顺序（仅写锁内访问，accessOrder 链表头为最旧条目） */
    private final LinkedHashMap<K, K> lruOrder;

    /** 写路径轻量锁（保护 lruOrder 与 map 的同步淘汰） */
    private final ReentrantLock lock = new ReentrantLock();

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
        this.map = new ConcurrentHashMap<>(Math.min(maxSize * 2, 64));
        this.lruOrder = new LinkedHashMap<>(Math.min(maxSize * 2, 64), 0.75f, false);
    }

    /**
     * 获取缓存值（无锁读路径）。
     *
     * <p>P0-2 修复：原先 accessOrder {@code LinkedHashMap.get()} 因链表重排副作用
     * 必须持写锁，导致序列化器缓存等热路径读全互斥。现改为 {@link ConcurrentHashMap}
     * 直读，读不更新淘汰顺序（近似 LRU，见类注释）。</p>
     *
     * @param key 键
     * @return 缓存值，不存在返回 null
     */
    public V get(K key) {
        return map.get(key);
    }

    /**
     * 写入缓存（若已存在则覆盖，并刷新淘汰顺序）。
     *
     * @param key 键
     * @param value 值
     * @return 之前的缓存值，不存在返回 null
     */
    public V put(K key, V value) {
        if (value == null) {
            // ConcurrentHashMap 不允许 null value；与旧实现保持一致语义（不缓存）
            return map.get(key);
        }
        lock.lock();
        try {
            V prev = map.put(key, value);
            lruOrder.remove(key);
            lruOrder.put(key, key);
            evictIfNeeded();
            return prev;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 执行"查-建-存"：键已存在直接返回；否则用 mappingFunction 创建并缓存。
     *
     * <p>构建函数在锁外执行（可能包含反射扫描全字段等重操作），避免冷启动并发时
     * 阻塞其他线程；构建完成后在写锁内双检，并发构建时先到者生效，保证全线程
     * 可见同一实例。构建函数内部递归调用本缓存方法是安全的（数据表为 CHM，
     * 不存在 CHM computeIfAbsent 的 Recursive update 问题）。</p>
     *
     * @param key 键
     * @param mappingFunction 值创建函数（不应为 null）
     * @return 缓存值；mappingFunction 返回 null 时不缓存并返回 null
     */
    public V computeIfAbsent(K key, Function<K, V> mappingFunction) {
        V existing = map.get(key);
        if (existing != null) {
            return existing;
        }
        V created = mappingFunction.apply(key);
        if (created == null) {
            return null;
        }
        lock.lock();
        try {
            V winner = map.putIfAbsent(key, created);
            if (winner != null) {
                // 并发竞争失败：保留先到者，丢弃本次构建（不污染淘汰顺序）
                return winner;
            }
            lruOrder.put(key, key);
            evictIfNeeded();
            return created;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 在持锁状态下淘汰超限条目（从两个容器同步移除最旧条目）。
     */
    private void evictIfNeeded() {
        while (map.size() > maxSize && !lruOrder.isEmpty()) {
            Iterator<Map.Entry<K, K>> it = lruOrder.entrySet().iterator();
            if (it.hasNext()) {
                K eldest = it.next().getKey();
                it.remove();
                map.remove(eldest);
            } else {
                break;
            }
        }
    }

    /**
     * 清空所有缓存条目。
     */
    public void clear() {
        lock.lock();
        try {
            map.clear();
            lruOrder.clear();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 当前缓存条目数。
     *
     * @return 条目数
     */
    public int size() {
        return map.size();
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
