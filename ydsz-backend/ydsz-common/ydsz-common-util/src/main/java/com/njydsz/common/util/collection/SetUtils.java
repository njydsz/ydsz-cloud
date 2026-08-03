package com.njydsz.common.util.collection;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Set 工具类 - 增强版
 * 
 * <p>参考互联网大厂（阿里巴巴、Google Guava、Apache Commons Collections）最佳实践设计，
 * 提供全面、高效、安全的 Set 操作方法。</p>
 * 
 * <p><b>主要功能：</b>
 * <ul>
 *   <li>判空检查：isEmpty、isNotEmpty</li>
 *   <li>集合创建：newHashSet、newLinkedHashSet、newTreeSet、emptySet、of</li>
 *   <li>集合运算：union、intersection、difference、symmetricDifference</li>
 *   <li>转换操作：transform、convertSet</li>
 *   <li>过滤操作：filter</li>
 *   <li>查找操作：find、findAll、containsAny、containsAll</li>
 *   <li>排序操作：toSortedSet</li>
 *   <li>其他操作：powerSet、cartesianProduct、distinct</li>
 * </ul>
 * 
 * <p><b>相比 Apache Commons Collections 的增强：</b>
 * <ul>
 *   <li>更全面的集合创建方法（支持多种 Set 实现）</li>
 *   <li>提供 Lambda 表达式支持的转换和过滤方法</li>
 *   <li>支持幂集、笛卡尔积等高级操作</li>
 *   <li>零第三方依赖，纯 JDK 实现</li>
 *   <li>更好的空指针安全防护</li>
 * </ul>
 * 
 * <p><b>使用示例：</b>
 * <pre>
 * // 1. 判空检查
 * if (SetUtils.isEmpty(set)) { ... }
 * 
 * // 2. 集合创建
 * Set&lt;String&gt; set = SetUtils.newHashSet("a", "b", "c");
 * 
 * // 3. 集合运算
 * Set&lt;Integer&gt; union = SetUtils.union(set1, set2);
 * Set&lt;Integer&gt; intersection = SetUtils.intersection(set1, set2);
 * 
 * // 4. 幂集
 * Set&lt;Set&lt;Integer&gt;&gt; powerSet = SetUtils.powerSet(set);
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */
public class SetUtils {

    private SetUtils() {
        throw new IllegalStateException("Utility class - cannot be instantiated");
    }

    // ==================== 判空方法 ====================

    /**
     * 判断 Set 是否为空
     *
     * @param set Set 对象
     * @return 如果 set 为 null 或 empty 则返回 true
     */
    public static boolean isEmpty(Set<?> set) {
        return set == null || set.isEmpty();
    }

    /**
     * 判断 Set 是否不为空
     *
     * @param set Set 对象
     * @return 如果 set 不为 null 且不为 empty 则返回 true
     */
    public static boolean isNotEmpty(Set<?> set) {
        return !isEmpty(set);
    }

    // ==================== 集合创建方法 ====================

    /**
     * 创建新的 HashSet
     *
     * @param <T> 元素类型
     * @return 新的 HashSet
     */
    public static <T> Set<T> newHashSet() {
        return new HashSet<>();
    }

    /**
     * 创建新的 HashSet，带初始容量
     *
     * @param initialCapacity 初始容量
     * @param <T> 元素类型
     * @return 新的 HashSet
     */
    public static <T> Set<T> newHashSet(int initialCapacity) {
        return new HashSet<>(initialCapacity);
    }

    /**
     * 创建新的 HashSet，包含指定元素
     *
     * @param elements 元素数组
     * @param <T> 元素类型
     * @return 新的 HashSet
     */
    public static <T> Set<T> newHashSet(T[] elements) {
        Set<T> set = new HashSet<>(elements.length);
        Collections.addAll(set, elements);
        return set;
    }

    /**
     * 创建新的 HashSet，包含指定集合的元素
     *
     * @param collection 集合
     * @param <T> 元素类型
     * @return 新的 HashSet
     */
    public static <T> Set<T> newHashSet(Collection<? extends T> collection) {
        if (collection == null) {
            return new HashSet<>();
        }
        return new HashSet<>(collection);
    }

    /**
     * 创建新的 LinkedHashSet
     *
     * @param <T> 元素类型
     * @return 新的 LinkedHashSet
     */
    public static <T> Set<T> newLinkedHashSet() {
        return new LinkedHashSet<>();
    }

