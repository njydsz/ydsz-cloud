package com.njydsz.common.json.module;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.njydsz.common.json.serializer.JsonSerializer;

/**
 * 模块化序列化器注册表
 *
 * <p>用于在模块中注册自定义序列化器，提供类型安全的注册接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class ModuleSerializerRegistry {

  private final Map<Class<?>, JsonSerializer<?>> serializers = new LinkedHashMap<>();

  ModuleSerializerRegistry() {}

  /**
   * 注册自定义序列化器
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
   * 获取已注册的序列化器
   *
   * @return 只读映射
   */
  Map<Class<?>, JsonSerializer<?>> getSerializers() {
    return Collections.unmodifiableMap(serializers);
  }

  /** 清空注册表 */
  void clear() {
    serializers.clear();
  }
}
