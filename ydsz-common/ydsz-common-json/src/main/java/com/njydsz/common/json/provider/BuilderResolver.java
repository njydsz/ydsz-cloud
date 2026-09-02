package com.njydsz.common.json.provider;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.json.parser.JsonParserUtil;

/**
 * Builder 模式反序列化处理器。
 *
 * <p>负责处理 Builder 设计模式的 JSON 反序列化逻辑。当目标类使用 Builder 模式构建时， 本类通过反射定位 Builder 类及其 setter 方法（如 {@code
 * withXxx()} 或 {@code fieldName()}）， 逐字段设置值后调用 {@code build()} 方法生成目标实例。
 *
 * <h3>支持的 Builder 模式</h3>
 *
 * <ul>
 *   <li><b>内部 Builder</b>：Builder 作为目标类的静态内部类，自动发现
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see CreatorResolver
 * @see TypeConverter
 */
@SuppressWarnings("deprecation")
final class BuilderResolver {

  private static final Logger LOGGER = LoggerFactory.getLogger(BuilderResolver.class);

  private BuilderResolver() {
    throw new UnsupportedOperationException();
  }

  /**
   * 使用内部 Builder 类（静态内部类）进行反序列化。
   *
   * <p>自动检测内部 Builder 类：查找方法名为 fieldName（如 {@code name()}）的 setter， 设置所有字段值后调用 {@code build()}
   * 方法生成目标实例。
   *
   * @param json JSON 字符串
   * @param clazz 目标类
   * @param builderClass Builder 内部类
   * @param <T> 目标类型
   * @return 反序列化后的实例
   */
  static <T> T deserializeWithInnerBuilder(String json, Class<T> clazz, Class<?> builderClass) {
    Map<String, Object> map = JsonParserUtil.parseObject(json);
    if (map == null || map.isEmpty()) {
      return CreatorResolver.createInstanceWithDefaultConstructor(clazz);
    }

    try {
      Constructor<?> builderConstructor = builderClass.getDeclaredConstructor();
      builderConstructor.setAccessible(true);
      Object builderInstance = builderConstructor.newInstance();

      for (Map.Entry<String, Object> entry : map.entrySet()) {
        String fieldName = entry.getKey();
        Object value = entry.getValue();

        try {
          Method setterMethod = findSetterMethod(builderClass, fieldName, value);
          if (setterMethod != null) {
            setterMethod.setAccessible(true);
            Object convertedValue =
                TypeConverter.convertValue(value, setterMethod.getParameterTypes()[0]);
            setterMethod.invoke(builderInstance, convertedValue);
          }
        } catch (Exception e) {
          LOGGER.warn(
              "Inner builder setter failed for {}.{}, skipping field",
              builderClass.getName(),
              fieldName,
              e);
        }
      }

      String buildMethodName = "build";
      try {
        Method buildMethod = builderClass.getMethod(buildMethodName);
        return clazz.cast(buildMethod.invoke(builderInstance));
      } catch (NoSuchMethodException e) {
        for (Method m : builderClass.getDeclaredMethods()) {
          if (m.getName().equals(buildMethodName)
              && m.getParameterCount() == 0
              && clazz.isAssignableFrom(m.getReturnType())) {
            m.setAccessible(true);
            return clazz.cast(m.invoke(builderInstance));
          }
        }
      }
    } catch (Exception e) {
      LOGGER.warn(
          "Inner builder deserialization failed for {}, falling back to map", clazz.getName(), e);
    }
    return clazz.cast(map);
  }

  /**
   * 查找目标类的内部 Builder 类。
   *
   * <p>遍历目标类的所有内部类，查找含 {@code build()} 方法且返回目标类型的类。
   *
   * @param targetClass 目标类
   * @return Builder 内部类，未找到时返回 {@code null}
   */
  static Class<?> findInnerBuilderClass(Class<?> targetClass) {
    for (Class<?> innerClass : targetClass.getDeclaredClasses()) {
      if (isBuilderClass(innerClass, targetClass)) {
        return innerClass;
      }
    }
    return null;
  }

  /**
   * 判断一个内部类是否为 Builder 类。
   *
   * <p>判定标准：该类包含名为 {@code "build"} 且返回类型等于目标类的方法。
   *
   * @param innerClass 待判定的内部类
   * @param targetClass 目标类
   * @return 是 Builder 类返回 {@code true}
   */
  static boolean isBuilderClass(Class<?> innerClass, Class<?> targetClass) {
    for (Method method : innerClass.getDeclaredMethods()) {
      if (method.getName().equals("build") && method.getReturnType().equals(targetClass)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 在 Builder 类中查找匹配的 setter 方法。
   *
   * <p>匹配规则：方法名相同 + 单参数 + 参数类型可赋值。 对于 Number 类型，只要参数和值都是 Number 子类即视为匹配（支持 Integer→Long 等转换）。
   *
   * @param builderClass Builder 类
   * @param methodName 方法名
   * @param value 待设置的值（用于类型推断）
   * @return 匹配的 Method，未找到返回 {@code null}
   */
  static Method findSetterMethod(Class<?> builderClass, String methodName, Object value) {
    Class<?> valueClass = value != null ? value.getClass() : Object.class;

    for (Method method : builderClass.getDeclaredMethods()) {
      if (method.getName().equals(methodName) && method.getParameterCount() == 1) {
        Class<?> paramType = method.getParameterTypes()[0];
        if (paramType.isAssignableFrom(valueClass)) {
          return method;
        }
        if (Number.class.isAssignableFrom(valueClass) && Number.class.isAssignableFrom(paramType)) {
          return method;
        }
      }
    }
    return null;
  }

  /**
   * 将字符串首字母大写，用于拼接 Builder setter 方法名。
   *
   * @param str 输入字符串
   * @return 首字母大写的字符串，null 或空字符串原样返回
   */
  static String capitalize(String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }
    return Character.toUpperCase(str.charAt(0)) + str.substring(1);
  }
}
