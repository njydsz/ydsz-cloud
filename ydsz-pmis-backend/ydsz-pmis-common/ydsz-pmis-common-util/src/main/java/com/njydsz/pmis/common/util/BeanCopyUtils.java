package com.njydsz.pmis.common.util;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Bean 拷贝工具类
 *
 * <p>基于 Spring BeanWrapper 实现高性能属性拷贝，支持忽略指定属性。
 * 对标 remi-comm BeanCopyUtils。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public final class BeanCopyUtils {

    private BeanCopyUtils() {
    }

    /**
     * 拷贝源对象属性到目标对象
     *
     * @param source 源对象
     * @param target 目标对象
     */
    public static void copyProperties(Object source, Object target) {
        if (source == null || target == null) {
            return;
        }
        org.springframework.beans.BeanUtils.copyProperties(source, target);
    }

    /**
     * 拷贝源对象属性到目标对象（忽略指定属性）
     *
     * @param source           源对象
     * @param target           目标对象
     * @param ignoreProperties 忽略的属性名
     */
    public static void copyProperties(Object source, Object target, String... ignoreProperties) {
        if (source == null || target == null) {
            return;
        }
        org.springframework.beans.BeanUtils.copyProperties(source, target, ignoreProperties);
    }

    /**
     * 创建目标类实例并拷贝源对象属性
     *
     * @param source      源对象
     * @param targetClass 目标类
     * @param <T>         目标类型
     * @return 目标对象实例，源对象为 null 时返回 null
     */
    public static <T> T copy(Object source, Class<T> targetClass) {
        if (source == null) {
            return null;
        }
        try {
            T target = targetClass.getDeclaredConstructor().newInstance();
            copyProperties(source, target);
            return target;
        } catch (Exception e) {
            throw new RuntimeException("Bean copy failed: " + targetClass.getName(), e);
        }
    }

    /**
     * 创建目标类实例并拷贝源对象属性（忽略指定属性）
     *
     * @param source           源对象
     * @param targetClass      目标类
     * @param ignoreProperties 忽略的属性名
     * @param <T>              目标类型
     * @return 目标对象实例
     */
    public static <T> T copy(Object source, Class<T> targetClass, String... ignoreProperties) {
        if (source == null) {
            return null;
        }
        try {
            T target = targetClass.getDeclaredConstructor().newInstance();
            copyProperties(source, target, ignoreProperties);
            return target;
        } catch (Exception e) {
            throw new RuntimeException("Bean copy failed: " + targetClass.getName(), e);
        }
    }

    /**
     * 拷贝列表中的元素到新类型的列表
     *
     * @param sourceList  源列表
     * @param targetClass 目标类
     * @param <S>         源类型
     * @param <T>         目标类型
     * @return 目标列表
     */
    public static <S, T> java.util.List<T> copyList(java.util.List<S> sourceList, Class<T> targetClass) {
        if (CollectionUtils.isEmpty(sourceList)) {
            return new java.util.ArrayList<>();
        }
        return sourceList.stream()
                .map(source -> copy(source, targetClass))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 获取对象中值为 null 的属性名
     *
     * @param source 源对象
     * @return null 属性名数组
     */
    public static String[] getNullPropertyNames(Object source) {
        final BeanWrapper src = new BeanWrapperImpl(source);
        java.beans.PropertyDescriptor[] pds = src.getPropertyDescriptors();
        Set<String> emptyNames = new HashSet<>();
        for (java.beans.PropertyDescriptor pd : pds) {
            Object srcValue = src.getPropertyValue(pd.getName());
            if (srcValue == null) {
                emptyNames.add(pd.getName());
            }
        }
        return emptyNames.toArray(new String[0]);
    }

    /**
     * 拷贝非 null 属性（常用于更新操作）
     *
     * @param source 源对象
     * @param target 目标对象
     */
    public static void copyNonNullProperties(Object source, Object target) {
        if (source == null || target == null) {
            return;
        }
        org.springframework.beans.BeanUtils.copyProperties(source, target, getNullPropertyNames(source));
    }
}
