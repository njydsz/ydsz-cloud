package com.njydsz.pmis.common.util.collection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * List 工具类 - 增强版
 * 
 * <p>参考互联网大厂（阿里巴巴、Google Guava、Apache Commons Collections）最佳实践设计，
 * 提供全面、高效、安全的 List 操作方法。</p>
 * 
 * <p><b>主要功能：</b>
 * <ul>
 *   <li>判空检查：isEmpty、isNotEmpty</li>
 *   <li>安全获取：get、getFirst、getLast、getOrDefault</li>
 *   <li>集合创建：newArrayList、newLinkedList、emptyList、of</li>
 *   <li>元素操作：add、addAll、remove、removeAll</li>
 *   <li>集合运算：union、intersection、difference、symmetricDifference</li>
 *   <li>转换操作：transform、convertList</li>
 *   <li>过滤操作：filter</li>
 *   <li>查找操作：find、findAll、containsAny、containsAll</li>
 *   <li>排序操作：sort、reverseSort、shuffle</li>
 *   <li>分区操作：partition、divide</li>
 *   <li>其他操作：reverse、distinct、flatten</li>
 * </ul>
 * 
 * <p><b>相比 Apache Commons Collections 的增强：</b>
 * <ul>
 *   <li>更全面的元素获取方法（支持默认值）</li>
 *   <li>提供 Lambda 表达式支持的转换和过滤方法</li>
 *   <li>支持集合分区、去重、扁平化等高级操作</li>
 *   <li>零第三方依赖，纯 JDK 实现</li>
 *   <li>更好的空指针安全防护</li>
 * </ul>
 * 
 * <p><b>使用示例：</b>
 * <pre>
 * // 1. 判空检查
 * if (ListUtils.isEmpty(list)) { ... }
 * 
 * // 2. 安全获取
 * String first = ListUtils.getFirst(list);
 * String last = ListUtils.getLast(list, "default");
 * 
 * // 3. 集合创建
 * List&lt;String&gt; list = ListUtils.newArrayList("a", "b", "c");
 * 
 * // 4. 集合运算
 * List&lt;Integer&gt; union = ListUtils.union(list1, list2);
 * 
 * // 5. 分区操作
 * List&lt;List&lt;Integer&gt;&gt; partitions = ListUtils.partition(list, 10);
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
public class ListUtils {

    private ListUtils() {
        throw new IllegalStateException("Utility class - cannot be instantiated");
    }

    // ==================== 判空方法 ====================

