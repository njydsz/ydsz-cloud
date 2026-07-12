package com.njydsz.pmis.common.util;

import java.lang.reflect.Array;
import java.util.Arrays;

/**
 * 数组工具类
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public final class ArrayUtils {

    private ArrayUtils() {
    }

    /**
     * 判断数组是否为空
     *
     * @param array 数组
     * @return true 如果数组为 null 或长度为 0
     */
    public static boolean isEmpty(Object[] array) {
        return array == null || array.length == 0;
    }

    /**
     * 判断数组是否非空
     *
     * @param array 数组
     * @return true 如果数组不为 null 且长度大于 0
     */
    public static boolean isNotEmpty(Object[] array) {
        return !isEmpty(array);
    }

    /**
     * 判断 byte 数组是否为空
     */
    public static boolean isEmpty(byte[] array) {
        return array == null || array.length == 0;
    }

    /**
     * 判断 int 数组是否为空
     */
    public static boolean isEmpty(int[] array) {
        return array == null || array.length == 0;
    }

    /**
     * 判断数组中是否包含指定元素
     *
     * @param array   数组
     * @param element 元素
     * @return true 如果包含
     */
    public static boolean contains(Object[] array, Object element) {
        if (isEmpty(array)) {
            return false;
        }
        for (Object item : array) {
            if (ObjectUtils.equals(item, element)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将数组转换为列表
     *
     * @param array 数组
     * @param <T>   元素类型
     * @return 列表
     */
    @SafeVarargs
    public static <T> java.util.List<T> toList(T... array) {
        if (isEmpty(array)) {
            return new java.util.ArrayList<>();
        }
        return new java.util.ArrayList<>(Arrays.asList(array));
    }

    /**
     * 合并两个数组
     *
     * @param array1 数组1
     * @param array2 数组2
     * @param <T>    元素类型
     * @return 合并后的数组
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] concat(T[] array1, T[] array2) {
        if (isEmpty(array1)) {
            return array2;
        }
        if (isEmpty(array2)) {
            return array1;
        }
        T[] result = (T[]) Array.newInstance(array1.getClass().getComponentType(), array1.length + array2.length);
        System.arraycopy(array1, 0, result, 0, array1.length);
        System.arraycopy(array2, 0, result, array1.length, array2.length);
        return result;
    }

    /**
     * 在数组头部添加元素
     *
     * @param array   数组
     * @param element 元素
     * @param <T>     元素类型
     * @return 新数组
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] addFirst(T[] array, T element) {
        int len = array == null ? 0 : array.length;
        T[] newArray = (T[]) Array.newInstance(
                array != null ? array.getClass().getComponentType() : element.getClass(), len + 1);
        newArray[0] = element;
        if (len > 0) {
            System.arraycopy(array, 0, newArray, 1, len);
        }
        return newArray;
    }

    /**
     * 在数组尾部添加元素
     *
     * @param array   数组
     * @param element 元素
     * @param <T>     元素类型
     * @return 新数组
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] addLast(T[] array, T element) {
        int len = array == null ? 0 : array.length;
        T[] newArray = (T[]) Array.newInstance(
                array != null ? array.getClass().getComponentType() : element.getClass(), len + 1);
        if (len > 0) {
            System.arraycopy(array, 0, newArray, 0, len);
        }
        newArray[len] = element;
        return newArray;
    }

    /**
     * 反转数组
     *
     * @param array 数组
     * @param <T>   元素类型
     * @return 反转后的数组
     */
    public static <T> T[] reverse(T[] array) {
        if (isEmpty(array)) {
            return array;
        }
        T[] result = array.clone();
        for (int i = 0; i < result.length / 2; i++) {
            T temp = result[i];
            result[i] = result[result.length - 1 - i];
            result[result.length - 1 - i] = temp;
        }
        return result;
    }

    /**
     * 获取数组长度
     *
     * @param array 数组
     * @return 长度，null 返回 0
     */
    public static int length(Object[] array) {
        return array == null ? 0 : array.length;
    }
}
