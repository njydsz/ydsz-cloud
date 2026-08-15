package com.njydsz.common.json.serializer;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.njydsz.common.json.deserializer.JsonDeserializer;

/**
 * 自定义序列化器注册中心。
 *
 * <p>支持用户注册和管理自定义序列化器，实现类似 Jackson Module 的扩展机制。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * // 注册自定义序列化器
 * SerializerRegistry.getInstance().register(User.class, new CustomUserSerializer());
 *
 * // 获取序列化器
 * JsonSerializer serializer = SerializerRegistry.getInstance().get(User.class);
 * </pre>
 *
 * <p><b>线程安全：</b></p>
 * <ul>
 *   <li>使用 ConcurrentHashMap 保证并发安全</li>
 *   <li>注册操作原子执行</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class SerializerRegistry {

    private static volatile SerializerRegistry instance;

    private final Map<Class<?>, JsonSerializer<?>> serializers = new ConcurrentHashMap<>();

    private final Map<Class<?>, JsonDeserializer<?>> deserializers = new ConcurrentHashMap<>();

    private SerializerRegistry() {
    }

    /**
     * 获取注册中心实例（单例）
     *
     * @return 注册中心实例
     */
    public static SerializerRegistry getInstance() {
        if (instance == null) {
            synchronized (SerializerRegistry.class) {
                if (instance == null) {
                    instance = new SerializerRegistry();
                }
            }
        }
        return instance;
    }

    /**
     * 注册自定义序列化器。
     *
     * @param type 目标类型
     * @param serializer 序列化器
     * @param <T> 类型参数
     */
    public <T> void register(Class<T> type, JsonSerializer<T> serializer) {
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null");
        }
        if (serializer == null) {
            throw new IllegalArgumentException("Serializer cannot be null");
        }
        serializers.put(type, serializer);
    }

    /**
     * 仅当类型尚未注册时注册序列化器（等价于 {@code Map.putIfAbsent}）。
     *
     * <p>供模块系统（{@code JsonModuleRegistry}）在 {@code initialize()} 阶段回填
     * 模块序列化器时使用，保证"先注册优先"语义：用户直接注册（{@link #register}）
     * 的序列化器优先于模块注册，反之亦然，按注册时序决定。</p>
     *
     * @param type 目标类型
     * @param serializer 序列化器
     * @return 已存在（未被覆盖）的旧序列化器，若此前未注册返回 null
     * @since 1.2.3
     */
    public JsonSerializer<?> registerIfAbsent(Class<?> type, JsonSerializer<?> serializer) {
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null");
        }
        if (serializer == null) {
            throw new IllegalArgumentException("Serializer cannot be null");
        }
        return serializers.putIfAbsent(type, serializer);
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
     * 仅当类型尚未注册时注册反序列化器（等价于 {@code Map.putIfAbsent}）。
     *
     * <p>语义与 {@link #registerIfAbsent(Class, JsonSerializer)} 一致，用于模块系统
     * 回填模块反序列化器。</p>
     *
     * @param type 目标类型
     * @param deserializer 反序列化器
     * @return 已存在（未被覆盖）的旧反序列化器，若此前未注册返回 null
     * @since 1.2.3
     */
    public JsonDeserializer<?> registerIfAbsent(Class<?> type, JsonDeserializer<?> deserializer) {
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null");
        }
        if (deserializer == null) {
            throw new IllegalArgumentException("Deserializer cannot be null");
        }
        return deserializers.putIfAbsent(type, deserializer);
    }

    /**
     * 获取序列化器
     *
     * @param type 目标类型
     * @return 序列化器，如果未注册返回 null
     */

    public <T> JsonSerializer<T> get(Class<T> type) {
        return castSerializer(serializers.get(type));
    }

    /**
     * 获取反序列化器。
     *
     * @param type 目标类型
     * @return 反序列化器，如果未注册返回null
     */
    public <T> JsonDeserializer<T> getDeserializer(Class<T> type) {
        return castDeserializer(deserializers.get(type));
    }

    private static <T> JsonSerializer<T> castSerializer(JsonSerializer<?> serializer) {
        return (JsonSerializer<T>) serializer;
    }

    private static <T> JsonDeserializer<T> castDeserializer(
            JsonDeserializer<?> deserializer) {
        return (JsonDeserializer<T>) deserializer;
    }

    /**
     * 是否已注册序列化器。
     *
     * @param type 目标类型
     * @return 如果已注册返回 true
     */
    public boolean hasSerializer(Class<?> type) {
        return serializers.containsKey(type);
    }

    /**
     * 是否已注册反序列化器
     *
     * @param type 目标类型
     * @return 如果已注册返回 true
     */
    public boolean hasDeserializer(Class<?> type) {
        return deserializers.containsKey(type);
    }

    /**
     * 移除序列化器
     *
     * @param type 目标类型
     * @return 被移除的序列化器，如果未注册返回 null
     */
    public JsonSerializer<?> unregister(Class<?> type) {
        return serializers.remove(type);
    }

    /**
     * 移除反序列化器。
     *
     * @param type 目标类型
     * @return 被移除的反序列化器，如果未注册返回null
     */
    public JsonDeserializer<?> unregisterDeserializer(Class<?> type) {
        return deserializers.remove(type);
    }

    /**
     * 批量移除指定类型的序列化器（用于模块卸载时清理模块来源的注册）。
     *
     * <p>仅移除 {@code types} 中列出的类型，不影响其他类型与用户直接注册的序列化器。
     * 空集合或 null 为安全空操作。</p>
     *
     * @param types 要移除的类型集合
     * @since 1.2.3
     */
    public void unregisterAll(Set<Class<?>> types) {
        if (types == null || types.isEmpty()) {
            return;
        }
        for (Class<?> type : types) {
            serializers.remove(type);
        }
    }

    /**
     * 批量移除指定类型的反序列化器（用于模块卸载时清理模块来源的注册）。
     *
     * <p>仅移除 {@code types} 中列出的类型，不影响其他类型与用户直接注册的反序列化器。
     * 空集合或 null 为安全空操作。</p>
     *
     * @param types 要移除的类型集合
     * @since 1.2.3
     */
    public void unregisterAllDeserializers(Set<Class<?>> types) {
        if (types == null || types.isEmpty()) {
            return;
        }
        for (Class<?> type : types) {
            deserializers.remove(type);
        }
    }

    /**
     * 清空所有注入
     */
    public void clear() {
        serializers.clear();
        deserializers.clear();
    }

    /**
     * 获取已注册的序列化器数量
     *
     * @return 数量
     */
    public int getSerializerCount() {
        return serializers.size();
    }

    /**
     * 获取已注册的反序列化器数量。
     *
     * @return 数量
     */
    public int getDeserializerCount() {
        return deserializers.size();
    }
}
