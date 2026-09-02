package com.njydsz.common.util.bean;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.util.string.StringUtils;

/**
 * Bean 映射工具类——将 {@code Map<String, Object>} 转换为 Java Bean / Record。
 *
 * <p>从 {@code MapUtils} 中独立出来的 Bean 映射专用类，承担原本由 MapUtils 同时承担的 Map 操作和 Bean 映射两个职责中的后者（单一职责原则）。
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li>Map → Bean：基于 setter 反射绑定字段，自动类型转换（Integer/Long/Double/Boolean/Java-Time/UUID 等）
 *   <li>Map → Record：基于规范构造器绑定组件，支持下划线/驼峰命名互换
 *   <li>Map → 嵌套 Bean / List&lt;T&gt; / Map&lt;K,V&gt;：通过 {@link TypeReference} 捕获泛型信息递归转换
 * </ul>
 *
 * <p>典型用法：
 *
 * <pre>{@code
 * Map<String, Object> userData = Map.of("name", "张三", "age", 25);
 * UserDO user = BeanMapper.toBean(userData, UserDO.class);
 *
 * List<Map<String,Object>> rawList = getList(data, "users");
 * List<User> users = BeanMapper.toBean(rawList, new BeanMapper.TypeReference<List<User>>() {});
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class BeanMapper {

  private BeanMapper() {
    throw new UnsupportedOperationException(
        "BeanMapper is a utility class and cannot be instantiated");
  }

  private static final Logger LOG = LoggerFactory.getLogger(BeanMapper.class);

  // ==================== 缓存与常量 ====================

  /**
   * Setter 方法缓存，按 Class 维度索引，避免重复反射扫描。
   *
   * <p>缓存结构：Class → (字段名 → Method)。 使用 {@link Class} 对象本身作为 key，避免不同 ClassLoader 下同名类串缓存； 使用
   * ConcurrentHashMap 保证并发安全，computeIfAbsent 保证单线程初始化。 （Spring Boot 应用无类热卸载场景，以 Class 为 key
   * 不会造成类泄露。）
   */
  private static final ConcurrentHashMap<Class<?>, Map<String, Method>> SETTER_CACHE =
      new ConcurrentHashMap<>();

  /**
   * Setter 调用器缓存（Method → BiConsumer），通过 LambdaMetafactory 生成， 将反射 {@code Method.invoke}
   * 提升为接近直接调用的性能（约 5~10 倍）。
   */
  private static final ConcurrentHashMap<Method, BiConsumer<Object, Object>> SETTER_INVOKER_CACHE =
      new ConcurrentHashMap<>();

  /** 默认日期时间格式：{@code yyyy-MM-dd HH:mm:ss} */
  private static final DateTimeFormatter DEFAULT_DATE_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  // ==================== 内部方法（兼容 MapUtils 历史签名） ====================

  /**
   * 将 {@code Map<String, Object>} 转换为指定类型的 Java Bean（内部实现）。
   *
   * @param map 源 Map
   * @param targetClass 目标 Bean 类型
   * @param <T> Bean 类型
   * @return 填充后的 Bean 实例
   * @since 26.09.01
   */
  public static <T> T toBeanInternal(Map<String, Object> map, Class<T> targetClass) {
    if (map == null) {
      throw new IllegalArgumentException("map cannot be null");
    }
    if (targetClass == null) {
      throw new IllegalArgumentException("targetClass cannot be null");
    }

    T bean = createInstance(targetClass);
    if (map.isEmpty()) {
      return bean;
    }

    Map<String, Method> setters = getCachedSetters(targetClass);
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      String fieldName = entry.getKey();
      Object value = entry.getValue();
      if (value == null) {
        continue;
      }
      Method setter = setters.get(fieldName);
      // 命名兼容：Map key 为 snake_case 时转驼峰匹配 setter
      if (setter == null) {
        setter = setters.get(StringUtils.toCamelCase(fieldName));
      }
      if (setter == null) {
        continue;
      }
      Class<?> paramType = setter.getParameterTypes()[0];
      Object converted = convertValue(value, paramType, setter);
      if (converted != null) {
        try {
          BiConsumer<Object, Object> invoker =
              SETTER_INVOKER_CACHE.computeIfAbsent(setter, BeanMapper::createSetterInvoker);
          invoker.accept(bean, converted);
        } catch (Exception e) {
          // 设置失败（业务 setter 抛异常等），跳过该字段
        }
      }
    }
    return bean;
  }

  /**
   * 通过 LambdaMetafactory 为 setter 生成接近直接调用性能的调用器。
   *
   * <p>对声明受检异常或无法生成 Lambda 的 setter，回退到反射调用。
   *
   * @param setter setter 方法
   * @return 调用器（第一个参数为 bean 实例，第二个参数为待写入的值）
   */
  private static BiConsumer<Object, Object> createSetterInvoker(Method setter) {
    try {
      MethodHandles.Lookup lookup = MethodHandles.lookup();
      MethodHandle handle = lookup.unreflect(setter);
      CallSite site =
          LambdaMetafactory.metafactory(
              lookup,
              "accept",
              MethodType.methodType(BiConsumer.class),
              MethodType.methodType(void.class, Object.class, Object.class),
              handle,
              handle.type());
      return (BiConsumer<Object, Object>) site.getTarget().invokeExact();
    } catch (Throwable t) {
      // 声明受检异常或不可达 setter，回退到反射
      return (bean, value) -> {
        try {
          setter.invoke(bean, value);
        } catch (ReflectiveOperationException e) {
          throw new IllegalStateException("Failed to invoke setter " + setter.getName(), e);
        }
      };
    }
  }

  /**
   * 获取指定 Class 的 setter 方法缓存。
   *
   * @param clazz 目标类型
   * @return 字段名 → setter Method 映射（字段名采用原始 setter 名去除 set + 首字母小写）
   * @since 26.09.01
   */
  public static Map<String, Method> getCachedSetters(Class<?> clazz) {
    return SETTER_CACHE.computeIfAbsent(clazz, k -> scanSetters(clazz));
  }

  /**
   * 扫描类的所有 public void setXxx(Type) 方法，提取字段名 → Method 映射。
   *
   * @param clazz 目标类型
   * @return 字段名 → setter 的不可变 Map
   * @since 26.09.01
   */
  public static Map<String, Method> scanSetters(Class<?> clazz) {
    Map<String, Method> setterMap = new LinkedHashMap<>(16);