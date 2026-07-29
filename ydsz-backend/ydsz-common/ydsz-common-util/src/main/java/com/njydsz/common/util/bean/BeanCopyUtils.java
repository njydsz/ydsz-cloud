package com.njydsz.common.util.bean;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;

import com.njydsz.common.util.BeanUpdateUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Bean 拷贝工具类
 *
 * <p>功能特性：
 * 1. PropertyDescriptor 缓存，避免重复反射
 * 2. 灵活的拷贝选项（忽略字段、null 值处理、自定义转换器）
 * 3. 支持集合和数组映射
 * 4. 提供 Lambda 表达式支持
 * 5. 异常统一抛出 BeanCopyException
 * </p>
 *
 * <p><b>注意：</b>深拷贝请使用序列化方式（如 {@link Cloneable} 或 JSON 序列化/反序列化）。
 * Map ↔ Bean 转换请使用 {@code YdszJson.parseMap} / {@code YdszJson.toJson}。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
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
     * 缓存最大容量，超过后触发 LRU 淘汰最旧条目（防止内存泄漏）
     */
    private static final int MAX_CACHE_SIZE = 1024;

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

    // ==================== 集合转换方法 ====================

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
    @SuppressWarnings("unchecked")
    public static <T> T[] coverArray(Object[] source, Class<T> clazz) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(clazz, "targetClass must not be null");
        if (source.length == 0) {
            return (T[]) java.lang.reflect.Array.newInstance(clazz, 0);
        }
        Object[] temp = new Object[source.length];
        for (int i = 0; i < source.length; i++) {
            temp[i] = copyProperties(source[i], clazz);
        }
        return Arrays.copyOf(temp, source.length, (Class<? extends T[]>) clazz.arrayType());
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 忽略 null 值的拷贝实现
     *
     * <p>委托给 {@link com.njydsz.common.util.BeanUpdateUtil#copyNonNull} 统一实现，
     * 消除两个工具类之间的功能重叠。BeanUpdateUtil 是「PATCH 语义部分更新」的单一职责入口。
     */
    private static void copyPropertiesWithIgnoreNull(Object source, Object target) {
        BeanUpdateUtil.copyNonNull(source, target);
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
