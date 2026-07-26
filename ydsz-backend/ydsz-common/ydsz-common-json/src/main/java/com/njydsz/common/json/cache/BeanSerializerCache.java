package com.njydsz.common.json.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.njydsz.common.json.writer.BeanSerializer;

/**
 * Bean 序列化器缓存（FastJSON2 架构）
 *
 * <p>为每个 Bean 类缓存预计算的序列化器，避免重复创建。</p>
 *
 * <p><b>缓存策略：</b></p>
 * <ul>
 *   <li>ConcurrentHashMap - 线程安全的并发缓存</li>
 *   <li>初始容量 1024 - 预分配空间减少扩容</li>
 *   <li>类为 Key - 每个类只缓存一次</li>
 *   <li>BeanSerializer 为 Value - 预计算的序列化器</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class BeanSerializerCache {
    
    /** Bean 序列化器缓存 */
    private static final ConcurrentMap<Class<?>, BeanSerializer> CACHE = new ConcurrentHashMap<>(1024);
    
    private BeanSerializerCache() {
        throw new UnsupportedOperationException();
    }
    
    /**
     * 获取或创建 Bean 序列化器
     *
     * @param clazz Bean 类
     * @param fieldMetas 字段元数据数组
     * @return Bean 序列化器实例
     */
    public static BeanSerializer getOrCreate(Class<?> clazz, FieldMeta[] fieldMetas) {
        BeanSerializer serializer = CACHE.get(clazz);
        if (serializer == null) {
            serializer = new BeanSerializer(clazz, fieldMetas);
            CACHE.put(clazz, serializer);
        }
        return serializer;
    }
    
    /**
     * 清理所有缓存
     */
    public static void clear() {
        CACHE.clear();
    }
    
    /**
     * 获取缓存大小
     *
     * @return 缓存的序列化器数量
     */
    public static int size() {
        return CACHE.size();
    }
}
