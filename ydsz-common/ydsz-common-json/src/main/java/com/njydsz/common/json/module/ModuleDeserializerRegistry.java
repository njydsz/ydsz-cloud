package com.njydsz.common.json.module;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.njydsz.common.json.deserializer.JsonDeserializer;

/**
 * 模块化反序列化器注册表
 *
 * <p>用于在模块中注册自定义反序列化器，提供类型安全的注册接口。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class ModuleDeserializerRegistry {

  private final Map<Class<?>, JsonDeserializer<?>> deserializers = new LinkedHashMap<>(16);

  ModuleDeserializerRegistry() {}

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
   * 获取已注册的反序列化器
   *
   * @return 只读映射
   */
  Map<Class<?>, JsonDeserializer<?>> getDeserializers() {
    return Collections.unmodifiableMap(deserializers);
  }

  /** 清空注册表 */
  void clear() {
    deserializers.clear();
  }
}
