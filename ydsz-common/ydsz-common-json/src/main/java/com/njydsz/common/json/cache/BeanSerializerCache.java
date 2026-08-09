package com.njydsz.common.json.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.njydsz.common.json.naming.PropertyNamingStrategy;
import com.njydsz.common.json.util.BoundedLruCache;
import com.njydsz.common.json.writer.BeanSerializer;

/**
 * Bean 序列化器缓存（FastJSON2 架构）
 *
 * <p>为每个 Bean 类 + 命名策略缓存预计算的序列化器，避免重复创建。</p>
 *
 * <p><b>缓存策略（P0-1 并发安全修复，2026-08-05）：</b></p>
 * <p>修复前仅以 Class 为 Key，导致不同命名策略的 BeanSerializer 被覆盖——
 * 先加载的线程的 BeanSerializer（含特定命名字段名）被后加载的线程复用，
 * 导致字段名输出与当前命名策略不一致。修复后内层以 PropertyNamingStrategy 引用为 Key，
 * 不同策略独立缓存各自的 BeanSerializer。</p>
 *
 * <p><b>缓存治理（1.2.1）：</b></p>
 * <p>外层采用 {@link BoundedLruCache}（容量 1024），超限按 LRU 淘汰，
 * 防止热部署/动态类加载场景下缓存无界增长导致 OOM。</p>
 *
 * <ul>
 *   <li>双层 Key：外层 Class -> 内层 命名策略 -> BeanSerializer</li>
 *   <li>外层有界 LRU（1024），内层 ConcurrentHashMap</li>
 *   <li>BeanSerializer 为 Value - 预计算的序列化器</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class BeanSerializerCache {

    /** 外层有界 LRU：Class -> 内层命名策略映射（容量 1024） */
    private static final BoundedLruCache<Class<?>, ConcurrentMap<PropertyNamingStrategy, BeanSerializer>> CACHE =
        new BoundedLruCache<>(1024);

    private BeanSerializerCache() {
        throw new UnsupportedOperationException();
    }

    /**
     * 获取或创建 Bean 序列化器（按当前命名策略隔离缓存）
     *
     * @param clazz Bean 类
     * @param fieldMetas 字段元数据数组（按当前命名策略加载）
     * @param strategy 当前线程的命名策略
     * @return Bean 序列化器实例
     */
    public static BeanSerializer getOrCreate(Class<?> clazz, FieldMeta[] fieldMetas, PropertyNamingStrategy strategy) {
        ConcurrentMap<PropertyNamingStrategy, BeanSerializer> strategyMap =
            CACHE.computeIfAbsent(clazz, k -> new ConcurrentHashMap<>());
        BeanSerializer serializer = strategyMap.get(strategy);
        if (serializer == null) {
            serializer = new BeanSerializer(clazz, fieldMetas);
            strategyMap.put(strategy, serializer);
        }
        return serializer;
    }

    /**
     * 仅查找已缓存的 BeanSerializer（不创建新实例）。
     *
     * <p>用于序列化热路径中快速判断 primitiveOnly 标记，避免不必要的
     * IdentityHashSet 循环引用检测。</p>
     *
     * @param clazz Bean 类
     * @param strategy 命名策略
     * @return 已缓存的 BeanSerializer，或 null 如果尚未缓存
     * @since 1.2.0
     */
    public static BeanSerializer get(Class<?> clazz, PropertyNamingStrategy strategy) {
        ConcurrentMap<PropertyNamingStrategy, BeanSerializer> strategyMap = CACHE.get(clazz);
        if (strategyMap == null) {
            return null;
        }
        return strategyMap.get(strategy);
    }

    /**
     * 清理所有缓存
     */
    public static void clear() {
        CACHE.clear();
    }

    /**
     * 获取缓存大小（已加载的 Class 数量）
     *
     * @return 缓存的序列化器数量
     */
    public static int size() {
        return CACHE.size();
    }

    /**
     * 获取指定 Class 的命名策略维度缓存条目数
     *
     * @param clazz 目标类
     * @return 该类的命名策略缓存条目数
     */
    public static int strategySize(Class<?> clazz) {
        ConcurrentMap<PropertyNamingStrategy, BeanSerializer> strategyMap = CACHE.get(clazz);
        return strategyMap != null ? strategyMap.size() : 0;
    }
}
