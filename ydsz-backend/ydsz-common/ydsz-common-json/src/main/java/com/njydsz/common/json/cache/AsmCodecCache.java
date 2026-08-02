package com.njydsz.common.json.cache;

import java.lang.ref.SoftReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.StampedLock;

import com.njydsz.common.json.asm.AsmBeanCodecGenerator;
import com.njydsz.common.json.asm.AsmDeserializer;
import com.njydsz.common.json.asm.AsmSerializer;
import com.njydsz.common.json.writer.JSONWriter;

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
 * @author ydsz-team
 * @since 1.0.0
 */
public final class AsmCodecCache {

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

    static class LruSoftCache<T> {
                private final int maxSize;
        private final LinkedHashMap<Class<?>, SoftReference<T>> map;
        private final StampedLock lock = new StampedLock();

        LruSoftCache(int maxSize) {
            this.maxSize = maxSize;
            this.map = new LinkedHashMap<Class<?>, SoftReference<T>>(
                Math.min(maxSize, 16), 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Class<?>, SoftReference<T>> eldest) {
                    return size() > maxSize;
                }
            };
        }

        T get(Class<?> key) {
            long stamp = lock.tryOptimisticRead();
            SoftReference<T> ref = map.get(key);
            if (ref != null && lock.validate(stamp)) {
                T value = ref.get();
                if (value != null) {
                    return value;
                }
            }

            stamp = lock.readLock();
            try {
                ref = map.get(key);
                if (ref == null) {
                    return null;
                }
                T value = ref.get();
                if (value == null) {
                    long writeStamp = lock.tryConvertToWriteLock(stamp);
                    if (writeStamp == 0L) {
                        lock.unlockRead(stamp);
                        writeStamp = lock.writeLock();
                    } else {
                        stamp = writeStamp;
                    }
                    try {
                        map.remove(key);
                    } finally {
                        lock.unlock(writeStamp);
                    }
                    return null;
                }
                return value;
            } finally {
                lock.unlockRead(stamp);
            }
        }

        void put(Class<?> key, T value) {
            long stamp = lock.writeLock();
            try {
                map.put(key, new SoftReference<>(value));
            } finally {
                lock.unlockWrite(stamp);
            }
        }

        void clear() {
            long stamp = lock.writeLock();
            try {
                map.clear();
            } finally {
                lock.unlockWrite(stamp);
            }
        }

        int size() {
            long stamp = lock.tryOptimisticRead();
            int size = map.size();
            if (lock.validate(stamp)) {
                return size;
            }
            stamp = lock.readLock();
            try {
                return map.size();
            } finally {
                lock.unlockRead(stamp);
            }
        }
    }

    static class LruCache<T> {
                private final int maxSize;
        private final LinkedHashMap<Class<?>, T> map;
        private final StampedLock lock = new StampedLock();

        LruCache(int maxSize) {
            this.maxSize = maxSize;
            this.map = new LinkedHashMap<Class<?>, T>(
                Math.min(maxSize, 16), 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Class<?>, T> eldest) {
                    return size() > maxSize;
                }
            };
        }

        T get(Class<?> key) {
            long stamp = lock.tryOptimisticRead();
            T value = map.get(key);
            if (lock.validate(stamp)) {
                return value;
            }
            stamp = lock.readLock();
            try {
                return map.get(key);
            } finally {
                lock.unlockRead(stamp);
            }
        }

        void put(Class<?> key, T value) {
            long stamp = lock.writeLock();
            try {
                map.put(key, value);
            } finally {
                lock.unlockWrite(stamp);
            }
        }

        boolean containsKey(Class<?> key) {
            long stamp = lock.tryOptimisticRead();
            boolean result = map.containsKey(key);
            if (lock.validate(stamp)) {
                return result;
            }
            stamp = lock.readLock();
            try {
                return map.containsKey(key);
            } finally {
                lock.unlockRead(stamp);
            }
        }

        void clear() {
            long stamp = lock.writeLock();
            try {
                map.clear();
            } finally {
                lock.unlockWrite(stamp);
            }
        }

        int size() {
            long stamp = lock.tryOptimisticRead();
            int size = map.size();
            if (lock.validate(stamp)) {
                return size;
            }
            stamp = lock.readLock();
            try {
                return map.size();
            } finally {
                lock.unlockRead(stamp);
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
            return castSerializer(cached);
        }

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
            System.err.println("[AsmCodecCache] generation FAILED for " + beanType.getName() + ": " + e);
            e.printStackTrace(System.err);
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
            return cached;
        }

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
            System.err.println("[AsmCodecCache] generation FAILED for " + beanType.getName() + ": " + e);
            e.printStackTrace(System.err);
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
            return castDeserializer(cached);
        }

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
            return cached;
        }

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
        return true;
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
}