    /**
     * 创建新的 LinkedHashSet，包含指定元素
     *
     * @param elements 元素数组
     * @param <T> 元素类型
     * @return 新的 LinkedHashSet
     */
    public static <T> Set<T> newLinkedHashSet(T[] elements) {
        Set<T> set = new LinkedHashSet<>();
        Collections.addAll(set, elements);
        return set;
    }

    /**
     * 创建新的 TreeSet
     *
     * @param <T> 元素类型（必须实现 Comparable）
     * @return 新的 TreeSet
     */
    public static <T extends Comparable<? super T>> Set<T> newTreeSet() {
        return new TreeSet<>();
    }

    /**
     * 创建新的 TreeSet，带比较器
     *
     * @param comparator 比较器
     * @param <T> 元素类型
     * @return 新的 TreeSet
     */
    public static <T> Set<T> newTreeSet(Comparator<? super T> comparator) {
        return new TreeSet<>(comparator);
    }

    /**
     * 创建新的 TreeSet，包含指定元素
     *
     * @param elements 元素数组
     * @param <T> 元素类型（必须实现 Comparable）
     * @return 新的 TreeSet
     */
    public static <T extends Comparable<? super T>> Set<T> newTreeSet(T[] elements) {
        Set<T> set = new TreeSet<>();
        Collections.addAll(set, elements);
        return set;
    }

    /**
     * 创建空的不可变 Set
     *
     * @param <T> 元素类型
     * @return 空的不可变 Set
     */
    public static <T> Set<T> emptySet() {
        return Collections.emptySet();
    }

    /**
     * 创建单元素的不可变 Set
     *
     * @param element 元素
     * @param <T> 元素类型
     * @return 不可变 Set
     */
    public static <T> Set<T> of(T element) {
        return Collections.singleton(element);
    }

    /**
     * 创建双元素的不可变 Set
     *
     * @param e1 元素 1
     * @param e2 元素 2
     * @param <T> 元素类型
     * @return 不可变 Set
     */
    public static <T> Set<T> of(T e1, T e2) {
        Set<T> set = new HashSet<>(2);
        set.add(e1);
        set.add(e2);
        return Collections.unmodifiableSet(set);
    }

    /**
     * 创建多元素的不可变 Set
     *
     * @param elements 元素数组
     * @param <T> 元素类型
     * @return 不可变 Set
     */
    public static <T> Set<T> of(T[] elements) {
        if (elements == null || elements.length == 0) {
            return emptySet();
        }
        Set<T> set = new HashSet<>(elements.length);
        Collections.addAll(set, elements);
        return Collections.unmodifiableSet(set);
    }

    // ==================== 集合添加方法 ====================

    /**
     * 添加元素到 Set
     *
     * @param set Set 对象
     * @param element 元素
     * @param <T> 元素类型
     * @return 添加后的 Set
     */
    public static <T> Set<T> add(Set<T> set, T element) {
        if (set == null) {
            set = new HashSet<>();
        }
        set.add(element);
        return set;
    }

    /**
     * 添加多个元素到 Set
     *
     * @param set Set 对象
     * @param elements 元素数组
     * @param <T> 元素类型
     * @return 添加后的 Set
     */
    public static <T> Set<T> addAll(Set<T> set, T[] elements) {
        if (set == null) {
            set = new HashSet<>();
        }
        if (elements != null) {
            Collections.addAll(set, elements);
        }
        return set;
    }

    /**
     * 添加集合到 Set
     *
     * @param set Set 对象
     * @param collection 集合
     * @param <T> 元素类型
     * @return 添加后的 Set
     */
    public static <T> Set<T> addAll(Set<T> set, Collection<? extends T> collection) {
        if (set == null) {
            set = new HashSet<>();
        }
        if (collection != null && !collection.isEmpty()) {
            set.addAll(collection);
        }
        return set;
    }

    // ==================== 集合移除方法 ====================

    /**
     * 移除指定元素
     *
     * @param set Set 对象
     * @param element 元素
     * @param <T> 元素类型
     * @return 是否移除成功
     */
    public static <T> boolean remove(Set<T> set, T element) {
        if (isEmpty(set)) {
            return false;
        }
        return set.remove(element);
    }

    /**
     * 移除所有匹配的元素
     *
     * @param set Set 对象
     * @param elements 元素数组
     * @param <T> 元素类型
     * @return 是否移除成功
     */
    public static <T> boolean removeAll(Set<T> set, T[] elements) {
        if (isEmpty(set) || elements == null || elements.length == 0) {
            return false;
        }
        return set.removeAll(Arrays.asList(elements));
    }

