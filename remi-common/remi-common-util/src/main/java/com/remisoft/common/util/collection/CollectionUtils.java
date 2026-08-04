package com.remisoft.common.util.collection;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 集合工具类
 *
 * <p>提供项目高频使用的集合操作方法，聚焦于 JDK 未覆盖的能力：
 * <ul>
 *   <li>判空检查：isEmpty / isNotEmpty（支持 Collection、Map、Iterable，null 安全）</li>
 *   <li>类型转换：listToMap、listToGroup、convertList（null 安全的 stream 包装）</li>
 *   <li>过滤操作：filter（null 安全）</li>
 *   <li>查找操作：findFirst、findLast（null 安全，findLast 对 List 做了优化）</li>
 * </ul>
 *
 * <p><b>不提供的能力（直接使用 JDK / Stream API）：</b>
 * <ul>
 *   <li>集合创建 → {@code new ArrayList<>()} / {@link Collections#emptyList()} / {@link List#of(Object[])}</li>
 *   <li>集合运算（并集/交集/差集）→ {@link java.util.HashSet} + stream</li>
 *   <li>元素增删 → {@link Collection#add(Object)} / {@link Collection#remove(Object)} / {@link Collection#removeAll(Collection)}</li>
 *   <li>集合切片 → {@link List#subList(int, int)} / {@link java.util.stream.Stream#limit(long)} / {@link java.util.stream.Stream#skip(long)}</li>
 *   <li>统计方法 → {@link java.util.stream.Stream#max(java.util.Comparator)} / {@link java.util.stream.IntStream#sum()}</li>
 *   <li>并行流 → {@link Collection#parallelStream()}</li>
 *   <li>不可变/同步包装 → {@link Collections#unmodifiableList(List)} / {@link Collections#synchronizedList(List)}</li>
 *   <li>包含判断 → {@link Collection#contains(Object)} / {@link Collection#containsAll(Collection)}</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
public class CollectionUtils {

    private CollectionUtils() {
        throw new UnsupportedOperationException("CollectionUtils is a utility class and cannot be instantiated");
    }

    // ==================== 判空方法 ====================

    /**
     * 判断集合是否为空（null 安全）
     *
     * @param collection 待判断的集合
     * @return 如果为 null 或不包含任何元素返回 true
     */
    public static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * 判断 Map 是否为空（null 安全）
     *
     * @param map 待判断的映射
     * @return 如果为 null 或不包含任何键值对返回 true
     */
    public static boolean isEmpty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    /**
     * 判断 Iterable 是否为空（null 安全）
     *
     * @param iterable 待判断的可迭代对象
     * @return 如果为 null 或没有元素返回 true
     */
    public static boolean isEmpty(Iterable<?> iterable) {
        if (iterable == null) {
            return true;
        }
        if (iterable instanceof Collection) {
            return ((Collection<?>) iterable).isEmpty();
        }
        return !iterable.iterator().hasNext();
    }

    /**
     * 判断集合是否不为空（null 安全）
     *
     * @see #isEmpty(Collection)
     */
    public static boolean isNotEmpty(Collection<?> collection) {
        return !isEmpty(collection);
    }

    /**
     * 判断 Map 是否不为空（null 安全）
     *
     * @see #isEmpty(Map)
     */
    public static boolean isNotEmpty(Map<?, ?> map) {
        return !isEmpty(map);
    }

    /**
     * 判断 Iterable 是否不为空（null 安全）
     *
     * @see #isEmpty(Iterable)
     */
    public static boolean isNotEmpty(Iterable<?> iterable) {
        return !isEmpty(iterable);
    }

    // ==================== 类型转换方法 ====================

    /**
     * 将 List 转换为 Map（以指定字段取值作为键，元素本身作为值）
     *
     * <p>存在重复键时取第一个出现的元素。入参为空返回空 Map。
     *
     * @param list      待转换的列表
     * @param keyMapper 键提取函数
     * @return 转换后的 Map
     */
    public static <K, V> Map<K, V> listToMap(Collection<V> list, Function<? super V, ? extends K> keyMapper) {
        Objects.requireNonNull(keyMapper, "keyMapper must not be null");
        if (isEmpty(list)) {
            return Collections.emptyMap();
        }
        return list.stream().collect(Collectors.toMap(keyMapper, Function.identity(), (v1, v2) -> v1));
    }

    /**
     * 将 List 转换为 Map（自定义键和值映射）
     *
     * <p>存在重复键时取第一个出现的元素。入参为空返回空 Map。
     *
     * @param list        待转换的列表
     * @param keyMapper   键提取函数
     * @param valueMapper 值提取函数
     * @return 转换后的 Map
     */
    public static <K, V, T> Map<K, V> listToMap(Collection<T> list,
                                                  Function<? super T, ? extends K> keyMapper,
                                                  Function<? super T, ? extends V> valueMapper) {
        Objects.requireNonNull(keyMapper, "keyMapper must not be null");
        Objects.requireNonNull(valueMapper, "valueMapper must not be null");
        if (isEmpty(list)) {
            return Collections.emptyMap();
        }
        return list.stream().collect(Collectors.toMap(keyMapper, valueMapper, (v1, v2) -> v1));
    }

    /**
     * 将 List 按指定字段分组
     *
     * @param list       待分组的列表
     * @param classifier 分类函数
     * @return 分组后的 Map，入参为空返回空 Map
     */
    public static <K, V> Map<K, List<V>> listToGroup(Collection<V> list, Function<? super V, ? extends K> classifier) {
        Objects.requireNonNull(classifier, "classifier must not be null");
        if (isEmpty(list)) {
            return Collections.emptyMap();
        }
        return list.stream().collect(Collectors.groupingBy(classifier));
    }

    /**
     * 将 List 转换为另一种类型的 List（常用于 Entity 转 VO、DTO 转换）
     *
     * @param source 待转换的列表
     * @param mapper 转换函数
     * @return 转换后的列表，入参为空返回空 List
     */
    public static <T, R> List<R> convertList(Collection<T> source, Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        if (isEmpty(source)) {
            return Collections.emptyList();
        }
        return source.stream().map(mapper).collect(Collectors.toList());
    }

    // ==================== 过滤方法 ====================

    /**
     * 过滤集合（null 安全）
     *
     * @param source    待过滤的集合
     * @param predicate 过滤条件
     * @return 过滤后的列表，入参为空返回空 List
     */
    public static <T> List<T> filter(Collection<T> source, Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate must not be null");
        if (isEmpty(source)) {
            return Collections.emptyList();
        }
        return source.stream().filter(predicate).collect(Collectors.toList());
    }

    // ==================== 查找方法 ====================

    /**
     * 安全获取第一个元素（null 安全）
     *
     * @param collection 待查找的集合
     * @return 包含第一个元素的 Optional，集合为空返回空 Optional
     */
    public static <T> Optional<T> findFirst(Collection<T> collection) {
        if (isEmpty(collection)) {
            return Optional.empty();
        }
        return collection.stream().findFirst();
    }

    /**
     * 安全获取最后一个元素（null 安全，对 List 做了 O(1) 优化）
     *
     * @param collection 待查找的集合
     * @return 包含最后一个元素的 Optional，集合为空返回空 Optional
     */
    public static <T> Optional<T> findLast(Collection<T> collection) {
        if (isEmpty(collection)) {
            return Optional.empty();
        }
        if (collection instanceof List) {
            List<T> list = (List<T>) collection;
            return Optional.ofNullable(list.get(list.size() - 1));
        }
        return collection.stream().reduce((first, second) -> second);
    }
}
