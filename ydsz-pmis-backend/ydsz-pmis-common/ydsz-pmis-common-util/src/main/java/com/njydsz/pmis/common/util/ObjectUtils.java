package com.njydsz.pmis.common.util;

import java.util.Objects;

/**
 * 对象工具类
 *
 * <p>提供对象操作的通用方法。
 * 对标 remi-comm ObjectUtils。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public final class ObjectUtils {

    private ObjectUtils() {
    }

    /**
     * 返回第一个非 null 的对象
     *
     * @param objects 对象数组
     * @param <T>     对象类型
     * @return 第一个非 null 的对象，全为 null 时返回 null
     */
    @SafeVarargs
    public static <T> T firstNonNull(T... objects) {
        if (objects == null) {
            return null;
        }
        for (T obj : objects) {
            if (obj != null) {
                return obj;
            }
        }
        return null;
    }

    /**
     * 返回第一个非 null 的对象（带默认值）
     *
     * @param defaultValue 默认值
     * @param objects      对象数组
     * @param <T>          对象类型
     * @return 第一个非 null 的对象，全为 null 时返回默认值
     */
    @SafeVarargs
    public static <T> T firstNonNull(T defaultValue, T... objects) {
        T result = firstNonNull(objects);
        return result != null ? result : defaultValue;
    }

    /**
     * 判断对象是否为 null
     *
     * @param obj 对象
     * @return true 如果对象为 null
     */
    public static boolean isNull(Object obj) {
        return obj == null;
    }

    /**
     * 判断对象是否非 null
     *
     * @param obj 对象
     * @return true 如果对象非 null
     */
    public static boolean isNotNull(Object obj) {
        return obj != null;
    }

    /**
     * 判断两个对象是否相等
     *
     * @param a 对象 a
     * @param b 对象 b
     * @return true 如果相等
     */
    public static boolean equals(Object a, Object b) {
        return Objects.equals(a, b);
    }

    /**
     * 获取对象的 hashCode
     *
     * @param obj 对象
     * @return hashCode
     */
    public static int hashCode(Object obj) {
        return Objects.hashCode(obj);
    }

    /**
     * 获取对象的字符串表示
     *
     * @param obj 对象
     * @return 字符串，null 返回空字符串
     */
    public static String toString(Object obj) {
        return obj == null ? "" : obj.toString();
    }

    /**
     * 获取对象的字符串表示（带默认值）
     *
     * @param obj          对象
     * @param defaultValue 默认值
     * @return 字符串
     */
    public static String toString(Object obj, String defaultValue) {
        return obj == null ? defaultValue : obj.toString();
    }

    /**
     * 类型转换
     *
     * @param obj 对象
     * @param <T> 目标类型
     * @return 转换后的对象
     */
    @SuppressWarnings("unchecked")
    public static <T> T cast(Object obj) {
        return (T) obj;
    }

    /**
     * 判断对象是否为指定类型
     *
     * @param obj   对象
     * @param clazz 类型
     * @return true 如果对象是指定类型
     */
    public static boolean isInstanceOf(Object obj, Class<?> clazz) {
        return clazz != null && clazz.isInstance(obj);
    }

    /**
     * 默认值（null 时返回默认值）
     *
     * @param obj          对象
     * @param defaultValue 默认值
     * @param <T>          对象类型
     * @return 对象或默认值
     */
    public static <T> T defaultIfNull(T obj, T defaultValue) {
        return obj != null ? obj : defaultValue;
    }
}
