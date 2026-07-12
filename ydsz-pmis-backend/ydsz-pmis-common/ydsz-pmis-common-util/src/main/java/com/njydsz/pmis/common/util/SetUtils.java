package com.njydsz.pmis.common.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Set 工具类
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public final class SetUtils {

    private SetUtils() {
    }

    /**
     * 判断 Set 是否为空
     */
    public static boolean isEmpty(Set<?> set) {
        return set == null || set.isEmpty();
    }

    /**
     * 判断 Set 是否非空
     */
    public static boolean isNotEmpty(Set<?> set) {
        return !isEmpty(set);
    }

    /**
     * 创建空 Set
     */
    public static <T> Set<T> emptySet() {
        return new HashSet<>();
    }

    /**
     * 从元素创建 Set
     */
    @SafeVarargs
    public static <T> Set<T> of(T... elements) {
        if (elements == null || elements.length == 0) {
            return new HashSet<>();
        }
        return new HashSet<>(Arrays.asList(elements));
    }

    /**
     * 交集
     */
    public static <T> Set<T> intersection(Set<T> set1, Set<T> set2) {
        if (isEmpty(set1) || isEmpty(set2)) {
            return new HashSet<>();
        }
        return set1.stream().filter(set2::contains).collect(Collectors.toSet());
    }

    /**
     * 并集
     */
    public static <T> Set<T> union(Set<T> set1, Set<T> set2) {
        Set<T> result = new HashSet<>();
        if (isNotEmpty(set1)) {
            result.addAll(set1);
        }
        if (isNotEmpty(set2)) {
            result.addAll(set2);
        }
        return result;
    }

    /**
     * 差集（set1 - set2）
     */
    public static <T> Set<T> difference(Set<T> set1, Set<T> set2) {
        if (isEmpty(set1)) {
            return new HashSet<>();
        }
        if (isEmpty(set2)) {
            return new HashSet<>(set1);
        }
        return set1.stream().filter(e -> !set2.contains(e)).collect(Collectors.toSet());
    }
}
