package com.njydsz.workflow.server.engine.impl;

import java.lang.reflect.InaccessibleObjectException;
import java.util.Map;
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
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
public class FlowVariableReplacer {

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
   * 解析对象字段值（兼容 Java 17+ JPMS 模块封装）
   *
   * <p>优先尝试 getter 方法（getXxx / isXxx），避免反射访问私有字段在 Java 17+ 下抛出 {@code
   * InaccessibleObjectException}。getter 不可用时降级为字段反射， 并捕获 {@code
   * InaccessibleObjectException} 给出明确错误提示。
   *
   * @param target 目标对象
   * @param fieldName 字段名
   * @return 字段值；解析失败时返回 null
   */
  public Object resolveFieldValue(Object target, String fieldName) {
    Class<?> clazz = target.getClass();
    String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    String isName = "is" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    for (String methodName : new String[] {getterName, isName}) {
      try {
        var method = clazz.getMethod(methodName);
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
      var field = clazz.getDeclaredField(fieldName);
      field.setAccessible(true);
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
}
