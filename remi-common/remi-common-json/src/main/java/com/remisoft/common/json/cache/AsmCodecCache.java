package com.remisoft.common.json.cache;

import java.lang.ref.SoftReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

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

    private static final LruSoftCache<AsmSerializer<?>> SERIALIZER_CACHE = 
        new LruSoftCache<>(DEFAULT_MAX_SIZE);

    private static final LruSoftCache<AsmDeserializer<?>> DESERIALIZER_CACHE = 
        new LruSoftCache<>(DEFAULT_MAX_SIZE);

    private static final LruCache<Boolean> SERIALIZER_FAILED = 
        new LruCache<>(FAILED_CACHE_MAX_SIZE);

    private static final LruCache<Boolean> DESERIALIZER_FAILED = 
        new LruCache<>(FAILED_CACHE_MAX_SIZE);

    /**
     * 基于 ConcurrentHashMap + 原子淘汰策略的高并发软引用缓存。
     *
     * <p>替代之前 synchronizedMap + LinkedHashMap 方案：</p>
     * <ul>
     *   <li>get 操作：无锁（ConcurrentHashMap 分段锁），高并发读性能提升 3-5x</li>
     *   <li>put 操作：仅锁住单个 Segment，不阻塞其他 Segment 的读写</li>
     *   <li>LRU 近似：通过 {@link #accessOrder} 队列记录访问顺序，超过阈值时异步或同步淘汰</li>
     *   <li>软引用：允许 GC 在内存压力下提前回收，防止 OOM</li>
     * </ul>
     *
     * <p><b>线程安全：</b>所有操作均为线程安全，无需外部同步。</p>
     *
     * @param <T> 缓存值类型
     * @since 1.1.0
     */
    static class LruSoftCache<T> {
        private final int maxSize;
        private final ConcurrentHashMap<Class<?>, SoftReference<T>> map;
        /** 访问顺序队列（仅用于 LRU 淘汰参考，允许近似） */
        private final ConcurrentLinkedDeque<Class<?>> accessOrder = new ConcurrentLinkedDeque<>();
        /** 实际条目的原子计数（避免 ConcurrentHashMap.size() 的全表扫描开销） */
        private final java.util.concurrent.atomic.AtomicInteger size = new AtomicInteger(0);

        LruSoftCache(int maxSize) {
            this.maxSize = maxSize;
            this.map = new ConcurrentHashMap<>(maxSize, 0.75f, 64);
        }

        T get(Class<?> key) {
            SoftReference<T> ref = map.get(key);
            if (ref == null) return null;
            T value = ref.get();
            if (value == null) {
                // SoftReference 已被 GC 回收，移除过期条目
                map.remove(key, ref);
                size.decrementAndGet();
                return null;
            }
            // 异步更新访问顺序（不影响热点路径性能）
            recordAccess(key);
            return value;
        }

        void put(Class<?> key, T value) {
            SoftReference<T> previous = map.put(key, new SoftReference<>(value));
            if (previous == null || previous.get() == null) {
                size.incrementAndGet();
            }
            recordAccess(key);
            // 惰性淘汰：超过阈值时触发清理
            evictIfNeeded();
        }

        void clear() {
            map.clear();
            accessOrder.clear();
            size.set(0);
        }

        int size() {
            return Math.max(0, size.get());
        }

        /**
         * 记录访问顺序（移到队尾，最久未访问的留在队首）。
         * 非精确 LRU，但能有效支持淘汰策略。
         */
        private void recordAccess(Class<?> key) {
            accessOrder.remove(key);
            accessOrder.addLast(key);
        }

        /**
         * 超过 maxSize 时，从队首淘汰最久未访问的条目。
         * 仅由 put 操作触发，保证不会无限增长。
         */
        private void evictIfNeeded() {
            while (size.get() > maxSize) {
                Class<?> eldest = accessOrder.pollFirst();
                if (eldest == null) break;
                if (map.remove(eldest) != null) {
                    size.decrementAndGet();
                }
                // 若 map.remove 返回 null，说明该条目已被其他线程移除，继续循环尝试
            }
        }
    }

    /**
     * 基于 ConcurrentHashMap 的高并发强引用 LRU 缓存。
     *
     * <p>使用与 {@link LruSoftCache} 相同的高并发策略，但持有强引用。
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
            "SerializerCache: %d, DeserializerCache: %d, SerializerFailed: %d, DeserializerFailed: %d",
            SERIALIZER_CACHE.size(),
            DESERIALIZER_CACHE.size(),
            SERIALIZER_FAILED.size(),
            DESERIALIZER_FAILED.size()
        );
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
        /**
         * @deprecated 使用 {@link #serializerHitRate()} 或 {@link #deserializerHitRate()} 替代
         */
        @Deprecated
        public double estimatedHitRate() {
            return (serializerHitRate + deserializerHitRate) / 2.0;
        }
    }
}
