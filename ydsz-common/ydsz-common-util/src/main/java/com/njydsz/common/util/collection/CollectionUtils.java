package com.njydsz.common.util.collection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 集合工具类
 *
 * <p>提供项目高频使用的集合操作方法，聚焦于 JDK 未覆盖的能力：
 *
 * <ul>
 *   <li>判空检查：isEmpty / isNotEmpty（支持 Collection、Map、Iterable，null 安全）
 *   <li>类型转换：listToMap、listToGroup、convertList（null 安全的 stream 包装）
 *   <li>过滤操作：filter（null 安全）
 *   <li>查找操作：findFirst、findLast（null 安全，findLast 对 List 做了优化）
 * </ul>
 *
 * <p><b>不提供的能力（直接使用 JDK / Stream API）：</b>
 *
 * <ul>
 *   <li>集合创建 → {@code new ArrayList<>()} / {@link Collections#emptyList()} / {@link
 *       List#of(Object[])}
 *   <li>集合运算（并集/交集/差集）→ {@link java.util.HashSet} + stream
 *   <li>元素增删 → {@link Collection#add(Object)} / {@link Collection#remove(Object)} / {@link
 *       Collection#removeAll(Collection)}
 *   <li>集合切片 → {@link List#subList(int, int)} / {@link java.util.stream.Stream#limit(long)} /
 *       {@link java.util.stream.Stream#skip(long)}
 *   <li>统计方法 → {@link java.util.stream.Stream#max(java.util.Comparator)} / {@link
 *       java.util.stream.IntStream#sum()}
 *   <li>并行流 → {@link Collection#parallelStream()}
 *   <li>不可变/同步包装 → {@link Collections#unmodifiableList(List)} / {@link
 *       Collections#synchronizedList(List)}
 *   <li>包含判断 → {@link Collection#contains(Object)} / {@link Collection#containsAll(Collection)}
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class CollectionUtils {

  private CollectionUtils() {
    throw new UnsupportedOperationException(
        "CollectionUtils is a utility class and cannot be instantiated");
  }

  // ==================== 判空方法 ====================

  /**
   * 判断集合是否为空（null 安全）
   *
   * @param collection 待判断的集合
   * @return 如果为 null 或不包含任何元素返回 true
   */
  public static boolean isEmpty(Collection<?> collection) {
    return collection == null || collection.isEmpty();
  }

  /**
   * 判断 Map 是否为空（null 安全）
   *
   * @param map 待判断的映射
   * @return 如果为 null 或不包含任何键值对返回 true
   */
  public static boolean isEmpty(Map<?, ?> map) {
    return map == null || map.isEmpty();
  }

  /**
   * 判断 Iterable 是否为空（null 安全）
   *
   * @param iterable 待判断的可迭代对象
   * @return 如果为 null 或没有元素返回 true
   */
  public static boolean isEmpty(Iterable<?> iterable) {
    if (iterable == null) {
      return true;
    }
    if (iterable instanceof Collection) {
      return ((Collection<?>) iterable).isEmpty();
    }
    return !iterable.iterator().hasNext();
  }

  /**
   * 判断集合是否不为空（null 安全）
   *
   * @see #isEmpty(Collection)
   * @param collection 集合
   * @return 判断结果
   */
  public static boolean isNotEmpty(Collection<?> collection) {
    return !isEmpty(collection);
  }

  /**
   * 判断 Map 是否不为空（null 安全）
   *
   * @see #isEmpty(Map)
   * @param map 映射
   * @return 判断结果
   */
  public static boolean isNotEmpty(Map<?, ?> map) {
    return !isEmpty(map);
  }

  /**
   * 判断 Iterable 是否不为空（null 安全）
   *
   * @see #isEmpty(Iterable)
   * @param iterable 可迭代对象
   * @return 判断结果
   */
  public static boolean isNotEmpty(Iterable<?> iterable) {
    return !isEmpty(iterable);
  }

  // ==================== 类型转换方法 ====================

  /**
   * 将 List 转换为 Map（以指定字段取值作为键，元素本身作为值）
   *
   * <p>存在重复键时取第一个出现的元素。入参为空返回空 Map。
   *
   * @param list 待转换的列表
   * @param keyMapper 键提取函数
   * @return 转换后的 Map
   * @param <K> 泛型参数类型
   * @param <V> 泛型参数类型
   */
  public static <K, V> Map<K, V> listToMap(
      Collection<V> list, Function<? super V, ? extends K> keyMapper) {
    Objects.requireNonNull(keyMapper, "keyMapper must not be null");
    if (isEmpty(list)) {
      return Collections.emptyMap();
    }
    return list.stream().collect(Collectors.toMap(keyMapper, Function.identity(), (v1, v2) -> v1));
  }

  /**
   * 将 List 转换为 Map（自定义键和值映射）
   *
   * <p>存在重复键时取第一个出现的元素。入参为空返回空 Map。
   *
   * @param list 待转换的列表
   * @param keyMapper 键提取函数
   * @param valueMapper 值提取函数
   * @return 转换后的 Map
   * @param <K> 泛型参数类型
   * @param <V> 泛型参数类型
   * @param <T> 泛型参数类型
   */
  public static <K, V, T> Map<K, V> listToMap(
      Collection<T> list,
      Function<? super T, ? extends K> keyMapper,
      Function<? super T, ? extends V> valueMapper) {
    Objects.requireNonNull(keyMapper, "keyMapper must not be null");
    Objects.requireNonNull(valueMapper, "valueMapper must not be null");
    if (isEmpty(list)) {
      return Collections.emptyMap();
    }
    return list.stream().collect(Collectors.toMap(keyMapper, valueMapper, (v1, v2) -> v1));
  }

  /**
   * 将 List 按指定字段分组
   *
   * @param list 待分组的列表
   * @param classifier 分类函数
   * @return 分组后的 Map，入参为空返回空 Map
   * @param <K> 泛型参数类型
   * @param <V> 泛型参数类型
   */
  public static <K, V> Map<K, List<V>> listToGroup(
      Collection<V> list, Function<? super V, ? extends K> classifier) {
    Objects.requireNonNull(classifier, "classifier must not be null");
    if (isEmpty(list)) {
      return Collections.emptyMap();
    }
    return list.stream().collect(Collectors.groupingBy(classifier));
  }

  /**
   * 将 List 转换为另一种类型的 List（常用于 Entity 转 VO、DTO 转换）
   *
   * @param source 待转换的列表
   * @param mapper 转换函数
   * @return 转换后的列表，入参为空返回空 List
   * @param <T> 泛型参数类型
   * @param <R> 泛型参数类型
   */
  public static <T, R> List<R> convertList(
      Collection<T> source, Function<? super T, ? extends R> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    if (isEmpty(source)) {
      return Collections.emptyList();
    }
    return source.stream().map(mapper).collect(Collectors.toList());
  }

  // ==================== 过滤方法 ====================

  /**
   * 过滤集合（null 安全）
   *
   * @param source 待过滤的集合
   * @param predicate 过滤条件
   * @return 过滤后的列表，入参为空返回空 List
   * @param <T> 泛型参数类型
   */
  public static <T> List<T> filter(Collection<T> source, Predicate<? super T> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    if (isEmpty(source)) {
      return Collections.emptyList();
    }
    return source.stream().filter(predicate).collect(Collectors.toList());
  }

  // ==================== 查找方法 ====================

  /**
   * 安全获取第一个元素（null 安全）
   *
   * @param collection 待查找的集合
   * @return 包含第一个元素的 Optional，集合为空返回空 Optional
   * @param <T> 泛型参数类型
   */
  public static <T> Optional<T> findFirst(Collection<T> collection) {
    if (isEmpty(collection)) {
      return Optional.empty();
    }
    return collection.stream().findFirst();
  }

  /**
   * 安全获取最后一个元素（null 安全，对 List 做了 O(1) 优化）
   *
   * @param collection 待查找的集合
   * @return 包含最后一个元素的 Optional，集合为空返回空 Optional
   * @param <T> 泛型参数类型
   */
  public static <T> Optional<T> findLast(Collection<T> collection) {
    if (isEmpty(collection)) {
      return Optional.empty();
    }
    if (collection instanceof List) {
      List<T> list = (List<T>) collection;
      return Optional.ofNullable(list.get(list.size() - 1));
    }
    return collection.stream().reduce((first, second) -> second);
  }

  // ==================== 集合合并方法 ====================

  /**
   * 将多个集合按顺序合并为一个新 List（null 安全）。
   *
   * <p>所有输入集合都会被展平到同一个可变 ArrayList 中，不会修改原始集合。
   *
   * <p>示例：
   *
   * <pre>{@code
   * concat(List.of(1, 2), List.of(3), null, List.of(4, 5))
   * // => [1, 2, 3, 4, 5]
   * }</pre>
   *
   * @param collections 待合并的集合数组（可包含 null 元素）
   * @param <T> 元素类型
   * @return 合并后的可变 ArrayList；所有输入为 null 时返回空 List
   * @since 26.09.01
   */
  @SafeVarargs
  public static <T> List<T> concat(Collection<? extends T>... collections) {
    if (collections == null || collections.length == 0) {
      return new ArrayList<>();
    }
    int totalSize = 0;
    for (Collection<? extends T> coll : collections) {
      if (coll != null) {
        totalSize += coll.size();
      }
    }
    List<T> result = new ArrayList<>(totalSize);
    for (Collection<? extends T> coll : collections) {
      if (coll != null) {
        result.addAll(coll);
      }
    }
    return result;
  }

  /**
   * 将多个 Iterable 按顺序合并为一个新 List（null 安全）。
   *
   * @param iterables 待合并的 Iterable 数组（可包含 null 元素）
   * @param <T> 元素类型
   * @return 合并后的可变 ArrayList；所有输入为 null 时返回空 List
   * @since 26.09.01
   */
  @SafeVarargs
  public static <T> List<T> concatIterables(Iterable<? extends T>... iterables) {
    if (iterables == null || iterables.length == 0) {
      return new ArrayList<>();
    }
    List<T> result = new ArrayList<>();
    for (Iterable<? extends T> iterable : iterables) {
      if (iterable != null) {
        for (T item : iterable) {
          result.add(item);
        }
      }
    }
    return result;
  }

  /**
   * 将嵌套集合（Collection<Collection<T>>）展平为一个新 List。
   *
   * <p>示例：
   *
   * <pre>{@code
   * flatten(List.of(List.of(1, 2), List.of(3), List.of()))
   * // => [1, 2, 3]
   * }</pre>
   *
   * @param nested 嵌套集合（可包含 null 子集合）
   * @param <T> 元素类型
   * @return 展平后的可变 ArrayList；输入为 null 时返回空 List
   * @since 26.09.01
   */
  public static <T> List<T> flatten(Collection<? extends Collection<T>> nested) {
    if (isEmpty(nested)) {
      return new ArrayList<>();
    }
    List<T> result = new ArrayList<>();
    for (Collection<T> inner : nested) {
      if (inner != null) {
        result.addAll(inner);
      }
    }
    return result;
  }

  /**
   * 将 Iterable<Iterable<T>> 展平为一个新 List。
   *
   * @param nested 嵌套 Iterable（可包含 null 子 Iterable）
   * @param <T> 元素类型
   * @return 展平后的可变 ArrayList；输入为 null 时返回空 List
   * @since 26.09.01
   */
  public static <T> List<T> flattenIterables(Iterable<? extends Iterable<T>> nested) {
    if (nested == null) {
      return new ArrayList<>();
    }
    List<T> result = new ArrayList<>();
    for (Iterable<T> inner : nested) {
      if (inner != null) {
        for (T item : inner) {
          result.add(item);
        }
      }
    }
    return result;
  }

  // ==================== 集合分片方法 ====================

  /**
   * 将集合按指定大小分片（最后一个分片可小于 batchSize）。
   *
   * <p>适用于批量操作场景（如 SQL 批量插入每次最多 500 条、HTTP 批量请求等）。
   *
   * <p>示例：
   *
   * <pre>{@code
   * partition(List.of(1,2,3,4,5), 2)
   * // => [[1, 2], [3, 4], [5]]
   * }</pre>
   *
   * @param source 待分片的集合
   * @param batchSize 每片大小（≥ 1）
   * @param <T> 元素类型
   * @return 分片结果 List，每个元素是一个子 List；输入为空时返回空 List
   * @throws IllegalArgumentException batchSize < 1 时抛出
   * @since 26.09.01
   */
  public static <T> List<List<T>> partition(Collection<T> source, int batchSize) {
    if (batchSize < 1) {
      throw new IllegalArgumentException("batchSize must be >= 1, got " + batchSize);
    }
    if (isEmpty(source)) {
      return new ArrayList<>();
    }
    List<List<T>> result = new ArrayList<>((source.size() + batchSize - 1) / batchSize);
    List<T> currentBatch = new ArrayList<>(batchSize);
    for (T item : source) {
      currentBatch.add(item);
      if (currentBatch.size() == batchSize) {
        result.add(currentBatch);
        currentBatch = new ArrayList<>(batchSize);
      }
    }
    if (!currentBatch.isEmpty()) {
      result.add(currentBatch);
    }
    return result;
  }

  // ==================== 集合去重方法 ====================

  /**
   * 对 List 去重，保留首次出现的元素（保持原顺序）。
   *
   * <p>基于 {@link LinkedHashSet} 实现，时间复杂度 O(n)。
   *
   * <p>示例：
   *
   * <pre>{@code
   * distinct(List.of(1, 2, 2, 3, 1))
   * // => [1, 2, 3]
   * }</pre>
   *
   * @param source 待去重的 List
   * @param <T> 元素类型（需正确实现 equals/hashCode）
   * @return 去重后的可变 ArrayList；输入为 null 时返回空 List
   * @since 26.09.01
   */
  public static <T> List<T> distinct(Collection<T> source) {
    if (isEmpty(source)) {
      return new ArrayList<>();
    }
    return new ArrayList<>(new LinkedHashSet<>(source));
  }

  /**
   * 对 List 按指定键去重，保留首次出现的元素（保持原顺序）。
   *
   * <p>示例：
   *
   * <pre>{@code
   * distinctBy(List.of("aa", "bb", "cc"), String::length)
   * // => ["aa"]（保留第一个长度为 2 的元素）
   * }</pre>
   *
   * @param source 待去重的集合
   * @param keyMapper 键提取函数
   * @param <T> 元素类型
   * @param <K> 键类型（需正确实现 equals/hashCode）
   * @return 去重后的可变 ArrayList；输入为 null 时返回空 List
   * @since 26.09.01
   */
  public static <T, K> List<T> distinctBy(
      Collection<T> source, Function<? super T, ? extends K> keyMapper) {
    Objects.requireNonNull(keyMapper, "keyMapper must not be null");
    if (isEmpty(source)) {
      return new ArrayList<>();
    }
    Set<K> seen = new LinkedHashSet<>();
    List<T> result = new ArrayList<>(source.size());
    for (T item : source) {
      if (seen.add(keyMapper.apply(item))) {
        result.add(item);
      }
    }
    return result;
  }
}
