package com.njydsz.pmis.common.util;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * 断言工具类
 *
 * <p>提供参数校验方法，校验失败时抛出 {@link IllegalArgumentException}。
 * 对标 remi-comm AssertUtils。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public final class AssertUtils {

    private AssertUtils() {
    }

    /**
     * 断言对象不为 null
     *
     * @param obj     对象
     * @param message 错误消息
     */
    public static void notNull(Object obj, String message) {
        if (obj == null) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言对象为 null
     *
     * @param obj     对象
     * @param message 错误消息
     */
    public static void isNull(Object obj, String message) {
        if (obj != null) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言条件为 true
     *
     * @param expression 条件
     * @param message    错误消息
     */
    public static void isTrue(boolean expression, String message) {
        if (!expression) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言条件为 false
     *
     * @param expression 条件
     * @param message    错误消息
     */
    public static void isFalse(boolean expression, String message) {
        if (expression) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言字符串非空
     *
     * @param str     字符串
     * @param message 错误消息
     */
    public static void notBlank(String str, String message) {
        if (str == null || str.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言集合非空
     *
     * @param coll    集合
     * @param message 错误消息
     */
    public static void notEmpty(Collection<?> coll, String message) {
        if (CollectionUtils.isEmpty(coll)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言 Map 非空
     *
     * @param map     Map
     * @param message 错误消息
     */
    public static void notEmpty(Map<?, ?> map, String message) {
        if (CollectionUtils.isEmpty(map)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言数组非空
     *
     * @param arr     数组
     * @param message 错误消息
     */
    public static void notEmpty(Object[] arr, String message) {
        if (CollectionUtils.isEmpty(arr)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言两个对象相等
     *
     * @param obj1    对象1
     * @param obj2    对象2
     * @param message 错误消息
     */
    public static void equals(Object obj1, Object obj2, String message) {
        if (!Objects.equals(obj1, obj2)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言两个对象不相等
     *
     * @param obj1    对象1
     * @param obj2    对象2
     * @param message 错误消息
     */
    public static void notEquals(Object obj1, Object obj2, String message) {
        if (Objects.equals(obj1, obj2)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言数值为正数
     *
     * @param number  数值
     * @param message 错误消息
     */
    public static void positive(Number number, String message) {
        notNull(number, message);
        if (number.doubleValue() <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 断言数值为非负数
     *
     * @param number  数值
     * @param message 错误消息
     */
    public static void nonNegative(Number number, String message) {
        notNull(number, message);
        if (number.doubleValue() < 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