    /**
     * 移除所有匹配集合的元素
     *
     * @param set Set 对象
     * @param collection 集合
     * @param <T> 元素类型
     * @return 是否移除成功
     */
    public static <T> boolean removeAll(Set<T> set, Collection<?> collection) {
        if (isEmpty(set) || collection == null || collection.isEmpty()) {
            return false;
        }
        return set.removeAll(collection);
    }

    // ==================== 集合运算方法 ====================

    /**
     * 并集
     *
     * @param set1 第一个 Set
     * @param set2 第二个 Set
     * @param <T> 元素类型
     * @return 并集后的新 Set
     */
    public static <T> Set<T> union(Set<T> set1, Set<T> set2) {
        Set<T> result = newHashSet();
        if (isNotEmpty(set1)) {
            result.addAll(set1);
        }
        if (isNotEmpty(set2)) {
            result.addAll(set2);
        }
        return result;
    }

    /**
     * 并集（返回不可变视图）
     *
     * @param set1 第一个 Set
     * @param set2 第二个 Set
     * @param <T> 元素类型
     * @return 并集的不可变视图
     */
    public static <T> Set<T> unionView(Set<T> set1, Set<T> set2) {
        if (isEmpty(set1)) {
            return isEmpty(set2) ? emptySet() : Collections.unmodifiableSet(set2);
        }
        if (isEmpty(set2)) {
            return Collections.unmodifiableSet(set1);
        }
        return Collections.unmodifiableSet(union(set1, set2));
    }

