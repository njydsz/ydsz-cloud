package com.njydsz.common.excel.converter.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.njydsz.common.excel.converter.CellValueConverter;
import com.njydsz.common.excel.converter.ConvertContext;

/**
 * Enum类型转换器
 *
 * <p>处理目标类型为Enum的转换。支持从String等原始值转换。
 * 支持通过{@link #registerMapping}注册自定义枚举映射。
 *
 * <p><b>P2-1 优化</b>：使用 {@code ConcurrentHashMap} 缓存每个枚举类型的名称→常量映射，
 * 首次访问时通过 {@link Enum#getEnumConstants()} 一次性读取全部枚举常量构建索引，
 * 后续转换直接 O(1) 查表，避免逐次反射调用 {@code Enum.valueOf}。
 * 在大量枚举转换场景（如枚举列批量导入）下性能提升显著。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class EnumConverter implements CellValueConverter {

  /**
   * 自定义枚举映射：优先级高于标准名称索引。
   *
   * <p>用于注册字符串值到枚举常量的非标准映射关系（如"启用" → {@code StatusEnum.ACTIVE}）。
   */
  private static final Map<Class<?>, Map<String, Enum<?>>> CUSTOM_MAPPINGS =
      new ConcurrentHashMap<>();

  /**
   * 标准名称→枚举常量的缓存索引。
   *
   * <p>键为枚举类型，值为该类型所有常量名（{@link Enum#name()}）到常量本身的映射。
   * 首次访问某枚举类型时惰性填充，使用 {@link Class#isEnum()} 判定后一次性读取。
   */
  private static final Map<Class<? extends Enum<?>>, Map<String, Enum<?>>> NAME_INDEX_CACHE =
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

    // 优先查找自定义映射
    Map<String, Enum<?>> customMap = CUSTOM_MAPPINGS.get(targetType);
    if (customMap != null) {
      Enum<?> customMatch = customMap.get(strValue);
      if (customMatch != null) {
        return customMatch;
      }
    }

    // 通过缓存索引查找（O(1)），惰性构建索引
    return nameIndex(targetType).get(strValue);
  }

  @Override
  public int priority() {
    return 110;
  }

  /**
   * 获取（或惰性构建）指定枚举类型的名称→常量索引。
   *
   * <p>首次访问时通过 {@code getEnumConstants()} 读取全部常量并构建不可变映射，
   * 后续转换直接查表。线程安全由 {@link ConcurrentHashMap#computeIfAbsent} 保证。
   *
   * @param targetType 枚举类型（已由 {@link #supports} 确认为枚举）
   * @return 该类型所有常量的名称映射，永不为 {@code null}
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Enum<?>> nameIndex(Class<?> targetType) {
    return NAME_INDEX_CACHE.computeIfAbsent(
        (Class<? extends Enum<?>>) targetType,
        clazz -> {
          Enum<?>[] constants = (Enum<?>[]) clazz.getEnumConstants();
          Map<String, Enum<?>> index = new ConcurrentHashMap<>(constants.length * 2);
          for (Enum<?> constant : constants) {
            index.put(constant.name(), constant);
          }
          return index;
        });
  }

  /**
   * 注册自定义枚举映射
   *
   * @param enumClass 枚举类型
   * @param stringValue 字符串值
   * @param enumValue 枚举值
   */
  @SuppressWarnings("unchecked")
  public static void registerMapping(Class<?> enumClass, String stringValue, Enum<?> enumValue) {
    CUSTOM_MAPPINGS
        .computeIfAbsent(enumClass, k -> new ConcurrentHashMap<>())
        .put(stringValue, enumValue);
  }
}
