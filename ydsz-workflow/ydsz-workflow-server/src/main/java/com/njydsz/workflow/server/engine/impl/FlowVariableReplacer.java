package com.njydsz.workflow.server.engine.impl;

import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 流程变量替换器
 *
 * <p>负责 ${} 占位符替换、支持点路径（如 {@code user.deptId}）的变量查找，以及通过 getter / 反射解析对象字段值。
 *
 * <p>本组件被 {@link FlowExpressionEvaluator}（条件求值器）和 {@link FlowAssigneeExpressionResolver}（办理人解析器）共同依赖。
 *
 * <p><b>性能优化：</b>使用 {@code ConcurrentHashMap} 缓存反射 Method/Field 对象，避免重复的
 * {@code getMethod()} / {@code getDeclaredField()} 调用（高频调用路径上反射开销显著）。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Slf4j
@Component
public class FlowVariableReplacer {

  /**
   * 反射 Method/Field 缓存（P1-8 性能优化）。
   *
   * <p>Key 格式：{@code Class全名 + "#" + 字段名}，Value：缓存的 Method 或 Field 对象。
   * Method 优先于 Field（getter 方法性能优于字段反射）。使用 {@code ConcurrentHashMap} 保证线程安全。
   */
  private static final ConcurrentHashMap<String, Object> REFLECTION_CACHE = new ConcurrentHashMap<>();

  /** 缓存最大条目数（防止内存无限增长） */
  private static final int MAX_CACHE_SIZE = 512;