    /**
     * 交集
     *
     * @param set1 第一个 Set
     * @param set2 第二个 Set
     * @param <T> 元素类型
     * @return 交集后的新 Set
     */
    public static <T> Set<T> intersection(Set<T> set1, Set<T> set2) {
        if (isEmpty(set1) || isEmpty(set2)) {
            return newHashSet();
        }
        Set<T> result = newHashSet();
        Set<T> smaller = set1.size() < set2.size() ? set1 : set2;
        Set<T> larger = set1.size() < set2.size() ? set2 : set1;
        for (T item : smaller) {
            if (larger.contains(item)) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * 差集（set1 - set2）
     *
     * @param set1 第一个 Set
     * @param set2 第二个 Set
     * @param <T> 元素类型
     * @return 差集后的新 Set
     */
    public static <T> Set<T> difference(Set<T> set1, Set<T> set2) {
        if (isEmpty(set1)) {
            return newHashSet();
        }
        if (isEmpty(set2)) {
            return newHashSet(set1);
        }
        Set<T> result = newHashSet(set1);
        result.removeAll(set2);
        return result;
    }

    /**
     * 对称差集
     *
     * @param set1 第一个 Set
     * @param set2 第二个 Set
     * @param <T> 元素类型
     * @return 对称差集后的新 Set
     */
    public static <T> Set<T> symmetricDifference(Set<T> set1, Set<T> set2) {
        Set<T> result = newHashSet();
        if (isEmpty(set1) && isEmpty(set2)) {
            return result;
        }
        if (isEmpty(set1)) {
            result.addAll(set2);
            return result;
        }
        if (isEmpty(set2)) {
            result.addAll(set1);
            return result;
        }
        for (T item : set1) {
            if (!set2.contains(item)) {
                result.add(item);
            }
        }
        for (T item : set2) {
            if (!set1.contains(item)) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * 补集
     *
     * @param set 源 Set
     * @param universe 全集
     * @param <T> 元素类型
     * @return 补集后的新 Set
     */
    public static <T> Set<T> complement(Set<T> set, Set<T> universe) {
        if (isEmpty(universe)) {
            return newHashSet();
        }
        if (isEmpty(set)) {
            return newHashSet(universe);
        }
        Set<T> result = newHashSet(universe);
        result.removeAll(set);
        return result;
    }

    // ==================== Set 转换方法 ====================

    /**
     * 转换 Set 元素类型
     *
     * @param set 源 Set
     * @param converter 转换器
     * @param <T> 源元素类型
     * @param <R> 目标元素类型
     * @return 转换后的 Set
     */
    public static <T, R> Set<R> convertSet(Set<T> set, Function<? super T, ? extends R> converter) {
        if (isEmpty(set) || converter == null) {
            return newHashSet();
        }
        return set.stream()
                .map(converter)
                .collect(Collectors.toSet());
    }

    /**
     * 转换 Set 元素类型（带过滤）
     *
     * @param set 源 Set
     * @param converter 转换器
     * @param filter 过滤器
     * @param <T> 源元素类型
     * @param <R> 目标元素类型
     * @return 转换后的 Set
     */
    public static <T, R> Set<R> convertSet(Set<T> set, Function<? super T, ? extends R> converter,
                                             Predicate<? super T> filter) {
        if (isEmpty(set) || converter == null || filter == null) {
            return newHashSet();
        }
        return set.stream()
                .filter(filter)
                .map(converter)
                .collect(Collectors.toSet());
    }

    /**
     * 过滤 Set 元素
     *
     * @param set 源 Set
     * @param predicate 过滤条件
     * @param <T> 元素类型
     * @return 过滤后的 Set
     */
    public static <T> Set<T> filter(Set<T> set, Predicate<? super T> predicate) {
        if (isEmpty(set) || predicate == null) {
            return newHashSet();
        }
        return set.stream()
                .filter(predicate)
                .collect(Collectors.toSet());
    }

    // ==================== 查找方法 ====================

    /**
     * 查找第一个匹配的元素
     *
     * @param set 源 Set
     * @param predicate 查找条件
     * @param <T> 元素类型
     * @return 匹配的元素，如果未找到则返回 null
     */
    public static <T> T find(Set<T> set, Predicate<? super T> predicate) {
        if (isEmpty(set) || predicate == null) {
            return null;
        }
        return set.stream()
                .filter(predicate)
                .findFirst()
                .orElse(null);
    }

    /**
     * 查找所有匹配的元素
     *
     * @param set 源 Set
     * @param predicate 查找条件
     * @param <T> 元素类型
     * @return 匹配的元素 Set
     */
    public static <T> Set<T> findAll(Set<T> set, Predicate<? super T> predicate) {
        return filter(set, predicate);
    }

    /**
     * 判断 Set 是否包含任意一个给定的元素
     *
     * @param set Set 对象
     * @param elements 元素数组
     * @param <T> 元素类型
     * @return 如果包含任意一个元素则返回 true
     */
    @SafeVarargs
    public static <T> boolean containsAny(Set<T> set, T... elements) {
        if (isEmpty(set) || elements == null || elements.length == 0) {
            return false;
        }
        for (T element : elements) {
            if (set.contains(element)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断 Set 是否包含所有给定的元素
     *
     * <p>空集是任意集合的子集：当 elements 为 null 或空数组时返回 true。
     *
     * @param set Set 对象
     * @param elements 元素数组
     * @param <T> 元素类型
     * @return 如果包含所有元素则返回 true；elements 为空时返回 true
     */
    @SafeVarargs
    public static <T> boolean containsAll(Set<T> set, T... elements) {
        if (elements == null || elements.length == 0) {
            return true;
        }
        if (isEmpty(set)) {
            return false;
        }
        for (T element : elements) {
            if (!set.contains(element)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断 Set 是否包含所有给定集合的元素
     *
     * <p>空集是任意集合的子集：当 collection 为 null 或空时返回 true。
     *
     * @param set Set 对象
     * @param collection 集合
     * @param <T> 元素类型
     * @return 如果包含所有元素则返回 true；collection 为空时返回 true
     */
    public static <T> boolean containsAll(Set<T> set, Collection<?> collection) {
        if (collection == null || collection.isEmpty()) {
            return true;
        }
        if (isEmpty(set)) {
            return false;
        }
        return set.containsAll(collection);
    }

    // ==================== 排序方法 ====================

    /**
     * 转换为有序 Set（自然排序）
     *
     * @param set Set 对象
     * @param <T> 元素类型（必须实现 Comparable）
     * @return 排序后的 TreeSet
     */
    public static <T extends Comparable<? super T>> Set<T> toSortedSet(Set<T> set) {
        if (isEmpty(set)) {
            return newTreeSet();
        }
        Set<T> result = newTreeSet();
        result.addAll(set);
        return result;
    }

    // ==================== 其他高级操作 ====================

    /**
     * 幂集（所有子集的集合）
     *
     * @param set 源 Set
     * @param <T> 元素类型
     * @return 幂集
     */
    public static <T> Set<Set<T>> powerSet(Set<T> set) {
        Set<Set<T>> result = newHashSet();
        if (isEmpty(set)) {
            result.add(emptySet());
            return result;
        }

        result.add(emptySet());
        for (T element : set) {
            Set<Set<T>> newSubsets = newHashSet();
            for (Set<T> subset : result) {
                Set<T> newSubset = newHashSet(subset);
                newSubset.add(element);
                newSubsets.add(newSubset);
            }
            result.addAll(newSubsets);
        }
        return result;
    }

    /**
     * 笛卡尔积
     *
     * @param set1 第一个 Set
     * @param set2 第二个 Set
     * @param <T> 第一个元素类型
     * @param <U> 第二个元素类型
     * @return 笛卡尔积（所有可能的二元组）
     */
    public static <T, U> Set<Map.Entry<T, U>> cartesianProduct(Set<T> set1, Set<U> set2) {
        Set<Map.Entry<T, U>> result = newHashSet();
        if (isEmpty(set1) || isEmpty(set2)) {
            return result;
        }
        for (T t : set1) {
            for (U u : set2) {
                result.add(new AbstractMap.SimpleEntry<>(t, u));
            }
        }
        return result;
    }

    /**
     * 获取 Set 的大小，如果为 null 则返回 0
     *
     * @param set Set 对象
     * @return Set 的大小
     */
    public static int size(Set<?> set) {
        return set == null ? 0 : set.size();
    }

    /**
     * 复制 Set
     *
     * @param set Set 对象
     * @param <T> 元素类型
     * @return 复制后的新 Set
     */
    public static <T> Set<T> copy(Set<T> set) {
        if (isEmpty(set)) {
            return newHashSet();
        }
        return newHashSet(set);
    }

    /**
     * 合并多个 Set
     *
     * @param sets 多个 Set
     * @param <T> 元素类型
     * @return 合并后的新 Set
     */
    @SafeVarargs
    public static <T> Set<T> concat(Set<T>... sets) {
        Set<T> result = newHashSet();
        if (sets != null) {
            for (Set<T> set : sets) {
                if (isNotEmpty(set)) {
                    result.addAll(set);
                }
            }
        }
        return result;
    }

    /**
     * 判断两个 Set 是否相等（忽略顺序）
     *
     * @param set1 第一个 Set
     * @param set2 第二个 Set
     * @param <T> 元素类型
     * @return 如果相等则返回 true
     */
    public static <T> boolean isEqual(Set<T> set1, Set<T> set2) {
        if (set1 == set2) {
            return true;
        }
        if (set1 == null || set2 == null) {
            return false;
        }
        return set1.equals(set2);
    }

    /**
     * 判断两个 Set 是否有交集
     *
     * @param set1 第一个 Set
     * @param set2 第二个 Set
     * @param <T> 元素类型
     * @return 如果有交集则返回 true
     */
    public static <T> boolean hasIntersection(Set<T> set1, Set<T> set2) {
        if (isEmpty(set1) || isEmpty(set2)) {
            return false;
        }
        Set<T> smaller = set1.size() < set2.size() ? set1 : set2;
        Set<T> larger = set1.size() < set2.size() ? set2 : set1;
        for (T item : smaller) {
            if (larger.contains(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断 Set 是否是另一个 Set 的子集
     *
     * @param set1 可能的子集
     * @param set2 可能的超集
     * @param <T> 元素类型
     * @return 如果 set1 是 set2 的子集则返回 true
     */
    public static <T> boolean isSubset(Set<T> set1, Set<T> set2) {
        if (isEmpty(set1)) {
            return true;
        }
        if (isEmpty(set2)) {
            return false;
        }
        return set2.containsAll(set1);
    }

    /**
     * 判断 Set 是否是另一个 Set 的超集
     *
     * @param set1 可能的超集
     * @param set2 可能的子集
     * @param <T> 元素类型
     * @return 如果 set1 是 set2 的超集则返回 true
     */
    public static <T> boolean isSuperset(Set<T> set1, Set<T> set2) {
        return isSubset(set2, set1);
    }

    /**
     * 去重（将 List 转换为 Set）
     *
     * @param list List 对象
     * @param <T> 元素类型
     * @return 去重后的 Set
     */
    public static <T> Set<T> distinct(List<T> list) {
        if (list == null || list.isEmpty()) {
            return newHashSet();
        }
        return newHashSet(list);
    }

    /**
     * 去重（自定义比较器）
     *
     * @param list List 对象
     * @param keyExtractor 键提取器
     * @param <T> 元素类型
     * @param <K> 键类型
     * @return 去重后的 List
     */
    public static <T, K> List<T> distinct(List<T> list, Function<? super T, ? extends K> keyExtractor) {
        if (list == null || list.isEmpty() || keyExtractor == null) {
            return ListUtils.newArrayList();
        }
        Set<K> seen = newHashSet();
        List<T> result = ListUtils.newArrayList();
        for (T item : list) {
            K key = keyExtractor.apply(item);
            if (seen.add(key)) {
                result.add(item);
            }
        }
        return result;
    }
}
