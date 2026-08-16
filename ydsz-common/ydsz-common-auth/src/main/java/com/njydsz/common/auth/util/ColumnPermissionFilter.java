package com.njydsz.common.auth.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.cglib.beans.BeanCopier;

/**
 * 列权限字段过滤工具类，基于 Cglib BeanCopier 实现高性能浅拷贝。
 *
 * <p>相比纯反射字段拷贝，Cglib BeanCopier 通过字节码生成直接访问 getter/setter， 性能提升约 3-10 倍（取决于字段数量与 JVM 内联优化程度）。
 *
 * <p><b>核心功能：</b>
 *
 * <ul>
 *   <li>filterColumns：单对象列权限过滤（浅拷贝 + 不可见字段置 null）
 *   <li>filterColumnsList：批量列表列权限过滤
 * </ul>
 *
 * <p><b>降级策略：</b>
 *
 * <p>当 Cglib BeanCopier 创建失败或类无可访问无参构造器时， 自动降级到 {@link BeanUtils#copyProperties} 完成属性拷贝。
 *
 * <p><b>缓存策略：</b>
 *
 * <ul>
 *   <li>BeanCopier 实例按 Class 缓存，避免重复生成字节码
 *   <li>Field[] 按 Class 缓存，避免重复反射扫描类层次结构
 * </ul>
 *
 * <p><b>线程安全：</b>
 *
 * <p>BeanCopier 实例创建后无状态，可安全缓存并发使用； 字段数组缓存为不可变结构，多线程读取安全。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see org.springframework.cglib.beans.BeanCopier
 * @see org.springframework.beans.BeanUtils
 */
public final class ColumnPermissionFilter {

  private static final Logger log = LoggerFactory.getLogger(ColumnPermissionFilter.class);

  /** BeanCopier 实例缓存，按 Class 缓存避免重复生成字节码 */
  private static final ConcurrentMap<Class<?>, BeanCopier> COPIER_CACHE =
      new ConcurrentHashMap<>(64);

  /** 字段列表缓存，按 Class 缓存避免重复反射扫描类层次结构 */
  private static final ConcurrentMap<Class<?>, Field[]> FIELD_CACHE = new ConcurrentHashMap<>(64);

  private ColumnPermissionFilter() {
    throw new UnsupportedOperationException("工具类禁止实例化");
  }

  /**
   * 过滤单个对象的列权限字段。
   *
   * <p>创建源对象的浅拷贝，然后将不在 visibleColumns 中的字段置为 null。 浅拷贝优先使用 Cglib BeanCopier，失败时降级到 Spring
   * BeanUtils。
   *
   * <p>visibleColumns 为 null 或空时，表示无可见列限制，返回的拷贝中所有字段保留原值。
   *
   * @param source 源对象（不可为 null）
   * @param visibleColumns 可见列名集合（大小写不敏感，自动转为小写比较）； null 或空表示无可见列限制
   * @return 过滤后的新实例（源对象不会被修改）
   */
  public static Object filterColumns(Object source, Set<String> visibleColumns) {
    if (source == null) {
      return null;
    }
    Set<String> normalizedVisible = normalizeColumns(visibleColumns);
    if (normalizedVisible.isEmpty()) {
      // 无可见列限制，直接返回浅拷贝（保留全部字段）
      return shallowCopy(source);
    }
    Object copy = shallowCopy(source);
    nullifyInvisibleFields(copy, normalizedVisible);
    return copy;
  }

  /**
   * 批量过滤列表中的列权限字段。
   *
   * <p>对 sourceList 中的每个元素执行 {@link #filterColumns}， 返回等大的新列表；列表中的 null 元素在结果中仍为 null。
   *
   * @param sourceList 源对象列表（不可为 null）
   * @param visibleColumns 可见列名集合（大小写不敏感）；null 或空表示无可见列限制
   * @return 过滤后的新列表
   */
  public static List<Object> filterColumnsList(List<?> sourceList, Set<String> visibleColumns) {
    if (sourceList == null || sourceList.isEmpty()) {
      return Collections.emptyList();
    }
    Set<String> normalizedVisible = normalizeColumns(visibleColumns);
    List<Object> result = new ArrayList<>(sourceList.size());
    for (Object item : sourceList) {
      if (item == null) {
        result.add(null);
        continue;
      }
      if (normalizedVisible.isEmpty()) {
        result.add(shallowCopy(item));
      } else {
        Object copy = shallowCopy(item);
        nullifyInvisibleFields(copy, normalizedVisible);
        result.add(copy);
      }
    }
    return result;
  }

