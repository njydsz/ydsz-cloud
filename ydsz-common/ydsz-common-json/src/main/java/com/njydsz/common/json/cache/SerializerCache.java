package com.njydsz.common.json.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import com.njydsz.common.json.internal.JsonConfig;
import com.njydsz.common.json.naming.PropertyNamingStrategy;
import com.njydsz.common.json.util.BoundedLruCache;

/**
 * 序列化器缓存（按类 + 命名策略双层隔离 + 版本感知自动失效）
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
 * <p><b>版本感知自动失效（P0-2，2026-08-05）：</b></p>
 * <p>注册为 {@link JsonConfig.ConfigChangeListener}，当全局配置版本变更时（如命名策略切换），
 * 自动清理全部缓存条目，消除"命名策略对已缓存类无效"的隐患。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class SerializerCache {

    /** 字段元数据缓存（双层：外层 Class -> 内层 命名策略 -> FieldMeta[]），
     *  外层有界 LRU（容量 1024），防止动态类加载场景下无界增长。 */
    private static final BoundedLruCache<Class<?>, ConcurrentMap<PropertyNamingStrategy, FieldMeta[]>> FIELD_META_CACHE =
        new BoundedLruCache<>(1024);

    /** 上次清理时的配置版本号（用于检测配置变更） */
    private static final AtomicLong LAST_CONFIG_VERSION = new AtomicLong(0);

    /** 因配置变更自动清理的次数（监控指标） */
    private static final AtomicLong AUTO_INVALIDATE_COUNT = new AtomicLong(0);

    static {
        // 注册为配置变更监听器，自动清理缓存
        JsonConfig.addChangeListener(SerializerCache::onConfigChanged);
    }

    private SerializerCache() {
        throw new UnsupportedOperationException();
    }

    /**
     * 配置变更回调：检测命名策略或日期格式等影响字段元数据的配置变更，自动清理缓存。
     */
    private static void onConfigChanged(JsonConfig oldConfig, JsonConfig newConfig, long newVersion) {
        // 仅当影响字段元数据的配置发生变更时才清理
        if (oldConfig != null
                && oldConfig.getNamingStrategy() == newConfig.getNamingStrategy()
                && java.util.Objects.equals(oldConfig.getDateFormat(), newConfig.getDateFormat())) {
            // 配置未变更关键字段，跳过清理
            LAST_CONFIG_VERSION.set(newVersion);
            return;
        }
        // 清理缓存并更新版本号
        FIELD_META_CACHE.clear();
        AUTO_INVALIDATE_COUNT.incrementAndGet();
        LAST_CONFIG_VERSION.set(newVersion);
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
     * 清理所有缓存
     */
    public static void clear() {
        FIELD_META_CACHE.clear();
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

    /**
     * 获取因配置变更自动清理的次数。
     *
     * @return 自动清理次数（监控指标）
     * @since 1.1.0
     */
    public static long getAutoInvalidateCount() {
        return AUTO_INVALIDATE_COUNT.get();
    }

    /**
     * 获取上次清理时的配置版本号。
     *
     * @return 版本号
     * @since 1.1.0
     */
    public static long getLastConfigVersion() {
        return LAST_CONFIG_VERSION.get();
    }
}
