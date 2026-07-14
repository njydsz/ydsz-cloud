package com.njydsz.pmis.common.json.cache;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 带 LRU 淘汰策略的字段元数据缓存
 *
 * <p>解决原有 ConcurrentHashMap 无限增长的问题，采用分段 LRU 淘汰策略。</p>
 *
 * <p><b>缓存策略：</b></p>
 * <ul>
 *   <li>最大容量 4096 - 防止内存溢出</li>
 *   <li>LRU 淘汰 - 最近最少使用的条目优先淘汰</li>
 *   <li>分段锁 - 提高并发性能</li>
 *   <li>命中率统计 - 便于性能监控</li>
 * </ul>
 *
 * <p><b>线程安全：</b></p>
 * <ul>
 *   <li>读操作 - 无锁并发（volatile 保证可见性）</li>
 *   <li>写操作 - 读写锁保证互斥</li>
 *   <li>淘汰操作 - 写锁保护</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public final class LruFieldMetaCache {

    /** 默认最大容量 */
    private static final int DEFAULT_MAX_CAPACITY = 4096;

    /** 缓存条目数组（环形缓冲区） */
    private volatile Entry[] table;

    /** 最大容量 */
    private final int maxCapacity;

    /** 当前大小 */
    private final AtomicInteger size = new AtomicInteger(0);

    /** 访问顺序链表头 */
    private volatile Entry head;

    /** 访问顺序链表尾 */
    private volatile Entry tail;

    /** 读写锁 */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /** 命中计数 */
    private final AtomicInteger hitCount = new AtomicInteger(0);

    /** 未命中计数 */
    private final AtomicInteger missCount = new AtomicInteger(0);

    /** 淘汰计数 */
    private final AtomicInteger evictCount = new AtomicInteger(0);

    /**
     * 缓存条目
     */
    private static final class Entry {
        final Class<?> key;
        final FieldMeta[] value;
        final int hash;
        volatile Entry next;
                volatile Entry prev;
        volatile Entry accessPrev;
        volatile Entry accessNext;

        Entry(Class<?> key, FieldMeta[] value, int hash) {
            this.key = key;
            this.value = value;
            this.hash = hash;
        }
    }

    /**
     * 创建默认容量的 LRU 缓存
     */
    public LruFieldMetaCache() {
        this(DEFAULT_MAX_CAPACITY);
    }

    /**
     * 创建指定容量的 LRU 缓存
     *
     * @param maxCapacity 最大容量（必须是 2 的幂次）
     */
    public LruFieldMetaCache(int maxCapacity) {
        int cap = 1;
        while (cap < maxCapacity) {
            cap <<= 1;
        }
        this.maxCapacity = cap;
        this.table = new Entry[cap];
    }

    /**
     * 获取字段元数据
     *
     * @param clazz 类
     * @return 字段元数据数组，如果不存在返回 null
     */
    public FieldMeta[] get(Class<?> clazz) {
        if (clazz == null) {
            return null;
        }

        int hash = hash(clazz);
        int index = hash & (table.length - 1);

        // 无锁快速读取路径
        Entry[] currentTable = table;
        Entry entry = currentTable[index];
        while (entry != null) {
            if (entry.hash == hash && entry.key == clazz) {
                hitCount.incrementAndGet();
                moveToHead(entry);
                return entry.value;
            }
            entry = entry.next;
        }

        missCount.incrementAndGet();
        return null;
    }

    /**
     * 缓存字段元数据
     *
     * @param clazz 类
     * @param metas 字段元数据数组
     */
    public void put(Class<?> clazz, FieldMeta[] metas) {
        if (clazz == null || metas == null) {
            return;
        }

        int hash = hash(clazz);
        int index = hash & (table.length - 1);

        lock.writeLock().lock();
        try {
            Entry[] currentTable = table;
            Entry entry = currentTable[index];

            // 检查是否已存在
            while (entry != null) {
                if (entry.hash == hash && entry.key == clazz) {
                    return;
                }
                entry = entry.next;
            }

            // 检查是否需要淘汰
            if (size.get() >= maxCapacity) {
                evictLRU();
            }

            // 创建新条目并插入到链表头部
            Entry newEntry = new Entry(clazz, metas, hash);
            newEntry.next = currentTable[index];
            currentTable[index] = newEntry;

            // 添加到访问链表头部
            addToAccessHead(newEntry);

            size.incrementAndGet();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 淘汰最近最少使用的条目
     */
    private void evictLRU() {
        Entry toRemove = tail;
        if (toRemove != null) {
            removeFromAccessList(toRemove);
            removeFromTable(toRemove);
            size.decrementAndGet();
            evictCount.incrementAndGet();
        }
    }

    /**
     * 从哈希表中移除条目
     */
    private void removeFromTable(Entry entry) {
        int index = entry.hash & (table.length - 1);
        Entry current = table[index];
        Entry prev = null;

        while (current != null) {
            if (current == entry) {
                if (prev == null) {
                    table[index] = current.next;
                } else {
                    prev.next = current.next;
                }
                break;
            }
            prev = current;
            current = current.next;
        }
    }

    /**
     * 移动条目到访问链表头部
     */
    private void moveToHead(Entry entry) {
        lock.writeLock().lock();
        try {
            if (entry != head) {
                removeFromAccessList(entry);
                addToAccessHead(entry);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 添加到访问链表头部
     */
    private void addToAccessHead(Entry entry) {
        entry.accessPrev = null;
        entry.accessNext = head;

        if (head != null) {
            head.accessPrev = entry;
        }

        head = entry;

        if (tail == null) {
            tail = entry;
        }
    }

    /**
     * 从访问链表中移除条目
     */
    private void removeFromAccessList(Entry entry) {
        if (entry.accessPrev != null) {
            entry.accessPrev.accessNext = entry.accessNext;
        } else {
            head = entry.accessNext;
        }

        if (entry.accessNext != null) {
            entry.accessNext.accessPrev = entry.accessPrev;
        } else {
            tail = entry.accessPrev;
        }

        entry.accessPrev = null;
        entry.accessNext = null;
    }

    /**
     * 计算哈希值
     */
    private static int hash(Class<?> clazz) {
        int h = System.identityHashCode(clazz);
        h ^= (h >>> 20) ^ (h >>> 12);
        return h ^ (h >>> 7) ^ (h >>> 4);
    }

    /**
     * 获取命中率
     */
    public double getHitRate() {
        int hits = hitCount.get();
        int misses = missCount.get();
        int total = hits + misses;
        return total == 0 ? 0.0 : (double) hits / total;
    }

    /**
     * 获取当前缓存大小
     */
    public int getSize() {
        return size.get();
    }

    /**
     * 获取最大容量
     */
    public int getMaxCapacity() {
        return maxCapacity;
    }

    /**
     * 获取命中次数
     */
    public int getHitCount() {
        return hitCount.get();
    }

    /**
     * 获取未命中次数
     */
    public int getMissCount() {
        return missCount.get();
    }

    /**
     * 获取淘汰次数
     */
    public int getEvictCount() {
        return evictCount.get();
    }

    /**
     * 重置统计信息
     */
    public void resetStats() {
        hitCount.set(0);
        missCount.set(0);
        evictCount.set(0);
    }
}