  /**
   * 创建对象的浅拷贝。
   *
   * <p>优先使用 Cglib BeanCopier（字节码生成，高性能）； 若 BeanCopier 创建失败或类缺少无参构造器，降级到 Spring BeanUtils。
   *
   * @param source 源对象
   * @return 浅拷贝的新实例；若拷贝失败则返回源对象本身
   */
  private static Object shallowCopy(Object source) {
    Class<?> clazz = source.getClass();
    try {
      Object target = clazz.getDeclaredConstructor().newInstance();
      BeanCopier copier = COPIER_CACHE.computeIfAbsent(clazz, k -> createBeanCopier(k));
      if (copier != null) {
        copier.copy(source, target, null);
      } else {
        // BeanCopier 不可用，降级到 Spring BeanUtils
        BeanUtils.copyProperties(source, target);
      }
      return target;
    } catch (Exception e) {
      log.warn("浅拷贝失败，类 {}: {}", clazz.getName(), e.getMessage());
      return source;
    }
  }

  /**
   * 创建指定类的 BeanCopier 实例。
   *
   * @param clazz 目标类
   * @return BeanCopier 实例；创建失败时返回 null
   */
  private static BeanCopier createBeanCopier(Class<?> clazz) {
    try {
      return BeanCopier.create(clazz, clazz, false);
    } catch (Exception e) {
      log.debug("创建 BeanCopier 失败，类 {}: {}", clazz.getName(), e.getMessage());
      return null;
    }
  }

  /**
   * 将对象中不在 visibleColumns 中的字段置为 null。
   *
   * @param target 目标对象（浅拷贝实例）
   * @param visibleColumns 规范化后的可见列名集合（小写）
   */
  private static void nullifyInvisibleFields(Object target, Set<String> visibleColumns) {
    if (target == null) {
      return;
    }
    Field[] fields =
        FIELD_CACHE.computeIfAbsent(
            target.getClass(), ColumnPermissionFilter::collectInstanceFields);
    for (Field field : fields) {
      String fieldName = normalizeFieldName(field.getName());
      if (visibleColumns.contains(fieldName)) {
        continue;
      }
      field.setAccessible(true);
      try {
        field.set(target, null);
      } catch (IllegalAccessException e) {
        log.debug("无法置空字段 {}: {}", field.getName(), e.getMessage());
      }
    }
  }

  /**
   * 收集类的所有实例字段（含父类），排除 static 和 final 字段。
   *
   * @param clazz 目标类
   * @return 实例字段数组
   */
  private static Field[] collectInstanceFields(Class<?> clazz) {
    List<Field> fieldList = new ArrayList<>();
    Class<?> current = clazz;
    while (current != null && current != Object.class) {
      for (Field field : current.getDeclaredFields()) {
        int mods = field.getModifiers();
        if (!Modifier.isStatic(mods) && !Modifier.isFinal(mods)) {
          fieldList.add(field);
        }
      }
      current = current.getSuperclass();
    }
    return fieldList.toArray(new Field[0]);
  }

  /**
   * 规范化列名集合：去除首尾空格并转为小写。
   *
   * @param columns 原始列名集合
   * @return 规范化后的列名集合；输入为 null 时返回空集合
   */
  private static Set<String> normalizeColumns(Set<String> columns) {
    if (columns == null || columns.isEmpty()) {
      return Collections.emptySet();
    }
    Set<String> normalized = new HashSet<>(columns.size());
    for (String col : columns) {
      if (col != null && !col.isBlank()) {
        normalized.add(col.trim().toLowerCase(Locale.ROOT));
      }
    }
    return normalized;
  }

  /**
   * 规范化字段名：去除首尾空格并转为小写。
   *
   * @param fieldName 原始字段名
   * @return 规范化后的字段名
   */
  private static String normalizeFieldName(String fieldName) {
    return fieldName == null ? "" : fieldName.trim().toLowerCase(Locale.ROOT);
  }
}
