package com.njydsz.pmis.common.json.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 序列化器缓存
 *
 * <p>字段元数据缓存：避免重复反射获取字段信息</p>
 *
 * <p><b>缓存策略：</b></p>
 * <ul>
 *   <li>ConcurrentHashMap - 线程安全的并发缓存</li>
 *   <li>初始容量 1024 - 预分配空间减少扩容</li>
 *   <li>类为 Key - 每个类只缓存一次</li>
 *   <li>FieldMeta[] 为 Value - 字段元数据数组</li>
 *   <li>BeanSerializerInfo - 预计算的序列化信息（v4.0.0 新增）</li>
 * </ul>
 *
 * <p><b>性能提升：</b></p>
 * <ul>
 *   <li>反射获取字段：~500ns/次</li>
 *   <li>缓存命中：~5ns/次（提升 100 倍）</li>
 * </ul>
 *
 * <p><b>使用场景：</b></p>
 * <ul>
 *   <li>序列化时缓存字段元数据</li>
 *   <li>反序列化时缓存构造器信息</li>
 *   <li>字段排序和过滤</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @email limw1888@126.com
 * @since 1.3.0
 * @see FieldMeta
 * @see BeanSerializerInfo
 */
public final class SerializerCache {

    /** 字段元数据缓存 */
    private static final ConcurrentMap<Class<?>, FieldMeta[]> FIELD_META_CACHE = new ConcurrentHashMap<>(1024);

    /** Bean 序列化信息缓存（v4.0.0 新增） */
    private static final ConcurrentMap<Class<?>, BeanSerializerInfo> BEAN_SERIALIZER_CACHE = new ConcurrentHashMap<>(1024);

    private SerializerCache() {
        throw new UnsupportedOperationException();
    }

    /**
     * 获取字段元数据
     */
    public static FieldMeta[] getFieldMeta(Class<?> clazz) {
        return FIELD_META_CACHE.get(clazz);
    }

    /**
     * 缓存字段元数据
     */
    public static void putFieldMeta(Class<?> clazz, FieldMeta[] metas) {
        FIELD_META_CACHE.put(clazz, metas);
    }

    /**
     * 获取 Bean 序列化信息（v4.0.0 新增）
     *
     * @param clazz Bean 类
     * @return 序列化信息，如果未缓存返回 null
     */
    public static BeanSerializerInfo getBeanSerializerInfo(Class<?> clazz) {
        return BEAN_SERIALIZER_CACHE.get(clazz);
    }

    /**
     * 缓存 Bean 序列化信息（v4.0.0 新增）
     *
     * @param clazz Bean 类
     * @param info 序列化信息
     */
    public static void putBeanSerializerInfo(Class<?> clazz, BeanSerializerInfo info) {
        BEAN_SERIALIZER_CACHE.put(clazz, info);
    }

    /**
     * 清理所有缓存
     */
    public static void clear() {
        FIELD_META_CACHE.clear();
        BEAN_SERIALIZER_CACHE.clear();
    }

    /**
     * 获取缓存大小
     */
    public static int size() {
        return FIELD_META_CACHE.size();
    }
}
