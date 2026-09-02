package com.njydsz.common.redis.serializer;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.type.JsonType;

/**
 * YdszJson 版本的 Redis 序列化工具类
 *
 * <p>提供基于 {@link YdszJson} 的高性能序列化实现，用于 Redis 值的序列化/反序列化。 统一使用 ydsz-common-json 中的 YdszJson
 * 引擎，确保全项目 JSON 处理的一致性。
 *
 * <p><b>主要功能：</b>
 *
 * <ul>
 *   <li>对象序列化为 JSON 字节数组（通过 YdszJson.toJsonBytes）
 *   <li>JSON 字节数组反序列化为对象（通过 YdszJson.fromJsonBytes）
 *   <li>支持 Java 8 时间类型（由 YdszJson 内部处理）
 *   <li>支持复杂对象嵌套
 *   <li>支持泛型类型（如 {@code List<User>}、{@code Map<String, Object>}）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class YdszJsonRedisSerializer implements RedisSerializer<Object> {

  /** 默认字符集 */
  public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

  /** 要序列化的对象类型（用于简单类型反序列化） */
  private final Class<?> clazz;

  /** 泛型类型引用（用于泛型类型反序列化，优先于 clazz 使用） */
  private final JsonType<?> typeRef;

  /**
   * 无参构造器（兼容 Spring 反射创建）
   *
   * <p>警告：使用此构造器创建的序列化器在反序列化时无法确定具体类型， 将反序列化为 Object 类型。
   */
  public YdszJsonRedisSerializer() {
    this.clazz = Object.class;
    this.typeRef = null;
  }

  /**
   * 构造器
   *
   * @param clazz 要序列化的对象类型
   */
  public YdszJsonRedisSerializer(Class<?> clazz) {
    this.clazz = clazz != null ? clazz : Object.class;
    this.typeRef = null;
  }

  /**
   * 泛型类型构造器
   *
   * <p>支持 {@code List<User>}、{@code Map<String, Object>} 等泛型类型的反序列化， 解决简单 Class 类型无法表达泛型参数的问题。
   *
   * @param typeRef 泛型类型引用
   * @since 26.09.01
   */
  public YdszJsonRedisSerializer(JsonType<?> typeRef) {
    this.clazz = Object.class;
    this.typeRef = typeRef;
  }

  /**
   * 序列化对象
   *
   * <p>使用 {@link YdszJson#toJsonBytes(Object)} 将对象转换为 JSON 字节数组。
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
      throw new SerializationException("Redis对象序列化失败", e);
    }
  }

  /**
   * 反序列化字节数组
   *
   * <p>使用 {@link YdszJson#fromJsonBytes(byte[], Class)} 将字节数组反序列化为对象。
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
      if (typeRef != null) {
        return YdszJson.fromJsonBytes(bytes, typeRef);
      }
      return YdszJson.fromJsonBytes(bytes, clazz);
    } catch (Exception e) {
      throw new SerializationException("Redis对象反序列化失败", e);
    }
  }

  /**
   * 创建指定类型的序列化器
   *
   * @param type 目标类型
   * @return 序列化器实例
   */
  public static YdszJsonRedisSerializer of(Class<?> type) {
    return new YdszJsonRedisSerializer(type);
  }

  /**
   * 创建支持泛型类型的序列化器
   *
   * @param typeRef 泛型类型引用
   * @return 序列化器实例
   * @since 26.09.01
   */
  public static YdszJsonRedisSerializer of(JsonType<?> typeRef) {
    return new YdszJsonRedisSerializer(typeRef);
  }
}
