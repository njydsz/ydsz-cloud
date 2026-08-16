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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
 * @since 4.0.0
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

  /** {@code java.time.*} 包类名前缀集合，用于 toBean 时区分日期类型。 */
  private static final Set<String> JAVA_TIME_TYPES =
      new HashSet<>(
          Arrays.asList(
              "java.time.LocalDate",
              "java.time.LocalDateTime",
              "java.time.LocalTime",
              "java.time.Instant",
              "java.time.ZonedDateTime"));

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
   * @since 4.0.0
   */
  @SuppressWarnings("unchecked")
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
   * @since 4.0.0
   */
  public static Map<String, Method> getCachedSetters(Class<?> clazz) {
    return SETTER_CACHE.computeIfAbsent(clazz, k -> scanSetters(clazz));
  }

  /**
   * 扫描类的所有 public void setXxx(Type) 方法，提取字段名 → Method 映射。
   *
   * @param clazz 目标类型
   * @return 字段名 → setter 的不可变 Map
   * @since 4.0.0
   */
  public static Map<String, Method> scanSetters(Class<?> clazz) {
    Map<String, Method> setterMap = new LinkedHashMap<>();
    Method[] methods = clazz.getMethods();
    for (Method method : methods) {
      if (!isSetter(method)) {
        continue;
      }
      // 提取字段名：setter 名去掉 "set"，首字母小写
      String methodName = method.getName();
      String fieldName = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
      setterMap.put(fieldName, method);
    }
    return setterMap;
  }

  /**
   * 判断方法是否为标准的 setter 方法。
   *
   * <p>标准 setter：public、非 static、void 返回值、单参数、方法名以 set 开头。 同时过滤桥接方法（{@link
   * Method#isBridge()}），避免泛型类型擦除导致的重复 setter 条目。
   *
   * @param method 方法对象
   * @return 是否为 setter
   * @since 4.0.0
   */
  public static boolean isSetter(Method method) {
    if (method == null) {
      return false;
    }
    if (method.isBridge()) {
      return false;
    }
    int modifiers = method.getModifiers();
    if (!Modifier.isPublic(modifiers) || Modifier.isStatic(modifiers)) {
      return false;
    }
    if (!void.class.equals(method.getReturnType())) {
      return false;
    }
    if (method.getName().length() <= 3 || !method.getName().startsWith("set")) {
      return false;
    }
    return method.getParameterCount() == 1;
  }

  /**
   * 默认日期时间格式：{@code yyyy-MM-dd HH:mm:ss}
   *
   * @return 默认日期时间格式化器
   * @since 4.0.0
   */
  public static DateTimeFormatter getDefaultDateFormatter() {
    return DEFAULT_DATE_FORMATTER;
  }

  /**
   * 将值转换为目标类型（使用默认日期格式 {@code yyyy-MM-dd HH:mm:ss}）。
   *
   * @param value 原始值
   * @param paramType 目标参数类型
   * @param setter setter 方法（用于提取泛型参数，可为 null）
   * @return 转换后的值，转换失败返回 null
   * @since 4.0.0
   */
  public static Object convertValue(Object value, Class<?> paramType, Method setter) {
    return convertValue(value, paramType, DEFAULT_DATE_FORMATTER, setter);
  }

  /**
   * 将值转换为目标类型（支持常见类型间的互转，使用指定日期格式）。
   *
   * <p>新增支持（4.0.0）：
   *
   * <ul>
   *   <li>{@code Optional<T>} 字段：自动解包后包装为 Optional
   *   <li>{@code UUID} 类型：通过 UUID.fromString 解析
   *   <li>{@code Duration} 类型：通过 Duration.parse 解析
   *   <li>{@code YearMonth} 类型：通过 YearMonth.parse 解析
   * </ul>
   *
   * @param value 原始值
   * @param paramType 目标参数类型
   * @param dateFormatter 日期格式化器
   * @param setter setter 方法（用于提取泛型参数，可为 null）
   * @return 转换后的值，转换失败返回 null
   * @since 4.0.0
   */
  public static Object convertValue(
      Object value, Class<?> paramType, DateTimeFormatter dateFormatter, Method setter) {
    if (paramType.isInstance(value)) {
      return value;
    }

    // 4.0.0: Optional<T> 字段支持 — 从 setter 泛型提取 T
    if (paramType == Optional.class && setter != null) {
      return convertOptional(value, setter.getGenericParameterTypes()[0]);
    }

    String str = value.toString();
    if (str.isEmpty()) {
      return null;
    }

    try {
      // 整数类型
      if (paramType == int.class || paramType == Integer.class) {
        return Integer.valueOf(str);
      }
      if (paramType == long.class || paramType == Long.class) {
        return Long.valueOf(str);
      }
      if (paramType == short.class || paramType == Short.class) {
        return Short.valueOf(str);
      }
      if (paramType == byte.class || paramType == Byte.class) {
        return Byte.valueOf(str);
      }
      // 浮点类型
      if (paramType == double.class || paramType == Double.class) {
        return Double.valueOf(str);
      }
      if (paramType == float.class || paramType == Float.class) {
        return Float.valueOf(str);
      }
      // Boolean
      if (paramType == boolean.class || paramType == Boolean.class) {
        Boolean b = toBoolean(value);
        return b != null ? b : null;
      }
      // BigDecimal / BigInteger
      if (paramType == BigDecimal.class) {
        return new BigDecimal(str);
      }
      if (paramType == BigInteger.class) {
        return new BigInteger(str);
      }
      // 4.0.0: 新增 JDK 常用业务类型
      if (paramType == UUID.class) {
        return UUID.fromString(str);
      }
      if (paramType == YearMonth.class) {
        return YearMonth.parse(str);
      }
      if (paramType == Duration.class) {
        return Duration.parse(str);
      }
      // 日期时间类型
      if (paramType == LocalDateTime.class) {
        return LocalDateTime.parse(str, dateFormatter);
      }
      if (paramType == LocalDate.class) {
        return LocalDate.parse(str);
      }
      if (paramType == LocalTime.class) {
        return LocalTime.parse(str);
      }
      if (paramType == Instant.class) {
        return Instant.parse(str);
      }
      if (paramType == java.util.Date.class) {
        LocalDateTime ldt = LocalDateTime.parse(str, dateFormatter);
        return java.util.Date.from(ldt.atZone(java.time.ZoneId.systemDefault()).toInstant());
      }
      // String
      if (paramType == String.class) {
        return str;
      }
    } catch (Exception e) {
      return null;
    }

    // 嵌套 Bean 递归
    if (value instanceof Map<?, ?> nestedMap
        && !paramType.isInterface()
        && !Modifier.isAbstract(paramType.getModifiers())) {
      Map<String, Object> nestedStringMap = toStringObjectMap(nestedMap);
      try {
        return toBeanOrRecord(nestedStringMap, paramType);
      } catch (Exception e) {
        return null;
      }
    }

    // List<T> 泛型字段处理：将 List<Object>（元素为 Map）转换为 List<T>
    if (value instanceof List<?> rawList
        && List.class.isAssignableFrom(paramType)
        && setter != null) {
      return convertToList(rawList, setter, dateFormatter);
    }

    // Map<K,V> 泛型字段处理：将 Map<?,?> 转换为 Map<K,V>
    if (value instanceof Map<?, ?> rawMap
        && Map.class.isAssignableFrom(paramType)
        && setter != null) {
      return convertToMap(rawMap, setter);
    }

    return null;
  }

  /**
   * 转换 Optional<T> 字段：提取泛型参数 T，转换值后包装为 Optional。
   *
   * <p>若 value 为 null 则返回 {@code Optional.empty()}； 若 value 本身是 Optional 则直接返回； 否则提取泛型 T 后对 value
   * 做类型转换并包装。
   *
   * @param value 原始值
   * @param optionalGenericType Optional 的泛型类型（如 Optional<User> 的 ParameterizedType）
   * @return 包装后的 Optional
   * @since 4.0.0
   */
  public static Object convertOptional(Object value, Type optionalGenericType) {
    if (value == null) {
      return Optional.empty();
    }
    if (value instanceof Optional<?>) {
      return value;
    }
    // 提取 Optional<T> 中的 T
    if (optionalGenericType instanceof ParameterizedType pt) {
      Type innerType = pt.getActualTypeArguments()[0];
      if (innerType instanceof Class<?> clazz) {
        Object converted =
            (value instanceof Map<?, ?> m)
                ? toBeanOrRecord(toStringObjectMap(m), clazz)
                : convertValue(value, clazz, DEFAULT_DATE_FORMATTER, null);
        return Optional.ofNullable(converted);
      }
    }
    return Optional.ofNullable(value);
  }

  /**
   * 将 List<Object> 转换为 List<T>，通过 setter 的泛型参数提取元素类型。
   *
   * <p>若未能提取泛型参数或元素非 Map 类型，则直接返回原始 List。
   *
   * @param rawList 原始 List
   * @param setter setter 方法（用于提取泛型参数）
   * @param formatter 日期格式化器
   * @return 转换后的 List；无法转换时返回原始 List
   * @since 4.0.0
   */
  public static Object convertToList(List<?> rawList, Method setter, DateTimeFormatter formatter) {
    try {
      Type genericParam = setter.getGenericParameterTypes()[0];
      if (!(genericParam instanceof ParameterizedType pt)) {
        return rawList;
      }
      Type[] typeArgs = pt.getActualTypeArguments();
      if (typeArgs.length != 1 || !(typeArgs[0] instanceof Class<?> elementType)) {
        return rawList;
      }
      // 仅处理 Table → Bean 或 Table → Table 的嵌套转换
      if (elementType == Object.class || elementType == String.class) {
        return new ArrayList<>(rawList);
      }

      List<Object> result = new ArrayList<>(rawList.size());
      for (Object item : rawList) {
        if (item instanceof Map<?, ?> itemMap) {
          if (!elementType.isInterface() && !Modifier.isAbstract(elementType.getModifiers())) {
            result.add(toBeanOrRecord(toStringObjectMap(itemMap), elementType));
          } else {
            result.add(item);
          }
        } else {
          // 非 Map 元素（已是目标基本类型）直接保留
          result.add(item);
        }
      }
      return result;
    } catch (Exception e) {
      return rawList;
    }
  }

  /**
   * 将 Map&lt;?,?&gt; 转换为 Map&lt;K,V&gt;，通过 setter 的泛型参数提取 value 类型。
   *
   * <p>若未能提取泛型参数或 value 类型为 Object/String，则直接返回浅拷贝的 Map。
   *
   * @param rawMap 原始 Map
   * @param setter setter 方法（用于提取泛型参数）
   * @return 转换后的 Map；无法转换时返回浅拷贝 Map
   * @since 4.1.0
   */
  private static Object convertToMap(Map<?, ?> rawMap, Method setter) {
    try {
      Type genericParam = setter.getGenericParameterTypes()[0];
      if (!(genericParam instanceof ParameterizedType pt)) {
        return new LinkedHashMap<>(rawMap);
      }
      Type[] typeArgs = pt.getActualTypeArguments();
      if (typeArgs.length != 2) {
        return new LinkedHashMap<>(rawMap);
      }
      Type valueType = typeArgs[1];
      if (valueType == Object.class || valueType == String.class) {
        return new LinkedHashMap<>(rawMap);
      }
      return convertMapWithType(rawMap, valueType);
    } catch (Exception e) {
      return new LinkedHashMap<>(rawMap);
    }
  }

  /**
   * 通过无参构造器创建实例。
   *
   * @param clazz 目标类型
   * @param <T> 类型
   * @return 新实例
   * @throws IllegalArgumentException 无无参构造器或实例化失败
   * @since 4.0.0
   */
  @SuppressWarnings("unchecked")
  public static <T> T createInstance(Class<T> clazz) {
    try {
      return clazz.getDeclaredConstructor().newInstance();
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(
          "Class " + clazz.getName() + " 缺少无参构造器，无法通过 BeanMapper.toBean 转换", e);
    } catch (ReflectiveOperationException e) {
      throw new IllegalArgumentException(
          "实例化失败: " + clazz.getName() + ", 原因: " + e.getMessage(), e);
    }
  }

  // ==================== 泛型 TypeReference 支持 ====================

  /**
   * 泛型类型引用——用于捕获参数化类型信息，解决 Java 泛型擦除导致的运行时类型丢失。
   *
   * <p>用法：
   *
   * <pre>{@code
   * List<User> users = BeanMapper.toBean(map.getList("users"), new BeanMapper.TypeReference<List<User>>() {});
   * Map<String, Order> orders = BeanMapper.toBean(map.getMap("orders"), new BeanMapper.TypeReference<Map<String, Order>>() {});
   * }</pre>
   *
   * <p>实现原理：通过匿名子类的 {@code getGenericSuperclass()} 捕获 {@code ParameterizedType}， 从而在运行时获取完整的泛型参数信息。
   *
   * @param <T> 目标泛型类型
   * @since 4.0.0
   */
  public abstract static class TypeReference<T> {
    private final Type type;

    protected TypeReference() {
      Type superClass = getClass().getGenericSuperclass();
      if (!(superClass instanceof ParameterizedType)) {
        throw new IllegalStateException(
            "TypeReference must be created as anonymous subclass with type parameter");
      }
      this.type = ((ParameterizedType) superClass).getActualTypeArguments()[0];
    }

    /**
     * 获取完整的泛型类型（包含参数化信息）。
     *
     * @return 泛型类型
     */
    public Type getType() {
      return type;
    }

    /**
     * 获取原始类型（擦除泛型后的 Class）。
     *
     * @return 原始类型 Class
     */
    @SuppressWarnings("unchecked")
    public Class<T> getRawType() {
      if (type instanceof Class<?> c) return (Class<T>) c;
      if (type instanceof ParameterizedType pt) return (Class<T>) pt.getRawType();
      return (Class<T>) Object.class;
    }
  }

  // ==================== 公开 API ====================

  /**
   * 将 {@code Map<String, Object>} 转换为指定类型的 Java Bean。
   *
   * <p><b>实现思路：</b>
   *
   * <ol>
   *   <li>通过 targetClass.getDeclaredConstructor().newInstance() 创建 Bean 实例（要求无参构造器）
   *   <li>扫描 targetClass 的所有 setter 方法（{@code setXxx(Type)}），按字段名与 Map key 匹配
   *   <li>类型匹配时直接赋值；类型不匹配时尝试 String→目标类型 的基础转换（Integer/Long/Double/Boolean/LocalDateTime/Date）
   *   <li>缓存每个 Class 的 setter 元数据，避免重复扫描（首次反射后命中率 100%）
   * </ol>
   *
   * <p><b>类型转换规则：</b>
   *
   * <ul>
   *   <li>{@code String} → {@code Integer / Long / Double / Float / Boolean}：调用 parseXxx 或 valueOf
   *   <li>{@code String} → {@code LocalDateTime}：调用 {@code LocalDateTime.parse(text)}
   *   <li>{@code String} → {@code LocalDate}：调用 {@code LocalDate.parse(text)}
   *   <li>{@code String} → {@code java.util.Date}：按 ISO 格式解析后转 Date
   *   <li>{@code Map} → 嵌套 Bean：递归调用 toBean
   *   <li>类型不兼容且无法转换：跳过该字段（不抛异常）
   * </ul>
   *
   * <p><b>典型用法：</b>
   *
   * <pre>{@code
   * Map<String, Object> userData = Map.of(
   *     "name", "张三",
   *     "age", 25,
   *     "createTime", "2024-01-15 10:30:00"
   * );
   * UserDO user = BeanMapper.toBeanOrRecord(userData, UserDO.class);
   * // user.getName() == "张三", user.getAge() == 25
   * }</pre>
   *
   * <p><b>注意事项：</b>
   *
   * <ul>
   *   <li>不处理复杂泛型字段（如 {@code List<SubBean>}），需要时请配合专用 JSON 框架
   *   <li>字段无 setter 时不会被赋值（不会直接写 Field）
   *   <li>性能敏感场景（QPS > 10k）建议配合字节码生成框架（如 ReflectASM）或专用 BeanUtils
   * </ul>
   *
   * @param source 源 Map，不可为 null
   * @param targetClass 目标 Bean 类型，不可为 null
   * @param <T> Bean 类型
   * @return 填充后的 Bean 实例
   * @throws IllegalArgumentException 入参为 null、targetClass 无无参构造器、或实例化失败
   * @since 4.0.0
   */
  public static <T> T toBean(Map<String, Object> source, Class<T> targetClass) {
    Objects.requireNonNull(targetClass, "targetClass must not be null");
    if (source == null) {
      return null;
    }
    return toBeanOrRecord(source, targetClass);
  }

  /**
   * 泛型版 toBean，支持 List&lt;T&gt;、Map&lt;K,V&gt; 等参数化类型转换。
   *
   * <p>与 {@link #toBean(Map, Class)} 不同，本方法通过 {@link TypeReference} 捕获泛型信息， 能正确处理集合元素类型。
   *
   * <p>使用示例：
   *
   * <pre>{@code
   * // List<User> 场景
   * List<Map<String, Object>> rawList =.getList(data, "users");
   * List<User> users = BeanMapper.toBean(rawList, new TypeReference<List<User>>() {});
   *
   * // Map<String, Order> 场景
   * Map<String, Object> rawMap = getMap(data, "orders");
   * Map<String, Order> orders = BeanMapper.toBean(rawMap, new TypeReference<Map<String, Order>>() {});
   * }</pre>
   *
   * @param source 源数据（List 或 Map）
   * @param typeRef 泛型类型引用
   * @param <T> 目标类型
   * @return 转换后的对象
   * @since 4.0.0
   */
  @SuppressWarnings("unchecked")
  public static <T> T toBean(Object source, TypeReference<T> typeRef) {
    Objects.requireNonNull(typeRef, "typeRef must not be null");
    Type type = typeRef.getType();

    // List<T>
    if (type instanceof ParameterizedType pt && pt.getRawType() == List.class) {
      if (!(source instanceof List<?> rawList)) {
        throw new IllegalArgumentException(
            "Expected List, got " + (source == null ? "null" : source.getClass()));
      }
      Type elementType = pt.getActualTypeArguments()[0];
      return (T) convertListWithType(rawList, elementType);
    }

    // Map<K, V>
    if (type instanceof ParameterizedType pt && pt.getRawType() == Map.class) {
      if (!(source instanceof Map<?, ?> rawMap)) {
        throw new IllegalArgumentException(
            "Expected Map, got " + (source == null ? "null" : source.getClass()));
      }
      Type valueType = pt.getActualTypeArguments()[1];
      return (T) convertMapWithType(rawMap, valueType);
    }

    // 非参数化类型，退化为 Class 版本（经 toBeanOrRecord 支持 Record 类型）
    if (type instanceof Class<?> clazz) {
      if (source instanceof Map<?, ?> rawMap) {
        @SuppressWarnings("unchecked")
        Class<T> target = (Class<T>) clazz;
        return toBeanOrRecord(toStringObjectMap(rawMap), target);
      }
      if (clazz.isInstance(source)) {
        return (T) source;
      }
      throw new IllegalArgumentException("Cannot convert " + source.getClass() + " to " + clazz);
    }

    throw new IllegalArgumentException("Unsupported type: " + type);
  }

  /**
   * 将 Map 转换为指定类型的 Bean（支持普通 Bean 与 Record）。
   *
   * <p>普通 Bean 基于 setter 反射绑定字段；Record 基于规范构造器绑定组件值。
   *
   * @param source 源 Map（String 键）
   * @param targetClass 目标类型
   * @param <T> 目标类型泛型
   * @return 转换后的对象；source 为 null 时返回 null
   * @since 4.0.0
   */
  public static <T> T toBeanOrRecord(Map<String, Object> source, Class<T> targetClass) {
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(targetClass, "targetClass must not be null");

    if (targetClass.isRecord()) {
      return instantiateRecord(source, targetClass);
    }
    return toBeanInternal(source, targetClass);
  }

  // ==================== List/Map 泛型转换内部方法 ====================

  /**
   * 将 List 转换为 List&lt;T&gt;，通过 Type 而非 Class 处理元素类型（支持嵌套泛型）。
   *
   * @param rawList 原始 List
   * @param elementType 元素类型
   * @return 转换后的 List
   * @since 4.0.0
   */
  public static List<Object> convertListWithType(List<?> rawList, Type elementType) {
    List<Object> result = new ArrayList<>(rawList.size());
    for (Object item : rawList) {
      result.add(convertSingleItem(item, elementType));
    }
    return result;
  }

  /**
   * 将 Map 转换为 Map&lt;String, V&gt;，value 类型由 Type 指定。
   *
   * @param rawMap 原始 Map
   * @param valueType 值类型
   * @return 转换后的 Map
   * @since 4.0.0
   */
  public static Map<String, Object> convertMapWithType(Map<?, ?> rawMap, Type valueType) {
    Map<String, Object> result = new LinkedHashMap<>(rawMap.size());
    for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
      result.put(String.valueOf(entry.getKey()), convertSingleItem(entry.getValue(), valueType));
    }
    return result;
  }

  /**
   * 转换单个元素到目标类型（支持 Class、ParameterizedType）。
   *
   * @param item 原始元素
   * @param targetType 目标类型
   * @return 转换后的元素
   * @since 4.0.0
   */
  public static Object convertSingleItem(Object item, Type targetType) {
    if (item == null) return null;
    if (targetType instanceof Class<?> clazz) {
      if (clazz.isInstance(item)) return item;
      if (item instanceof Map<?, ?> itemMap) {
        return toBeanOrRecord(toStringObjectMap(itemMap), clazz);
      }
      return item;
    }
    if (targetType instanceof ParameterizedType pt) {
      // 嵌套泛型：List<List<T>> / Map<K, List<V>> 等
      if (pt.getRawType() == List.class && item instanceof List<?> nestedList) {
        return convertListWithType(nestedList, pt.getActualTypeArguments()[0]);
      }
      if (pt.getRawType() == Map.class && item instanceof Map<?, ?> nestedMap) {
        return convertMapWithType(nestedMap, pt.getActualTypeArguments()[1]);
      }
    }
    return item;
  }

  // ==================== Record 支持 ====================

  /**
   * 将 Map 转换为 Java Record（不可变对象）。
   *
   * <p>Record 没有无参构造器，需要通过全参构造器实例化。本方法自动提取 RecordComponent 并按照参数顺序从 Map 中取值（支持驼峰/下划线命名互换）。
   *
   * <pre>{@code
   * public record Point(double x, double y) {}
   *
   * Map<String, Object> data = Map.of("x", 1.0, "y", 2.0);
   * Point point = BeanMapper.toBean(data, Point.class); // 自动使用全参构造器
   * }</pre>
   *
   * <p>自动检测 Record 类型，优先尝试全参构造器；如果不是 Record 则退化为 setter 模式。
   *
   * @param map 源 Map
   * @param clazz 目标类型（Record 或 POJO）
   * @param <T> 目标类型
   * @return 填充后的实例
   * @since 4.0.0
   */
  public static <T> T instantiateRecord(Map<String, Object> map, Class<T> clazz) {
    RecordComponent[] components = clazz.getRecordComponents();
    Class<?>[] paramTypes = new Class[components.length];
    Object[] args = new Object[components.length];

    for (int i = 0; i < components.length; i++) {
      paramTypes[i] = components[i].getType();
      String name = components[i].getName();
      Object rawValue = map.get(name);
      // 命名兼容：组件名为 camelCase，Map key 可能为 snake_case（或相反）
      if (rawValue == null) {
        rawValue = map.get(StringUtils.toUnderScoreCase(name));
      }
      if (rawValue == null) {
        rawValue = map.get(StringUtils.toCamelCase(name));
      }
      Type genericType = components[i].getGenericType();
      args[i] =
          (rawValue != null) ? convertComponentValue(rawValue, paramTypes[i], genericType) : null;
    }

    try {
      Constructor<T> constructor = clazz.getDeclaredConstructor(paramTypes);
      return constructor.newInstance(args);
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(
          "Record " + clazz.getName() + " missing canonical constructor", e);
    } catch (ReflectiveOperationException e) {
      throw new IllegalArgumentException(
          "Failed to create record " + clazz.getName() + ": " + e.getMessage(), e);
    }
  }

  /**
   * 转换 Record 组件值到目标类型（支持嵌套 Bean、List、Optional）。
   *
   * @param value 原始值
   * @param paramType 目标参数类型
   * @param genericType 泛型类型
   * @return 转换后的值
   * @since 4.0.0
   */
  public static Object convertComponentValue(Object value, Class<?> paramType, Type genericType) {
    // 类型完全匹配
    if (paramType.isInstance(value)) return value;

    // Optional 解包
    if (paramType == Optional.class) {
      if (genericType instanceof ParameterizedType pt) {
        Type innerType = pt.getActualTypeArguments()[0];
        if (value instanceof Map<?, ?> m) {
          if (innerType instanceof Class<?> clazz) {
            return Optional.of(toBeanOrRecord(toStringObjectMap(m), clazz));
          }
        }
      }
      return Optional.ofNullable(value);
    }

    // 嵌套 Record
    if (value instanceof Map<?, ?> m && paramType.isRecord()) {
      return instantiateRecord(toStringObjectMap(m), paramType);
    }

    // 嵌套 Bean
    if (value instanceof Map<?, ?> m && !paramType.isInterface()) {
      return toBeanOrRecord(toStringObjectMap(m), paramType);
    }

    // 标准类型转换
    return convertValue(value, paramType, DEFAULT_DATE_FORMATTER, null);
  }

  // ==================== 其他工具方法 ====================

  /**
   * 转换为 Boolean
   *
   * <p>识别的真值：{@code "true"}、{@code "1"}、{@code "yes"}（大小写不敏感）。
   *
   * <p>识别的假值：{@code "false"}、{@code "0"}、{@code "no"}（大小写不敏感）。
   *
   * <p>其他值（包括无法解析的字符串）返回 {@code null}，以便调用方区分「假值」与「不可解析」。
   *
   * @param value 值
   * @return Boolean 值，不可解析返回 null
   */
  private static Boolean toBoolean(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Boolean) {
      return (Boolean) value;
    }
    String str = value.toString().toLowerCase();
    if ("true".equals(str) || "1".equals(str) || "yes".equals(str)) {
      return Boolean.TRUE;
    }
    if ("false".equals(str) || "0".equals(str) || "no".equals(str)) {
      return Boolean.FALSE;
    }
    return null;
  }

  /**
   * 将 {@code Map<?,?>} 安全转换为 {@code Map<String, Object>}。
   *
   * <p>用于 JSON 反序列化后 Map 的类型归一化：当 JSON 解析器返回 {@code Map<?, ?>}（如 FastJSON / Jackson 的默认行为）时，
   * 调用本方法将其转换为 {@code Map<String, Object>} 以便业务使用。
   *
   * @param map 原始 Map（可为 null）
   * @return 转换后的 Map；入参为 null 时返回空 Map
   */
  private static Map<String, Object> toStringObjectMap(Map<?, ?> map) {
    if (map == null) {
      return new LinkedHashMap<>();
    }
    Map<String, Object> result = new LinkedHashMap<>(map.size());
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      result.put(String.valueOf(entry.getKey()), entry.getValue());
    }
    return result;
  }
}
