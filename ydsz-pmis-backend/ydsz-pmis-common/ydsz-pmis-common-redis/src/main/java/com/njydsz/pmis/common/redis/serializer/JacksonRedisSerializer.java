package com.njydsz.pmis.common.redis.serializer;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import com.njydsz.pmis.common.json.YdszJson;

/**
 * Jackson 版本的 Redis 序列化工具类
 *
 * <p>提供基于 {@link JsonUtils} 的高性能序列化实现，用于 Redis 值的序列化/反序列化。
 * 统一使用 ydsz-pmis-common-util 中的 JsonUtils 工具类，确保全项目 JSON 处理的一致性。
 *
 * <p><b>主要功能：</b>
 * <ul>
 *   <li>对象序列化为 JSON 字节数组（通过 YdszJson.toJsonBytes）</li>
 *   <li>JSON 字节数组反序列化为对象（通过 YdszJson.fromJsonBytes）</li>
 *   <li>支持 Java 8 时间类型（由 JsonUtils 内部 JavaTimeModule 处理）</li>
 *   <li>支持复杂对象嵌套</li>
 * </ul>
 *
 * <p><b>依赖说明：</b>
 * <ul>
 *   <li>Jackson 由 ydsz-pmis-common-util 传递依赖引入，无需显式声明</li>
 *   <li>序列化/反序列化逻辑统一委托给 JsonUtils，保持全项目一致</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 4.0.0
 * @since 1.0.0
 */
public class JacksonRedisSerializer implements RedisSerializer<Object> {

    /**
     * 默认字符集（保留用于兼容性）
     */
    public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    /**
     * 要序列化的对象类型
     */
    private final Class<?> clazz;

    /**
     * 无参构造器（兼容 Spring 反射创建）
     * <p>警告：使用此构造器创建的序列化器在反序列化时无法确定具体类型，
     * 将反序列化为 Object 类型。
     */
    public JacksonRedisSerializer() {
        this.clazz = Object.class;
    }

    /**
     * 构造器
     *
     * @param clazz 要序列化的对象类型
     */
    public JacksonRedisSerializer(Class<?> clazz) {
        this.clazz = clazz != null ? clazz : Object.class;
    }

    /**
     * 序列化对象
     *
     * <p>使用 {@link JsonUtils#toJsonBytes(Object)} 将对象转换为 JSON 字节数组。
     *
     * @param t 要序列化的对象
     * @return 序列化后的字节数组
     * @throws SerializationException 如果序列化失败
     */
    @Override
    public byte[] serialize(@Nullable Object t) throws SerializationException {
        if (t == null) {
            return new byte[0];
        }
        try {
            return YdszJson.toJsonBytes(t);
        } catch (Exception e) {
            throw new SerializationException("Redis对象序列化失败（Jackson）", e);
        }
    }

    /**
     * 反序列化字节数组
     *
     * <p>使用 {@link JsonUtils#fromJsonBytes(byte[], Class)} 将字节数组反序列化为对象。
     *
     * @param bytes 序列化后的字节数组
     * @return 反序列化后的对象
     * @throws SerializationException 如果反序列化失败
     */
    @Override
    @Nullable
    public Object deserialize(@Nullable byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length <= 0) {
            return null;
        }
        try {
            return YdszJson.fromJsonBytes(bytes, clazz);
        } catch (Exception e) {
            throw new SerializationException("Redis对象反序列化失败（Jackson）", e);
        }
    }

    /**
     * 创建指定类型的序列化器
     *
     * @param type 目标类型
     * @return 序列化器实例
     */
    public static JacksonRedisSerializer of(Class<?> type) {
        return new JacksonRedisSerializer(type);
    }
}