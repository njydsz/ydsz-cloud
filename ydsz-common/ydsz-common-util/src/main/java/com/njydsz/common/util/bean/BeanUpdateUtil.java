package com.njydsz.common.util.bean;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.HashSet;
import java.util.Set;

/**
 * Bean 动态更新工具类。
 *
 * <p>核心能力：复制源对象中 <b>非 null</b> 属性到目标对象，避免覆盖目标对象已有值。用于「PATCH 语义」的部分更新场景（PUT/POST 请求中
 * DTO 只携带需要变更的字段）。
 *
 * <h3>背景</h3>
 *
 * <p>Spring 的 {@code BeanUtils.copyProperties} 默认会复制源对象的所有属性（包括 null），导致目标对象的已有字段被 null
 * 覆盖。常见做法是手动传入忽略属性名数组，但每个 Service 都重复实现一遍「收集 null 属性名」既冗余又容易遗漏。
 *
 * <h3>统一方案</h3>
 *
 * <p>本工具提供 {@link #copyNonNull(Object, Object, String...)} 方法，封装「忽略 null 属性 + 可选固定忽略属性」语义，替代各
 * Service 中分散的「复制属性并跳过 null 字段」模式。
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * // 仅复制 dto 中非 null 的字段到 entity，并额外忽略 id
 * BeanUpdateUtil.copyNonNull(dto, entity, "id");
 *
 * // 仅复制 dto 中非 null 的字段，并额外忽略 id 和 builtIn
 * BeanUpdateUtil.copyNonNull(dto, entity, "id", "builtIn");
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class BeanUpdateUtil {

  private BeanUpdateUtil() {
    throw new UnsupportedOperationException("工具类不可实例化");
  }

  /**
   * 复制源对象中 <b>非 null</b> 的属性到目标对象，可指定额外的固定忽略属性。
   *
   * <p>语义：
   *
   * <ul>
   *   <li>源对象属性值为 {@code null} 的字段不会被复制（保留目标对象原值）
   *   <li>{@code ignoreProperties} 中列出的属性名不会被复制（无论值是否为 null）
   *   <li>其他非 null 属性会被复制到目标对象
   * </ul>
   *
   * <p>实现使用 JDK 标准 {@link Introspector introspection}，不依赖 Spring。
   *
   * @param source 源对象（通常是 DTO）
   * @param target 目标对象（通常是 Entity）
   * @param ignoreProperties 额外固定忽略的属性名（如 "id"、"builtIn"），可为空
   * @param <T> 目标对象类型
   * @return 传入的 target 对象（便于链式调用）
   * @throws RuntimeException 如果属性访问失败（包装原始反射异常）
   */
  public static <T> T copyNonNull(Object source, T target, String... ignoreProperties) {
    if (source == null) {
      return target;
    }
    String[] combinedIgnore = combineNullPropertyNames(source, ignoreProperties);
    Set<String> ignoreSet = new HashSet<>(combinedIgnore.length * 2 + 1);
    for (String name : combinedIgnore) {
      ignoreSet.add(name);
    }
    try {
      PropertyDescriptor[] sourcePds = Introspector.getBeanInfo(source.getClass()).getPropertyDescriptors();
      for (PropertyDescriptor sourcePd : sourcePds) {
        if (sourcePd.getReadMethod() == null) {
          continue;
        }
        String name = sourcePd.getName();
        if (ignoreSet.contains(name)) {
          continue;
        }
        Object value = sourcePd.getReadMethod().invoke(source);
        if (value == null) {
          continue;
        }
        PropertyDescriptor targetPd = getPropertyDescriptor(target.getClass(), name);
        if (targetPd == null || targetPd.getWriteMethod() == null) {
          continue;
        }
        if (!sourcePd.getPropertyType().equals(targetPd.getPropertyType())) {
          continue;
        }
        try {
          targetPd.getWriteMethod().invoke(target, value);
        } catch (IllegalArgumentException ignored) {
          // 属性类型不匹配（泛型擦除导致），安全跳过
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Bean copy failed: " + e.getMessage(), e);
    }
    return target;
  }

  /**
   * 计算源对象中值为 null 的属性名 + 额外固定忽略的属性名的合集。
   *
   * <p>抽取为独立方法便于单元测试与扩展（例如未来需要支持嵌套属性、Collection 类型忽略等）。
   *
   * @param source 源对象
   * @param ignoreProperties 额外固定忽略的属性名
   * @return 合并后的忽略属性名数组（不含重复项）
   */
  private static String[] combineNullPropertyNames(Object source, String... ignoreProperties) {
    try {
      Set<String> ignored = new HashSet<>(16);
      if (ignoreProperties != null) {
        for (String name : ignoreProperties) {
          ignored.add(name);
        }
      }
      for (PropertyDescriptor pd : Introspector.getBeanInfo(source.getClass()).getPropertyDescriptors()) {
        if (pd.getReadMethod() == null) {
          continue;
        }
        try {
          Object value = pd.getReadMethod().invoke(source);
          if (value == null) {
            ignored.add(pd.getName());
          }
        } catch (Exception e) {
          ignored.add(pd.getName());
        }
      }
      return ignored.toArray(new String[0]);
    } catch (IntrospectionException e) {
      throw new RuntimeException("Failed to introspect source object: " + e.getMessage(), e);
    }
  }

  /**
   * 在指定类中查找指定名称的属性描述符。
   *
   * @param beanClass bean 的类型
   * @param propertyName 属性名
   * @return 匹配的 PropertyDescriptor，未找到返回 null
   */
  private static PropertyDescriptor getPropertyDescriptor(Class<?> beanClass, String propertyName) {
    try {
      for (PropertyDescriptor pd : Introspector.getBeanInfo(beanClass).getPropertyDescriptors()) {
        if (propertyName.equals(pd.getName())) {
          return pd;
        }
      }
    } catch (IntrospectionException ignored) {
      // 忽略：返回 null 表示未找到
    }
    return null;
  }
}
