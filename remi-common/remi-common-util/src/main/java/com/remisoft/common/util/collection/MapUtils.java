package com.remisoft.common.util.collection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Map 工具类
 *
 * <p>聚焦于 JSON Map 解析场景下的类型安全读取与归一化，提供 null 安全的取值方法。
 * 典型用途：JSON 反序列化后得到 {@code Map<String, Object>} 或 {@code Map<?, ?>}，
 * 调用本类方法按 key 安全取出 String / Integer / Long / Boolean / Map / List 值。
 *
 * <p><b>主要功能：</b>
 * <ul>
 *   <li>判空检查：isEmpty / isNotEmpty（null 安全）</li>
 *   <li>类型安全取值：getString / getInteger / getLong / getBoolean / getMap / getList</li>
 *   <li>JSON Map 归一化：toStringObjectMap / safeCastMap / safeCastList</li>
 *   <li>嵌套 JSON 解析：getListOfMaps / getMapFromList</li>
 *   <li>Map 转 Bean：toBean（基于 setter 反射，适配常见 JSON 反序列化后的字段绑定）</li>
 * </ul>
 *
 * <p><b>不提供的能力（直接使用 JDK / Stream API）：</b>
 * <ul>
 *   <li>Map 创建 → {@code new HashMap<>()} / {@code new LinkedHashMap<>()} / {@link Map#of(Object, Object)}</li>
 *   <li>Map 转换/过滤 → {@link java.util.Map#replaceAll(java.util.function.BiFunction)} / stream</li>
 *   <li>Map 合并 → {@link Map#merge(Object, Object, java.util.function.BiFunction)} / {@code new HashMap<>(m1) {{ putAll(m2); }}</code>}</li>
 *   <li>Map 排序 → {@link java.util.TreeMap} / stream + {@link java.util.LinkedHashMap}</li>
 *   <li>Map 反转/扁平化/深拷贝 → stream 自行实现</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
public class MapUtils {

    private MapUtils() {
        throw new UnsupportedOperationException("MapUtils is a utility class and cannot be instantiated");
    }

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
     * <p>用于 JSON 反序列化后 Map 的类型归一化：当 JSON 解析器返回
     * {@code Map<?, ?>}（如 FastJSON / Jackson 的默认行为）时，
     * 调用本方法将其转换为 {@code Map<String, Object>} 以便业务使用。
     *
     * <p>会创建新的 LinkedHashMap 并逐条复制（类型安全）；
     * 若需要深拷贝嵌套 Map 请使用 stream 自行实现。
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
     * <p>典型场景：从 JSON Map 中按 key 取出一个 Object 字段（值为
     * {@code Map<?, ?>}），需要将其归一化为 {@code Map<String, Object>}。
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
     * <p>典型场景：从 JSON Map 中按 key 取出一个 List 字段（值为
     * {@code List<?>} 或 {@code List<Map<String,Object>>}），需要按元素类型逐个 cast。
     *
     * <p>入参为 null / 非 List 时返回空 List（不抛异常）。
     * 元素类型不匹配时跳过该元素（不抛 ClassCastException）。
     *
     * <p>返回的 List 始终为可变 {@link ArrayList}（包括空 List 情况），
     * 调用方可以安全地进行增删操作。
     *
     * @param obj     原始对象
     * @param element 元素类型
     * @return 类型安全的可变 List
     */
    public static <T> List<T> safeCastList(Object obj, Class<T> element) {
        if (!(obj instanceof List<?> raw)) {
            return new ArrayList<>();
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
     * <p>用于解析嵌套 JSON Map：取出某个 key 对应的 List，
     * 其中每个元素强制为 {@code Map<String, Object>}。
     * 入参为 null / 非 List / 元素非 Map 时返回空 List。
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
     * <p>典型场景：JSON 反序列化后得到 {@code List<?>}（如 BPMN 节点列表），
     * 需要按下标取出每个元素并归一化为 {@code Map<String, Object>} 以便业务读取字段。
     *
     * <p>入参为 null / 下标越界 / 元素非 Map 时返回 null（不抛异常）。
     *
     * @param list  原始 List
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
     * <p>识别的假值：{@code "false"}、{@code "0"}、{@code "no"}（大小写不敏感）。
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

    // ==================== Map 转 Bean ====================

    /**
     * Setter 方法缓存，按 Class 维度索引，避免重复反射扫描。
     *
     * <p>缓存结构：Class → (字段名 → Method)。
     * 使用 ConcurrentHashMap 保证并发安全，computeIfAbsent 保证单线程初始化。
     */
    private static final ConcurrentHashMap<String, Map<String, Method>> SETTER_CACHE = new ConcurrentHashMap<>();

    /**
     * {@code java.time.*} 包类名前缀集合，用于 toBean 时区分日期类型。
     */
    private static final Set<String> JAVA_TIME_TYPES = new HashSet<>(Arrays.asList(
            "java.time.LocalDate",
            "java.time.LocalDateTime",
            "java.time.LocalTime",
            "java.time.Instant",
            "java.time.ZonedDateTime"
    ));

    /**
     * 将 {@code Map<String, Object>} 转换为指定类型的 Java Bean。
     *
     * <p><b>实现思路：</b>
     * <ol>
     *   <li>通过 targetClass.getDeclaredConstructor().newInstance() 创建 Bean 实例（要求无参构造器）</li>
     *   <li>扫描 targetClass 的所有 setter 方法（{@code setXxx(Type)}），按字段名与 Map key 匹配</li>
     *   <li>类型匹配时直接赋值；类型不匹配时尝试 String→目标类型 的基础转换（Integer/Long/Double/Boolean/LocalDateTime/Date）</li>
     *   <li>缓存每个 Class 的 setter 元数据，避免重复扫描（首次反射后命中率 100%）</li>
     * </ol>
     *
     * <p><b>类型转换规则：</b>
     * <ul>
     *   <li>{@code String} → {@code Integer / Long / Double / Float / Boolean}：调用 parseXxx 或 valueOf</li>
     *   <li>{@code String} → {@code LocalDateTime}：调用 {@code LocalDateTime.parse(text)}</li>
     *   <li>{@code String} → {@code LocalDate}：调用 {@code LocalDate.parse(text)}</li>
     *   <li>{@code String} → {@code java.util.Date}：按 ISO 格式解析后转 Date</li>
     *   <li>{@code Map} → 嵌套 Bean：递归调用 toBean</li>
     *   <li>类型不兼容且无法转换：跳过该字段（不抛异常）</li>
     * </ul>
     *
     * <p><b>典型用法：</b>
     * <pre>{@code
     * Map<String, Object> userData = Map.of(
     *     "name", "张三",
     *     "age", 25,
     *     "createTime", "2024-01-15 10:30:00"
     * );
     * UserDO user = MapUtils.toBean(userData, UserDO.class);
     * // user.getName() == "张三", user.getAge() == 25
     * }</pre>
     *
     * <p><b>注意事项：</b>
     * <ul>
     *   <li>不处理复杂泛型字段（如 {@code List<SubBean>}），需要时请配合专用 JSON 框架</li>
     *   <li>字段无 setter 时不会被赋值（不会直接写 Field）</li>
     *   <li>性能敏感场景（QPS > 10k）建议配合字节码生成框架（如 ReflectASM）或专用 BeanUtils</li>
     * </ul>
     *
     * @param map         源 Map，不可为 null
     * @param targetClass 目标 Bean 类型，不可为 null
     * @param <T>         Bean 类型
     * @return 填充后的 Bean 实例
     * @throws IllegalArgumentException 入参为 null、targetClass 无无参构造器、或实例化失败
     * @since 1.3.0
     */
    @SuppressWarnings("unchecked")
    public static <T> T toBean(Map<String, Object> map, Class<T> targetClass) {
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
            Method setter = setters.get(fieldName);
            if (setter == null || value == null) {
                continue;
            }
            Class<?> paramType = setter.getParameterTypes()[0];
            Object converted = convertValue(value, paramType);
            if (converted != null) {
                try {
                    setter.invoke(bean, converted);
                } catch (Exception e) {
                    // 设置失败（业务 setter 抛异常等），跳过该字段
                }
            }
        }
        return bean;
    }

    /**
     * 获取指定 Class 的 setter 方法缓存。
     *
     * @param clazz 目标类型
     * @return 字段名 → setter Method 映射（字段名采用原始 setter 名去除 set + 首字母小写）
     */
    private static Map<String, Method> getCachedSetters(Class<?> clazz) {
        String key = clazz.getName();
        return SETTER_CACHE.computeIfAbsent(key, k -> scanSetters(clazz));
    }

    /**
     * 扫描类的所有 public void setXxx(Type) 方法，提取字段名 → Method 映射。
     *
     * @param clazz 目标类型
     * @return 字段名 → setter 的不可变 Map
     */
    private static Map<String, Method> scanSetters(Class<?> clazz) {
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
     * <p>标准 setter：public、非 static、void 返回值、单参数、方法名以 set 开头。
     *
     * @param method 方法对象
     * @return 是否为 setter
     */
    private static boolean isSetter(Method method) {
        if (method == null) {
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
     * 将 {@code Map<String, Object>} 转换为指定类型的 Java Bean（可指定日期格式）。
     *
     * <p>与 {@link #toBean(Map, Class)} 行为完全一致，仅日期解析使用传入的
     * {@code dateFormatter} 代替默认的 {@code "yyyy-MM-dd HH:mm:ss"} 格式。
     *
     * @param map           源 Map，不可为 null
     * @param targetClass   目标 Bean 类型，不可为 null
     * @param dateFormatter 日期时间格式（用于 LocalDateTime / Date 字段的解析），不可为 null
     * @param <T>           Bean 类型
     * @return 填充后的 Bean 实例
     * @throws IllegalArgumentException 入参为 null 时抛出
     * @since 1.4.0
     */
    public static <T> T toBean(Map<String, Object> map, Class<T> targetClass,
                               java.time.format.DateTimeFormatter dateFormatter) {
        if (map == null) {
            throw new IllegalArgumentException("map cannot be null");
        }
        if (targetClass == null) {
            throw new IllegalArgumentException("targetClass cannot be null");
        }
        if (dateFormatter == null) {
            throw new IllegalArgumentException("dateFormatter cannot be null");
        }

        T bean = createInstance(targetClass);
        if (map.isEmpty()) {
            return bean;
        }

        Map<String, Method> setters = getCachedSetters(targetClass);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();
            Method setter = setters.get(fieldName);
            if (setter == null || value == null) {
                continue;
            }
            Class<?> paramType = setter.getParameterTypes()[0];
            Object converted = convertValue(value, paramType, dateFormatter);
            if (converted != null) {
                try {
                    setter.invoke(bean, converted);
                } catch (Exception e) {
                    // 设置失败，跳过该字段
                }
            }
        }
        return bean;
    }

    /**
     * 默认日期时间格式：{@code yyyy-MM-dd HH:mm:ss}
     */
    private static final java.time.format.DateTimeFormatter DEFAULT_DATE_FORMATTER =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 将值转换为目标类型（支持常见类型间的互转，使用指定日期格式）。
     *
     * @param value         原始值
     * @param paramType     目标参数类型
     * @param dateFormatter 日期时间格式
     * @return 转换后的值，转换失败返回 null
     */
    private static Object convertValue(Object value, Class<?> paramType,
                                       java.time.format.DateTimeFormatter dateFormatter) {
        if (paramType.isInstance(value)) {
            return value;
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
            if (paramType == java.math.BigDecimal.class) {
                return new java.math.BigDecimal(str);
            }
            if (paramType == java.math.BigInteger.class) {
                return new java.math.BigInteger(str);
            }
            // 日期时间类型
            if (paramType == java.time.LocalDateTime.class) {
                return java.time.LocalDateTime.parse(str, dateFormatter);
            }
            if (paramType == java.time.LocalDate.class) {
                return java.time.LocalDate.parse(str);
            }
            if (paramType == java.time.LocalTime.class) {
                return java.time.LocalTime.parse(str);
            }
            if (paramType == java.time.Instant.class) {
                return java.time.Instant.parse(str);
            }
            if (paramType == java.util.Date.class) {
                java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(str, dateFormatter);
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
        if (value instanceof Map<?, ?> nestedMap && !paramType.isInterface() && !Modifier.isAbstract(paramType.getModifiers())) {
            Map<String, Object> nestedStringMap = toStringObjectMap(nestedMap);
            try {
                return toBean(nestedStringMap, paramType, dateFormatter);
            } catch (Exception e) {
                return null;
            }
        }

        return null;
    }

    /**
     * 将值转换为目标类型（使用默认日期格式 {@code yyyy-MM-dd HH:mm:ss}）。
     *
     * @param value     原始值
     * @param paramType 目标参数类型
     * @return 转换后的值，转换失败返回 null（调用方会跳过）
     */
    private static Object convertValue(Object value, Class<?> paramType) {
        return convertValue(value, paramType, DEFAULT_DATE_FORMATTER);
    }

    /**
     * 通过无参构造器创建实例。
     *
     * @param clazz 目标类型
     * @param <T>   类型
     * @return 新实例
     * @throws IllegalArgumentException 无无参构造器或实例化失败
     */
    @SuppressWarnings("unchecked")
    private static <T> T createInstance(Class<T> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(
                    "Class " + clazz.getName() + " 缺少无参构造器，无法通过 MapUtils.toBean 转换", e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(
                    "实例化失败: " + clazz.getName() + ", 原因: " + e.getMessage(), e);
        }
    }
}
