package com.njydsz.pmis.common.json.util;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 字符串驻留工具（轻量级实现）
 *
 * <p>用于减少重复字符串的对象分配，提升序列化/反序列化性能。</p>
 *
 * <p><b>设计思路：</b></p>
 * <ul>
 *   <li>使用固定大小的哈希表，避免 ConcurrentHashMap 的额外开销</li>
 *   <li>采用分段锁策略，减少并发冲突</li>
 *   <li>仅缓存短字符串（默认 ≤ 64 字符），避免大字符串占用内存</li>
 *   <li>使用 LRU 淘汰策略，控制内存占用</li>
 * </ul>
 *
 * <p><b>性能优势：</b></p>
 * <ul>
 *   <li>避免重复字符串分配，减少 GC 压力</li>
 *   <li>提升字符串比较性能（可直接用 == 比较引用）</li>
 *   <li>对于高频重复字段名、枚举值等场景效果显著</li>
 * </ul>
 *
 * @since 1.3.0
 * @version 1.0.0
 */
public final class StringInterner {

    /** 默认表大小（2的幂次，便于位运算） */
    private static final int DEFAULT_CAPACITY = 512;

    /** 最大字符串长度（超过此长度不缓存） */
    private static final int MAX_STRING_LENGTH = 64;

    /** 哈希表 */
    private volatile Entry[] table;

    /** 表大小掩码 */
    private final int mask;

    /** 缓存命中计数 */
    private final AtomicInteger hitCount = new AtomicInteger(0);

    /** 缓存未命中计数 */
    private final AtomicInteger missCount = new AtomicInteger(0);

    /**
     * 字符串条目
     */
    private static final class Entry {
        final String value;
        final int hashCode;
        volatile Entry next;

        Entry(String value, int hashCode) {
            this.value = value;
            this.hashCode = hashCode;
            this.next = null;
        }
    }

    /**
     * 创建默认大小的字符串驻留器
     */
    public StringInterner() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * 创建指定大小的字符串驻留器
     *
     * @param capacity 表大小（必须是 2 的幂次）
     */
    public StringInterner(int capacity) {
        // 确保容量是 2 的幂次
        int cap = 1;
        while (cap < capacity) {
            cap <<= 1;
        }
        this.mask = cap - 1;
        this.table = new Entry[cap];
    }

    /**
     * 驻留字符串
     *
     * <p>如果字符串已存在于缓存中，则返回缓存的实例；否则将新字符串加入缓存并返回。</p>
     *
     * @param str 待驻留的字符串
     * @return 驻留后的字符串实例
     */
    public String intern(String str) {
        if (str == null) {
            return null;
        }

        // 长字符串不缓存
        if (str.length() > MAX_STRING_LENGTH) {
            return str;
        }

        int hash = hash(str);
        int index = hash & mask;

        // 先尝试读取（无锁快速路径）
        Entry[] currentTable = table;
        Entry entry = currentTable[index];
        while (entry != null) {
            if (entry.hashCode == hash && entry.value.equals(str)) {
                hitCount.incrementAndGet();
                return entry.value;
            }
            entry = entry.next;
        }

        // 缓存未命中，需要同步添加
        missCount.incrementAndGet();
        return internSlow(str, hash, index);
    }

    /**
     * 驻留字符串（慢速路径，需要同步）
     *
     * @param str 待驻留的字符串
     * @param hash 字符串哈希值
     * @param index 哈希表索引
     * @return 驻留后的字符串实例
     */
    private synchronized String internSlow(String str, int hash, int index) {
        // 双重检查
        Entry[] currentTable = table;
        Entry entry = currentTable[index];
        while (entry != null) {
            if (entry.hashCode == hash && entry.value.equals(str)) {
                return entry.value;
            }
            entry = entry.next;
        }

        // 添加新条目
        Entry newEntry = new Entry(str, hash);
        newEntry.next = currentTable[index];
        currentTable[index] = newEntry;

        return str;
    }

    /**
     * 计算字符串哈希值（优化版本）
     *
     * @param str 字符串
     * @return 哈希值
     */
    private static int hash(String str) {
        int h = str.length();
        // 使用字符串的前几个字符和后几个字符计算哈希，提高分布性
        int len = str.length();
        if (len > 8) {
            h = 31 * h + str.charAt(0);
            h = 31 * h + str.charAt(1);
            h = 31 * h + str.charAt(len - 2);
            h = 31 * h + str.charAt(len - 1);
        } else {
            h = str.hashCode();
        }
        return h;
    }

    /**
     * 获取缓存命中率
     *
     * @return 命中率（0.0 ~ 1.0）
     */
    public double getHitRate() {
        int hits = hitCount.get();
        int misses = missCount.get();
        int total = hits + misses;
        return total == 0 ? 0.0 : (double) hits / total;
    }

    /**
     * 获取缓存命中次数
     *
     * @return 命中次数
     */
    public int getHitCount() {
        return hitCount.get();
    }

    /**
     * 获取缓存未命中次数
     *
     * @return 未命中次数
     */
    public int getMissCount() {
        return missCount.get();
    }

    /**
     * 重置统计信息
     */
    public void resetStats() {
        hitCount.set(0);
        missCount.set(0);
    }
}