  /**
   * 替换表达式中所有 ${var} 占位符为变量实际值
   *
   * @param input 含占位符的原始字符串
   * @param variables 流程变量上下文
   * @return 替换后的字符串（无变量时原样返回）
   */
  public String replacePlaceholders(String input, Map<String, Object> variables) {
    if (variables == null || variables.isEmpty()) {
      return input;
    }
    Matcher m = FlowExpressionUtils.PLACEHOLDER.matcher(input);
    StringBuffer sb = new StringBuffer();
    while (m.find()) {
      String key = m.group(1).trim();
      Object value = lookupValue(key, variables);
      m.appendReplacement(sb, Matcher.quoteReplacement(value == null ? "" : value.toString()));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  /**
   * 根据变量名查找值，支持点路径（如 user.deptId）
   *
   * @param key 变量名或点路径表达式
   * @param variables 流程变量上下文
   * @return 变量值（不存在时返回 null）
   */
  public Object lookupValue(String key, Map<String, Object> variables) {
    if (variables == null) {
      return null;
    }
    if (key.contains(".")) {
      String[] parts = key.split("\\.");
      Object cursor = variables.get(parts[0]);
      for (int i = 1; i < parts.length && cursor != null; i++) {
        if (cursor instanceof Map<?, ?> map) {
          cursor = map.get(parts[i]);
        } else {
          cursor = resolveFieldValue(cursor, parts[i]);
          if (cursor == null) {
            return null;
          }
        }
      }
      return cursor;
    }
    return variables.get(key);
  }

  /**
   * 解析对象字段值（兼容 Java 17+ JPMS 模块封装，带反射缓存优化）。
   *
   * <p>优先尝试 getter 方法（getXxx / isXxx），避免反射访问私有字段在 Java 17+ 下抛出 {@code
   * InaccessibleObjectException}。getter 不可用时降级为字段反射， 并捕获 {@code
   * InaccessibleObjectException} 给出明确错误提示。
   *
   * <p><b>性能优化：</b>通过 {@link #REFLECTION_CACHE} 缓存已解析的 Method/Field 对象，
   * 避免重复调用 {@code getMethod()} / {@code getDeclaredField()}（高频路径上反射开销显著）。
   *
   * @param target 目标对象
   * @param fieldName 字段名
   * @return 字段值；解析失败时返回 null
   */
  public Object resolveFieldValue(Object target, String fieldName) {
    Class<?> clazz = target.getClass();
    String cacheKey = clazz.getName() + "#" + fieldName;

    // 优先从缓存获取已解析的 Method/Field
    Object cached = REFLECTION_CACHE.get(cacheKey);
    if (cached instanceof Method method) {
      return invokeMethodSafely(method, target, fieldName, clazz);
    }
    if (cached instanceof Field field) {
      return invokeFieldSafely(field, target, fieldName, clazz);
    }

    // 缓存未命中：尝试 getter 方法
    String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    String isName = "is" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    for (String methodName : new String[] {getterName, isName}) {
      try {
        Method method = clazz.getMethod(methodName);
        putCache(cacheKey, method);
        return method.invoke(target);
      } catch (NoSuchMethodException e) {
        // getter 不存在 → 继续尝试下一个或降级到字段反射
      } catch (Exception e) {
        log.warn(
            "[FlowVariableReplacer] getter 调用失败 {}.{}(): {}",
            clazz.getSimpleName(),
            methodName,
            e.getMessage());
        return null;
      }
    }

    // 降级：字段反射（兼容 Java 17+ JPMS 模块封装）
    try {
      Field field = clazz.getDeclaredField(fieldName);
      field.setAccessible(true);
      putCache(cacheKey, field);
      return field.get(target);
    } catch (NoSuchFieldException e) {
      log.warn("[FlowVariableReplacer] 字段不存在 {}.{}", clazz.getSimpleName(), fieldName);
      return null;
    } catch (InaccessibleObjectException e) {
      // Java 17+ JPMS 模块封装导致 setAccessible 失败
      log.warn(
          "[FlowVariableReplacer] JPMS 模块封装阻止字段访问 {}.{}，建议添加 getter 方法: {}",
          clazz.getSimpleName(),
          fieldName,
          e.getMessage());
      return null;
    } catch (Exception e) {
      log.warn(
          "[FlowVariableReplacer] 反射读取字段失败 {}.{}: {}",
          clazz.getSimpleName(),
          fieldName,
          e.getMessage());
      return null;
    }
  }

  /**
   * 安全调用缓存的 Method（统一异常处理）。
   *
   * @param method 缓存的反射 Method
   * @param target 目标对象
   * @param fieldName 字段名（用于日志）
   * @param clazz 目标类（用于日志）
   * @return 方法返回值；失败时返回 null
   */
  private Object invokeMethodSafely(Method method, Object target, String fieldName, Class<?> clazz) {
    try {
      return method.invoke(target);
    } catch (Exception e) {
      log.warn(
          "[FlowVariableReplacer] 缓存 getter 调用失败 {}.{}(): {}",
          clazz.getSimpleName(),
          method.getName(),
          e.getMessage());
      return null;
    }
  }

  /**
   * 安全读取缓存的 Field（统一异常处理）。
   *
   * @param field 缓存的反射 Field
   * @param target 目标对象
   * @param fieldName 字段名（用于日志）
   * @param clazz 目标类（用于日志）
   * @return 字段值；失败时返回 null
   */
  private Object invokeFieldSafely(Field field, Object target, String fieldName, Class<?> clazz) {
    try {
      return field.get(target);
    } catch (Exception e) {
      log.warn(
          "[FlowVariableReplacer] 缓存字段读取失败 {}.{}: {}",
          clazz.getSimpleName(),
          fieldName,
          e.getMessage());
      return null;
    }
  }

  /**
   * 放入缓存（带容量上限保护）。
   *
   * <p>当缓存条目数超过 {@link #MAX_CACHE_SIZE} 时跳过放入，防止内存无限增长。
   * 工作流系统的变量类型有限（主要是 DTO/VO），512 条缓存足够覆盖。
   *
   * @param key 缓存键
   * @param value 缓存值（Method 或 Field）
   */
  private void putCache(String key, Object value) {
    if (REFLECTION_CACHE.size() < MAX_CACHE_SIZE) {
      REFLECTION_CACHE.put(key, value);
    }
  }
}
