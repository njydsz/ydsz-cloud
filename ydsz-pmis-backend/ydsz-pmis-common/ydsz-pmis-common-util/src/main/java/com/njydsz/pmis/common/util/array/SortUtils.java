package com.njydsz.pmis.common.util.array;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 排序与搜索工具类
 *
 * <p>提供数组和集合的排序、搜索、重排等操作。
 * 所有方法均对 null 值进行安全处理，避免空指针异常。
 *
 * <p><b>主要功能：</b>
 * <ul>
 *   <li>排序操作：sort、reverseSort、shuffle</li>
 *   <li>搜索操作：binarySearch、linearSearch、findMin、findMax</li>
 *   <li>数组合并：merge、mergeSorted</li>
 *   <li>去重排序：sortDistinct、removeDuplicate</li>
 *   <li>数组重排：reverse、rotate、swap</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class SortUtils {

    private SortUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ==================== 排序方法 ====================

    /**
     * 快速排序 (基本类型)
     */
    public static void sort(int[] arr) {
        if (arr != null) {
            Arrays.sort(arr);
        }
    }

    /**
     * 排序 (对象数组)
     */
    public static <T> void sort(T[] arr, Comparator<? super T> c) {
        if (arr != null) {
            Arrays.sort(arr, c);
        }
    }

    /**
     * 排序 (List)
     */
    public static <T> void sort(List<T> list, Comparator<? super T> c) {
        if (list != null) {
            list.sort(c);
        }
    }

    /**
     * 排序 (自然顺序，要求元素实现 Comparable 接口)
     */
    public static <T extends Comparable<? super T>> void sort(T[] arr) {
        if (arr != null) {
            Arrays.sort(arr);
        }
    }

    /**
     * 排序 List (自然顺序)
     */
    public static <T extends Comparable<? super T>> void sort(List<T> list) {
        if (list != null) {
            list.sort(null);
        }
    }

    /**
     * 降序排序 (对象数组)
     */
    public static <T> void reverseSort(T[] arr, Comparator<? super T> c) {
        if (arr != null) {
            Arrays.sort(arr, c.reversed());
        }
    }

    /**
     * 降序排序 List
     */
    public static <T> void reverseSort(List<T> list, Comparator<? super T> c) {
        if (list != null) {
            list.sort(c.reversed());
        }
    }

    /**
     * 降序排序 (自然顺序)
     */
    public static <T extends Comparable<? super T>> void reverseSort(T[] arr) {
        if (arr != null) {
            Arrays.sort(arr, Comparator.reverseOrder());
        }
    }

    /**
     * 降序排序 List (自然顺序)
     */
    public static <T extends Comparable<? super T>> void reverseSort(List<T> list) {
        if (list != null) {
            list.sort(Comparator.reverseOrder());
        }
    }

    // ==================== 搜索方法 ====================

    /**
     * 二分查找 (依赖已排序数组)
     */
    public static int binarySearch(int[] arr, int key) {
        return arr == null ? -1 : Arrays.binarySearch(arr, key);
    }

    /**
     * 二分查找 (对象数组)
     */
    public static <T> int binarySearch(T[] arr, T key, Comparator<? super T> c) {
        return arr == null ? -1 : Arrays.binarySearch(arr, key, c);
    }

    /**
     * 二分查找 (自然顺序)
     */
    public static <T extends Comparable<? super T>> int binarySearch(T[] arr, T key) {
        return arr == null ? -1 : Arrays.binarySearch(arr, key);
    }

    /**
     * 线性查找
     *
     * @param array 数组
     * @param target 目标值
     * @return 目标值的索引，未找到返回 -1
     */
    public static int linearSearch(int[] array, int target) {
        if (array == null) {
            return -1;
        }
        for (int i = 0; i < array.length; i++) {
            if (array[i] == target) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 线性查找 (对象数组)
     */
    public static <T> int linearSearch(T[] array, T target) {
        if (array == null) {
            return -1;
        }
        for (int i = 0; i < array.length; i++) {
            if (target == null) {
                if (array[i] == null) {
                    return i;
                }
            } else {
                if (target.equals(array[i])) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * 查找最小值
     */
    public static int findMin(int[] array) {
        if (array == null || array.length == 0) {
            throw new IllegalArgumentException("Array cannot be null or empty");
        }
        int min = array[0];
        for (int i = 1; i < array.length; i++) {
            if (array[i] < min) {
                min = array[i];
            }
        }
        return min;
    }

    /**
     * 查找最大值
     */
    public static int findMax(int[] array) {
        if (array == null || array.length == 0) {
            throw new IllegalArgumentException("Array cannot be null or empty");
        }
        int max = array[0];
        for (int i = 1; i < array.length; i++) {
            if (array[i] > max) {
                max = array[i];
            }
        }
        return max;
    }

    /**
     * 查找最小值 (对象数组)
     */
    public static <T extends Comparable<? super T>> T findMin(T[] array) {
        if (array == null || array.length == 0) {
            throw new IllegalArgumentException("Array cannot be null or empty");
        }
        T min = array[0];
        for (int i = 1; i < array.length; i++) {
            if (array[i] != null && min != null && array[i].compareTo(min) < 0) {
                min = array[i];
            }
        }
        return min;
    }

    /**
     * 查找最大值 (对象数组)
     */
    public static <T extends Comparable<? super T>> T findMax(T[] array) {
        if (array == null || array.length == 0) {
            throw new IllegalArgumentException("Array cannot be null or empty");
        }
        T max = array[0];
        for (int i = 1; i < array.length; i++) {
            if (array[i] != null && max != null && array[i].compareTo(max) > 0) {
                max = array[i];
            }
        }
        return max;
    }

    // ==================== 数组合并 ====================

    /**
     * 合并两个已排序的数组
     *
     * @param array1 已排序的数组 1
     * @param array2 已排序的数组 2
     * @return 合并后的已排序数组
     */
    public static int[] mergeSorted(int[] array1, int[] array2) {
        if (array1 == null || array1.length == 0) {
            return array2 == null ? new int[0] : Arrays.copyOf(array2, array2.length);
        }
        if (array2 == null || array2.length == 0) {
            return Arrays.copyOf(array1, array1.length);
        }

        int[] result = new int[array1.length + array2.length];
        int i = 0, j = 0, k = 0;

        while (i < array1.length && j < array2.length) {
            if (array1[i] <= array2[j]) {
                result[k++] = array1[i++];
            } else {
                result[k++] = array2[j++];
            }
        }

        while (i < array1.length) {
            result[k++] = array1[i++];
        }

        while (j < array2.length) {
            result[k++] = array2[j++];
        }

        return result;
    }

    /**
     * 合并两个已排序的对象数组
     */
    public static <T extends Comparable<? super T>> T[] mergeSorted(T[] array1, T[] array2) {
        if (array1 == null || array1.length == 0) {
            if (array2 == null) {
                return null;
            }
            return Arrays.copyOf(array2, array2.length);
        }
        if (array2 == null || array2.length == 0) {
            return Arrays.copyOf(array1, array1.length);
        }

        
        T[] result = (T[]) Array.newInstance(
            array1.getClass().getComponentType(), array1.length + array2.length);
        
        int i = 0, j = 0, k = 0;

        while (i < array1.length && j < array2.length) {
            if (array1[i].compareTo(array2[j]) <= 0) {
                result[k++] = array1[i++];
            } else {
                result[k++] = array2[j++];
            }
        }

        while (i < array1.length) {
            result[k++] = array1[i++];
        }

        while (j < array2.length) {
            result[k++] = array2[j++];
        }

        return result;
    }

    // ==================== 去重排序 ====================

    /**
     * 排序并去重
     */
    public static int[] sortDistinct(int[] array) {
        if (array == null || array.length == 0) {
            return new int[0];
        }
        Arrays.sort(array);
        int[] temp = new int[array.length];
        int count = 0;
        
        for (int i = 0; i < array.length; i++) {
            if (i == 0 || array[i] != array[i - 1]) {
                temp[count++] = array[i];
            }
        }
        
        return Arrays.copyOf(temp, count);
    }

    /**
     * 排序并去重 (对象数组)
     */
    
    public static <T extends Comparable<? super T>> T[] sortDistinct(T[] array) {
        if (array == null || array.length == 0) {
            Class<T> clazz = (Class<T>) (array != null ? array.getClass().getComponentType() : Object.class);
            T[] empty = (T[]) Array.newInstance(clazz, 0);
            return empty;
        }
        
        Arrays.sort(array);
        T[] temp = (T[]) Array.newInstance(
            array.getClass().getComponentType(), array.length);
        int count = 0;
        
        for (int i = 0; i < array.length; i++) {
            if (i == 0 || array[i].compareTo(array[i - 1]) != 0) {
                temp[count++] = array[i];
            }
        }
        
        return Arrays.copyOf(temp, count);
    }

    // ==================== 数组重排 ====================

    /**
     * 反转数组
     */
    public static void reverse(int[] array) {
        if (array == null || array.length <= 1) {
            return;
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
    }

    /**
     * 反转对象数组
     */
    public static <T> void reverse(T[] array) {
        if (array == null || array.length <= 1) {
            return;
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
    }

    /**
     * 反转 List
     */
    public static <T> void reverse(List<T> list) {
        if (list != null && list.size() > 1) {
            int left = 0;
            int right = list.size() - 1;
            while (left < right) {
                T temp = list.get(left);
                list.set(left, list.get(right));
                list.set(right, temp);
                left++;
                right--;
            }
        }
    }

    /**
     * 随机打乱数组
     */
    public static void shuffle(int[] array) {
        if (array == null || array.length <= 1) {
            return;
        }
        for (int i = array.length - 1; i > 0; i--) {
            int index = ThreadLocalRandom.current().nextInt(i + 1);
            int temp = array[index];
            array[index] = array[i];
            array[i] = temp;
        }
    }

    /**
     * 随机打乱对象数组
     */
    public static <T> void shuffle(T[] array) {
        if (array == null || array.length <= 1) {
            return;
        }
        for (int i = array.length - 1; i > 0; i--) {
            int index = ThreadLocalRandom.current().nextInt(i + 1);
            T temp = array[index];
            array[index] = array[i];
            array[i] = temp;
        }
    }

    /**
     * 随机打乱 List
     */
    public static <T> void shuffle(List<T> list) {
        if (list != null && list.size() > 1) {
            for (int i = list.size() - 1; i > 0; i--) {
                int index = ThreadLocalRandom.current().nextInt(i + 1);
                T temp = list.get(index);
                list.set(index, list.get(i));
                list.set(i, temp);
            }
        }
    }

    /**
     * 旋转数组 (向右旋转 k 个位置)
     * 例如：[1,2,3,4,5] 旋转 2 位 -> [4,5,1,2,3]
     */
    public static void rotate(int[] array, int k) {
        if (array == null || array.length <= 1) {
            return;
        }
        k = k % array.length;
        if (k < 0) {
            k += array.length;
        }
        if (k == 0) {
            return;
        }
        
        reverse(array, 0, array.length - 1);
        reverse(array, 0, k - 1);
        reverse(array, k, array.length - 1);
    }

    /**
     * 旋转对象数组
     */
    public static <T> void rotate(T[] array, int k) {
        if (array == null || array.length <= 1) {
            return;
        }
        k = k % array.length;
        if (k < 0) {
            k += array.length;
        }
        if (k == 0) {
            return;
        }
        
        reverse(array, 0, array.length - 1);
        reverse(array, 0, k - 1);
        reverse(array, k, array.length - 1);
    }

    /**
     * 反转数组的指定范围
     */
    private static void reverse(int[] array, int start, int end) {
        while (start < end) {
            int temp = array[start];
            array[start] = array[end];
            array[end] = temp;
            start++;
            end--;
        }
    }

    /**
     * 反转对象数组的指定范围
     */
    private static <T> void reverse(T[] array, int start, int end) {
        while (start < end) {
            T temp = array[start];
            array[start] = array[end];
            array[end] = temp;
            start++;
            end--;
        }
    }

    /**
     * 交换数组元素
     */
    public static void swap(int[] arr, int i, int j) {
        if (arr == null || i < 0 || j < 0 || i >= arr.length || j >= arr.length) {
            return;
        }
        int temp = arr[i];
        arr[i] = arr[j];
        arr[j] = temp;
    }

    /**
     * 交换对象数组元素
     */
    public static <T> void swap(T[] arr, int i, int j) {
        if (arr == null || i < 0 || j < 0 || i >= arr.length || j >= arr.length) {
            return;
        }
        T temp = arr[i];
        arr[i] = arr[j];
        arr[j] = temp;
    }

    /**
     * 交换 List 元素
     */
    public static <T> void swap(List<T> list, int i, int j) {
        if (list == null || i < 0 || j < 0 || i >= list.size() || j >= list.size()) {
            return;
        }
        T temp = list.get(i);
        list.set(i, list.get(j));
        list.set(j, temp);
    }

    // ==================== 其他实用方法 ====================

    /**
     * 判断数组是否已排序 (升序)
     */
    public static boolean isSorted(int[] array) {
        if (array == null || array.length <= 1) {
            return true;
        }
        for (int i = 1; i < array.length; i++) {
            if (array[i] < array[i - 1]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断数组是否已排序 (升序，对象数组)
     */
    public static <T extends Comparable<? super T>> boolean isSorted(T[] array) {
        if (array == null || array.length <= 1) {
            return true;
        }
        for (int i = 1; i < array.length; i++) {
            if (array[i] != null && array[i - 1] != null && 
                array[i].compareTo(array[i - 1]) < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断数组是否已排序 (降序)
     */
    public static boolean isSortedDesc(int[] array) {
        if (array == null || array.length <= 1) {
            return true;
        }
        for (int i = 1; i < array.length; i++) {
            if (array[i] > array[i - 1]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取数组的中位数
     */
    public static double median(int[] array) {
        if (array == null || array.length == 0) {
            throw new IllegalArgumentException("Array cannot be null or empty");
        }
        int[] sorted = Arrays.copyOf(array, array.length);
        Arrays.sort(sorted);
        int mid = sorted.length / 2;
        if (sorted.length % 2 == 0) {
            return (sorted[mid - 1] + sorted[mid]) / 2.0;
        } else {
            return sorted[mid];
        }
    }

    /**
     * 计算数组的和
     */
    public static int sum(int[] array) {
        if (array == null || array.length == 0) {
            return 0;
        }
        int total = 0;
        for (int value : array) {
            total += value;
        }
        return total;
    }

    /**
     * 计算数组的平均值
     */
    public static double average(int[] array) {
        if (array == null || array.length == 0) {
            throw new IllegalArgumentException("Array cannot be null or empty");
        }
        return sum(array) / (double) array.length;
    }
}
