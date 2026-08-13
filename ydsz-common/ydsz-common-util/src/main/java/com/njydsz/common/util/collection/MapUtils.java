package com.njydsz.common.util.collection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * @author ydsz-team
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
     * 使用 {@link Class} 对象本身作为 key，避免不同 ClassLoader 下同名类串缓存；
     * 使用 ConcurrentHashMap 保证并发安全，computeIfAbsent 保证单线程初始化。
     * （Spring Boot 应用无类热卸载场景，以 Class 为 key 不会造成类泄露。）
     */
    private static final ConcurrentHashMap<Class<?>, Map<String, Method>> SETTER_CACHE = new ConcurrentHashMap<>();

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
     * @deprecated 自 4.0.0 起标记废弃（forRemoval=true）。本方法仅支持 setter 注入的 POJO，无法处理：
     *             <ul>
     *               <li>Java Record（无 setter）→ 使用 {@link #toBeanOrRecord(Map, Class)}</li>
     *               <li>泛型集合（如 {@code List<T>}）→ 使用 JSON 框架（推荐 Fastjson2 / Jackson）</li>
     *             </ul>
     *             迁移示例：
     *             <pre>{@code
     *             // 旧：反射 toBean
     *             UserDO user = MapUtils.toBean(map, UserDO.class);
     *             // 新：JSON 框架（推荐）
     *             UserDO user = YdszJson.toJavaObject(map, UserDO.class);
     *             // 或：Record 自动检测
     *             UserDO user = MapUtils.toBeanOrRecord(map, UserDO.class);
     *             }</pre>
     */
    @Deprecated(since = "4.0.0", forRemoval = true)
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
            Object converted = convertValue(value, paramType, setter);
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
        return SETTER_CACHE.computeIfAbsent(clazz, k -> scanSetters(clazz));
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
     * 同时过滤桥接方法（{@link Method#isBridge()}），避免泛型类型擦除导致的重复 setter 条目。
     *
     * @param method 方法对象
     * @return 是否为 setter
     */
    private static boolean isSetter(Method method) {
        if (method == null) {
            return false;
        }
        // 过滤桥接方法：泛型类型擦除后编译器会合成一个返回值为原始类型（如 Object）的桥接 setter，
        // 若不过滤会导致缓存中同一个字段名出现两个 setter（泛型版 + 桥接版）。
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
     * @deprecated 自 4.0.0 起标记废弃（forRemoval=true）。请使用 {@link #toBeanOrRecord(Map, Class)} 替代
     *             或基于 JSON 框架（Fastjson2 / Jackson）进行类型转换。
     */
    @Deprecated(since = "4.0.0", forRemoval = true)
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
            Object converted = convertValue(value, paramType, dateFormatter, setter);
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
     * <p>新增支持（3.0.0）：
     * <ul>
     *   <li>{@code Optional<T>} 字段：自动解包后包装为 Optional</li>
     *   <li>{@code UUID} 类型：通过 UUID.fromString 解析</li>
     *   <li>{@code Duration} 类型：通过 Duration.parse 解析</li>
     *   <li>{@code YearMonth} 类型：通过 YearMonth.parse 解析</li>
     * </ul>
     */
    private static Object convertValue(Object value, Class<?> paramType,
                                       java.time.format.DateTimeFormatter dateFormatter,
                                       Method setter) {
        if (paramType.isInstance(value)) {
            return value;
        }

        // 3.0.0: Optional<T> 字段支持 — 从 setter 泛型提取 T
        if (paramType == java.util.Optional.class && setter != null) {
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
            if (paramType == java.math.BigDecimal.class) {
                return new java.math.BigDecimal(str);
            }
            if (paramType == java.math.BigInteger.class) {
                return new java.math.BigInteger(str);
            }
            // 3.0.0: 新增 JDK 常用业务类型
            if (paramType == java.util.UUID.class) {
                return java.util.UUID.fromString(str);
            }
            if (paramType == java.time.YearMonth.class) {
                return java.time.YearMonth.parse(str);
            }
            if (paramType == java.time.Duration.class) {
                return java.time.Duration.parse(str);
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

        // List<T> 泛型字段处理：将 List<Object>（元素为 Map）转换为 List<T>
        if (value instanceof List<?> rawList && List.class.isAssignableFrom(paramType) && setter != null) {
            return convertToList(rawList, setter, dateFormatter);
        }

        return null;
    }

    /**
     * 转换 Optional<T> 字段：提取泛型参数 T，转换值后包装为 Optional。
     *
     * <p>若 value 为 null 则返回 {@code Optional.empty()}；
     * 若 value 本身是 Optional 则直接返回；
     * 否则提取泛型 T 后对 value 做类型转换并包装。
     *
     * @param value           原始值
     * @param optionalGenericType Optional 的泛型类型（如 Optional<User> 的 ParameterizedType）
     * @return 包装后的 Optional
     */
    private static Object convertOptional(Object value, Type optionalGenericType) {
        if (value == null) {
            return java.util.Optional.empty();
        }
        if (value instanceof java.util.Optional<?>) {
            return value;
        }
        // 提取 Optional<T> 中的 T
        if (optionalGenericType instanceof ParameterizedType pt) {
            Type innerType = pt.getActualTypeArguments()[0];
            if (innerType instanceof Class<?> clazz) {
                Object converted = (value instanceof Map<?, ?> m)
                        ? toBean(toStringObjectMap(m), clazz)
                        : convertValue(value, clazz, DEFAULT_DATE_FORMATTER, null);
                return java.util.Optional.ofNullable(converted);
            }
        }
        return java.util.Optional.ofNullable(value);
    }

    /**
     * 将 List<Object> 转换为 List<T>，通过 setter 的泛型参数提取元素类型。
     *
     * <p>若未能提取泛型参数或元素非 Map 类型，则直接返回原始 List。
     *
     * @param rawList   原始 List
     * @param setter    setter 方法（用于提取泛型参数）
     * @param formatter 日期格式化器
     * @return 转换后的 List；无法转换时返回原始 List
     * @since 2.2.0
     */
    private static Object convertToList(List<?> rawList, Method setter, java.time.format.DateTimeFormatter formatter) {
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
                        result.add(toBean(toStringObjectMap(itemMap), elementType, formatter));
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
     * 将值转换为目标类型（使用默认日期格式 {@code yyyy-MM-dd HH:mm:ss}）。
     *
     * @param value     原始值
     * @param paramType 目标参数类型
     * @return 转换后的值，转换失败返回 null（调用方会跳过）
     */
    private static Object convertValue(Object value, Class<?> paramType, Method setter) {
        return convertValue(value, paramType, DEFAULT_DATE_FORMATTER, setter);
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

    // ==================== 泛型 TypeReference 支持 ====================

    /**
     * 泛型类型引用——用于捕获参数化类型信息，解决 Java 泛型擦除导致的运行时类型丢失。
     *
     * <p>用法：
     * <pre>{@code
     *   List<User> users = MapUtils.toBean(map.getList("users"), new MapUtils.TypeReference<List<User>>() {});
     *   Map<String, Order> orders = MapUtils.toBean(map.getMap("orders"), new MapUtils.TypeReference<Map<String, Order>>() {});
     * }</pre>
     *
     * <p>实现原理：通过匿名子类的 {@code getGenericSuperclass()} 捕获 {@code ParameterizedType}，
     * 从而在运行时获取完整的泛型参数信息。
     *
     * @param <T> 目标泛型类型
     * @since 3.0.0
     */
    public abstract static class TypeReference<T> {
        private final Type type;

        protected TypeReference() {
            Type superClass = getClass().getGenericSuperclass();
            if (!(superClass instanceof ParameterizedType)) {
                throw new IllegalStateException("TypeReference must be created as anonymous subclass with type parameter");
            }
            this.type = ((ParameterizedType) superClass).getActualTypeArguments()[0];
        }

        /**
         * 获取完整的泛型类型（包含参数化信息）。
         */
        public Type getType() {
            return type;
        }

        /**
         * 获取原始类型（擦除泛型后的 Class）。
         */
        @SuppressWarnings("unchecked")
        public Class<T> getRawType() {
            if (type instanceof Class<?> c) return (Class<T>) c;
            if (type instanceof ParameterizedType pt) return (Class<T>) pt.getRawType();
            return (Class<T>) Object.class;
        }
    }

    /**
     * 泛型版 toBean，支持 List&lt;T&gt;、Map&lt;K,V&gt; 等参数化类型转换。
     *
     * <p>与 {@link #toBean(Map, Class)} 不同，本方法通过 {@link TypeReference} 捕获泛型信息，
     * 能正确处理集合元素类型。
     *
     * <p>使用示例：
     * <pre>{@code
     *   // List<User> 场景
     *   List<Map<String, Object>> rawList = getList(data, "users");
     *   List<User> users = MapUtils.toBean(rawList, new TypeReference<List<User>>() {});
     *
     *   // Map<String, Order> 场景
     *   Map<String, Object> rawMap = getMap(data, "orders");
     *   Map<String, Order> orders = MapUtils.toBean(rawMap, new TypeReference<Map<String, Order>>() {});
     * }</pre>
     *
     * @param source 源数据（List 或 Map）
     * @param typeRef 泛型类型引用
     * @return 转换后的对象
     * @since 3.0.0
     */
    @SuppressWarnings("unchecked")
    public static <T> T toBean(Object source, TypeReference<T> typeRef) {
        Objects.requireNonNull(typeRef, "typeRef must not be null");
        Type type = typeRef.getType();

        // List<T>
        if (type instanceof ParameterizedType pt && pt.getRawType() == List.class) {
            if (!(source instanceof List<?> rawList)) {
                throw new IllegalArgumentException("Expected List, got " + (source == null ? "null" : source.getClass()));
            }
            Type elementType = pt.getActualTypeArguments()[0];
            return (T) convertListWithType(rawList, elementType);
        }

        // Map<K, V>
        if (type instanceof ParameterizedType pt && pt.getRawType() == Map.class) {
            if (!(source instanceof Map<?, ?> rawMap)) {
                throw new IllegalArgumentException("Expected Map, got " + (source == null ? "null" : source.getClass()));
            }
            Type valueType = pt.getActualTypeArguments()[1];
            return (T) convertMapWithType(rawMap, valueType);
        }

        // 非参数化类型，退化为 Class 版本
        if (type instanceof Class<?> clazz) {
            if (source instanceof Map<?, ?> rawMap) {
                // 显式强转 Class<?> 为 Class<T>，避免 javac 泛型推断失败（等式约束 capture 与 T 冲突）
                @SuppressWarnings("unchecked")
                Class<T> target = (Class<T>) clazz;
                return toBean(toStringObjectMap(rawMap), target);
            }
            if (clazz.isInstance(source)) {
                return (T) source;
            }
            throw new IllegalArgumentException("Cannot convert " + source.getClass() + " to " + clazz);
        }

        throw new IllegalArgumentException("Unsupported type: " + type);
    }

    /**
     * 将 List 转换为 List&lt;T&gt;，通过 Type 而非 Class 处理元素类型（支持嵌套泛型）。
     */
    private static List<Object> convertListWithType(List<?> rawList, Type elementType) {
        List<Object> result = new ArrayList<>(rawList.size());
        for (Object item : rawList) {
            result.add(convertSingleItem(item, elementType));
        }
        return result;
    }

    /**
     * 将 Map 转换为 Map&lt;String, V&gt;，value 类型由 Type 指定。
     */
    private static Map<String, Object> convertMapWithType(Map<?, ?> rawMap, Type valueType) {
        Map<String, Object> result = new LinkedHashMap<>(rawMap.size());
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            result.put(String.valueOf(entry.getKey()), convertSingleItem(entry.getValue(), valueType));
        }
        return result;
    }

    /**
     * 转换单个元素到目标类型（支持 Class、ParameterizedType）。
     */
    private static Object convertSingleItem(Object item, Type targetType) {
        if (item == null) return null;
        if (targetType instanceof Class<?> clazz) {
            if (clazz.isInstance(item)) return item;
            if (item instanceof Map<?, ?> itemMap) {
                return toBean(toStringObjectMap(itemMap), clazz);
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
     * <p>Record 没有无参构造器，需要通过全参构造器实例化。本方法自动提取
     * RecordComponent 并按照参数顺序从 Map 中取值（支持驼峰/下划线命名互换）。
     *
     * <pre>{@code
     *   public record Point(double x, double y) {}
     *
     *   Map<String, Object> data = Map.of("x", 1.0, "y", 2.0);
     *   Point point = MapUtils.toBean(data, Point.class); // 自动使用全参构造器
     * }</pre>
     *
     * <p>自动检测 Record 类型，优先尝试全参构造器；如果不是 Record 则退化为 setter 模式。
     *
     * @param map   源 Map
     * @param clazz 目标类型（Record 或 POJO）
     * @return 填充后的实例
     * @since 3.0.0
     */
    public static <T> T toBeanOrRecord(Map<String, Object> map, Class<T> clazz) {
        Objects.requireNonNull(map, "map must not be null");
        Objects.requireNonNull(clazz, "clazz must not be null");

        if (clazz.isRecord()) {
            return instantiateRecord(map, clazz);
        }
        return toBean(map, clazz);
    }

    /**
     * 通过 Record 全参构造器实例化。
     */
    @SuppressWarnings("unchecked")
    private static <T> T instantiateRecord(Map<String, Object> map, Class<T> clazz) {
        RecordComponent[] components = clazz.getRecordComponents();
        Class<?>[] paramTypes = new Class[components.length];
        Object[] args = new Object[components.length];

        for (int i = 0; i < components.length; i++) {
            paramTypes[i] = components[i].getType();
            String name = components[i].getName();
            Object rawValue = map.get(name);
            // 支持下划线命名自动转换：user_name → userName
            if (rawValue == null && name.contains("_")) {
                rawValue = map.get(snakeToCamel(name));
            } else if (rawValue == null && !name.equals(snakeToCamel(name))) {
                rawValue = map.get(camelToSnake(name));
            }
            Type genericType = components[i].getGenericType();
            args[i] = (rawValue != null) ? convertComponentValue(rawValue, paramTypes[i], genericType) : null;
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
     */
    private static Object convertComponentValue(Object value, Class<?> paramType, Type genericType) {
        // 类型完全匹配
        if (paramType.isInstance(value)) return value;

        // Optional 解包
        if (paramType == java.util.Optional.class) {
            if (genericType instanceof ParameterizedType pt) {
                Type innerType = pt.getActualTypeArguments()[0];
                if (value instanceof Map<?, ?> m) {
                    if (innerType instanceof Class<?> clazz) {
                        return java.util.Optional.of(toBean(toStringObjectMap(m), clazz));
                    }
                }
            }
            return java.util.Optional.ofNullable(value);
        }

        // 嵌套 Record
        if (value instanceof Map<?, ?> m && paramType.isRecord()) {
            return instantiateRecord(toStringObjectMap(m), paramType);
        }

        // 嵌套 Bean
        if (value instanceof Map<?, ?> m && !paramType.isInterface()) {
            return toBean(toStringObjectMap(m), paramType);
        }

        // 标准类型转换
        return convertValue(value, paramType, DEFAULT_DATE_FORMATTER, null);
    }

    // ==================== 命名转换辅助 ====================

    /**
     * 下划线命名转驼峰（user_name → userName）。
     */
    static String snakeToCamel(String snake) {
        if (snake == null || snake.isEmpty()) return snake;
        StringBuilder sb = new StringBuilder();
        boolean upperNext = false;
        for (char c : snake.toCharArray()) {
            if (c == '_') {
                upperNext = true;
            } else if (upperNext) {
                sb.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 驼峰命名转下划线（userName → user_name）。
     */
    static String camelToSnake(String camel) {
        if (camel == null || camel.isEmpty()) return camel;
        StringBuilder sb = new StringBuilder();
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) {
                sb.append('_').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
