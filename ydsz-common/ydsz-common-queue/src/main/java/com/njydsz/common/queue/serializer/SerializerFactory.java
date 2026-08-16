package com.njydsz.common.queue.serializer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 序列化器工厂
 *
 * <p>管理和创建 {@link MessageSerializer} 实例，支持按格式名称获取对应序列化器。
 *
 * <p>内置序列化器：
 *
 * <ul>
 *   <li>{@code json} - JsonMessageSerializer（默认）
 *   <li>{@code protobuf} - ProtobufMessageSerializer（可选，需 protobuf-java）
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 获取默认 JSON 序列化器
 * MessageSerializer serializer = SerializerFactory.getSerializer("json");
 *
 * // 注册自定义序列化器
 * SerializerFactory.register("custom", new CustomSerializer());
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class SerializerFactory {

  /** 序列化器缓存 */
  private static final Map<String, MessageSerializer> REGISTRY = new ConcurrentHashMap<>();

  private SerializerFactory() {
    throw new UnsupportedOperationException("Utility class should not be instantiated");
  }

  // 静态注册内置序列化器
  static {
    register(new JsonMessageSerializer());
    register(new ProtobufMessageSerializer());
  }

  /**
   * 注册序列化器
   *
   * @param serializer 序列化器实例
   */
  public static void register(MessageSerializer serializer) {
    if (serializer != null && serializer.getFormatName() != null) {
      REGISTRY.put(serializer.getFormatName().toLowerCase(), serializer);
    }
  }

  /**
   * 获取指定格式的序列化器
   *
   * @param format 格式名称（如 "json"、"protobuf"）
   * @return 对应序列化器，未找到时返回默认 JSON 序列化器
   */
  public static MessageSerializer getSerializer(String format) {
    if (format == null || format.trim().isEmpty()) {
      return getDefault();
    }
    MessageSerializer serializer = REGISTRY.get(format.toLowerCase());
    if (serializer == null) {
      return getDefault();
    }
    // 检查 Protobuf 是否可用
    if ("protobuf".equalsIgnoreCase(format) && !ProtobufMessageSerializer.isProtobufAvailable()) {
      return getDefault();
    }
    return serializer;
  }

  /**
   * 获取默认序列化器（JSON）
   *
   * @return JSON 序列化器
   */
  public static MessageSerializer getDefault() {
    return REGISTRY.get("json");
  }

  /**
   * 移除已注册的序列化器
   *
   * @param format 格式名称
   */
  public static void unregister(String format) {
    if (format != null && !"json".equalsIgnoreCase(format)) {
      REGISTRY.remove(format.toLowerCase());
    }
  }
}
