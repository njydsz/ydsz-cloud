package com.njydsz.pmis.common.util.array;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * 数组工具类
 *
 * <p>提供数组操作的常用方法，包括判空、查找、转换、合并等功能。
 * 所有方法均对 null 值进行安全处理，避免空指针异常。
 *
 * <p><b>主要功能：</b>
 * <ul>
 *   <li>判空检查：isEmpty、isNotEmpty（支持各种类型数组）</li>
 *   <li>查找操作：contains、indexOf、lastIndexOf</li>
 *   <li>转换操作：toArray、toList、convertArray</li>
 *   <li>数组合并：merge、concat</li>
 *   <li>数组截取：subArray、slice</li>
 *   <li>去重操作：removeDuplicate、distinct</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
public class ArrayUtils {

    private ArrayUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ==================== 判空方法 ====================

    /**
     * 判断对象是否为非空数组
     */
    public static boolean isNotEmpty(Object array) {
        return !isEmpty(array);
    }

    /**
     * 通用数组空判断 (支持 Object[] 及各种基本类型数组)
     */
    public static boolean isEmpty(Object array) {
        if (array == null) {
            return true;
        }
        if (!array.getClass().isArray()) {
            return false;
        }
        return Array.getLength(array) == 0;
    }

    /**
     * 针对 Object 数组的优化判断
     */
    public static boolean isEmpty(Object[] array) {
        return array == null || array.length == 0;
    }

    /**
     * 针对 Object 数组的非空判断
     */
    public static boolean isNotEmpty(Object[] array) {
        return !isEmpty(array);
    }

    /**
     * 判断 int 数组是否为空
     */
    public static boolean isEmpty(int[] array) {
        return array == null || array.length == 0;
    }

    /**
     * 判断 int 数组是否不为空
     */
    public static boolean isNotEmpty(int[] array) {
        return !isEmpty(array);
    }

    /**
     * 判断 long 数组是否为空
     */
    public static boolean isEmpty(long[] array) {
        return array == null || array.length == 0;
    }

    /**
     * 判断 long 数组是否不为空
     */
    public static boolean isNotEmpty(long[] array) {
        return !isEmpty(array);
    }

    /**
     * 判断 double 数组是否为空
     */
    public static boolean isEmpty(double[] array) {
        return array == null || array.length == 0;
    }

    /**
     * 判断 double 数组是否不为空
     */
    public static boolean isNotEmpty(double[] array) {
        return !isEmpty(array);
    }

    /**
     * 判断 float 数组是否为空
     */
    public static boolean isEmpty(float[] array) {
        return array == null || array.length == 0;
    }

    /**
     * 判断 float 数组是否不为空
     */
    public static boolean isNotEmpty(float[] array) {
        return !isEmpty(array);
    }

    /**
     * 判断 boolean 数组是否为空
     */
    public static boolean isEmpty(boolean[] array) {
        return array == null || array.length == 0;
    }

    /**
     * 判断 boolean 数组是否不为空
     */
    public static boolean isNotEmpty(boolean[] array) {
        return !isEmpty(array);
    }

    /**
     * 判断 char 数组是否为空
     */
    public static boolean isEmpty(char[] array) {
        return array == null || array.length == 0;
    }

    /**
     * 判断 char 数组是否不为空
     */
    public static boolean isNotEmpty(char[] array) {
        return !isEmpty(array);
    }

    /**
     * 判断 short 数组是否为空
     */
    public static boolean isEmpty(short[] array) {
        return array == null || array.length == 0;
    }

    /**
     * 判断 short 数组是否不为空
     */
    public static boolean isNotEmpty(short[] array) {
        return !isEmpty(array);
    }

    /**
     * 判断 byte 数组是否为空
     */
    public static boolean isEmpty(byte[] array) {
        return array == null || array.length == 0;
    }

    /**
     * 判断 byte 数组是否不为空
     */
    public static boolean isNotEmpty(byte[] array) {
        return !isEmpty(array);
    }

    // ==================== 查找方法 ====================

