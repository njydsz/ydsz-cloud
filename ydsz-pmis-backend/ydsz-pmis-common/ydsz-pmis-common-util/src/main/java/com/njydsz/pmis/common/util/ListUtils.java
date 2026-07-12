package com.njydsz.pmis.common.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 列表工具类
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public final class ListUtils {

    private ListUtils() {
    }

    /**
     * 判断列表是否为空
     */
    public static boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }

    /**
     * 判断列表是否非空
     */
    public static boolean isNotEmpty(List<?> list) {
        return !isEmpty(list);
    }

    /**
     * 创建空列表
     */
    public static <T> List<T> emptyList() {
        return new ArrayList<>();
    }

    /**
     * 从元素创建列表
     *
     * @param elements 元素
     * @param <T>      元素类型
     * @return 列表
     */
    @SafeVarargs
    public static <T> List<T> of(T... elements) {
        if (elements == null || elements.length == 0) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(elements));
    }

    /**
     * 不可变列表
     */
    @SafeVarargs
    public static <T> List<T> unmodifiable(T... elements) {
        if (elements == null || elements.length == 0) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(Arrays.asList(elements));
    }

    /**
     * 分割列表
     *
     * @param list      源列表
     * @param batchSize 每批大小
     * @param <T>       元素类型
     * @return 分割后的列表
     */
    public static <T> List<List<T>> partition(List<T> list, int batchSize) {
        return CollectionUtils.partition(list, batchSize);
    }

    /**
     * 获取列表第一个元素
     *
     * @param list 列表
     * @param <T>  元素类型
     * @return 第一个元素，空列表返回 null
     */
    public static <T> T getFirst(List<T> list) {
        return CollectionUtils.isEmpty(list) ? null : list.get(0);
    }

    /**
     * 获取列表最后一个元素
     *
     * @param list 列表
     * @param <T>  元素类型
     * @return 最后一个元素，空列表返回 null
     */
    public static <T> T getLast(List<T> list) {
        return CollectionUtils.isEmpty(list) ? null : list.get(list.size() - 1);
    }

    /**
     * 去重
     */
    public static <T> List<T> distinct(List<T> list) {
        return CollectionUtils.distinct(list);
    }

    /**
     * 反转列表
     */
    public static <T> List<T> reverse(List<T> list) {
        if (isEmpty(list)) {
            return new ArrayList<>();
        }
        List<T> result = new ArrayList<>(list);
        Collections.reverse(result);
        return result;
    }
}
