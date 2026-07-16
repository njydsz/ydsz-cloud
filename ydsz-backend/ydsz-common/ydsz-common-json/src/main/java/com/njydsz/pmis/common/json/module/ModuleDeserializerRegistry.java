package com.njydsz.common.json.module;

import java.util.*;

import com.njydsz.common.json.deserializer.JsonDeserializer;

/**
 * 模块化反序列化器注册表
 *
 * <p>用于在模块中注册自定义反序列化器，提供类型安全的注册接口。</p>
 *
 * @since 1.0.0
 */
public final class ModuleDeserializerRegistry {

    private final Map<Class<?>, JsonDeserializer<?>> deserializers = new LinkedHashMap<>();

    private final List<JsonModule> orderedModules = new ArrayList<>();

    ModuleDeserializerRegistry() {
    }

    /**
     * 注册自定义反序列化器
     *
     * @param type 目标类型
     * @param deserializer 反序列化器
     * @param <T> 类型参数
     */
    public <T> void register(Class<T> type, JsonDeserializer<T> deserializer) {
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null");
        }
        if (deserializer == null) {
            throw new IllegalArgumentException("Deserializer cannot be null");
        }
        deserializers.put(type, deserializer);
    }

    /**
     * 注册自定义反序列化器（带模块标记，用于优先级排序）
     *
     * @param type 目标类型
     * @param deserializer 反序列化器
     * @param module 来源模块
     * @param <T> 类型参数
     */
    <T> void register(Class<T> type, JsonDeserializer<T> deserializer, JsonModule module) {
        register(type, deserializer);
        if (!orderedModules.contains(module)) {
            orderedModules.add(module);
        }
    }

    /**
     * 获取已注册的反序列化器
     *
     * @return 只读映射
     */
    Map<Class<?>, JsonDeserializer<?>> getDeserializers() {
        return Collections.unmodifiableMap(deserializers);
    }

    /**
     * 获取已排序的模块列表（按优先级降序）
     *
     * @return 只读列表
     */
    List<JsonModule> getOrderedModules() {
        return Collections.unmodifiableList(orderedModules);
    }

    /**
     * 清空注册表
     */
    void clear() {
        deserializers.clear();
        orderedModules.clear();
    }
}