    /**
     * 判断 List 是否为空
     *
     * @param list List 对象
     * @return 如果 list 为 null 或 empty 则返回 true
     */
    public static boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }

    /**
     * 判断 List 是否不为空
     *
     * @param list List 对象
     * @return 如果 list 不为 null 且不为 empty 则返回 true
     */
    public static boolean isNotEmpty(List<?> list) {
        return !isEmpty(list);
    }

    // ==================== 安全获取方法 ====================

    /**
     * 安全获取 List 中的元素
     *
     * @param list List 对象
     * @param index 索引
     * @param <T> 元素类型
     * @return 元素，如果 list 为空或索引越界则返回 null
     */
    public static <T> T get(List<T> list, int index) {
        if (isEmpty(list) || index < 0 || index >= list.size()) {
            return null;
        }
        return list.get(index);
    }

    /**
     * 安全获取 List 中的元素，带默认值
     *
     * @param list List 对象
     * @param index 索引
     * @param defaultValue 默认值
     * @param <T> 元素类型
     * @return 元素，如果 list 为空或索引越界则返回 defaultValue
     */
    public static <T> T getOrDefault(List<T> list, int index, T defaultValue) {
        T value = get(list, index);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取第一个元素
     *
     * @param list List 对象
     * @param <T> 元素类型
     * @return 第一个元素，如果 list 为空则返回 null
     */
    public static <T> T getFirst(List<T> list) {
        if (isEmpty(list)) {
            return null;
        }
        return list.get(0);
    }

    /**
     * 获取第一个元素，带默认值
     *
     * @param list List 对象
     * @param defaultValue 默认值
     * @param <T> 元素类型
     * @return 第一个元素，如果 list 为空则返回 defaultValue
     */
    public static <T> T getFirstOrDefault(List<T> list, T defaultValue) {
        T value = getFirst(list);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取最后一个元素
     *
     * @param list List 对象
     * @param <T> 元素类型
     * @return 最后一个元素，如果 list 为空则返回 null
     */
    public static <T> T getLast(List<T> list) {
        if (isEmpty(list)) {
            return null;
        }
        return list.get(list.size() - 1);
    }

    /**
     * 获取最后一个元素，带默认值
     *
     * @param list List 对象
     * @param defaultValue 默认值
     * @param <T> 元素类型
     * @return 最后一个元素，如果 list 为空则返回 defaultValue
     */
    public static <T> T getLastOrDefault(List<T> list, T defaultValue) {
        T value = getLast(list);
        return value != null ? value : defaultValue;
    }

    // ==================== 集合创建方法 ====================

    /**
     * 创建新的 ArrayList
     *
     * @param <T> 元素类型
     * @return 新的 ArrayList
     */
    public static <T> List<T> newArrayList() {
        return new ArrayList<>();
    }

    /**
     * 创建新的 ArrayList，带初始容量
     *
     * @param initialCapacity 初始容量
     * @param <T> 元素类型
     * @return 新的 ArrayList
     */
    public static <T> List<T> newArrayList(int initialCapacity) {
        return new ArrayList<>(initialCapacity);
    }

    /**
     * 创建新的 ArrayList，包含指定元素
     *
     * @param elements 元素数组
     * @param <T> 元素类型
     * @return 新的 ArrayList
     */
    public static <T> List<T> newArrayList(T[] elements) {
        List<T> list = new ArrayList<>(elements.length);
        Collections.addAll(list, elements);
        return list;
    }

    /**
     * 创建新的 ArrayList，包含指定集合的元素
     *
     * @param collection 集合
     * @param <T> 元素类型
     * @return 新的 ArrayList
     */
    public static <T> List<T> newArrayList(Collection<? extends T> collection) {
        if (collection == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(collection);
    }

    /**
     * 创建新的 LinkedList
     *
     * @param <T> 元素类型
     * @return 新的 LinkedList
     */
    public static <T> List<T> newLinkedList() {
        return new LinkedList<>();
    }

    /**
     * 创建新的 LinkedList，包含指定元素
     *
     * @param elements 元素数组
     * @param <T> 元素类型
     * @return 新的 LinkedList
     */
    public static <T> List<T> newLinkedList(T[] elements) {
        List<T> list = new LinkedList<>();
        Collections.addAll(list, elements);
        return list;
    }

    /**
     * 创建空的不可变 List
     *
     * @param <T> 元素类型
     * @return 空的不可变 List
     */
    public static <T> List<T> emptyList() {
        return Collections.emptyList();
    }

    /**
     * 创建单元素的不可变 List
     *
     * @param element 元素
     * @param <T> 元素类型
     * @return 不可变 List
     */
    public static <T> List<T> of(T element) {
        return Collections.singletonList(element);
    }

    /**
     * 创建双元素的不可变 List
     *
     * @param e1 元素 1
     * @param e2 元素 2
     * @param <T> 元素类型
     * @return 不可变 List
     */
    public static <T> List<T> of(T e1, T e2) {
        List<T> list = new ArrayList<>(2);
        list.add(e1);
        list.add(e2);
        return Collections.unmodifiableList(list);
    }

    /**
     * 创建多元素的不可变 List
     *
     * @param elements 元素数组
     * @param <T> 元素类型
     * @return 不可变 List
     */
    public static <T> List<T> of(T[] elements) {
        if (elements == null || elements.length == 0) {
            return emptyList();
        }
        List<T> list = new ArrayList<>(elements.length);
        Collections.addAll(list, elements);
        return Collections.unmodifiableList(list);
    }

    // ==================== 元素添加方法 ====================

    /**
     * 添加元素到 List
     *
     * @param list List 对象
     * @param element 元素
     * @param <T> 元素类型
     * @return 添加后的 List
     */
    public static <T> List<T> add(List<T> list, T element) {
        if (list == null) {
            list = new ArrayList<>();
        }
        list.add(element);
        return list;
    }

    /**
     * 添加多个元素到 List
     *
     * @param list List 对象
     * @param elements 元素数组
     * @param <T> 元素类型
     * @return 添加后的 List
     */
    public static <T> List<T> addAll(List<T> list, T[] elements) {
        if (list == null) {
            list = new ArrayList<>();
        }
        if (elements != null) {
            Collections.addAll(list, elements);
        }
        return list;
    }

    /**
     * 添加集合到 List
     *
     * @param list List 对象
     * @param collection 集合
     * @param <T> 元素类型
     * @return 添加后的 List
     */
    public static <T> List<T> addAll(List<T> list, Collection<? extends T> collection) {
        if (list == null) {
            list = new ArrayList<>();
        }
        if (collection != null && !collection.isEmpty()) {
            list.addAll(collection);
        }
        return list;
    }

    // ==================== 元素移除方法 ====================

    /**
     * 移除指定元素
     *
     * @param list List 对象
     * @param element 元素
     * @param <T> 元素类型
     * @return 是否移除成功
     */
    public static <T> boolean remove(List<T> list, T element) {
        if (isEmpty(list)) {
            return false;
        }
        return list.remove(element);
    }

    /**
     * 移除指定索引的元素
     *
     * @param list List 对象
     * @param index 索引
     * @param <T> 元素类型
     * @return 被移除的元素，如果移除失败则返回 null
     */
    public static <T> T removeAt(List<T> list, int index) {
        if (isEmpty(list) || index < 0 || index >= list.size()) {
            return null;
        }
        return list.remove(index);
    }

    /**
     * 移除所有匹配的元素
     *
     * @param list List 对象
     * @param elements 元素数组
     * @param <T> 元素类型
     * @return 是否移除成功
     */
    public static <T> boolean removeAll(List<T> list, T[] elements) {
        if (isEmpty(list) || elements == null || elements.length == 0) {
            return false;
        }
        return list.removeAll(Arrays.asList(elements));
    }

    /**
     * 移除所有匹配集合的元素
     *
     * @param list List 对象
     * @param collection 集合
     * @param <T> 元素类型
     * @return 是否移除成功
     */
    public static <T> boolean removeAll(List<T> list, Collection<?> collection) {
        if (isEmpty(list) || collection == null || collection.isEmpty()) {
            return false;
        }
        return list.removeAll(collection);
    }

    // ==================== 集合运算方法 ====================

    /**
     * 并集
     *
     * @param list1 第一个 List
     * @param list2 第二个 List
     * @param <T> 元素类型
     * @return 并集后的新 List
     */
    public static <T> List<T> union(List<T> list1, List<T> list2) {
        List<T> result = newArrayList();
        if (isNotEmpty(list1)) {
            result.addAll(list1);
        }
        if (isNotEmpty(list2)) {
            result.addAll(list2);
        }
        return result;
    }

    /**
     * 交集
     *
     * @param list1 第一个 List
     * @param list2 第二个 List
     * @param <T> 元素类型
     * @return 交集后的新 List
     */
    public static <T> List<T> intersection(List<T> list1, List<T> list2) {
        if (isEmpty(list1) || isEmpty(list2)) {
            return newArrayList();
        }
        List<T> result = newArrayList();
        Set<T> set2 = new HashSet<>(list2);
        for (T item : list1) {
            if (set2.contains(item) && !result.contains(item)) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * 差集（list1 - list2）
     *
     * @param list1 第一个 List
     * @param list2 第二个 List
     * @param <T> 元素类型
     * @return 差集后的新 List
     */
    public static <T> List<T> difference(List<T> list1, List<T> list2) {
        if (isEmpty(list1)) {
            return newArrayList();
        }
        if (isEmpty(list2)) {
            return newArrayList(list1);
        }
        List<T> result = newArrayList();
        Set<T> set2 = new HashSet<>(list2);
        for (T item : list1) {
            if (!set2.contains(item)) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * 对称差集
     *
     * @param list1 第一个 List
     * @param list2 第二个 List
     * @param <T> 元素类型
     * @return 对称差集后的新 List
     */
    public static <T> List<T> symmetricDifference(List<T> list1, List<T> list2) {
        List<T> result = newArrayList();
        if (isEmpty(list1) && isEmpty(list2)) {
            return result;
        }
        if (isEmpty(list1)) {
            result.addAll(list2);
            return result;
        }
        if (isEmpty(list2)) {
            result.addAll(list1);
            return result;
        }
        Set<T> set1 = new HashSet<>(list1);
        Set<T> set2 = new HashSet<>(list2);
        for (T item : list1) {
            if (!set2.contains(item)) {
                result.add(item);
            }
        }
        for (T item : list2) {
            if (!set1.contains(item)) {
                result.add(item);
            }
        }
        return result;
    }

    // ==================== List 转换方法 ====================

    /**
     * 转换 List 元素类型
     *
     * @param list 源 List
     * @param converter 转换器
     * @param <T> 源元素类型
     * @param <R> 目标元素类型
     * @return 转换后的 List
     */
    public static <T, R> List<R> convertList(List<T> list, Function<? super T, ? extends R> converter) {
        if (isEmpty(list) || converter == null) {
            return newArrayList();
        }
        return list.stream()
                .map(converter)
                .collect(Collectors.toList());
    }

    /**
     * 转换 List 元素类型（带过滤）
     *
     * @param list 源 List
     * @param converter 转换器
     * @param filter 过滤器
     * @param <T> 源元素类型
     * @param <R> 目标元素类型
     * @return 转换后的 List
     */
    public static <T, R> List<R> convertList(List<T> list, Function<? super T, ? extends R> converter,
                                               Predicate<? super T> filter) {
        if (isEmpty(list) || converter == null || filter == null) {
            return newArrayList();
        }
        return list.stream()
                .filter(filter)
                .map(converter)
                .collect(Collectors.toList());
    }

    /**
     * 过滤 List 元素
     *
     * @param list 源 List
     * @param predicate 过滤条件
     * @param <T> 元素类型
     * @return 过滤后的 List
     */
    public static <T> List<T> filter(List<T> list, Predicate<? super T> predicate) {
        if (isEmpty(list) || predicate == null) {
            return newArrayList();
        }
        return list.stream()
                .filter(predicate)
                .collect(Collectors.toList());
    }

    // ==================== 查找方法 ====================

    /**
     * 查找第一个匹配的元素
     *
     * @param list 源 List
     * @param predicate 查找条件
     * @param <T> 元素类型
     * @return 匹配的元素，如果未找到则返回 null
     */
    public static <T> T find(List<T> list, Predicate<? super T> predicate) {
        if (isEmpty(list) || predicate == null) {
            return null;
        }
        return list.stream()
                .filter(predicate)
                .findFirst()
                .orElse(null);
    }

    /**
     * 查找所有匹配的元素
     *
     * @param list 源 List
     * @param predicate 查找条件
     * @param <T> 元素类型
     * @return 匹配的元素列表
     */
    public static <T> List<T> findAll(List<T> list, Predicate<? super T> predicate) {
        return filter(list, predicate);
    }

    /**
     * 判断 List 是否包含任意一个给定的元素
     *
     * @param list List 对象
     * @param elements 元素数组
     * @param <T> 元素类型
     * @return 如果包含任意一个元素则返回 true
     */
    @SafeVarargs
    public static <T> boolean containsAny(List<T> list, T... elements) {
        if (isEmpty(list) || elements == null || elements.length == 0) {
            return false;
        }
        for (T element : elements) {
            if (list.contains(element)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断 List 是否包含所有给定的元素
     *
     * @param list List 对象
     * @param elements 元素数组
     * @param <T> 元素类型
     * @return 如果包含所有元素则返回 true
     */
    @SafeVarargs
    public static <T> boolean containsAll(List<T> list, T... elements) {
        if (isEmpty(list) || elements == null || elements.length == 0) {
            return false;
        }
        for (T element : elements) {
            if (!list.contains(element)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断 List 是否包含所有给定集合的元素
     *
     * @param list List 对象
     * @param collection 集合
     * @param <T> 元素类型
     * @return 如果包含所有元素则返回 true
     */
    public static <T> boolean containsAll(List<T> list, Collection<?> collection) {
        if (isEmpty(list) || collection == null || collection.isEmpty()) {
            return false;
        }
        return list.containsAll(collection);
    }

    // ==================== 排序方法 ====================

    /**
     * 排序 List（升序）
     *
     * @param list List 对象
     * @param <T> 元素类型（必须实现 Comparable）
     * @return 排序后的新 List
     */
    public static <T extends Comparable<? super T>> List<T> sort(List<T> list) {
        if (isEmpty(list)) {
            return newArrayList();
        }
        List<T> result = newArrayList(list);
        Collections.sort(result);
        return result;
    }

    /**
     * 排序 List（自定义比较器）
     *
     * @param list List 对象
     * @param comparator 比较器
     * @param <T> 元素类型
     * @return 排序后的新 List
     */
    public static <T> List<T> sort(List<T> list, Comparator<? super T> comparator) {
        if (isEmpty(list) || comparator == null) {
            return newArrayList();
        }
        List<T> result = newArrayList(list);
        Collections.sort(result, comparator);
        return result;
    }

    /**
     * 排序 List（降序）
     *
     * @param list List 对象
     * @param <T> 元素类型（必须实现 Comparable）
     * @return 降序排序后的新 List
     */
    public static <T extends Comparable<? super T>> List<T> reverseSort(List<T> list) {
        if (isEmpty(list)) {
            return newArrayList();
        }
        List<T> result = newArrayList(list);
        Collections.sort(result, Collections.reverseOrder());
        return result;
    }

    /**
     * 反转 List
     *
     * @param list List 对象
     * @param <T> 元素类型
     * @return 反转后的新 List
     */
    public static <T> List<T> reverse(List<T> list) {
        if (isEmpty(list)) {
            return newArrayList();
        }
        List<T> result = newArrayList(list);
        Collections.reverse(result);
        return result;
    }

    /**
     * 随机打乱 List
     *
     * @param list List 对象
     * @param <T> 元素类型
     * @return 打乱后的新 List
     */
    public static <T> List<T> shuffle(List<T> list) {
        if (isEmpty(list)) {
            return newArrayList();
        }
        List<T> result = newArrayList(list);
        Collections.shuffle(result);
        return result;
    }

    /**
     * 随机打乱 List（指定随机源）
     *
     * @param list List 对象
     * @param random 随机源
     * @param <T> 元素类型
     * @return 打乱后的新 List
     */
    public static <T> List<T> shuffle(List<T> list, Random random) {
        if (isEmpty(list) || random == null) {
            return newArrayList();
        }
        List<T> result = newArrayList(list);
        Collections.shuffle(result, random);
        return result;
    }

    // ==================== 分区方法 ====================

    /**
     * 分区 List
     *
     * @param list List 对象
     * @param size 每个分区的大小
     * @param <T> 元素类型
     * @return 分区后的 List
     */
    public static <T> List<List<T>> partition(List<T> list, int size) {
        if (isEmpty(list) || size <= 0) {
            return newArrayList();
        }
        List<List<T>> result = newArrayList();
        int total = list.size();
        for (int i = 0; i < total; i += size) {
            int end = Math.min(i + size, total);
            result.add(new ArrayList<>(list.subList(i, end)));
        }
        return result;
    }

    /**
     * 平均分区 List
     *
     * @param list List 对象
     * @param numPartitions 分区数量
     * @param <T> 元素类型
     * @return 分区后的 List
     */
    public static <T> List<List<T>> divide(List<T> list, int numPartitions) {
        if (isEmpty(list) || numPartitions <= 0) {
            return newArrayList();
        }
        List<List<T>> result = newArrayList();
        int total = list.size();
        int size = (int) Math.ceil((double) total / numPartitions);
        for (int i = 0; i < numPartitions && i * size < total; i++) {
            int start = i * size;
            int end = Math.min(start + size, total);
            result.add(new ArrayList<>(list.subList(start, end)));
        }
        return result;
    }

    // ==================== 其他高级操作 ====================

    /**
     * 去重
     *
     * @param list List 对象
     * @param <T> 元素类型
     * @return 去重后的新 List
     */
    public static <T> List<T> distinct(List<T> list) {
        if (isEmpty(list)) {
            return newArrayList();
        }
        return list.stream()
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 去重（自定义比较器）
     *
     * @param list List 对象
     * @param keyExtractor 键提取器
     * @param <T> 元素类型
     * @param <K> 键类型
     * @return 去重后的新 List
     */
    public static <T, K> List<T> distinct(List<T> list, Function<? super T, ? extends K> keyExtractor) {
        if (isEmpty(list) || keyExtractor == null) {
            return newArrayList();
        }
        Set<K> seen = new HashSet<>();
        List<T> result = newArrayList();
        for (T item : list) {
            K key = keyExtractor.apply(item);
            if (seen.add(key)) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * 扁平化嵌套 List
     *
     * @param nestedList 嵌套 List
     * @param <T> 元素类型
     * @return 扁平化后的 List
     */
    public static <T> List<T> flatten(List<List<T>> nestedList) {
        if (isEmpty(nestedList)) {
            return newArrayList();
        }
        return nestedList.stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    /**
     * 获取 List 的大小，如果为 null 则返回 0
     *
     * @param list List 对象
     * @return List 的大小
     */
    public static int size(List<?> list) {
        return list == null ? 0 : list.size();
    }

    /**
     * 获取 List 的索引
     *
     * @param list List 对象
     * @param element 元素
     * @param <T> 元素类型
     * @return 元素的索引，如果未找到则返回 -1
     */
    public static <T> int indexOf(List<T> list, T element) {
        if (isEmpty(list)) {
            return -1;
        }
        return list.indexOf(element);
    }

    /**
     * 获取 List 的最后一个索引
     *
     * @param list List 对象
     * @param element 元素
     * @param <T> 元素类型
     * @return 元素的最后一个索引，如果未找到则返回 -1
     */
    public static <T> int lastIndexOf(List<T> list, T element) {
        if (isEmpty(list)) {
            return -1;
        }
        return list.lastIndexOf(element);
    }

    /**
     * 截取子 List
     *
     * @param list List 对象
     * @param fromIndex 起始索引（包含）
     * @param toIndex 结束索引（不包含）
     * @param <T> 元素类型
     * @return 子 List
     */
    public static <T> List<T> subList(List<T> list, int fromIndex, int toIndex) {
        if (isEmpty(list) || fromIndex >= toIndex || fromIndex < 0 || toIndex > list.size()) {
            return newArrayList();
        }
        return new ArrayList<>(list.subList(fromIndex, toIndex));
    }

    /**
     * 获取前 N 个元素
     *
     * @param list List 对象
     * @param n 数量
     * @param <T> 元素类型
     * @return 前 N 个元素组成的 List
     */
    public static <T> List<T> limit(List<T> list, int n) {
        if (isEmpty(list) || n <= 0) {
            return newArrayList();
        }
        int size = Math.min(n, list.size());
        return new ArrayList<>(list.subList(0, size));
    }

    /**
     * 跳过前 N 个元素
     *
     * @param list List 对象
     * @param n 数量
     * @param <T> 元素类型
     * @return 跳过前 N 个元素后的 List
     */
    public static <T> List<T> skip(List<T> list, int n) {
        if (isEmpty(list) || n <= 0 || n >= list.size()) {
            return isEmpty(list) ? newArrayList() : new ArrayList<>(list);
        }
        return new ArrayList<>(list.subList(n, list.size()));
    }

    /**
     * 复制 List
     *
     * @param list List 对象
     * @param <T> 元素类型
     * @return 复制后的新 List
     */
    public static <T> List<T> copy(List<T> list) {
        if (isEmpty(list)) {
            return newArrayList();
        }
        return new ArrayList<>(list);
    }

    /**
     * 合并多个 List
     *
     * @param lists 多个 List
     * @param <T> 元素类型
     * @return 合并后的新 List
     */
    @SafeVarargs
    public static <T> List<T> concat(List<T>... lists) {
        List<T> result = newArrayList();
        if (lists != null) {
            for (List<T> list : lists) {
                if (isNotEmpty(list)) {
                    result.addAll(list);
                }
            }
        }
        return result;
    }
}
