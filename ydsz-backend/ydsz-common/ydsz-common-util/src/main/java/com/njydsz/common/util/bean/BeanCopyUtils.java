package com.njydsz.common.util.bean;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import lombok.extern.slf4j.Slf4j;

/**
 * Bean 拷贝工具类
 *
 * <p>功能特性：
 * 1. PropertyDescriptor 缓存，避免重复反射
 * 2. 循环引用检测，防止 StackOverflowError
 * 3. 灵活的拷贝选项（忽略字段、null 值处理、自定义转换器）
 * 4. 支持集合和数组映射
 * 5. 提供 Lambda 表达式支持
 * 6. 异常统一抛出 BeanCopyException
 * </p>
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */
@Slf4j
public class BeanCopyUtils {

    private BeanCopyUtils() {
        throw new UnsupportedOperationException("BeanCopyUtils is a utility class and cannot be instantiated");
    }

    /**
     * PropertyDescriptor 缓存，提升属性拷贝性能
     *
     * <p>使用 synchronized LinkedHashMap（accessOrder=true）实现 LRU 淘汰策略，
     * 避免全量清空导致缓存命中率骤降。
     */
    private static final Map<Class<?>, PropertyDescriptor[]> PROPERTY_CACHE =
            Collections.synchronizedMap(new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Class<?>, PropertyDescriptor[]> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            });

    /**
     * 字段缓存，提升 entityToMap 性能
     *
     * <p>使用 synchronized LinkedHashMap（accessOrder=true）实现 LRU 淘汰策略。
     */
    private static final Map<Class<?>, Field[]> FIELD_CACHE =
            Collections.synchronizedMap(new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Class<?>, Field[]> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            });

    /**
     * 缓存最大容量，超过后触发全量清空（防止内存泄漏）
     */
    private static final int MAX_CACHE_SIZE = 1024;

    /**
     * 可忽略的字段名（默认忽略 serialVersionUID）
     */
    private static final Set<String> DEFAULT_IGNORE_FIELDS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("serialVersionUID", "$jacocoData"))
    );

    // ==================== 基础拷贝方法 ====================

    /**
     * 浅拷贝 List
     *
     * @param source 数据源
     * @param clazz  目标类
     * @param <T>    目标泛型
     * @return 目标对象列表
     */
    public static <T> List<T> copyListProperties(List<?> source, Class<T> clazz) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(clazz, "targetClass must not be null");
        if (source.isEmpty()) {
            return new ArrayList<>(0);
        }
        List<T> target = new ArrayList<>(source.size());
        for (Object o : source) {
            T t = copyProperties(o, clazz);
            if (t != null) {
                target.add(t);
            }
        }
        return target;
    }

    /**
     * 源对象和目标对象浅拷贝
     *
     * @param sourceObj 源对象
     * @param targetObj 目标对象
     */
    public static void copyProperties(Object sourceObj, Object targetObj) {
        Objects.requireNonNull(sourceObj, "sourceObj must not be null");
        Objects.requireNonNull(targetObj, "targetObj must not be null");
        BeanUtils.copyProperties(sourceObj, targetObj);
    }

    /**
     * 拷贝属性并创建新对象
     *
     * @param sourceObj 源对象
     * @param clazz     目标类
     * @param <T>       目标泛型
     * @return 目标对象实例
     * @throws BeanCopyException 当拷贝失败时抛出
     */
    public static <T> T copyProperties(Object sourceObj, Class<T> clazz) {
        Objects.requireNonNull(sourceObj, "sourceObj must not be null");
        Objects.requireNonNull(clazz, "targetClass must not be null");
        try {
            T targetObj = clazz.getDeclaredConstructor().newInstance();
            copyProperties(sourceObj, targetObj);
            return targetObj;
        } catch (Exception e) {
            throw new BeanCopyException("Failed to copy properties for class " + clazz.getName(), e);
        }
    }

    // ==================== 带选项的拷贝方法 ====================

    /**
     * 带选项的拷贝（忽略 null 值）
     *
     * @param source     源对象
     * @param target     目标对象
     * @param ignoreNull 是否忽略 null 值
     */
    public static void copyProperties(Object source, Object target, boolean ignoreNull) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(target, "target must not be null");
        if (ignoreNull) {
            copyPropertiesWithIgnoreNull(source, target);
        } else {
            copyProperties(source, target);
        }
    }

    /**
     * 带选项的拷贝（忽略指定字段）
     *
     * @param source           源对象
     * @param target           目标对象
     * @param ignoreProperties 要忽略的字段名数组
     */
    public static void copyProperties(Object source, Object target, String... ignoreProperties) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(target, "target must not be null");
        BeanUtils.copyProperties(source, target, ignoreProperties);
    }

    /**
     * 带选项的拷贝（综合选项）
     *
     * @param source  源对象
     * @param target  目标对象
     * @param options 拷贝选项
     */
    public static void copyProperties(Object source, Object target, BeanCopyOptions options) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(target, "target must not be null");
        if (options == null) {
            copyProperties(source, target);
            return;
        }

        copyPropertiesWithOptions(source, target, options);
    }

    /**
     * 带选项的拷贝并创建新对象
     *
     * @param source  源对象
     * @param clazz   目标类
     * @param options 拷贝选项
     * @param <T>     目标泛型
     * @return 目标对象实例
     * @throws BeanCopyException 当拷贝失败时抛出
     */
    public static <T> T copyProperties(Object source, Class<T> clazz, BeanCopyOptions options) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(clazz, "targetClass must not be null");
        try {
            T target = clazz.getDeclaredConstructor().newInstance();
            copyProperties(source, target, options);
            return target;
        } catch (Exception e) {
            throw new BeanCopyException("Failed to copy properties with options for class " + clazz.getName(), e);
        }
    }

    // ==================== 带循环引用检测的深拷贝 ====================

    /**
     * 深拷贝（支持循环引用检测）
     *
     * @param source 源对象
     * @param <T>    目标泛型
     * @return 深拷贝后的对象
     * @throws BeanCopyException 当拷贝失败时抛出
     */
    public static <T> T deepCopy(T source) {
        if (source == null) {
            return null;
        }
        IdentityHashMap<Object, Object> visited = new IdentityHashMap<>();
        return deepCopyInternal(source, visited);
    }

    private static <T> T deepCopyInternal(T source, IdentityHashMap<Object, Object> visited) {
        if (source == null) {
            return null;
        }

        if (visited.containsKey(source)) {
            return (T) visited.get(source);
        }

        Class<?> clazz = source.getClass();
        if (clazz.isPrimitive() || clazz == String.class
                || Number.class.isAssignableFrom(clazz)
                || Boolean.class == clazz
                || Character.class == clazz
                || Enum.class.isAssignableFrom(clazz)) {
            return source;
        }

        if (clazz.isArray()) {
            return deepCopyArray(source, visited);
        }

        try {
            T target = castToT(clazz.getDeclaredConstructor().newInstance());
            visited.put(source, target);

            PropertyDescriptor[] props = computeIfAbsentBounded(PROPERTY_CACHE, clazz, BeanUtils::getPropertyDescriptors);
            for (PropertyDescriptor pd : props) {
                if (pd.getReadMethod() == null || pd.getWriteMethod() == null) {
                    continue;
                }
                String name = pd.getName();
                if ("class".equals(name) || DEFAULT_IGNORE_FIELDS.contains(name)) {
                    continue;
                }

                Object value = pd.getReadMethod().invoke(source);
                if (value == null) {
                    continue;
                }

                if (Collection.class.isAssignableFrom(value.getClass())) {
                    Collection<?> srcCollection = (Collection<?>) value;
                    Collection<Object> targetCollection = createCollection(value.getClass());
                    for (Object item : srcCollection) {
                        targetCollection.add(deepCopyInternal(item, visited));
                    }
                    pd.getWriteMethod().invoke(target, targetCollection);
                } else if (Map.class.isAssignableFrom(value.getClass())) {
                    Map<?, ?> srcMap = (Map<?, ?>) value;
                    Map<Object, Object> targetMap = createMap(value.getClass());
                    for (Map.Entry<?, ?> entry : srcMap.entrySet()) {
                        targetMap.put(
                                deepCopyInternal(entry.getKey(), visited),
                                deepCopyInternal(entry.getValue(), visited)
                        );
                    }
                    pd.getWriteMethod().invoke(target, targetMap);
                } else {
                    pd.getWriteMethod().invoke(target, deepCopyInternal(value, visited));
                }
            }

            return target;
        } catch (Exception e) {
            throw new BeanCopyException("Failed to deep copy class " + clazz.getName(), e);
        }
    }

    private static Collection<Object> createCollection(Class<?> clazz) {
        if (List.class.isAssignableFrom(clazz)) {
            return new ArrayList<>();
        } else if (Set.class.isAssignableFrom(clazz)) {
            return new HashSet<>();
        }
        return new ArrayList<>();
    }

    /** 内部辅助方法：安全转换对象到泛型类型 T（深拷贝场景使用） */
    private static <T> T castToT(Object obj) {
        return (T) obj;
    }

    private static Map<Object, Object> createMap(Class<?> clazz) {
        if (LinkedHashMap.class.isAssignableFrom(clazz)) {
            return new LinkedHashMap<>();
        } else if (TreeMap.class.isAssignableFrom(clazz)) {
            return new TreeMap<>();
        }
        return new HashMap<>();
    }

    /**
     * 深拷贝数组
     */
    private static <T> T deepCopyArray(T source, IdentityHashMap<Object, Object> visited) {
        if (source == null) {
            return null;
        }

        if (visited.containsKey(source)) {
            return castToT(visited.get(source));
        }

        Class<?> componentType = source.getClass().getComponentType();
        int length = Array.getLength(source);
        Object newArray = Array.newInstance(componentType, length);
        visited.put(source, newArray);

        if (componentType.isPrimitive() || componentType == String.class
                || Number.class.isAssignableFrom(componentType)
                || Boolean.class == componentType
                || Character.class == componentType
                || Enum.class.isAssignableFrom(componentType)) {
            System.arraycopy(source, 0, newArray, 0, length);
        } else {
            for (int i = 0; i < length; i++) {
                Object element = Array.get(source, i);
                Array.set(newArray, i, deepCopyInternal(element, visited));
            }
        }

        return castToT(newArray);
    }

    // ==================== Lambda 表达式支持 ====================

    /**
     * Lambda 表达式支持的拷贝方法（类型安全）
     *
     * @param source    源对象
     * @param target    目标对象
     * @param converter 自定义转换器（可选）
     */
    public static <S, T> void copyProperties(S source, T target, BiConsumer<S, T> converter) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(target, "target must not be null");
        copyProperties(source, target);
        if (converter != null) {
            converter.accept(source, target);
        }
    }

    /**
     * Lambda 表达式支持的拷贝方法（字段级转换）
     *
     * @param source         源对象
     * @param clazz          目标类
     * @param fieldConverter 字段转换器
     * @param <T>            目标泛型
     * @return 目标对象实例
     */
    public static <T> T copyProperties(Object source, Class<T> clazz,
                                       Function<Map<String, Object>, Map<String, Object>> fieldConverter) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(clazz, "targetClass must not be null");
        T target = copyProperties(source, clazz);
        if (target != null && fieldConverter != null) {
            Map<String, Object> sourceMap = entityToMap(source);
            Map<String, Object> convertedMap = fieldConverter.apply(sourceMap);
            copyMapToProperties(convertedMap, target);
        }
        return target;
    }

    // ==================== Map 相关方法 ====================

    /**
     * 将实体类转换为 Map
     *
     * @param object 实体对象
     * @return 包含字段名和值的 Map
     */
    public static Map<String, Object> entityToMap(Object object) {
        if (object == null) {
            return Collections.emptyMap();
        }
        return entityToMap(object, false);
    }

    /**
     * 将实体类转换为 Map
     *
     * @param object     实体对象
     * @param ignoreNull 是否忽略 null 值字段
     * @return 包含字段名和值的 Map
     */
    public static Map<String, Object> entityToMap(Object object, boolean ignoreNull) {
        if (object == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> resultMap = new HashMap<>();
        Class<?> clazz = object.getClass();

        Field[] fields = computeIfAbsentBounded(FIELD_CACHE, clazz, c -> {
            List<Field> allFields = new ArrayList<>();
            Class<?> current = c;
            while (current != null && current != Object.class) {
                allFields.addAll(Arrays.asList(current.getDeclaredFields()));
                current = current.getSuperclass();
            }
            return allFields.toArray(new Field[0]);
        });

        for (Field field : fields) {
            if (DEFAULT_IGNORE_FIELDS.contains(field.getName())) {
                continue;
            }
            try {
                if (!field.trySetAccessible()) {
                    continue;
                }
                Object value = field.get(object);
                if (ignoreNull && value == null) {
                    continue;
                }
                resultMap.put(field.getName(), value);
            } catch (IllegalAccessException e) {
                throw new BeanCopyException("Failed to access field " + field.getName() + " of class " + clazz.getName(), e);
            }
        }

        return resultMap;
    }

    /**
     * 将 Map 转换为实体类
     *
     * @param map   数据 Map
     * @param clazz 目标类
     * @param <T>   目标泛型
     * @return 目标对象实例
     * @throws BeanCopyException 当转换失败时抛出
     */
    public static <T> T mapToEntity(Map<String, Object> map, Class<T> clazz) {
        Objects.requireNonNull(map, "map must not be null");
        Objects.requireNonNull(clazz, "targetClass must not be null");
        if (map.isEmpty()) {
            return null;
        }
        try {
            T target = clazz.getDeclaredConstructor().newInstance();
            copyMapToProperties(map, target);
            return target;
        } catch (Exception e) {
            throw new BeanCopyException("Failed to convert map to entity for class " + clazz.getName(), e);
        }
    }

    /**
     * 将 Map 数据拷贝到对象
     *
     * @param map    数据 Map
     * @param target 目标对象
     */
    public static void copyMapToProperties(Map<String, Object> map, Object target) {
        Objects.requireNonNull(map, "map must not be null");
        Objects.requireNonNull(target, "target must not be null");
        BeanWrapper wrapper = new BeanWrapperImpl(target);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            try {
                wrapper.setPropertyValue(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                log.warn("【BeanCopyUtils】Map 属性拷贝失败 | property={} | error={}",
                        entry.getKey(), e.getMessage());
            }
        }
    }

    // ==================== 集合转换方法 ====================

    /**
     * 转换 List 泛型（别名，内部调用 copyListProperties）
     *
     * @param source 数据源
     * @param clazz  目标类
     * @return 目标对象列表
     */
    public static <T> List<T> coverList(List<?> source, Class<T> clazz) {
        return copyListProperties(source, clazz);
    }

    /**
     * 转换 Set 泛型
     *
     * @param source 数据源
     * @param clazz  目标类
     * @param <T>    目标泛型
     * @return 目标对象集合
     */
    public static <T> Set<T> coverSet(Set<?> source, Class<T> clazz) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(clazz, "targetClass must not be null");
        if (source.isEmpty()) {
            return new HashSet<>(0);
        }
        return source.stream()
                .map(s -> copyProperties(s, clazz))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * 转换数组
     *
     * @param source 数据源数组
     * @param clazz  目标类
     * @param <T>    目标泛型
     * @return 目标对象数组
     */
    public static <T> T[] coverArray(Object[] source, Class<T> clazz) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(clazz, "targetClass must not be null");
        if (source.length == 0) {
            return Arrays.copyOf(new Object[0], 0, getArrayClass(clazz));
        }
        Object[] temp = new Object[source.length];
        for (int i = 0; i < source.length; i++) {
            temp[i] = copyProperties(source[i], clazz);
        }
        return Arrays.copyOf(temp, source.length, getArrayClass(clazz));
    }

    private static <T> Class<? extends T[]> getArrayClass(Class<T> componentType) {
        return (Class<? extends T[]>) componentType.arrayType();
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 忽略 null 值的拷贝实现
     *
     * <p>使用缓存的 PropertyDescriptor 直接读写属性值，避免创建 Map 中间层和 BeanWrapper 实例。
     */
    private static void copyPropertiesWithIgnoreNull(Object source, Object target) {
        Class<?> sourceClass = source.getClass();
        PropertyDescriptor[] props = computeIfAbsentBounded(PROPERTY_CACHE, sourceClass, BeanUtils::getPropertyDescriptors);
        for (PropertyDescriptor pd : props) {
            if (pd.getReadMethod() == null || pd.getWriteMethod() == null) {
                continue;
            }
            String name = pd.getName();
            if ("class".equals(name) || DEFAULT_IGNORE_FIELDS.contains(name)) {
                continue;
            }
            try {
                Object value = pd.getReadMethod().invoke(source);
                if (value != null) {
                    pd.getWriteMethod().invoke(target, value);
                }
            } catch (Exception e) {
                log.warn("【BeanCopyUtils】属性拷贝失败 | property={} | error={}",
                        name, e.getMessage());
            }
        }
    }

    /**
     * 带选项的拷贝（使用 Spring BeanUtils）
     */
    private static void copyPropertiesWithOptions(Object source, Object target, BeanCopyOptions options) {
        switch (resolveCopyStrategy(options)) {
            case IGNORE_PROPERTIES:
                BeanUtils.copyProperties(source, target, options.getIgnoreProperties());
                break;
            case IGNORE_NULL:
                copyPropertiesWithIgnoreNull(source, target);
                break;
            default:
                BeanUtils.copyProperties(source, target);
                break;
        }

        if (options.getAfterCopyHandler() != null) {
            options.getAfterCopyHandler().accept(source, target);
        }
    }

    private enum CopyStrategy {
        FULL_COPY, IGNORE_PROPERTIES, IGNORE_NULL
    }

    /**
     * 根据选项解析拷贝策略
     */
    private static CopyStrategy resolveCopyStrategy(BeanCopyOptions options) {
        if (options.getIgnoreProperties() != null && options.getIgnoreProperties().length > 0) {
            return CopyStrategy.IGNORE_PROPERTIES;
        }
        if (options.isIgnoreNull()) {
            return CopyStrategy.IGNORE_NULL;
        }
        return CopyStrategy.FULL_COPY;
    }

    /**
     * 从缓存获取或计算值（LRU 淘汰策略已由 LinkedHashMap 自动管理）
     *
     * @param cache  缓存 Map
     * @param key    缓存 key
     * @param mapper 缓存值生成函数
     * @param <K>    key 类型
     * @param <V>    value 类型
     * @return 缓存值
     */
    private static <K, V> V computeIfAbsentBounded(Map<K, V> cache, K key, Function<K, V> mapper) {
        V existing = cache.get(key);
        if (existing != null) {
            return existing;
        }
        V value = mapper.apply(key);
        cache.put(key, value);
        return value;
    }

    /**
     * 清空缓存（用于测试或内存优化）
     */
    public static void clearCache() {
        FIELD_CACHE.clear();
        PROPERTY_CACHE.clear();
        log.info("BeanCopyUtils cache cleared");
    }

    /**
     * 获取缓存统计信息
     *
     * @return 缓存统计 Map
     */
    public static Map<String, Integer> getCacheStats() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("fieldCacheSize", FIELD_CACHE.size());
        stats.put("propertyCacheSize", PROPERTY_CACHE.size());
        return stats;
    }
}
