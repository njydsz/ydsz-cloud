package com.njydsz.pmis.common.util.collection;

import java.lang.reflect.Array;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;

/**
 * 集合工具类
 *
 * <p>提供全面的集合操作方法，功能对标 Apache Commons Collections、Google Guava、
 * Spring CollectionUtils 和 Hutool，并进行了增强和优化。
 *
 * <p><b>主要功能：</b>
 * <ul>
 *   <li>判空检查：isEmpty、isNotEmpty（支持 Collection、Map、Array、Iterator、Iterable）</li>
 *   <li>集合创建：emptyList、emptySet、emptyMap、newList、newSet、newMap 等</li>
 *   <li>类型转换：listToMap、convertList、distinctList、setToList、listToSet 等</li>
 *   <li>分组操作：listToGroup</li>
 *   <li>过滤操作：filter</li>
 *   <li>查找操作：findFirst、findLast、findAll、containsAny、containsAll 等</li>
 *   <li>集合运算：union、intersection、difference、symmetricDifference</li>
 *   <li>元素操作：addElement、addElements、removeElement、removeElements</li>
 *   <li>集合切片：subList、first、last、limit、skip</li>
 *   <li>统计方法：max、min、sum、average、count</li>
 *   <li>并行流优化：parallelConvertList、parallelFilter</li>
 * </ul>
 *
 * <p><b>相比 Apache/Spring/Guava 的增强：</b>
 * <ul>
 *   <li>更全面的 isEmpty/isNotEmpty 判断（支持更多类型）</li>
 *   <li>提供便捷的集合创建方法（of、empty、newList 等）</li>
 *   <li>完整的集合运算支持（并集、交集、差集、对称差集）</li>
 *   <li>提供并行流版本的转换方法，提升大数据集性能</li>
 *   <li>所有方法 null 安全处理，避免空指针异常</li>
 *   <li>提供索引访问方法（get、safeGet）</li>
 *   <li>提供元素统计方法（max、min、sum、average）</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>
 * // 判空
 * if (CollectionUtils.isEmpty(list)) { ... }
 *
 * // 创建集合
 * List&lt;String&gt; list = CollectionUtils.newArrayList("a", "b", "c");
 * Set&lt;Integer&gt; set = CollectionUtils.newHashSet(1, 2, 3);
 *
 * // 集合运算
 * List&lt;String&gt; union = CollectionUtils.union(list1, list2);
 * List&lt;String&gt; intersection = CollectionUtils.intersection(list1, list2);
 *
 * // 转换和过滤
 * List&lt;String&gt; names = CollectionUtils.convertList(users, User::getName);
 * List&lt;User&gt; adults = CollectionUtils.filter(users, u -&gt; u.getAge() &gt;= 18);
 *
 * // 查找
 * Optional&lt;User&gt; first = CollectionUtils.findFirst(users);
 * Optional&lt;User&gt; last = CollectionUtils.findLast(users);
 * boolean hasAdmin = CollectionUtils.containsAny(users, roles, Role::ADMIN);
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
public class CollectionUtils {

    private CollectionUtils() {
        throw new UnsupportedOperationException("CollectionUtils is a utility class and cannot be instantiated");
    }

    // ==================== 判空方法 ====================

    /**
     * 判断集合是否为空
     *
     * <p>如果集合为 null 或不包含任何元素，返回 true
     *
     * @param collection 待判断的集合
     * @return 如果为空返回 true，否则返回 false
     */
    public static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * 判断 Map 是否为空
     *
     * <p>如果映射为 null 或不包含任何键值对，返回 true
     *
     * @param map 待判断的映射
     * @return 如果为空返回 true，否则返回 false
     */
    public static boolean isEmpty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    /**
     * 判断数组是否为空
     *
     * <p>如果数组为 null 或不包含任何元素，返回 true
     *
     * @param array 待判断的数组
     * @return 如果为空返回 true，否则返回 false
     */
    public static boolean isEmpty(Object[] array) {
        return array == null || array.length == 0;
    }

    /**
     * 判断 Iterator 是否为空
     *
     * <p>如果 Iterator 为 null 或没有下一个元素，返回 true
     *
     * @param iterator 待判断的迭代器
     * @return 如果为空返回 true，否则返回 false
     */
    public static boolean isEmpty(Iterator<?> iterator) {
        return iterator == null || !iterator.hasNext();
    }

    /**
     * 判断 Iterable 是否为空
     *
     * <p>如果 Iterable 为 null 或没有元素，返回 true
     *
     * @param iterable 待判断的可迭代对象
     * @return 如果为空返回 true，否则返回 false
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
     * 判断集合是否不为空
     *
     * @param collection 待判断的集合
     * @return 如果不为空返回 true，否则返回 false
     * @see #isEmpty(Collection)
     */
    public static boolean isNotEmpty(Collection<?> collection) {
        return !isEmpty(collection);
    }

    /**
     * 判断 Map 是否不为空
     *
     * @param map 待判断的映射
     * @return 如果不为空返回 true，否则返回 false
     * @see #isEmpty(Map)
     */
    public static boolean isNotEmpty(Map<?, ?> map) {
        return !isEmpty(map);
    }

    /**
     * 判断数组是否不为空
     *
     * @param array 待判断的数组
     * @return 如果不为空返回 true，否则返回 false
     * @see #isEmpty(Object[])
     */
    public static boolean isNotEmpty(Object[] array) {
        return !isEmpty(array);
    }

    /**
     * 判断 Iterator 是否不为空
     *
     * @param iterator 待判断的迭代器
     * @return 如果不为空返回 true，否则返回 false
     * @see #isEmpty(Iterator)
     */
    public static boolean isNotEmpty(Iterator<?> iterator) {
        return !isEmpty(iterator);
    }

    /**
     * 判断 Iterable 是否不为空
     *
     * @param iterable 待判断的可迭代对象
     * @return 如果不为空返回 true，否则返回 false
     * @see #isEmpty(Iterable)
     */
    public static boolean isNotEmpty(Iterable<?> iterable) {
        return !isEmpty(iterable);
    }

    // ==================== 集合创建方法 ====================

    /**
     * 创建空的不可变列表
     *
     * @param <T> 元素类型
     * @return 空的不可变列表
     */
    public static <T> List<T> emptyList() {
        return Collections.emptyList();
    }

    /**
     * 创建空的不可变集合
     *
     * @param <T> 元素类型
     * @return 空的不可变集合
     */
    public static <T> Set<T> emptySet() {
        return Collections.emptySet();
    }

    /**
     * 创建空的不可变映射
     *
     * @param <K> 键类型
     * @param <V> 值类型
     * @return 空的不可变映射
     */
    public static <K, V> Map<K, V> emptyMap() {
        return Collections.emptyMap();
    }

    /**
     * 创建新的 ArrayList
     *
     * @param <T> 元素类型
     * @return 新的 ArrayList
     */
    public static <T> List<T> newList() {
        return new ArrayList<>();
    }

    /**
     * 创建新的 ArrayList，包含指定元素
     *
     * @param elements 初始元素
     * @param <T> 元素类型
     * @return 包含指定元素的 ArrayList
     */
    public static <T> List<T> newList(T[] elements) {
        if (elements == null || elements.length == 0) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(elements));
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
     * 创建新的 HashSet
     *
     * @param <T> 元素类型
     * @return 新的 HashSet
     */
    public static <T> Set<T> newSet() {
        return new HashSet<>();
    }

    /**
     * 创建新的 HashSet，包含指定元素
     *
     * @param elements 初始元素
     * @param <T> 元素类型
     * @return 包含指定元素的 HashSet
     */
    public static <T> Set<T> newSet(T[] elements) {
        if (elements == null || elements.length == 0) {
            return new HashSet<>();
        }
        return new HashSet<>(Arrays.asList(elements));
    }

    /**
     * 创建新的 LinkedHashSet
     *
     * @param <T> 元素类型
     * @return 新的 LinkedHashSet
     */
    public static <T> Set<T> newLinkedSet() {
        return new LinkedHashSet<>();
    }

    /**
     * 创建新的 HashMap
     *
     * @param <K> 键类型
     * @param <V> 值类型
     * @return 新的 HashMap
     */
    public static <K, V> Map<K, V> newMap() {
        return new HashMap<>();
    }

    /**
     * 创建新的 LinkedHashMap
     *
     * @param <K> 键类型
     * @param <V> 值类型
     * @return 新的 LinkedHashMap
     */
    public static <K, V> Map<K, V> newLinkedMap() {
        return new LinkedHashMap<>();
    }

    /**
     * 创建新的 TreeMap
     *
     * @param <K> 键类型
     * @param <V> 值类型
     * @return 新的 TreeMap
     */
    public static <K extends Comparable<? super K>, V> Map<K, V> newTreeMap() {
        return new TreeMap<>();
    }

    /**
     * 创建包含指定元素的不可变列表
     *
     * @param elements 元素数组
     * @param <T> 元素类型
     * @return 不可变列表
     */
    public static <T> List<T> of(T[] elements) {
        if (elements == null || elements.length == 0) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(elements)));
    }

    // ==================== 类型转换方法 ====================

    /**
     * 将 List 转换为 Map
     *
     * <p>以指定字段的取值作为 Map 的键，元素本身作为值。
     * 如果存在重复键，取第一个出现的元素作为值。
     *
     * @param list      待转换的列表
     * @param keyMapper 键提取函数
     * @param <K>       键的类型
     * @param <V>       值的类型
     * @return 转换后的 Map，如果列表为空则返回空 Map
     * @throws NullPointerException 当列表为 null 时抛出
     */
    public static <K, V> Map<K, V> listToMap(Collection<V> list, Function<? super V, ? extends K> keyMapper) {
        Objects.requireNonNull(keyMapper, "keyMapper must not be null");
        if (isEmpty(list)) {
            return Collections.emptyMap();
        }
        return list.stream().collect(Collectors.toMap(keyMapper, Function.identity(), (v1, v2) -> v1));
    }

    /**
     * 将 List 转换为 Map（自定义值映射）
     *
     * <p>以指定字段作为键和值
     *
     * @param list        待转换的列表
     * @param keyMapper   键提取函数
     * @param valueMapper 值提取函数
     * @param <K>         键的类型
     * @param <V>         值的类型
     * @param <T>         源元素类型
     * @return 转换后的 Map，如果列表为空则返回空 Map
     */
    public static <K, V, T> Map<K, V> listToMap(Collection<T> list,
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
     * 将 List 转换为 Map（处理重复键）
     *
     * <p>可以自定义重复键的合并策略
     *
     * @param list         待转换的列表
     * @param keyMapper    键提取函数
     * @param valueMapper  值提取函数
     * @param mergeFunction 重复键合并函数
     * @param <K>          键的类型
     * @param <V>          值的类型
     * @param <T>          源元素类型
     * @return 转换后的 Map，如果列表为空则返回空 Map
     */
    public static <K, V, T> Map<K, V> listToMap(Collection<T> list,
                                                  Function<? super T, ? extends K> keyMapper,
                                                  Function<? super T, ? extends V> valueMapper,
                                                  BinaryOperator<V> mergeFunction) {
        Objects.requireNonNull(keyMapper, "keyMapper must not be null");
        Objects.requireNonNull(valueMapper, "valueMapper must not be null");
        Objects.requireNonNull(mergeFunction, "mergeFunction must not be null");
        if (isEmpty(list)) {
            return Collections.emptyMap();
        }
        return list.stream().collect(Collectors.toMap(keyMapper, valueMapper, mergeFunction));
    }

    /**
     * 将 List 按指定字段分组
     *
     * <p>根据分类函数将列表中的元素分组到不同的列表中
     *
     * @param list       待分组的列表
     * @param classifier 分类函数
     * @param <K>        分组键的类型
     * @param <V>        元素的类型
     * @return 分组后的 Map，如果列表为空则返回空 Map
     */
    public static <K, V> Map<K, List<V>> listToGroup(Collection<V> list, Function<? super V, ? extends K> classifier) {
        Objects.requireNonNull(classifier, "classifier must not be null");
        if (isEmpty(list)) {
            return Collections.emptyMap();
        }
        return list.stream().collect(Collectors.groupingBy(classifier));
    }

    /**
     * 将 List 转换为另一种类型的 List
     *
     * <p>常见用于 Entity 转 VO、DTO 转换等场景
     *
     * @param source 待转换的列表
     * @param mapper 转换函数
     * @param <T>    源元素类型
     * @param <R>    目标元素类型
     * @return 转换后的列表，如果源列表为空则返回空 List
     */
    public static <T, R> List<R> convertList(Collection<T> source, Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        if (isEmpty(source)) {
            return Collections.emptyList();
        }
        return source.stream().map(mapper).collect(Collectors.toList());
    }

    /**
     * 将 List 转换为 Set
     *
     * @param source 待转换的列表
     * @param <T>    元素类型
     * @return 转换后的 Set，如果源列表为空则返回空 Set
     */
    public static <T> Set<T> listToSet(Collection<T> source) {
        if (isEmpty(source)) {
            return Collections.emptySet();
        }
        return source.stream().collect(Collectors.toSet());
    }

    /**
     * 将 Set 转换为 List
     *
     * @param source 待转换的集合
     * @param <T>    元素类型
     * @return 转换后的 List，如果源集合为空则返回空 List
     */
    public static <T> List<T> setToList(Set<T> source) {
        if (isEmpty(source)) {
            return Collections.emptyList();
        }
        return new ArrayList<>(source);
    }

    /**
     * 将数组转换为 List
     *
     * @param array 待转换的数组
     * @param <T>   元素类型
     * @return 转换后的 List，如果数组为空则返回空 List
     */
    public static <T> List<T> arrayToList(T[] array) {
        if (isEmpty(array)) {
            return Collections.emptyList();
        }
        return new ArrayList<>(Arrays.asList(array));
    }

    /**
     * 将 List 转换为数组
     *
     * @param source 待转换的列表
     * @param clazz  数组类型
     * @param <T>    元素类型
     * @return 转换后的数组，如果列表为空则返回空数组
     */
    public static <T> T[] listToArray(Collection<T> source, Class<T[]> clazz) {
        if (isEmpty(source)) {
            return clazz.cast(Array.newInstance(clazz.getComponentType(), 0));
        }
        return source.toArray(clazz.cast(Array.newInstance(clazz.getComponentType(), source.size())));
    }

    /**
     * 转换为去重后的 List
     *
     * <p>使用流的 distinct() 方法去重，基于 equals() 方法判断重复
     *
     * @param collection 待去重的集合
     * @param <T>        元素类型
     * @return 去重后的列表，如果源集合为空则返回空 List
     */
    public static <T> List<T> distinctList(Collection<T> collection) {
        if (isEmpty(collection)) {
            return Collections.emptyList();
        }
        return collection.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 根据指定字段去重
     *
     * @param collection 待去重的集合
     * @param keyExtractor 键提取函数
     * @param <T> 元素类型
     * @param <K> 键类型
     * @return 去重后的列表
     */
    public static <T, K> List<T> distinctBy(Collection<T> collection, Function<? super T, ? extends K> keyExtractor) {
        Objects.requireNonNull(keyExtractor, "keyExtractor must not be null");
        if (isEmpty(collection)) {
            return Collections.emptyList();
        }
        return collection.stream()
                .collect(Collectors.toMap(keyExtractor, Function.identity(), (v1, v2) -> v1))
                .values()
                .stream()
                .collect(Collectors.toList());
    }

    // ==================== 过滤方法 ====================

    /**
     * 过滤集合
     *
     * <p>根据谓词条件筛选集合中的元素
     *
     * @param source    待过滤的集合
     * @param predicate 过滤条件
     * @param <T>       元素类型
     * @return 过滤后的列表，如果源集合为空则返回空 List
     */
    public static <T> List<T> filter(Collection<T> source, Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate must not be null");
        if (isEmpty(source)) {
            return Collections.emptyList();
        }
        return source.stream().filter(predicate).collect(Collectors.toList());
    }

    /**
     * 过滤掉 null 元素
     *
     * @param source 待过滤的集合
     * @param <T>    元素类型
     * @return 过滤后的列表
     */
    public static <T> List<T> filterNonNull(Collection<T> source) {
        return filter(source, Objects::nonNull);
    }

    /**
     * 过滤掉 null 和空白字符串元素
     *
     * @param source 待过滤的集合
     * @return 过滤后的列表
     */
    public static List<String> filterNotBlank(Collection<String> source) {
        if (isEmpty(source)) {
            return Collections.emptyList();
        }
        return source.stream()
                .filter(s -> s != null && !s.trim().isEmpty())
                .collect(Collectors.toList());
    }

    // ==================== 查找方法 ====================

    /**
     * 安全获取第一个元素
     *
     * <p>返回一个 Optional 对象，包含集合的第一个元素（如果存在）
     *
     * @param collection 待查找的集合
     * @param <T>        元素类型
     * @return 包含第一个元素的 Optional，如果集合为空则返回空 Optional
     */
    public static <T> Optional<T> findFirst(Collection<T> collection) {
        if (isEmpty(collection)) {
            return Optional.empty();
        }
        return collection.stream().findFirst();
    }

    /**
     * 安全获取最后一个元素
     *
     * <p>返回一个 Optional 对象，包含集合的最后一个元素（如果存在）
     *
     * @param collection 待查找的集合
     * @param <T>        元素类型
     * @return 包含最后一个元素的 Optional，如果集合为空则返回空 Optional
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

    /**
     * 查找所有符合条件的元素
     *
     * @param source    待查找的集合
     * @param predicate 查找条件
     * @param <T>       元素类型
     * @return 符合条件的列表
     */
    public static <T> List<T> findAll(Collection<T> source, Predicate<? super T> predicate) {
        return filter(source, predicate);
    }

    /**
     * 判断集合是否包含指定元素
     *
     * @param collection 待查找的集合
     * @param element    待查找的元素
     * @param <T>        元素类型
     * @return 如果包含返回 true，否则返回 false
     */
    public static <T> boolean contains(Collection<T> collection, T element) {
        if (isEmpty(collection)) {
            return false;
        }
        return collection.contains(element);
    }

    /**
     * 判断集合是否包含任意一个目标元素
     *
     * @param collection 待查找的集合
     * @param elements   目标元素集合
     * @param <T>        元素类型
     * @return 如果包含任意一个返回 true，否则返回 false
     */
    public static <T> boolean containsAny(Collection<T> collection, Collection<T> elements) {
        if (isEmpty(collection) || isEmpty(elements)) {
            return false;
        }
        for (T element : elements) {
            if (collection.contains(element)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断集合是否包含所有目标元素
     *
     * @param collection 待查找的集合
     * @param elements   目标元素集合
     * @param <T>        元素类型
     * @return 如果包含所有返回 true，否则返回 false
     */
    public static <T> boolean containsAll(Collection<T> collection, Collection<T> elements) {
        if (isEmpty(elements)) {
            return true;
        }
        if (isEmpty(collection)) {
            return false;
        }
        return collection.containsAll(elements);
    }

    /**
     * 判断集合是否包含满足条件的元素
     *
     * @param collection 待查找的集合
     * @param predicate  查找条件
     * @param <T>        元素类型
     * @return 如果包含返回 true，否则返回 false
     */
    public static <T> boolean containsMatch(Collection<T> collection, Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate must not be null");
        if (isEmpty(collection)) {
            return false;
        }
        return collection.stream().anyMatch(predicate);
    }

    // ==================== 集合运算方法 ====================

    /**
     * 求两个集合的并集
     *
     * <p>返回包含两个集合中所有元素的新列表（去重）
     *
     * @param collection1 第一个集合
     * @param collection2 第二个集合
     * @param <T>         元素类型
     * @return 并集列表
     */
    public static <T> List<T> union(Collection<T> collection1, Collection<T> collection2) {
        if (isEmpty(collection1)) {
            return isEmpty(collection2) ? Collections.emptyList() : new ArrayList<>(collection2);
        }
        if (isEmpty(collection2)) {
            return new ArrayList<>(collection1);
        }
        Set<T> set = new HashSet<>(collection1);
        set.addAll(collection2);
        return new ArrayList<>(set);
    }

    /**
     * 求两个集合的交集
     *
     * <p>返回同时存在于两个集合中的元素
     *
     * @param collection1 第一个集合
     * @param collection2 第二个集合
     * @param <T>         元素类型
     * @return 交集列表
     */
    public static <T> List<T> intersection(Collection<T> collection1, Collection<T> collection2) {
        if (isEmpty(collection1) || isEmpty(collection2)) {
            return Collections.emptyList();
        }
        Set<T> set1 = new HashSet<>(collection1);
        return collection2.stream().filter(set1::contains).collect(Collectors.toList());
    }

    /**
     * 求两个集合的差集
     *
     * <p>返回存在于 collection1 但不存在于 collection2 的元素
     *
     * @param collection1 第一个集合
     * @param collection2 第二个集合
     * @param <T>         元素类型
     * @return 差集列表
     */
    public static <T> List<T> difference(Collection<T> collection1, Collection<T> collection2) {
        if (isEmpty(collection1)) {
            return Collections.emptyList();
        }
        if (isEmpty(collection2)) {
            return new ArrayList<>(collection1);
        }
        Set<T> set2 = new HashSet<>(collection2);
        return collection1.stream().filter(e -> !set2.contains(e)).collect(Collectors.toList());
    }

    /**
     * 求两个集合的对称差集
     *
     * <p>返回只存在于其中一个集合的元素（不包含同时存在于两个集合的元素）
     *
     * @param collection1 第一个集合
     * @param collection2 第二个集合
     * @param <T>         元素类型
     * @return 对称差集列表
     */
    public static <T> List<T> symmetricDifference(Collection<T> collection1, Collection<T> collection2) {
        if (isEmpty(collection1)) {
            return isEmpty(collection2) ? Collections.emptyList() : new ArrayList<>(collection2);
        }
        if (isEmpty(collection2)) {
            return new ArrayList<>(collection1);
        }
        Set<T> set1 = new HashSet<>(collection1);
        Set<T> set2 = new HashSet<>(collection2);
        List<T> result = new ArrayList<>();
        for (T e : set1) {
            if (!set2.contains(e)) {
                result.add(e);
            }
        }
        for (T e : set2) {
            if (!set1.contains(e)) {
                result.add(e);
            }
        }
        return result;
    }

    // ==================== 元素操作方法 ====================

    /**
     * 向集合添加元素（如果集合为 null 则创建新列表）
     *
     * @param collection 待操作的集合（可能为 null）
     * @param element    要添加的元素
     * @param <T>        元素类型
     * @return 添加元素后的列表
     */
    public static <T> List<T> addElement(Collection<T> collection, T element) {
        List<T> result = isEmpty(collection) ? new ArrayList<>() : new ArrayList<>(collection);
        result.add(element);
        return result;
    }

    /**
     * 向集合添加多个元素
     *
     * @param collection 待操作的集合（可能为 null）
     * @param elements   要添加的元素集合
     * @param <T>        元素类型
     * @return 添加元素后的列表
     */
    public static <T> List<T> addElements(Collection<T> collection, Collection<T> elements) {
        if (isEmpty(elements)) {
            return isEmpty(collection) ? Collections.emptyList() : new ArrayList<>(collection);
        }
        List<T> result = isEmpty(collection) ? new ArrayList<>() : new ArrayList<>(collection);
        result.addAll(elements);
        return result;
    }

    /**
     * 从集合移除元素
     *
     * @param collection 待操作的集合
     * @param element    要移除的元素
     * @param <T>        元素类型
     * @return 移除元素后的列表
     */
    public static <T> List<T> removeElement(Collection<T> collection, T element) {
        if (isEmpty(collection)) {
            return Collections.emptyList();
        }
        List<T> result = new ArrayList<>(collection);
        result.remove(element);
        return result;
    }

    /**
     * 从集合移除多个元素
     *
     * @param collection 待操作的集合
     * @param elements   要移除的元素集合
     * @param <T>        元素类型
     * @return 移除元素后的列表
     */
    public static <T> List<T> removeElements(Collection<T> collection, Collection<T> elements) {
        if (isEmpty(collection)) {
            return Collections.emptyList();
        }
        if (isEmpty(elements)) {
            return new ArrayList<>(collection);
        }
        List<T> result = new ArrayList<>(collection);
        result.removeAll(elements);
        return result;
    }

    // ==================== 集合切片方法 ====================

    /**
     * 安全获取指定索引的元素
     *
     * @param collection 待查找的集合
     * @param index      索引
     * @param <T>        元素类型
     * @return 指定位置的元素，如果索引越界则返回 null
     */
    public static <T> T get(Collection<T> collection, int index) {
        if (isEmpty(collection)) {
            return null;
        }
        if (collection instanceof List) {
            List<T> list = (List<T>) collection;
            if (index >= 0 && index < list.size()) {
                return list.get(index);
            }
            return null;
        }
        if (index < 0) {
            return null;
        }
        return collection.stream().skip(index).findFirst().orElse(null);
    }

    /**
     * 安全获取指定索引的元素，如果越界返回默认值
     *
     * @param collection 待查找的集合
     * @param index      索引
     * @param defaultValue 默认值
     * @param <T>        元素类型
     * @return 指定位置的元素或默认值
     */
    public static <T> T safeGet(Collection<T> collection, int index, T defaultValue) {
        T result = get(collection, index);
        return result != null ? result : defaultValue;
    }

    /**
     * 获取集合的前 N 个元素
     *
     * @param collection 待切片的集合
     * @param n          元素个数
     * @param <T>        元素类型
     * @return 前 N 个元素组成的列表
     */
    public static <T> List<T> first(Collection<T> collection, int n) {
        if (isEmpty(collection) || n <= 0) {
            return Collections.emptyList();
        }
        if (collection instanceof List) {
            List<T> list = (List<T>) collection;
            int end = Math.min(n, list.size());
            return list.subList(0, end);
        }
        return collection.stream().limit(n).collect(Collectors.toList());
    }

    /**
     * 获取集合的后 N 个元素
     *
     * @param collection 待切片的集合
     * @param n          元素个数
     * @param <T>        元素类型
     * @return 后 N 个元素组成的列表
     */
    public static <T> List<T> last(Collection<T> collection, int n) {
        if (isEmpty(collection) || n <= 0) {
            return Collections.emptyList();
        }
        if (collection instanceof List) {
            List<T> list = (List<T>) collection;
            int size = list.size();
            int start = Math.max(0, size - n);
            return list.subList(start, size);
        }
        List<T> all = new ArrayList<>(collection);
        int size = all.size();
        int start = Math.max(0, size - n);
        return all.subList(start, size);
    }

    /**
     * 获取集合的子列表
     *
     * @param collection 待切片的集合
     * @param fromIndex  起始索引（包含）
     * @param toIndex    结束索引（不包含）
     * @param <T>        元素类型
     * @return 子列表
     */
    public static <T> List<T> subList(Collection<T> collection, int fromIndex, int toIndex) {
        if (isEmpty(collection)) {
            return Collections.emptyList();
        }
        if (collection instanceof List) {
            List<T> list = (List<T>) collection;
            if (fromIndex < 0) {
                fromIndex = 0;
            }
            if (toIndex > list.size()) {
                toIndex = list.size();
            }
            if (fromIndex >= toIndex) {
                return Collections.emptyList();
            }
            return list.subList(fromIndex, toIndex);
        }
        return collection.stream().skip(fromIndex).limit(toIndex - fromIndex).collect(Collectors.toList());
    }

    /**
     * 跳过前 N 个元素
     *
     * @param collection 待处理的集合
     * @param n          跳过的元素个数
     * @param <T>        元素类型
     * @return 跳过后的列表
     */
    public static <T> List<T> skip(Collection<T> collection, int n) {
        if (isEmpty(collection) || n <= 0) {
            return isEmpty(collection) ? Collections.emptyList() : new ArrayList<>(collection);
        }
        if (collection instanceof List) {
            List<T> list = (List<T>) collection;
            if (n >= list.size()) {
                return Collections.emptyList();
            }
            return list.subList(n, list.size());
        }
        return collection.stream().skip(n).collect(Collectors.toList());
    }

    /**
     * 限制返回的元素个数
     *
     * @param collection 待处理的集合
     * @param maxSize    最大元素个数
     * @param <T>        元素类型
     * @return 限制后的列表
     */
    public static <T> List<T> limit(Collection<T> collection, int maxSize) {
        if (isEmpty(collection) || maxSize <= 0) {
            return Collections.emptyList();
        }
        if (collection instanceof List) {
            List<T> list = (List<T>) collection;
            if (list.size() <= maxSize) {
                return new ArrayList<>(list);
            }
            return list.subList(0, maxSize);
        }
        return collection.stream().limit(maxSize).collect(Collectors.toList());
    }

    // ==================== 统计方法 ====================

    /**
     * 获取集合的最大值
     *
     * @param collection 待处理的集合
     * @param comparator 比较器
     * @param <T>        元素类型
     * @return 包含最大值的 Optional
     */
    public static <T> Optional<T> max(Collection<T> collection, Comparator<? super T> comparator) {
        Objects.requireNonNull(comparator, "comparator must not be null");
        if (isEmpty(collection)) {
            return Optional.empty();
        }
        return collection.stream().max(comparator);
    }

    /**
     * 获取集合的最小值
     *
     * @param collection 待处理的集合
     * @param comparator 比较器
     * @param <T>        元素类型
     * @return 包含最小值的 Optional
     */
    public static <T> Optional<T> min(Collection<T> collection, Comparator<? super T> comparator) {
        Objects.requireNonNull(comparator, "comparator must not be null");
        if (isEmpty(collection)) {
            return Optional.empty();
        }
        return collection.stream().min(comparator);
    }

    /**
     * 获取集合的最大值（元素可比较）
     *
     * @param collection 待处理的集合
     * @param <T>        元素类型（必须实现 Comparable 接口）
     * @return 包含最大值的 Optional
     */
    public static <T extends Comparable<? super T>> Optional<T> max(Collection<T> collection) {
        return max(collection, Comparator.naturalOrder());
    }

    /**
     * 获取集合的最小值（元素可比较）
     *
     * @param collection 待处理的集合
     * @param <T>        元素类型（必须实现 Comparable 接口）
     * @return 包含最小值的 Optional
     */
    public static <T extends Comparable<? super T>> Optional<T> min(Collection<T> collection) {
        return min(collection, Comparator.naturalOrder());
    }

    /**
     * 统计集合中元素的总和（Integer）
     *
     * @param collection 待处理的集合
     * @param mapper     值提取函数
     * @return 总和
     */
    public static <T> int sumInt(Collection<T> collection, ToIntFunction<? super T> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        if (isEmpty(collection)) {
            return 0;
        }
        return collection.stream().mapToInt(mapper).sum();
    }

    /**
     * 统计集合中元素的总和（Long）
     *
     * @param collection 待处理的集合
     * @param mapper     值提取函数
     * @return 总和
     */
    public static <T> long sumLong(Collection<T> collection, ToLongFunction<? super T> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        if (isEmpty(collection)) {
            return 0L;
        }
        return collection.stream().mapToLong(mapper).sum();
    }

    /**
     * 统计集合中元素的总和（Double）
     *
     * @param collection 待处理的集合
     * @param mapper     值提取函数
     * @return 总和
     */
    public static <T> double sumDouble(Collection<T> collection, ToDoubleFunction<? super T> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        if (isEmpty(collection)) {
            return 0.0;
        }
        return collection.stream().mapToDouble(mapper).sum();
    }

    /**
     * 计算集合中元素的平均值（Integer）
     *
     * @param collection 待处理的集合
     * @param mapper     值提取函数
     * @return 平均值，如果集合为空则返回 Optional.empty()
     */
    public static <T> OptionalDouble averageInt(Collection<T> collection, ToIntFunction<? super T> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        if (isEmpty(collection)) {
            return OptionalDouble.empty();
        }
        return collection.stream().mapToInt(mapper).average();
    }

    /**
     * 计算集合中元素的平均值（Double）
     *
     * @param collection 待处理的集合
     * @param mapper     值提取函数
     * @return 平均值，如果集合为空则返回 OptionalDouble.empty()
     */
    public static <T> OptionalDouble averageDouble(Collection<T> collection, ToDoubleFunction<? super T> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        if (isEmpty(collection)) {
            return OptionalDouble.empty();
        }
        return collection.stream().mapToDouble(mapper).average();
    }

    /**
     * 统计集合中满足条件的元素个数
     *
     * @param collection 待处理的集合
     * @param predicate  统计条件
     * @param <T>        元素类型
     * @return 满足条件的元素个数
     */
    public static <T> long count(Collection<T> collection, Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate must not be null");
        if (isEmpty(collection)) {
            return 0L;
        }
        return collection.stream().filter(predicate).count();
    }

    // ==================== 并行流优化方法 ====================

    /**
     * 并行流转换列表
     *
     * <p>适用于大数据集，可以提升转换性能
     *
     * @param source 待转换的列表
     * @param mapper 转换函数
     * @param <T>    源元素类型
     * @param <R>    目标元素类型
     * @return 转换后的列表
     */
    public static <T, R> List<R> parallelConvertList(Collection<T> source, Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        if (isEmpty(source)) {
            return Collections.emptyList();
        }
        return source.parallelStream().map(mapper).collect(Collectors.toList());
    }

    /**
     * 并行流过滤
     *
     * @param source    待过滤的集合
     * @param predicate 过滤条件
     * @param <T>       元素类型
     * @return 过滤后的列表
     */
    public static <T> List<T> parallelFilter(Collection<T> source, Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate must not be null");
        if (isEmpty(source)) {
            return Collections.emptyList();
        }
        return source.parallelStream().filter(predicate).collect(Collectors.toList());
    }

    // ==================== 其他实用方法 ====================

    /**
     * 判断两个集合是否相等
     *
     * <p>当且仅当两个集合包含相同的元素（不考虑顺序和重复）时返回 true
     *
     * @param collection1 第一个集合
     * @param collection2 第二个集合
     * @param <T>         元素类型
     * @return 如果相等返回 true，否则返回 false
     */
    public static <T> boolean isEqual(Collection<T> collection1, Collection<T> collection2) {
        if (collection1 == collection2) {
            return true;
        }
        if (isEmpty(collection1) && isEmpty(collection2)) {
            return true;
        }
        if (isEmpty(collection1) || isEmpty(collection2)) {
            return false;
        }
        return new HashSet<>(collection1).equals(new HashSet<>(collection2));
    }

    /**
     * 将集合转换为字符串表示
     *
     * @param collection 待转换的集合
     * @param separator  分隔符
     * @return 字符串表示
     */
    public static String toString(Collection<?> collection, String separator) {
        if (isEmpty(collection)) {
            return "";
        }
        return collection.stream()
                .map(Objects::toString)
                .collect(Collectors.joining(separator));
    }

    /**
     * 创建不可变列表
     *
     * @param collection 源集合
     * @param <T>        元素类型
     * @return 不可变列表
     */
    public static <T> List<T> unmodifiableList(Collection<T> collection) {
        if (isEmpty(collection)) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(collection));
    }

    /**
     * 创建不可变集合
     *
     * @param collection 源集合
     * @param <T>        元素类型
     * @return 不可变集合
     */
    public static <T> Set<T> unmodifiableSet(Collection<T> collection) {
        if (isEmpty(collection)) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new HashSet<>(collection));
    }

    /**
     * 创建同步列表
     *
     * @param collection 源集合
     * @param <T>        元素类型
     * @return 同步列表
     */
    public static <T> List<T> synchronizedList(Collection<T> collection) {
        if (isEmpty(collection)) {
            return Collections.emptyList();
        }
        return Collections.synchronizedList(new ArrayList<>(collection));
    }

    /**
     * 创建同步集合
     *
     * @param collection 源集合
     * @param <T>        元素类型
     * @return 同步集合
     */
    public static <T> Set<T> synchronizedSet(Collection<T> collection) {
        if (isEmpty(collection)) {
            return Collections.emptySet();
        }
        return Collections.synchronizedSet(new HashSet<>(collection));
    }
}
