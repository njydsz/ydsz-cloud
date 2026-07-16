package com.njydsz.pmis.common.json.serializer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.njydsz.pmis.common.json.deserializer.JsonDeserializer;

/**
 * 自定义序列化器注册中。
 *
 * <p>支持用户注册和管理自定义序列化器，实现类型Jackson Module 的扩展机制。/p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * // 注册自定义序列化。
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
     * 注册自定义序列化。
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
     * 注册自定义反序列化器
     *
     * @param type 目标类型
     * @param deserializer 反序列化。
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
     * 获取序列化器
     *
     * @param type 目标类型
     * @return 序列化器，如果未注册返回 null
     */
    
    public <T> JsonSerializer<T> get(Class<T> type) {
        return castSerializer(serializers.get(type));
    }

    /**
     * 获取反序列化。
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
     * 是否已注册序列化。
     *
     * @param type 目标类型
     * @return 如果已注册返。true
     */
    public boolean hasSerializer(Class<?> type) {
        return serializers.containsKey(type);
    }

    /**
     * 是否已注册反序列化器
     *
     * @param type 目标类型
     * @return 如果已注册返。true
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
     * 移除反序列化。
     *
     * @param type 目标类型
     * @return 被移除的反序列化器，如果未注册返回null
     */
    public JsonDeserializer<?> unregisterDeserializer(Class<?> type) {
        return deserializers.remove(type);
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
     * 获取已注册的反序列化器数。
     *
     * @return 数量
     */
    public int getDeserializerCount() {
        return deserializers.size();
    }
}