    /**
     * 判断数组是否包含指定元素
     */
    public static boolean contains(Object[] array, Object value) {
        if (isEmpty(array)) {
            return false;
        }
        for (Object item : array) {
            if (value == null) {
                if (item == null) {
                    return true;
                }
            } else {
                if (value.equals(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 判断 int 数组是否包含指定元素
     */
    public static boolean contains(int[] array, int value) {
        if (isEmpty(array)) {
            return false;
        }
        for (int item : array) {
            if (item == value) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断 long 数组是否包含指定元素
     */
    public static boolean contains(long[] array, long value) {
        if (isEmpty(array)) {
            return false;
        }
        for (long item : array) {
            if (item == value) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断 double 数组是否包含指定元素
     */
    public static boolean contains(double[] array, double value) {
        if (isEmpty(array)) {
            return false;
        }
        for (double item : array) {
            if (Double.compare(item, value) == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断 float 数组是否包含指定元素
     */
    public static boolean contains(float[] array, float value) {
        if (isEmpty(array)) {
            return false;
        }
        for (float item : array) {
            if (Float.compare(item, value) == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断 boolean 数组是否包含指定元素
     */
    public static boolean contains(boolean[] array, boolean value) {
        if (isEmpty(array)) {
            return false;
        }
        for (boolean item : array) {
            if (item == value) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断 char 数组是否包含指定元素
     */
    public static boolean contains(char[] array, char value) {
        if (isEmpty(array)) {
            return false;
        }
        for (char item : array) {
            if (item == value) {
                return true;
            }
        }
        return false;
    }

    /**
     * 查找元素在数组中的索引位置
     *
     * @param array 数组
     * @param value 要查找的值
     * @return 元素索引，如果未找到返回 -1
     */
    public static int indexOf(Object[] array, Object value) {
        if (isEmpty(array)) {
            return -1;
        }
        for (int i = 0; i < array.length; i++) {
            if (value == null) {
                if (array[i] == null) {
                    return i;
                }
            } else {
                if (value.equals(array[i])) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * 查找元素在数组中的最后索引位置
     *
     * @param array 数组
     * @param value 要查找的值
     * @return 元素索引，如果未找到返回 -1
     */
    public static int lastIndexOf(Object[] array, Object value) {
        if (isEmpty(array)) {
            return -1;
        }
        for (int i = array.length - 1; i >= 0; i--) {
            if (value == null) {
                if (array[i] == null) {
                    return i;
                }
            } else {
                if (value.equals(array[i])) {
                    return i;
                }
            }
        }
        return -1;
    }

    // ==================== 转换方法 ====================

    /**
     * 将数组转换为 List
     */
    public static <T> List<T> toList(T[] array) {
        if (isEmpty(array)) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(array));
    }

    /**
     * 将 Collection 转换为数组
     *
     * @deprecated 请使用 {@link com.njydsz.pmis.common.util.collection.CollectionUtils#listToArray(Collection, Class)} 替代，
     *             CollectionUtils 更专注于集合操作且类型安全签名更明确
     */
    @Deprecated(since = "1.0.0", forRemoval = true)
    public static <T> T[] toArray(Collection<T> collection, Class<?> clazz) {
        Objects.requireNonNull(clazz, "clazz must not be null");
        if (collection == null || collection.isEmpty()) {
            return newArray(clazz, 0);
        }
        return collection.toArray(newArray(clazz, collection.size()));
    }

    /**
     * 创建指定类型的数组
     */
    
    public static <T> T[] newArray(Class<?> clazz, int length) {
        Objects.requireNonNull(clazz, "clazz must not be null");
        // Array.newInstance returns Object; cast to T[] is safe because clazz is Class<T>
        Object array = Array.newInstance(clazz, length);
        return castArray(array);
    }

    /**
     * 将 Object 数组安全转换为泛型数组（内部使用）
     * 此转换在运行时由调用方保证类型安全
     */
    private static <T> T[] castArray(Object array) {
        return (T[]) array;
    }

    /**
     * 将数组转换为字符串
     */
    public static String toString(Object[] array) {
        if (isEmpty(array)) {
            return "[]";
        }
        return Arrays.toString(array);
    }

    /**
     * 将 int 数组转换为字符串
     */
    public static String toString(int[] array) {
        if (isEmpty(array)) {
            return "[]";
        }
        return Arrays.toString(array);
    }

    /**
     * 将 long 数组转换为字符串
     */
    public static String toString(long[] array) {
        if (isEmpty(array)) {
            return "[]";
        }
        return Arrays.toString(array);
    }

    /**
     * 将 double 数组转换为字符串
     */
    public static String toString(double[] array) {
        if (isEmpty(array)) {
            return "[]";
        }
        return Arrays.toString(array);
    }

    /**
     * 数组类型转换
     *
     * @param source 源数组
     * @param mapper 转换函数
     * @param clazz  目标类型
     * @param <T>    源类型
     * @param <R>    目标类型
     * @return 转换后的数组
     */
    public static <T, R> R[] convertArray(T[] source, Function<? super T, ? extends R> mapper, Class<?> clazz) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        Objects.requireNonNull(clazz, "clazz must not be null");
        if (isEmpty(source)) {
            return newArray(clazz, 0);
        }
        R[] result = newArray(clazz, source.length);
        for (int i = 0; i < source.length; i++) {
            result[i] = mapper.apply(source[i]);
        }
        return result;
    }

    // ==================== 数组合并和连接 ====================

    /**
     * 合并两个数组
     */
    public static <T> T[] merge(T[][] arrays) {
        if (arrays == null || arrays.length == 0) {
            return null;
        }
        if (arrays.length == 1) {
            return arrays[0];
        }

        int totalLength = 0;
        for (T[] array : arrays) {
            if (isNotEmpty(array)) {
                totalLength += array.length;
            }
        }

        if (totalLength == 0) {
            Class<?> rawClazz = arrays[0].getClass().getComponentType();
            return newArray(rawClazz, 0);
        }

        Class<?> componentType = arrays[0].getClass().getComponentType();
        T[] result = newArray(componentType, totalLength);
        int currentIndex = 0;
        for (T[] array : arrays) {
            if (isNotEmpty(array)) {
                System.arraycopy(array, 0, result, currentIndex, array.length);
                currentIndex += array.length;
            }
        }
        return result;
    }

    /**
     * 添加所有元素到数组
     *
     * @param array 原数组
     * @param elements 要添加的元素列表
     * @param <T> 数组类型
     * @return 合并后的数组
     */
    public static <T> T[] addAll(T[] array, List<T> elements) {
        if (isEmpty(array)) {
            if (elements == null) {
                return null;
            }
            // array 为 null/空时无法推断组件类型，使用 Object[] 作为 fallback
            return elements.toArray(newArray(Object.class, elements.size()));
        }
        if (elements == null || elements.isEmpty()) {
            return Arrays.copyOf(array, array.length);
        }

        Class<?> componentType = array.getClass().getComponentType();
        T[] result = newArray(componentType, array.length + elements.size());
        System.arraycopy(array, 0, result, 0, array.length);
        for (int i = 0; i < elements.size(); i++) {
            result[array.length + i] = elements.get(i);
        }
        return result;
    }

    /**
     * 添加所有元素到数组
     *
     * @param array 原数组
     * @param elements 要添加的元素
     * @param <T> 数组类型
     * @return 合并后的数组
     */
    public static <T> T[] addAll(T[] array, T[] elements) {
        if (isEmpty(array)) {
            return elements == null ? null : Arrays.copyOf(elements, elements.length);
        }
        if (elements == null || elements.length == 0) {
            return Arrays.copyOf(array, array.length);
        }

        Class<?> componentType = array.getClass().getComponentType();
        T[] result = newArray(componentType, array.length + elements.length);
        System.arraycopy(array, 0, result, 0, array.length);
        System.arraycopy(elements, 0, result, array.length, elements.length);
        return result;
    }

    /**
     * 添加单个元素到数组
     *
     * @param array 原数组
     * @param element 要添加的元素
     * @param <T> 数组类型
     * @return 新数组
     */
    public static <T> T[] add(T[] array, T element) {
        if (isEmpty(array)) {
            Class<?> elementType = element.getClass();
            T[] result = newArray(elementType, 1);
            result[0] = element;
            return result;
        }

        Class<?> componentType = array.getClass().getComponentType();
        T[] result = newArray(componentType, array.length + 1);
        System.arraycopy(array, 0, result, 0, array.length);
        result[array.length] = element;
        return result;
    }

    /**
     * 在指定位置添加元素到数组
     *
     * @param array 原数组
     * @param index 插入位置
     * @param element 要添加的元素
     * @param <T> 数组类型
     * @return 新数组
     */
    public static <T> T[] add(T[] array, int index, T element) {
        if (array == null) {
            Class<?> elementType = element.getClass();
            T[] result = newArray(elementType, 1);
            result[0] = element;
            return result;
        }
        if (index < 0 || index > array.length) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Length: " + array.length);
        }

        Class<?> componentType = array.getClass().getComponentType();
        T[] result = newArray(componentType, array.length + 1);
        System.arraycopy(array, 0, result, 0, index);
        result[index] = element;
        System.arraycopy(array, index, result, index + 1, array.length - index);
        return result;
    }

    /**
     * 合并两个 int 数组
     */
    public static int[] merge(int[][] arrays) {
        if (arrays == null || arrays.length == 0) {
            return new int[0];
        }
        if (arrays.length == 1) {
            return arrays[0];
        }

        int totalLength = 0;
        for (int[] array : arrays) {
            if (isNotEmpty(array)) {
                totalLength += array.length;
            }
        }

        if (totalLength == 0) {
            return new int[0];
        }

        int[] result = new int[totalLength];
        int currentIndex = 0;
        for (int[] array : arrays) {
            if (isNotEmpty(array)) {
                System.arraycopy(array, 0, result, currentIndex, array.length);
                currentIndex += array.length;
            }
        }
        return result;
    }

    /**
     * 连接元素到数组
     */
    public static <T> T[] concat(T[] elements) {
        if (elements == null || elements.length == 0) {
            return null;
        }
        return Arrays.copyOf(elements, elements.length);
    }

    // ==================== 数组截取 ====================

    /**
     * 截取数组的一部分
     *
     * @param array 源数组
     * @param start 起始索引（包含）
     * @param end   结束索引（不包含）
     * @param <T>   数组类型
     * @return 截取后的数组
     */
    public static <T> T[] subArray(T[] array, int start, int end) {
        if (isEmpty(array)) {
            return Arrays.copyOf(array, 0);
        }
        if (start < 0) {
            start = 0;
        }
        if (end > array.length) {
            end = array.length;
        }
        if (start >= end) {
            Class<?> componentType = array.getClass().getComponentType();
            return newArray(componentType, 0);
        }
        return Arrays.copyOfRange(array, start, end);
    }

    /**
     * 截取 int 数组的一部分
     */
    public static int[] subArray(int[] array, int start, int end) {
        if (isEmpty(array)) {
            return new int[0];
        }
        if (start < 0) {
            start = 0;
        }
        if (end > array.length) {
            end = array.length;
        }
        if (start >= end) {
            return new int[0];
        }
        return Arrays.copyOfRange(array, start, end);
    }

    /**
     * 获取数组的前 N 个元素
     */
    public static <T> T[] limit(T[] array, int n) {
        if (isEmpty(array) || n <= 0) {
            Class<?> componentType = array.getClass().getComponentType();
            return newArray(componentType, 0);
        }
        if (n >= array.length) {
            return Arrays.copyOf(array, array.length);
        }
        return Arrays.copyOf(array, n);
    }

    /**
     * 获取数组的前 N 个元素（int 数组）
     */
    public static int[] limit(int[] array, int n) {
        if (isEmpty(array) || n <= 0) {
            return new int[0];
        }
        if (n >= array.length) {
            return Arrays.copyOf(array, array.length);
        }
        return Arrays.copyOf(array, n);
    }

    // ==================== 去重方法 ====================

    /**
     * 去除数组中的重复元素
     */
    public static <T> T[] removeDuplicate(T[] array) {
        if (isEmpty(array)) {
            Class<?> componentType = array.getClass().getComponentType();
            return newArray(componentType, 0);
        }
        Set<T> set = new LinkedHashSet<>();
        for (T item : array) {
            set.add(item);
        }

        Class<?> componentType = array.getClass().getComponentType();
        return toArray(set, componentType);
    }

    /**
     * 去除 int 数组中的重复元素
     */
    public static int[] removeDuplicate(int[] array) {
        if (isEmpty(array)) {
            return new int[0];
        }
        Set<Integer> set = new LinkedHashSet<>();
        for (int item : array) {
            set.add(item);
        }
        int[] result = new int[set.size()];
        int i = 0;
        for (Integer value : set) {
            result[i++] = value;
        }
        return result;
    }

    /**
     * 去除数组中的重复元素（保持顺序）
     */
    public static <T> T[] distinct(T[] array) {
        return removeDuplicate(array);
    }

    // ==================== 其他实用方法 ====================

    /**
     * 反转数组
     */
    public static <T> T[] reverse(T[] array) {
        if (isEmpty(array)) {
            return array;
        }
        int left = 0;
        int right = array.length - 1;
        while (left < right) {
            T temp = array[left];
            array[left] = array[right];
            array[right] = temp;
            left++;
            right--;
        }
        return array;
    }

    /**
     * 反转 int 数组
     */
    public static int[] reverse(int[] array) {
        if (isEmpty(array)) {
            return array;
        }
        int left = 0;
        int right = array.length - 1;
        while (left < right) {
            int temp = array[left];
            array[left] = array[right];
            array[right] = temp;
            left++;
            right--;
        }
        return array;
    }

    /**
     * 填充数组
     */
    public static <T> T[] fill(T[] array, T value) {
        if (isEmpty(array)) {
            return array;
        }
        Arrays.fill(array, value);
        return array;
    }

    /**
     * 填充 int 数组
     */
    public static int[] fill(int[] array, int value) {
        if (isEmpty(array)) {
            return array;
        }
        Arrays.fill(array, value);
        return array;
    }

    /**
     * 克隆数组
     */
    public static <T> T[] clone(T[] array) {
        if (isEmpty(array)) {
            Class<?> componentType = array.getClass().getComponentType();
            return newArray(componentType, 0);
        }
        return Arrays.copyOf(array, array.length);
    }

    /**
     * 克隆 int 数组
     */
    public static int[] clone(int[] array) {
        if (isEmpty(array)) {
            return new int[0];
        }
        return Arrays.copyOf(array, array.length);
    }

    /**
     * 获取数组长度
     */
    public static int length(Object array) {
        if (array == null) {
            return 0;
        }
        if (!array.getClass().isArray()) {
            throw new IllegalArgumentException("Object is not an array");
        }
        return Array.getLength(array);
    }
}
