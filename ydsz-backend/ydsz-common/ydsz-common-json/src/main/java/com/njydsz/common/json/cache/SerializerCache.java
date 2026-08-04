package com.njydsz.common.json.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.njydsz.common.json.naming.PropertyNamingStrategy;

/**
 * 序列化器缓存（按类 + 命名策略双层隔离）
 *
 * <p>字段元数据缓存：避免重复反射获取字段信息。</p>
 *
 * <p><b>缓存隔离说明（P0-1 并发安全修复，2026-08-04）：</b></p>
 * <p>修复前以外层仅以 Class 为 Key，导致同一类的不同命名策略共享同一个 FieldMeta[]，
 * jsonName 首次加载时被"烘焙固化"，后续不同策略的 Mapper 无法隔离——先加载者决定命名。
 * 修复后内层以 PropertyNamingStrategy 引用为 Key，不同策略独立缓存各自的 FieldMeta[]。
 * PropertyNamingStrategy 使用引用相等（==）语义——内置策略常量为接口静态字段，
 * 同一常量引用天然相等；自定义策略实例各自独立，行为正确。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class SerializerCache {

    /** 字段元数据缓存（双层：外层 Class -> 内层 命名策略 -> FieldMeta[]） */
    private static final ConcurrentMap<Class<?>, ConcurrentMap<PropertyNamingStrategy, FieldMeta[]>> FIELD_META_CACHE =
        new ConcurrentHashMap<>(1024);

    /** Bean 序列化信息缓存（双层：外层 Class -> 内层 命名策略 -> BeanSerializerInfo） */
    private static final ConcurrentMap<Class<?>, ConcurrentMap<PropertyNamingStrategy, BeanSerializerInfo>> BEAN_SERIALIZER_CACHE =
        new ConcurrentHashMap<>(1024);

    private SerializerCache() {
        throw new UnsupportedOperationException();
    }

    /**
     * 获取字段元数据（按当前命名策略隔离）
     *
     * @param clazz    目标类
     * @param strategy 当前线程的命名策略
     * @return 字段元数据数组；未缓存返回 null
     */
    public static FieldMeta[] getFieldMeta(Class<?> clazz, PropertyNamingStrategy strategy) {
        ConcurrentMap<PropertyNamingStrategy, FieldMeta[]> strategyMap = FIELD_META_CACHE.get(clazz);
        if (strategyMap == null) {
            return null;
        }
        return strategyMap.get(strategy);
    }

    /**
     * 缓存字段元数据（按命名策略隔离）
     */
    public static void putFieldMeta(Class<?> clazz, PropertyNamingStrategy strategy, FieldMeta[] metas) {
        FIELD_META_CACHE
            .computeIfAbsent(clazz, k -> new ConcurrentHashMap<>())
            .put(strategy, metas);
    }

    /**
     * 获取 Bean 序列化信息（按命名策略隔离）
     */
    public static BeanSerializerInfo getBeanSerializerInfo(Class<?> clazz, PropertyNamingStrategy strategy) {
        ConcurrentMap<PropertyNamingStrategy, BeanSerializerInfo> strategyMap = BEAN_SERIALIZER_CACHE.get(clazz);
        if (strategyMap == null) {
            return null;
        }
        return strategyMap.get(strategy);
    }

    /**
     * 缓存 Bean 序列化信息（按命名策略隔离）
     */
    public static void putBeanSerializerInfo(Class<?> clazz, PropertyNamingStrategy strategy, BeanSerializerInfo info) {
        BEAN_SERIALIZER_CACHE
            .computeIfAbsent(clazz, k -> new ConcurrentHashMap<>())
            .put(strategy, info);
    }

    /**
     * 清理所有缓存
     */
    public static void clear() {
        FIELD_META_CACHE.clear();
        BEAN_SERIALIZER_CACHE.clear();
    }

    /**
     * 获取外层缓存大小（已加载的 Class 数量）
     */
    public static int size() {
        return FIELD_META_CACHE.size();
    }

    /**
     * 获取指定 Class 的命名策略维度缓存条目数
     */
    public static int strategySize(Class<?> clazz) {
        ConcurrentMap<PropertyNamingStrategy, FieldMeta[]> strategyMap = FIELD_META_CACHE.get(clazz);
        return strategyMap != null ? strategyMap.size() : 0;
    }
}
