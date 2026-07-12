package com.njydsz.pmis.common.util;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 集合工具类
 *
 * <p>提供集合操作的通用方法，包括判空、转换、过滤、分组等。
 * 对标 remi-comm CollectionUtils，适配 PMIS 项目编码规范。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public final class CollectionUtils {

    private CollectionUtils() {
    }

    /**
     * 判断集合是否为空
     *
     * @param coll 集合
     * @return true 如果集合为 null 或空
     */
    public static boolean isEmpty(Collection<?> coll) {
        return coll == null || coll.isEmpty();
    }

    /**
     * 判断集合是否非空
     *
     * @param coll 集合
     * @return true 如果集合不为 null 且不为空
     */
    public static boolean isNotEmpty(Collection<?> coll) {
        return !isEmpty(coll);
    }

    /**
     * 判断 Map 是否为空
     *
     * @param map Map
     * @return true 如果 Map 为 null 或空
     */
    public static boolean isEmpty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    /**
     * 判断 Map 是否非空
     *
     * @param map Map
     * @return true 如果 Map 不为 null 且不为空
     */
    public static boolean isNotEmpty(Map<?, ?> map) {
        return !isEmpty(map);
    }

    /**
     * 判断数组是否为空
     *
     * @param arr 数组
     * @return true 如果数组为 null 或长度为 0
     */
    public static boolean isEmpty(Object[] arr) {
        return arr == null || arr.length == 0;
    }

    /**
     * 判断数组是否非空
     *
     * @param arr 数组
     * @return true 如果数组不为 null 且长度大于 0
     */
    public static boolean isNotEmpty(Object[] arr) {
        return !isEmpty(arr);
    }

    /**
     * 将列表转换为另一个类型的列表
     *
     * @param list     源列表
     * @param mapper   转换函数
     * @param <T>      源类型
     * @param <R>      目标类型
     * @return 转换后的列表，源列表为空时返回空列表
     */
    public static <T, R> List<R> toList(List<T> list, Function<T, R> mapper) {
        if (isEmpty(list)) {
            return new ArrayList<>();
        }
        return list.stream().map(mapper).collect(Collectors.toList());
    }

    /**
     * 将列表转换为 Set
     *
     * @param list     源列表
     * @param mapper   转换函数
     * @param <T>      源类型
     * @param <R>      目标类型
     * @return 转换后的 Set，源列表为空时返回空 Set
     */
    public static <T, R> Set<R> toSet(List<T> list, Function<T, R> mapper) {
        if (isEmpty(list)) {
            return new HashSet<>();
        }
        return list.stream().map(mapper).collect(Collectors.toSet());
    }

    /**
     * 从列表中提取指定字段，组成新的列表
     *
     * @param list       源列表
     * @param extractor  字段提取函数
     * @param <T>        源类型
     * @param <R>        字段类型
     * @return 字段值列表
     */
    public static <T, R> List<R> mapField(List<T> list, Function<T, R> extractor) {
        return toList(list, extractor);
    }

    /**
     * 按条件过滤列表
     *
     * @param list       源列表
     * @param predicate  过滤条件
     * @param <T>        元素类型
     * @return 过滤后的列表
     */
    public static <T> List<T> filter(List<T> list, Predicate<T> predicate) {
        if (isEmpty(list)) {
            return new ArrayList<>();
        }
        return list.stream().filter(predicate).collect(Collectors.toList());
    }

    /**
     * 查找第一个匹配的元素
     *
     * @param list       源列表
     * @param predicate  匹配条件
     * @param <T>        元素类型
     * @return 第一个匹配的元素，未找到返回 null
     */
    public static <T> T findFirst(List<T> list, Predicate<T> predicate) {
        if (isEmpty(list)) {
            return null;
        }
        return list.stream().filter(predicate).findFirst().orElse(null);
    }

    /**
     * 按指定字段分组
     *
     * @param list       源列表
     * @param classifier 分组函数
     * @param <T>        元素类型
     * @param <K>        键类型
     * @return 分组后的 Map
     */
    public static <T, K> Map<K, List<T>> groupBy(List<T> list, Function<T, K> classifier) {
        if (isEmpty(list)) {
            return new HashMap<>();
        }
        return list.stream().collect(Collectors.groupingBy(classifier));
    }

    /**
     * 将列表转换为 Map
     *
     * @param list    源列表
     * @param keyMapper   键提取函数
     * @param <T>     元素类型
     * @param <K>     键类型
     * @return Map
     */
    public static <T, K> Map<K, T> toMap(List<T> list, Function<T, K> keyMapper) {
        if (isEmpty(list)) {
            return new HashMap<>();
        }
        return list.stream().collect(Collectors.toMap(keyMapper, Function.identity(), (a, b) -> a));
    }

    /**
     * 将列表转换为 Map（指定键和值）
     *
     * @param list      源列表
     * @param keyMapper 键提取函数
     * @param valueMapper 值提取函数
     * @param <T>       元素类型
     * @param <K>       键类型
     * @param <V>       值类型
     * @return Map
     */
    public static <T, K, V> Map<K, V> toMap(List<T> list, Function<T, K> keyMapper, Function<T, V> valueMapper) {
        if (isEmpty(list)) {
            return new HashMap<>();
        }
        return list.stream().collect(Collectors.toMap(keyMapper, valueMapper, (a, b) -> a));
    }

    /**
     * 判断列表中是否存在匹配元素
     *
     * @param list       源列表
     * @param predicate  匹配条件
     * @param <T>        元素类型
     * @return true 如果存在匹配元素
     */
    public static <T> boolean anyMatch(List<T> list, Predicate<T> predicate) {
        if (isEmpty(list)) {
            return false;
        }
        return list.stream().anyMatch(predicate);
    }

    /**
     * 判断列表中是否所有元素都匹配
     *
     * @param list       源列表
     * @param predicate  匹配条件
     * @param <T>        元素类型
     * @return true 如果所有元素都匹配
     */
    public static <T> boolean allMatch(List<T> list, Predicate<T> predicate) {
        if (isEmpty(list)) {
            return true;
        }
        return list.stream().allMatch(predicate);
    }

    /**
     * 对列表分批处理
     *
     * @param list       源列表
     * @param batchSize  每批大小
     * @param <T>        元素类型
     * @return 分批后的列表
     */
    public static <T> List<List<T>> partition(List<T> list, int batchSize) {
        if (isEmpty(list) || batchSize <= 0) {
            return new ArrayList<>();
        }
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            int end = Math.min(i + batchSize, list.size());
            result.add(new ArrayList<>(list.subList(i, end)));
        }
        return result;
    }

    /**
     * 去重
     *
     * @param list 源列表
     * @param <T>  元素类型
     * @return 去重后的列表
     */
    public static <T> List<T> distinct(List<T> list) {
        if (isEmpty(list)) {
            return new ArrayList<>();
        }
        return list.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 按指定字段去重
     *
     * @param list       源列表
     * @param extractor  字段提取函数
     * @param <T>        元素类型
     * @param <R>        字段类型
     * @return 去重后的列表
     */
    public static <T, R> List<T> distinctByKey(List<T> list, Function<T, R> extractor) {
        if (isEmpty(list)) {
            return new ArrayList<>();
        }
        Set<R> seen = new HashSet<>();
        return list.stream().filter(item -> seen.add(extractor.apply(item))).collect(Collectors.toList());
    }

    /**
     * 安全获取列表大小
     *
     * @param coll 集合
     * @return 集合大小，null 返回 0
     */
    public static int size(Collection<?> coll) {
        return coll == null ? 0 : coll.size();
    }
}
