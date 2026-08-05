package com.remisoft.common.json.cache;

import java.lang.ref.SoftReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.remisoft.common.json.asm.AsmBeanCodecGenerator;
import com.remisoft.common.json.asm.AsmDeserializer;
import com.remisoft.common.json.asm.AsmSerializer;
import com.remisoft.common.json.writer.JSONWriter;

/**
 * ASM 序列化器/反序列化器缓存
 * 
 * <p>缓存 ASM 生成的专用序列化器和反序列化器，避免重复生成字节码。</p>
 * 
 * <p><b>工作原理：</b></p>
 * <ul>
 *   <li>首次使用时为 Bean 类生成专用序列化器、反序列化器。</li>
 *   <li>生成后的类缓存在 ConcurrentHashMap 。</li>
 *   <li>后续使用直接从缓存获取，零开销</li>
 * </ul>
 * 
 * @author remi-team
 * @since 1.0.0
 */
public final class AsmCodecCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsmCodecCache.class);

    private static final int DEFAULT_MAX_SIZE = 1024;

    private static final int FAILED_CACHE_MAX_SIZE = 256;

    /**
     * L1 缓存尺寸（强引用，保护最热条目永不 GC 回收）。
     *
     * <p>经验值：64 足以覆盖绝大多数业务系统的热点 Bean 类型（Controller/DTO
     * 通常不超过 30 种），在保证零 GC 抖动的同时控制强引用内存开销。</p>
     */
    private static final int L1_STRONG_SIZE = 64;

    /**
     * L2 缓存尺寸（软引用，作为溢出层）。
     *
     * <p>仍保留 1024 以满足超多 Bean 类型场景（如动态生成的 DTO），
     * GC 压力下优先回收 L2 中的冷条目而非 L1 热条目。</p>
     */
    private static final int L2_SOFT_SIZE = DEFAULT_MAX_SIZE;

    private static final TieredCodecCache<AsmSerializer<?>> SERIALIZER_CACHE =
        new TieredCodecCache<>(L1_STRONG_SIZE, L2_SOFT_SIZE);

    private static final TieredCodecCache<AsmDeserializer<?>> DESERIALIZER_CACHE =
        new TieredCodecCache<>(L1_STRONG_SIZE, L2_SOFT_SIZE);

    private static final LruCache<Boolean> SERIALIZER_FAILED = 
        new LruCache<>(FAILED_CACHE_MAX_SIZE);

    private static final LruCache<Boolean> DESERIALIZER_FAILED = 
        new LruCache<>(FAILED_CACHE_MAX_SIZE);

    /**
     * 缓存分层结果码。
     *
     * <p>标识一次 {@code get} 操作命中的层级或未命中，可供监控埋点使用。</p>
     */
    enum HitTier {
        /** L1 强引用缓存命中（最热数据） */
        L1_HIT,
        /** L2 软引用缓存命中（被重新提升到 L1 后返回） */
        L2_HIT,
        /** 两层均未命中 */
        MISS
    }

    /**
     * 双层缓存（L1 强引用 + L2 软引用），专为减少 GC 抖动设计。
     *
     * <p><b>架构动机：</b></p>
     * 纯 SoftRef 缓存在 GC 压力下会被一次性全部回收，引发缓存雪崩：
     * 大量未命中 → 集中重建 ASM 字节码 → CPU 尖峰 + 瞬时堆增长 → 再次 GC → 震荡。
     * 通过 L1 强引用层保护最热的 {@code l1Size} 个条目，
     * 永远不会被 GC 提前回收，从根本上消除抖动。
     *
     * <p><b>两层职责：</b></p>
     * <ul>
     *   <li>L1 (strong)：小容量强引用缓存，保存最热的 Bean 序列化器/反序列化器。
     *       命中 L1 等价于过去纯强引用方案的性能与稳定性。</li>
     *   <li>L2 (soft)：大容量软引用缓存，作为溢出层，收容 L1 淘汰的条目。
     *       GC 紧张时允许回收，但回收是渐进的（LRU 队尾最久未访问优先），
     *       不会出现雪崩式集体失效。</li>
     * </ul>
     *
     * <p><b>晋升/淘汰策略：</b></p>
     * <ul>
     *   <li>get：L1 miss → 查 L2。若 L2-hit 且条目仍存活，则晋升回 L1。</li>
     *   <li>put：入 L1。L1 溢出则队首降级到 L2；L2 溢出则队首逐出。</li>
     * </ul>
     *
     * <p><b>线程安全：</b>所有操作均基于 ConcurrentHashMap + AtomicInteger，无需外部同步。</p>
     *
     * @param <T> 缓存值类型
     * @since 1.1.0
     */
    static class TieredCodecCache<T> {
        /** L1 强引用最大条目数（建议 64 ~ 256） */
        private final int l1MaxSize;
        /** L2 软引用最大条目数 */
        private final int l2MaxSize;

        /** L1 强引用存储（最热条目） */
        private final ConcurrentHashMap<Class<?>, T> l1;
        /** L2 软引用存储（溢出层） */
        private final ConcurrentHashMap<Class<?>, SoftReference<T>> l2;

        /** L1 访问顺序队列 */
        private final ConcurrentLinkedDeque<Class<?>> l1Access = new ConcurrentLinkedDeque<>();
        /** L2 访问顺序队列 */
        private final ConcurrentLinkedDeque<Class<?>> l2Access = new ConcurrentLinkedDeque<>();

        /** L1 原子计数 */
        private final java.util.concurrent.atomic.AtomicInteger l1Size = new AtomicInteger(0);
        /** L2 原子计数 */
        private final java.util.concurrent.atomic.AtomicInteger l2Size = new AtomicInteger(0);

        TieredCodecCache(int l1MaxSize, int l2MaxSize) {
            this.l1MaxSize = l1MaxSize;
            this.l2MaxSize = l2MaxSize;
            this.l1 = new ConcurrentHashMap<>(l1MaxSize, 0.75f, 32);
            this.l2 = new ConcurrentHashMap<>(l2MaxSize, 0.75f, 64);
        }

        /**
         * 查找条目，L1 → L2 顺序，L2 命中时晋升到 L1。
         *
         * @return 命中的条目，或 {@code null}
         */
        T get(Class<?> key) {
            // L1（强引用，GC 安全，最热条目始终存活）
            T l1Val = l1.get(key);
            if (l1Val != null) {
                recordL1Access(key);
                return l1Val;
            }
            // L2（软引用，可能已被 GC 回收）
            SoftReference<T> l2Ref = l2.get(key);
            if (l2Ref == null) return null;
            T l2Val = l2Ref.get();
            if (l2Val == null) {
                // SoftReference 已被 GC，移除过期条目
                l2.remove(key, l2Ref);
                l2Size.decrementAndGet();
                return null;
            }
            // L2 命中 → 晋升回 L1
            promoteToL1(key, l2Val);
            return l2Val;
        }

        /**
         * 存入条目：仅入 L1（强引用层），L1 溢出时最久未访问条目降级到 L2。
         *
         * <p>L2 只通过 {@link #demoteL1ToL2} 被动填充（即 L1 淘汰），
         * 不存在"同写两层"的冗余，两层职责明确：L1 为热路径，L2 为冷备份。</p>
         */
        void put(Class<?> key, T value) {
            T previousL1 = l1.put(key, value);
            if (previousL1 == null) {
                l1Size.incrementAndGet();
            }
            recordL1Access(key);

            // L1 溢出 → 降级最久未访问条目到 L2
            demoteL1ToL2();
        }

        /**
         * 将条目从 L2 晋升到 L1。
         */
        private void promoteToL1(Class<?> key, T value) {
            l1.put(key, value);
            l1Size.incrementAndGet();
            recordL1Access(key);
            // 从 L2 移除（避免重复存在）
            SoftReference<T> removedL2 = l2.remove(key);
            if (removedL2 != null) {
                l2Size.decrementAndGet();
                l2Access.remove(key);
            }
            // 晋升可能引发 L1 溢出
            demoteL1ToL2();
        }

        /**
         * L1 超出容量时，把最久未访问的条目降级到 L2。
         */
        private void demoteL1ToL2() {
            while (l1Size.get() > l1MaxSize) {
                Class<?> eldest = l1Access.pollFirst();
                if (eldest == null) break;
                T evicted = l1.remove(eldest);
                if (evicted == null) continue;
                l1Size.decrementAndGet();
                // 降级到 L2（L2 的 put 在方法外层统一处理，这里只需尝试）
                SoftReference<T> previousL2 = l2.put(eldest, new SoftReference<>(evicted));
                if (previousL2 == null || previousL2.get() == null) {
                    // 若 L2 中原本没有此条目（条目首次被降级），增加计数
                    // 否则是刷新已有活条目，计数不变
                    l2Size.incrementAndGet();
                }
                recordL2Access(eldest);
                evictL2IfNeeded();
                break; // 每次 demote 一条即可
            }
        }

        void clear() {
            l1.clear();
            l2.clear();
            l1Access.clear();
            l2Access.clear();
            l1Size.set(0);
            l2Size.set(0);
        }

        /**
         * 返回缓存条目近似总数（L1 + L2 的弱一致快照）。
         *
         * <p>由于并发操作，结果仅供监控参考，非精确值。
         */
        int size() {
            return Math.max(0, l1Size.get() + l2Size.get());
        }

        /**
         * 返回 L1 层条目数（强引用保护的最热条目）。
         */
        int l1Size() {
            return Math.max(0, l1Size.get());
        }

        /**
         * 返回 L2 近似条目数（软引用，部分条目可能已被 GC 回收但尚未清理）。
         */
        int l2Size() {
            return Math.max(0, l2Size.get());
        }

        /**
         * 返回 L2 中实际存活的条目数（精确但 O(n)，仅用于低频监控）。
         */
        int l2LiveSize() {
            int total = 0;
            for (SoftReference<T> ref : l2.values()) {
                if (ref != null && ref.get() != null) ++total;
            }
            return total;
        }

        private void recordL1Access(Class<?> key) {
            l1Access.remove(key);
            l1Access.addLast(key);
        }

        private void recordL2Access(Class<?> key) {
            l2Access.remove(key);
            l2Access.addLast(key);
        }

        private void evictL2IfNeeded() {
            while (l2Size.get() > l2MaxSize) {
                Class<?> eldest = l2Access.pollFirst();
                if (eldest == null) break;
                SoftReference<T> removed = l2.remove(eldest);
                if (removed != null) {
                    l2Size.decrementAndGet();
                }
            }
        }
    }

    /**
     * 基于 ConcurrentHashMap 的高并发强引用 LRU 缓存。
     *
     * <p>使用与 {@link TieredCodecCache} 相同的高并发策略，但仅持有强引用（无软引用层）。
     * 适用于缓存失效成本较低或条目生命周期较短的场景。</p>
     *
     * <p><b>线程安全：</b>所有操作均为线程安全，无需外部同步。</p>
     *
     * @param <T> 缓存值的类型
     * @since 1.1.0
     */
    static class LruCache<T> {
        private final int maxSize;
        private final ConcurrentHashMap<Class<?>, T> map;
        /** 访问顺序队列（用于 LRU 淘汰参考） */
        private final ConcurrentLinkedDeque<Class<?>> accessOrder = new ConcurrentLinkedDeque<>();
        /** 实际条目的原子计数 */
        private final java.util.concurrent.atomic.AtomicInteger size = new AtomicInteger(0);

        LruCache(int maxSize) {
            this.maxSize = maxSize;
            this.map = new ConcurrentHashMap<>(maxSize, 0.75f, 64);
        }

        T get(Class<?> key) {
            T value = map.get(key);
            if (value != null) {
                recordAccess(key);
            }
            return value;
        }

        void put(Class<?> key, T value) {
            T previous = map.put(key, value);
            if (previous == null) {
                size.incrementAndGet();
            }
            recordAccess(key);
            evictIfNeeded();
        }

        boolean containsKey(Class<?> key) {
            return map.containsKey(key);
        }

        void clear() {
            map.clear();
            accessOrder.clear();
            size.set(0);
        }

        int size() {
            return Math.max(0, size.get());
        }

        private void recordAccess(Class<?> key) {
            accessOrder.remove(key);
            accessOrder.addLast(key);
        }

        private void evictIfNeeded() {
            while (size.get() > maxSize) {
                Class<?> eldest = accessOrder.pollFirst();
                if (eldest == null) break;
                if (map.remove(eldest) != null) {
                    size.decrementAndGet();
                }
            }
        }
    }

    /**
     * 获取或创建 ASM 序列化器
     *
     * <p>优化：使用单独的 ConcurrentHashMap.get() 替代 containsKey() + get() 双次查找</p>
     */
    
    public static <T> AsmSerializer<T> getOrCreateSerializer(Class<T> beanType) {
        AsmSerializer<?> cached = SERIALIZER_CACHE.get(beanType);
        if (cached != null) {
            recordSerializerHit();
            return castSerializer(cached);
        }

        recordSerializerMiss();

        if (SERIALIZER_FAILED.containsKey(beanType)) {
            return null;
        }

        if (!AsmBeanCodecGenerator.isAsmAvailable()) {
            return null;
        }

        try {
            Class<? extends AsmSerializer<T>> asmClass = AsmBeanCodecGenerator.generateSerializer(beanType);
            AsmSerializer<T> serializer = asmClass.getDeclaredConstructor().newInstance();
            SERIALIZER_CACHE.put(beanType, serializer);
            return serializer;
        } catch (Exception e) {
            LOGGER.warn("ASM serializer generation failed for {}, falling back to reflection", beanType.getName(), e);
            SERIALIZER_FAILED.put(beanType, Boolean.TRUE);
            return null;
        }
    }

    /**
     * 获取或创建 ASM 序列化器（非类型参数版本，接受 Class<?>）
     *
     * @param beanType Bean 类型
     * @return 序列化器实例，获取失败返回null
     */
    public static AsmSerializer<?> getOrCreateSerializerForType(Class<?> beanType) {
        AsmSerializer<?> cached = SERIALIZER_CACHE.get(beanType);
        if (cached != null) {
            recordSerializerHit();
            return cached;
        }

        recordSerializerMiss();

        if (SERIALIZER_FAILED.containsKey(beanType)) {
            return null;
        }

        if (!AsmBeanCodecGenerator.isAsmAvailable()) {
            return null;
        }

        try {
            Class<? extends AsmSerializer<?>> asmClass = AsmBeanCodecGenerator.generateSerializerForType(beanType);
            AsmSerializer<?> serializer = asmClass.getDeclaredConstructor().newInstance();
            SERIALIZER_CACHE.put(beanType, serializer);
            return serializer;
        } catch (Exception e) {
            LOGGER.warn("ASM serializer generation failed for {}, falling back to reflection", beanType.getName(), e);
            SERIALIZER_FAILED.put(beanType, Boolean.TRUE);
            return null;
        }
    }

    /**
     * 使用 ASM 序列化器序列化对。
     *
     * <p>封装从缓存获取序列化器并调用序列化的完整流程。
     * 内部通过泛型辅助方法捕获通配符类型，避免调用。unchecked cast</p>
     *
     * @param obj 要序列化的对。
     * @param writer JSON 写入。
     * @return 是否成功使用 ASM 序列化器完成序列。
     */
    public static boolean trySerialize(Object obj, JSONWriter writer) {
        if (obj == null) return false;
        AsmSerializer<?> serializer = getOrCreateSerializerForType(obj.getClass());
        if (serializer == null) return false;
        invokeSerializer(serializer, obj, writer);
        return true;
    }

    /**
     * 使用预解析的 ASM 序列化器序列化对。
     *
     * <p>适用于集合元素同类型场景，避免重复查找序列化器，
     * 内部通过泛型辅助方法捕获通配符类型，避免调用。unchecked cast</p>
     *
     * @param serializer 预解析的序列化器（可为 AsmSerializer<?>）
     * @param obj 要序列化的对。
     * @param writer JSON 写入。
     * @return 是否成功完成序列。
     */
    public static boolean serializeWithSerializer(AsmSerializer<?> serializer, Object obj, JSONWriter writer) {
        if (serializer == null || obj == null) return false;
        invokeSerializer(serializer, obj, writer);
        return true;
    }

    /**
     * 泛型辅助方法：捕获 AsmSerializer 的通配符类型，实现类型安全的序列化调用
     *
     * <p>调用方通过此方法间接调用serializer.serialize()。
     * 。unchecked cast 隔离在此方法内部，避免在各调用点重复出现</p>
     */
    private static <T> void invokeSerializer(AsmSerializer<T> serializer, Object obj, JSONWriter writer) {
        T typedObj = (T) obj;
        serializer.serialize(typedObj, writer);
    }

    private static <T> AsmSerializer<T> castSerializer(AsmSerializer<?> serializer) {
        return (AsmSerializer<T>) serializer;
    }

    /**
     * 获取或创建 ASM 反序列化器。
     *
     * <p>优化：使用单独的 ConcurrentHashMap.get() 替代 containsKey() + get() 双次查找</p>
     */
    
    public static <T> AsmDeserializer<T> getOrCreateDeserializer(Class<T> beanType) {
        AsmDeserializer<?> cached = DESERIALIZER_CACHE.get(beanType);
        if (cached != null) {
            recordDeserializerHit();
            return castDeserializer(cached);
        }

        recordDeserializerMiss();

        if (DESERIALIZER_FAILED.containsKey(beanType)) {
            return null;
        }

        if (!AsmBeanCodecGenerator.isAsmAvailable()) {
            return null;
        }

        try {
            Class<? extends AsmDeserializer<T>> asmClass = AsmBeanCodecGenerator.generateDeserializer(beanType);
            AsmDeserializer<T> deserializer = asmClass.getDeclaredConstructor().newInstance();
            DESERIALIZER_CACHE.put(beanType, deserializer);
            return deserializer;
        } catch (Exception e) {
            DESERIALIZER_FAILED.put(beanType, Boolean.TRUE);
            return null;
        }
    }

    /**
     * 获取或创。ASM 反序列化器（非类型参数版本，接受 Class<?>。
     *
     * @param beanType Bean 类型
     * @return 反序列化器实例，获取失败返回 null
     */
    public static AsmDeserializer<?> getOrCreateDeserializerForType(Class<?> beanType) {
        AsmDeserializer<?> cached = DESERIALIZER_CACHE.get(beanType);
        if (cached != null) {
            recordDeserializerHit();
            return cached;
        }

        recordDeserializerMiss();

        if (DESERIALIZER_FAILED.containsKey(beanType)) {
            return null;
        }

        if (!AsmBeanCodecGenerator.isAsmAvailable()) {
            return null;
        }

        try {
            Class<? extends AsmDeserializer<?>> asmClass = AsmBeanCodecGenerator.generateDeserializerForType(beanType);
            AsmDeserializer<?> deserializer = asmClass.getDeclaredConstructor().newInstance();
            DESERIALIZER_CACHE.put(beanType, deserializer);
            return deserializer;
        } catch (Exception e) {
            DESERIALIZER_FAILED.put(beanType, Boolean.TRUE);
            return null;
        }
    }

    private static <T> AsmDeserializer<T> castDeserializer(AsmDeserializer<?> deserializer) {
        return (AsmDeserializer<T>) deserializer;
    }

    /**
     * 检查是否启用 ASM 优化
     */
    public static boolean isEnabled() {
        return AsmBeanCodecGenerator.isAsmAvailable();
    }

    /**
     * 获取缓存大小监控信息
     *
     * @return 缓存统计信息字符。
     */
    public static String getCacheSize() {
        return String.format(
            "SerializerCache[L1=%d, L2=%d], DeserializerCache[L1=%d, L2=%d], SerializerFailed: %d, DeserializerFailed: %d",
            SERIALIZER_CACHE.l1Size(),
            SERIALIZER_CACHE.l2Size(),
            DESERIALIZER_CACHE.l1Size(),
            DESERIALIZER_CACHE.l2Size(),
            SERIALIZER_FAILED.size(),
            DESERIALIZER_FAILED.size()
        );
    }

    /**
     * @return 序列化器 L1（强引用）缓存大小
     */
    public static int serializerL1Size() {
        return SERIALIZER_CACHE.l1Size();
    }

    /**
     * @return 序列化器 L2（软引用）缓存大小
     */
    public static int serializerL2Size() {
        return SERIALIZER_CACHE.l2Size();
    }

    /**
     * @return 反序列化器 L1（强引用）缓存大小
     */
    public static int deserializerL1Size() {
        return DESERIALIZER_CACHE.l1Size();
    }

    /**
     * @return 反序列化器 L2（软引用）缓存大小
     */
    public static int deserializerL2Size() {
        return DESERIALIZER_CACHE.l2Size();
    }

    /**
     * 清理所有缓存
     */
    public static void clearCache() {
        SERIALIZER_CACHE.clear();
        DESERIALIZER_CACHE.clear();
        SERIALIZER_FAILED.clear();
        DESERIALIZER_FAILED.clear();
    }

    // ==================== 真实命中率计数 ====================

    /** 序列化器缓存命中次数（使用 LongAdder 支持高并发无锁累加） */
    private static final java.util.concurrent.atomic.LongAdder SERIALIZER_HITS =
        new java.util.concurrent.atomic.LongAdder();

    /** 序列化器缓存未命中次数 */
    private static final java.util.concurrent.atomic.LongAdder SERIALIZER_MISSES =
        new java.util.concurrent.atomic.LongAdder();

    /** 反序列化器缓存命中次数 */
    private static final java.util.concurrent.atomic.LongAdder DESERIALIZER_HITS =
        new java.util.concurrent.atomic.LongAdder();

    /** 反序列化器缓存未命中次数 */
    private static final java.util.concurrent.atomic.LongAdder DESERIALIZER_MISSES =
        new java.util.concurrent.atomic.LongAdder();

    /**
     * 记录序列化器缓存命中。
     * 在 {@link #getOrCreateSerializer} 命中缓存时调用。
     */
    static void recordSerializerHit() {
        SERIALIZER_HITS.increment();
    }

    /**
     * 记录序列化器缓存未命中。
     * 在 {@link #getOrCreateSerializer} 未命中缓存时调用。
     */
    static void recordSerializerMiss() {
        SERIALIZER_MISSES.increment();
    }

    /**
     * 记录反序列化器缓存命中。
     */
    static void recordDeserializerHit() {
        DESERIALIZER_HITS.increment();
    }

    /**
     * 记录反序列化器缓存未命中。
     */
    static void recordDeserializerMiss() {
        DESERIALIZER_MISSES.increment();
    }

    /**
     * 返回缓存统计快照。
     *
     * <p>提供真实的 hit/miss 计数和精确的命中率计算，替代原来基于饱和度的估算方式。</p>
     *
     * @return 缓存统计（包含条目数、命中数、未命中数、真实命中率）
     */
    public static CacheStats getCacheStats() {
        int serSize = SERIALIZER_CACHE.size();
        int deserSize = DESERIALIZER_CACHE.size();
        long serHits = SERIALIZER_HITS.sum();
        long serMisses = SERIALIZER_MISSES.sum();
        long deserHits = DESERIALIZER_HITS.sum();
        long deserMisses = DESERIALIZER_MISSES.sum();

        long serTotal = serHits + serMisses;
        long deserTotal = deserHits + deserMisses;

        double serHitRate = serTotal > 0 ? (double) serHits / serTotal : 0.0;
        double deserHitRate = deserTotal > 0 ? (double) deserHits / deserTotal : 0.0;

        return new CacheStats(
            serSize, deserSize,
            serHits, serMisses, deserHits, deserMisses,
            serHitRate, deserHitRate
        );
    }

    /**
     * 重置所有命中率计数器（用于测试或统计周期清零）。
     */
    public static void resetHitCounters() {
        SERIALIZER_HITS.reset();
        SERIALIZER_MISSES.reset();
        DESERIALIZER_HITS.reset();
        DESERIALIZER_MISSES.reset();
    }

    /**
     * ASM 缓存统计（包含真实命中率计数）。
     *
     * @param serializerCount      序列化器缓存条目数
     * @param deserializerCount    反序列化器缓存条目数
     * @param serializerHits       序列化器缓存命中次数
     * @param serializerMisses     序列化器缓存未命中次数
     * @param deserializerHits     反序列化器缓存命中次数
     * @param deserializerMisses   反序列化器缓存未命中次数
     * @param serializerHitRate    序列化器真实命中率 (0.0 ~ 1.0)
     * @param deserializerHitRate  反序列化器真实命中率 (0.0 ~ 1.0)
     * @since 1.0.0
     */
    public record CacheStats(
        int serializerCount,
        int deserializerCount,
        long serializerHits,
        long serializerMisses,
        long deserializerHits,
        long deserializerMisses,
        double serializerHitRate,
        double deserializerHitRate
    ) {
    }
}
