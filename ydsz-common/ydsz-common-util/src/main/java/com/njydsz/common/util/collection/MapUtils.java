package com.njydsz.common.util.collection;

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
 * Map 工具类
 *
 * <p>聚焦于 JSON Map 解析场景下的类型安全读取与归一化，提供 null 安全的取值方法。 典型用途：JSON 反序列化后得到 {@code Map<String, Object>} 或
 * {@code Map<?, ?>}， 调用本类方法按 key 安全取出 String / Integer / Long / Boolean / Map / List 值。
 *
 * <p><b>主要功能：</b>
 *
 * <ul>
 *   <li>判空检查：isEmpty / isNotEmpty（null 安全）
 *   <li>类型安全取值：getString / getInteger / getLong / getBoolean / getMap / getList
 *   <li>JSON Map 归一化：toStringObjectMap / safeCastMap / safeCastList
 *   <li>嵌套 JSON 解析：getListOfMaps / getMapFromList
 *   <li>Map 转 Bean：toBean / toBeanOrRecord（委托 {@link BeanMapper}）
 *   <li>命名转换：snakeToCamel / camelToSnake
 * </ul>
 *
 * <p><b>不提供的能力（直接使用 JDK / Stream API）：</b>
 *
 * <ul>
 *   <li>Map 创建 → {@code new HashMap<>()} / {@code new LinkedHashMap<>()} / {@link Map#of(Object,
 *       Object)}
 *   <li>Map 转换/过滤 → {@link java.util.Map#replaceAll(java.util.function.BiFunction)} / stream
 *   <li>Map 合并 → {@link Map#merge(Object, Object, java.util.function.BiFunction)} / {@code new
 *       HashMap<>(m1) {{ putAll(m2); }} }
 *   <li>Map 排序 → {@link java.util.TreeMap} / stream + {@link java.util.LinkedHashMap}
 *   <li>Map 反转/扁平化/深拷贝 → stream 自行实现
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class MapUtils {

  private MapUtils() {
    throw new UnsupportedOperationException(
        "MapUtils is a utility class and cannot be instantiated");
  }

  // ==================== Bean 映射缓存与常量（原 BeanMapper 内联） ====================

  private static final Logger LOG = LoggerFactory.getLogger(MapUtils.class);

  private static final ConcurrentHashMap<Class<?>, Map<String, Method>> SETTER_CACHE =
      new ConcurrentHashMap<>();

  private static final ConcurrentHashMap<Method, BiConsumer<Object, Object>> SETTER_INVOKER_CACHE =
      new ConcurrentHashMap<>();

  private static final DateTimeFormatter DEFAULT_DATE_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  // ==================== 判空方法 ====================

  /**
   * 判断 Map 是否为空（null 安全）
   *
   * @param map Map 对象
   * @return 如果为 null 或 empty 返回 true
   */
  public static boolean isEmpty(Map<?, ?> map) {
    return map == null || map.isEmpty();
  }

  /**
   * 判断 Map 是否不为空（null 安全）
   *
   * @see #isEmpty(Map)
   * @param map 映射
   * @return 判断结果
   */
  public static boolean isNotEmpty(Map<?, ?> map) {
    return !isEmpty(map);
  }

  // ==================== 类型安全取值方法 ====================

  /**
   * 获取 String 类型值
   *
   * @param map Map 对象
   * @param key 键
   * @return String 值（调用 toString），map 为空或 key 不存在返回 null
   */
  public static String getString(Map<?, ?> map, Object key) {
    Object value = map != null ? map.get(key) : null;
    return value != null ? value.toString() : null;
  }

  /**
   * 获取 Integer 类型值
   *
   * @param map Map 对象
   * @param key 键
   * @return Integer 值，转换失败返回 null
   */
  public static Integer getInteger(Map<?, ?> map, Object key) {
    Object value = map != null ? map.get(key) : null;
    return toInteger(value);
  }

  /**
   * 获取 Long 类型值
   *
   * @param map Map 对象
   * @param key 键
   * @return Long 值，转换失败返回 null
   */
  public static Long getLong(Map<?, ?> map, Object key) {
    Object value = map != null ? map.get(key) : null;
    return toLong(value);
  }

  /**
   * 获取 Boolean 类型值
   *
   * @param map Map 对象
   * @param key 键
   * @return Boolean 值，转换失败返回 null
   */
  public static Boolean getBoolean(Map<?, ?> map, Object key) {
    Object value = map != null ? map.get(key) : null;
    return toBoolean(value);
  }

  /**
   * 获取 Map 类型值
   *
   * @param map Map 对象
   * @param key 键
   * @return Map 值，非 Map 类型返回 null
   */
  public static Map<?, ?> getMap(Map<?, ?> map, Object key) {
    Object value = map != null ? map.get(key) : null;
    return value instanceof Map ? (Map<?, ?>) value : null;
  }

  /**
   * 获取 List 类型值
   *
   * @param map Map 对象
   * @param key 键
   * @return List 值，非 List 类型返回 null
   */
  public static List<?> getList(Map<?, ?> map, Object key) {
    Object value = map != null ? map.get(key) : null;
    return value instanceof List ? (List<?>) value : null;
  }

  // ==================== JSON Map 归一化方法 ====================

  /**
   * 将 {@code Map<?,?>} 安全转换为 {@code Map<String, Object>}。
   *
   * <p>用于 JSON 反序列化后 Map 的类型归一化：当 JSON 解析器返回 {@code Map<?, ?>}（如 FastJSON / Jackson 的默认行为）时，
   * 调用本方法将其转换为 {@code Map<String, Object>} 以便业务使用。
   *
   * <p>会创建新的 LinkedHashMap 并逐条复制（类型安全）； 若需要深拷贝嵌套 Map 请使用 stream 自行实现。
   *
   * @param map 原始 Map（可为 null）
   * @return 转换后的 Map；入参为 null 时返回空 Map
   */
  public static Map<String, Object> toStringObjectMap(Map<?, ?> map) {
    if (map == null) {
      return new LinkedHashMap<>();
    }
    Map<String, Object> result = new LinkedHashMap<>(map.size());
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      result.put(String.valueOf(entry.getKey()), entry.getValue());
    }
    return result;
  }

  /**
   * 安全将 {@code Object} 强转为 {@code Map<String, Object>}。
   *
   * <p>典型场景：从 JSON Map 中按 key 取出一个 Object 字段（值为 {@code Map<?, ?>}），需要将其归一化为 {@code Map<String,
   * Object>}。
   *
   * @param obj 原始对象
   * @return 强转后的 Map；入参为 null 或非 Map 时返回 null
   */
  public static Map<String, Object> safeCastMap(Object obj) {
    if (!(obj instanceof Map<?, ?> raw)) {
      return null;
    }
    return toStringObjectMap(raw);
  }

  /**
   * 安全将 {@code Object} 强转为 {@code List<T>}。
   *
   * <p>典型场景：从 JSON Map 中按 key 取出一个 List 字段（值为 {@code List<?>} 或 {@code
   * List<Map<String,Object>>}），需要按元素类型逐个 cast。
   *
   * <p>入参为 null / 非 List 时返回空 List（不抛异常）。 元素类型不匹配时跳过该元素（不抛 ClassCastException）。
   *
   * <p>返回的 List 始终为可变 {@link ArrayList}（包括空 List 情况）， 调用方可以安全地进行增删操作。
   *
   * @param obj 原始对象
   * @param element 元素类型
   * @return 类型安全的可变 List
   * @param <T> 泛型参数类型
   */
  public static <T> List<T> safeCastList(Object obj, Class<T> element) {
    if (!(obj instanceof List<?> raw)) {
      return new ArrayList<>(16);
    }
    List<T> result = new ArrayList<>(raw.size());
    for (Object item : raw) {
      if (element.isInstance(item)) {
        result.add(element.cast(item));
      }
    }
    return result;
  }

  // ==================== 嵌套 JSON 解析方法 ====================

  /**
   * 从 Map 中按 key 获取 {@code List<Map<String, Object>>} 值。
   *
   * <p>用于解析嵌套 JSON Map：取出某个 key 对应的 List， 其中每个元素强制为 {@code Map<String, Object>}。 入参为 null / 非 List
   * / 元素非 Map 时返回空 List。
   *
   * @param map 原始 Map
   * @param key 键
   * @return List of Map；不可变空 List 表示取不到
   */
  public static List<Map<String, Object>> getListOfMaps(Map<String, Object> map, String key) {
    if (isEmpty(map) || key == null) {
      return List.of();
    }
    Object val = map.get(key);
    if (!(val instanceof List<?> raw)) {
      return List.of();
    }
    List<Map<String, Object>> result = new ArrayList<>(raw.size());
    for (Object item : raw) {
      if (item instanceof Map<?, ?> m) {
        result.add(toStringObjectMap(m));
      }
    }
    return result;
  }

  /**
   * 从 List 中按下标取出元素并转换为 {@code Map<String, Object>}。
   *
   * <p>典型场景：JSON 反序列化后得到 {@code List<?>}（如 BPMN 节点列表）， 需要按下标取出每个元素并归一化为 {@code Map<String, Object>}
   * 以便业务读取字段。
   *
   * <p>入参为 null / 下标越界 / 元素非 Map 时返回 null（不抛异常）。
   *
   * @param list 原始 List
   * @param index 元素下标
   * @return 强转后的 Map；取不到时返回 null
   */
  public static Map<String, Object> getMapFromList(List<?> list, int index) {
    if (list == null || index < 0 || index >= list.size()) {
      return null;
    }
    Object item = list.get(index);
    return safeCastMap(item);
  }

  // ==================== Bean 映射（原 BeanMapper 内联）====================

  /**
   * 将 {@code Map<String, Object>} 转换为指定类型的 Java Bean。
   *
   * <p>普通 Bean 基于 setter 反射绑定字段；Record 基于规范构造器绑定组件值。
   *
   * @param source 源 Map（String 键）
   * @param targetClass 目标类型
   * @param <T> 目标类型泛型
   * @return 转换后的对象；source 为 null 时返回 null
   * @since 26.09.01
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
   * <p>使用示例：
   *
   * <pre>{@code
   * List<User> users = MapUtils.toBean(rawList, new MapUtils.TypeReference<List<User>>() {});
   * Map<String, Order> orders = MapUtils.toBean(rawMap, new MapUtils.TypeReference<Map<String, Order>>() {});
   * }</pre>
   *
   * @param source 源数据（List 或 Map）
   * @param typeRef 泛型类型引用
   * @param <T> 目标类型泛型
   * @return 转换后的对象
   * @since 26.09.01
   */
  public static <T> T toBean(Object source, MapUtils.TypeReference<T> typeRef) {
    Objects.requireNonNull(typeRef, "typeRef must not be null");
    Type type = typeRef.getType();

    if (type instanceof ParameterizedType pt && pt.getRawType() == List.class) {
      if (!(source instanceof List<?> rawList)) {
        throw new IllegalArgumentException(
            "Expected List, got " + (source == null ? "null" : source.getClass()));
      }
      Type elementType = pt.getActualTypeArguments()[0];
      return (T) convertListWithType(rawList, elementType);
    }

    if (type instanceof ParameterizedType pt && pt.getRawType() == Map.class) {
      if (!(source instanceof Map<?, ?> rawMap)) {
        throw new IllegalArgumentException(
            "Expected Map, got " + (source == null ? "null" : source.getClass()));
      }
      Type valueType = pt.getActualTypeArguments()[1];
      return (T) convertMapWithType(rawMap, valueType);
    }

    if (type instanceof Class<?> clazz) {
      if (source instanceof Map<?, ?> rawMap) {
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
   * 将 Map 转换为指定类型的 Java Bean（普通 Bean 或 Record）。
   *
   * <p>自动检测 Record 类型：优先尝试全参构造器；否则退化为 setter 模式。
   *
   * @param map 源 Map
   * @param clazz 目标类型（Record 或 POJO）
   * @param <T> 目标类型泛型
   * @return 填充后的实例
   * @since 26.09.01
   */
  public static <T> T toBeanOrRecord(Map<String, Object> map, Class<T> clazz) {
    Objects.requireNonNull(map, "map must not be null");
    Objects.requireNonNull(clazz, "clazz must not be null");

    if (clazz.isRecord()) {
      return instantiateRecord(map, clazz);
    }
    return toBeanInternal(map, clazz);
  }

  // ==================== Bean 映射内部方法 ====================

  private static <T> T toBeanInternal(Map<String, Object> map, Class<T> targetClass) {
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
              SETTER_INVOKER_CACHE.computeIfAbsent(setter, MapUtils::createSetterInvoker);
          invoker.accept(bean, converted);
        } catch (Exception e) {
          // 设置失败（业务 setter 抛异常等），跳过该字段
        }
      }
    }
    return bean;
  }

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
      return (bean, value) -> {
        try {
          setter.invoke(bean, value);
        } catch (ReflectiveOperationException e) {
          throw new IllegalStateException("Failed to invoke setter " + setter.getName(), e);
        }
      };
    }
  }

  public static Map<String, Method> getCachedSetters(Class<?> clazz) {
    return SETTER_CACHE.computeIfAbsent(clazz, k -> scanSetters(clazz));
  }

  public static Map<String, Method> scanSetters(Class<?> clazz) {
    Map<String, Method> setterMap = new LinkedHashMap<>();
    Method[] methods = clazz.getMethods();
    for (Method method : methods) {
      if (!isSetter(method)) {
        continue;
      }
      String methodName = method.getName();
      String fieldName = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
      setterMap.put(fieldName, method);
    }
    return setterMap;
  }

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

  public static DateTimeFormatter getDefaultDateFormatter() {
    return DEFAULT_DATE_FORMATTER;
  }

  public static Object convertValue(Object value, Class<?> paramType, Method setter) {
    return convertValue(value, paramType, DEFAULT_DATE_FORMATTER, setter);
  }

  public static Object convertValue(
      Object value, Class<?> paramType, DateTimeFormatter dateFormatter, Method setter) {
    if (paramType.isInstance(value)) {
      return value;
    }

    if (paramType == Optional.class && setter != null) {
      return convertOptional(value, setter.getGenericParameterTypes()[0]);
    }

    String str = value.toString();
    if (str.isEmpty()) {
      return null;
    }

    try {
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
      if (paramType == double.class || paramType == Double.class) {
        return Double.valueOf(str);
      }
      if (paramType == float.class || paramType == Float.class) {
        return Float.valueOf(str);
      }
      if (paramType == boolean.class || paramType == Boolean.class) {
        Boolean b = toBoolean(value);
        return b != null ? b : null;
      }
      if (paramType == BigDecimal.class) {
        return new BigDecimal(str);
      }
      if (paramType == BigInteger.class) {
        return new BigInteger(str);
      }
      if (paramType == UUID.class) {
        return UUID.fromString(str);
      }
      if (paramType == YearMonth.class) {
        return YearMonth.parse(str);
      }
      if (paramType == Duration.class) {
        return Duration.parse(str);
      }
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
      if (paramType == Date.class) {
        LocalDateTime ldt = LocalDateTime.parse(str, dateFormatter);
        return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
      }
      if (paramType == String.class) {
        return str;
      }
    } catch (Exception e) {
      return null;
    }

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

    if (value instanceof List<?> rawList
        && List.class.isAssignableFrom(paramType)
        && setter != null) {
      return convertToList(rawList, setter, dateFormatter);
    }

    if (value instanceof Map<?, ?> rawMap
        && Map.class.isAssignableFrom(paramType)
        && setter != null) {
      return convertToMap(rawMap, setter);
    }

    return null;
  }

  public static Object convertOptional(Object value, Type optionalGenericType) {
    if (value == null) {
      return Optional.empty();
    }
    if (value instanceof Optional<?>) {
      return value;
    }
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
          result.add(item);
        }
      }
      return result;
    } catch (Exception e) {
      return rawList;
    }
  }

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

  public static <T> T createInstance(Class<T> clazz) {
    try {
      Constructor<T> constructor = clazz.getDeclaredConstructor();
      return constructor.newInstance();
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(
          "Class " + clazz.getName() + " 缺少无参构造器，无法通过 MapUtils.toBean 转换", e);
    } catch (ReflectiveOperationException e) {
      throw new IllegalArgumentException(
          "实例化失败: " + clazz.getName() + ", 原因: " + e.getMessage(), e);
    }
  }

  public static List<Object> convertListWithType(List<?> rawList, Type elementType) {
    List<Object> result = new ArrayList<>(rawList.size());
    for (Object item : rawList) {
      result.add(convertSingleItem(item, elementType));
    }
    return result;
  }

  public static Map<String, Object> convertMapWithType(Map<?, ?> rawMap, Type valueType) {
    Map<String, Object> result = new LinkedHashMap<>(rawMap.size());
    for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
      result.put(String.valueOf(entry.getKey()), convertSingleItem(entry.getValue(), valueType));
    }
    return result;
  }

  public static Object convertSingleItem(Object item, Type targetType) {
    if (item == null) {
      return null;
    }
    if (targetType instanceof Class<?> clazz) {
      if (clazz.isInstance(item)) {
        return item;
      }
      if (item instanceof Map<?, ?> itemMap) {
        return toBeanOrRecord(toStringObjectMap(itemMap), clazz);
      }
      return item;
    }
    if (targetType instanceof ParameterizedType pt) {
      if (pt.getRawType() == List.class && item instanceof List<?> nestedList) {
        return convertListWithType(nestedList, pt.getActualTypeArguments()[0]);
      }
      if (pt.getRawType() == Map.class && item instanceof Map<?, ?> nestedMap) {
        return convertMapWithType(nestedMap, pt.getActualTypeArguments()[1]);
      }
    }
    return item;
  }

  public static <T> T instantiateRecord(Map<String, Object> map, Class<T> clazz) {
    RecordComponent[] components = clazz.getRecordComponents();
    Class<?>[] paramTypes = new Class[components.length];
    Object[] args = new Object[components.length];

    for (int i = 0; i < components.length; i++) {
      paramTypes[i] = components[i].getType();
      String name = components[i].getName();
      Object rawValue = map.get(name);
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

  public static Object convertComponentValue(Object value, Class<?> paramType, Type genericType) {
    if (paramType.isInstance(value)) {
      return value;
    }

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

    if (value instanceof Map<?, ?> m && paramType.isRecord()) {
      return instantiateRecord(toStringObjectMap(m), paramType);
    }

    if (value instanceof Map<?, ?> m && !paramType.isInterface()) {
      return toBeanOrRecord(toStringObjectMap(m), paramType);
    }

    return convertValue(value, paramType, DEFAULT_DATE_FORMATTER, null);
  }

  /**
   * 泛型类型引用——用于捕获参数化类型信息，解决 Java 泛型擦除导致的运行时类型丢失。
   *
   * <p>用法：
   *
   * <pre>{@code
   * List<User> users = MapUtils.toBean(map.getList("users"), new MapUtils.TypeReference<List<User>>() {});
   * Map<String, Order> orders = MapUtils.toBean(map.getMap("orders"),
   *     new MapUtils.TypeReference<Map<String, Order>>() {});
   * }</pre>
   *
   * @param <T> 目标泛型类型
   * @since 26.09.01
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

    public Type getType() {
      return type;
    }

    public Class<T> getRawType() {
      if (type instanceof Class<?> c) {
        return (Class<T>) c;
      }
      if (type instanceof ParameterizedType pt) {
        return (Class<T>) pt.getRawType();
      }
      return (Class<T>) Object.class;
    }
  }

  // ==================== 命名转换方法 ====================

  /**
   * 下划线命名（snake_case）转驼峰命名（camelCase）。
   *
   * <p>示例：{@code user_name} → {@code userName}，{@code order_item_id} → {@code orderItemId}。
   *
   * @param snake 下划线命名字符串
   * @return 驼峰命名字符串；入参为 null 时返回 null
   * @since 26.09.01
   */
  public static String snakeToCamel(String snake) {
    if (snake == null || snake.isEmpty()) {
      return snake;
    }
    StringBuilder sb = new StringBuilder(snake.length());
    boolean upperNext = false;
    for (int i = 0; i < snake.length(); i++) {
      char c = snake.charAt(i);
      if (c == '_') {
        upperNext = true;
      } else if (upperNext) {
        sb.append(Character.toUpperCase(c));
        upperNext = false;
      } else {
        sb.append(Character.toLowerCase(c));
      }
    }
    return sb.toString();
  }

  /**
   * 驼峰命名（camelCase）转下划线命名（snake_case）。
   *
   * <p>示例：{@code userName} → {@code user_name}，{@code orderItemId} → {@code order_item_id}。
   *
   * @param camel 驼峰命名字符串
   * @return 下划线命名字符串（小写）；入参为 null 时返回 null
   * @since 26.09.01
   */
  public static String camelToSnake(String camel) {
    if (camel == null || camel.isEmpty()) {
      return camel;
    }
    StringBuilder sb = new StringBuilder(camel.length() + 4);
    for (int i = 0; i < camel.length(); i++) {
      char c = camel.charAt(i);
      if (Character.isUpperCase(c)) {
        if (i > 0) {
          sb.append('_');
        }
        sb.append(Character.toLowerCase(c));
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  // ==================== 类型转换辅助方法 ====================

  /**
   * 转换为 Integer
   *
   * @param value 值
   * @return Integer 值，转换失败返回 null
   */
  private static Integer toInteger(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Integer) {
      return (Integer) value;
    }
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    try {
      return Integer.valueOf(value.toString());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * 转换为 Long
   *
   * @param value 值
   * @return Long 值，转换失败返回 null
   */
  private static Long toLong(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Long) {
      return (Long) value;
    }
    if (value instanceof Number) {
      return ((Number) value).longValue();
    }
    try {
      return Long.valueOf(value.toString());
    } catch (NumberFormatException e) {
      return null;
    }
  }

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
}
