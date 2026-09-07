package com.njydsz.common.excel.converter.impl;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.njydsz.common.excel.converter.CellValueConverter;
import com.njydsz.common.excel.converter.ConvertContext;

/**
 * Enum类型转换器
 *
 * <p>处理目标类型为Enum的转换。支持从String等原始值转换。 支持通过{@link #registerMapping}注册自定义枚举映射。
 *
 * @author ydsz-team

 * @version 26.09.01
 * @since 26.09.01
 */
public class EnumConverter implements CellValueConverter {

  private static final Map<Class<?>, Map<String, Enum<?>>> CUSTOM_MAPPINGS =
      new ConcurrentHashMap<>();

  @Override
  public boolean supports(Class<?> targetType) {
    return targetType != null && targetType.isEnum();
  }

  @Override
  public Object convert(Object rawValue, Class<?> targetType, ConvertContext context) {
    if (rawValue == null) {
      return null;
    }

    String strValue;
    if (rawValue instanceof String s) {
      strValue = s;
    } else {
      strValue = rawValue.toString();
    }

    if (strValue.isEmpty()) {
      return null;
    }

    Map<String, Enum<?>> map = CUSTOM_MAPPINGS.get(targetType);
    if (map != null && map.containsKey(strValue)) {
      return map.get(strValue);
    }

    try {
      // 反射调用枚举的 valueOf(String) 静态方法，避免 asSubclass(Enum.class) 返回
      // Class<? extends Enum> 与 Enum.valueOf 要求的 Class<T extends Enum<T>> 不匹配产生的 unchecked 转换
      Method valueOfMethod = targetType.getMethod("valueOf", String.class);
      return valueOfMethod.invoke(null, strValue);
    } catch (NoSuchMethodException | IllegalAccessException e) {
      // targetType 已由 supports() 确保是枚举类型，理论上不会到达此处
      return null;
    } catch (InvocationTargetException e) {
      // valueOf 找不到匹配名时抛 IllegalArgumentException，包装在 InvocationTargetException 内
      return null;
    }
  }

  @Override
  public int priority() {
    return 110;
  }

  /**
   * 注册自定义枚举映射
   *
   * @param enumClass 枚举类型
   * @param stringValue 字符串值
   * @param enumValue 枚举值
   */
  public static void registerMapping(Class<?> enumClass, String stringValue, Enum<?> enumValue) {
    CUSTOM_MAPPINGS
        .computeIfAbsent(enumClass, k -> new ConcurrentHashMap<>())
        .put(stringValue, enumValue);
  }
}
