package com.njydsz.common.json.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.njydsz.common.json.writer.BeanSerializer;

/**
 * Bean 序列化器缓存（FastJSON2 架构）
 * 
 * <p>为每个 Bean 类缓存预计算的序列化器，避免重复创建</p>
 * 
 * @author YdszJson Team
 */
public final class BeanSerializerCache {
    
    /** Bean 序列化器缓存 */
    private static final ConcurrentMap<Class<?>, BeanSerializer> CACHE = new ConcurrentHashMap<>(1024);
    
    private BeanSerializerCache() {
        throw new UnsupportedOperationException();
    }
    
    /**
     * 获取或创建 Bean 序列化器
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
     * 清理缓存
     */
    public static void clear() {
        CACHE.clear();
    }
    
    /**
     * 获取缓存大小
     */
    public static int size() {
        return CACHE.size();
    }
}
