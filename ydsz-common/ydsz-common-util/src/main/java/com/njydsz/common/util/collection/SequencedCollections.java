package com.njydsz.common.util.collection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedCollection;
import java.util.SequencedMap;
import java.util.SequencedSet;
import java.util.Set;

/**
 * JDK 21 SequencedCollection 兼容工具——统一 reverse/first/last 操作。
 *
 * <h2>背景</h2>
 * <p>JDK 21 引入 {@link SequencedCollection}、{@link SequencedSet}、{@link SequencedMap}，
 * 以及 {@link Collection#reversed()}、{@link List#getFirst()}、{@link List#getLast()} 等 API，
 * 统一了集合的"有序"操作。
 *
 * <p>本工具类：
 * <ul>
 *   <li>JDK 21+：委托给原生 SequencedCollection API</li>
 *   <li>JDK 17：提供兼容实现（内部通过 List.get(0)/get(size-1) 模拟）</li>
 * </ul>
 *
 * <p><b>预测未来：</b>JDK 21+ 迁移后本工具类可继续作为统一入口使用，
 * 无需修改调用方代码。
 *
 * <p>使用示例：
 * <pre>{@code
 *   // 获取首元素
 *   Optional&lt;String&gt; first = SequencedCollections.first(list);
 *
 *   // 获取末元素
 *   Optional&lt;String&gt; last = SequencedCollections.last(list);
 *
 *   // 获取反转视图（JDK 21+ 返回原生 reversed，JDK 17 返回新 ArrayList）
 *   Collection&lt;String&gt; reversed = SequencedCollections.reversed(list);
 *
 *   // 获取 Map 的首/末 entry
 *   Optional&lt;Map.Entry&lt;K, V&gt;&gt; firstEntry = SequencedCollections.firstEntry(map);
 *   Optional&lt;Map.Entry&lt;K, V&gt;&gt; lastEntry = SequencedCollections.lastEntry(map);
 * }</pre>
 *
 * @author ydsz-team
 * @since 3.0.0
 */
public final class SequencedCollections {

    private SequencedCollections() {
        throw new UnsupportedOperationException("SequencedCollections is a utility class");
    }

    // ==================== first / last ====================

    /**
     * 获取 Collection 的首元素。
     *
     * @param coll 有序集合
     * @return 首元素或 {@code Optional.empty()}
     * @param <T> 泛型参数类型
     */
    @SuppressWarnings("unchecked")
    public static <T> java.util.Optional<T> first(Collection<T> coll) {
        if (coll == null || coll.isEmpty()) {
            return java.util.Optional.empty();
        }
        if (coll instanceof SequencedCollection<T> sc) {
            return java.util.Optional.of(sc.getFirst());
        }
        // JDK 17 fallback
        if (coll instanceof List<T> list) {
            return java.util.Optional.of(list.get(0));
        }
        return java.util.Optional.of(coll.iterator().next());
    }

    /**
     * 获取 Collection 的末元素。
     *
     * @param coll 有序集合
     * @return 末元素或 {@code Optional.empty()}
     * @param <T> 泛型参数类型
     */
    @SuppressWarnings("unchecked")
    public static <T> java.util.Optional<T> last(Collection<T> coll) {
        if (coll == null || coll.isEmpty()) {
            return java.util.Optional.empty();
        }
        if (coll instanceof SequencedCollection<T> sc) {
            return java.util.Optional.of(sc.getLast());
        }
        // JDK 17 fallback
        if (coll instanceof List<T> list) {
            return java.util.Optional.of(list.get(list.size() - 1));
        }
        // 非 List 的迭代器遍历
        T last = null;
        for (T item : coll) {
            last = item;
        }
        return java.util.Optional.ofNullable(last);
    }

    // ==================== reversed ====================

    /**
     * 获取 Collection 的反转视图（JDK 21+ 返回原生 reversed()，JDK 17 返回新 ArrayList）。
     *
     * @param coll 源集合
     * @return 反转后的集合
     * @param <T> 泛型参数类型
     */
    @SuppressWarnings("unchecked")
    public static <T> Collection<T> reversed(Collection<T> coll) {
        if (coll == null || coll.isEmpty()) {
            return Collections.emptyList();
        }
        if (coll instanceof SequencedCollection<T> sc) {
            return sc.reversed();
        }
        // JDK 17 fallback
        if (coll instanceof List<T> list) {
            List<T> reversed = new ArrayList<>(list);
            Collections.reverse(reversed);
            return reversed;
        }
        List<T> list = new ArrayList<>(coll);
        Collections.reverse(list);
        return list;
    }

    // ==================== SequencedMap 兼容 ====================

    /**
     * 获取 Map 的首 entry（按插入顺序）。
     *
     * @param map 有序 Map
     * @return 首 entry 或 {@code Optional.empty()}
     * @param <K> 泛型参数类型
     * @param <V> 泛型参数类型
     */
    public static <K, V> java.util.Optional<Map.Entry<K, V>> firstEntry(Map<K, V> map) {
        if (map == null || map.isEmpty()) {
            return java.util.Optional.empty();
        }
        if (map instanceof SequencedMap<K, V> sm) {
            return java.util.Optional.of(sm.firstEntry());
        }
        // JDK 17 fallback
        return java.util.Optional.of(map.entrySet().iterator().next());
    }

    /**
     * 获取 Map 的末 entry（按插入顺序）。
     *
     * @param map 有序 Map
     * @return 末 entry 或 {@code Optional.empty()}
     * @param <K> 泛型参数类型
     * @param <V> 泛型参数类型
     */
    public static <K, V> java.util.Optional<Map.Entry<K, V>> lastEntry(Map<K, V> map) {
        if (map == null || map.isEmpty()) {
            return java.util.Optional.empty();
        }
        if (map instanceof SequencedMap<K, V> sm) {
            return java.util.Optional.of(sm.lastEntry());
        }
        // JDK 17 fallback
        Map.Entry<K, V> last = null;
        for (Map.Entry<K, V> entry : map.entrySet()) {
            last = entry;
        }
        return java.util.Optional.ofNullable(last);
    }

    /**
     * 获取 Map 的反转视图（JDK 21+ 返回原生 reversed()，JDK 17 返回新 LinkedHashMap）。
     *
     * @param map 源 Map
     * @return 反转后的 Map
     * @param <K> 泛型参数类型
     * @param <V> 泛型参数类型
     */
    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> reversed(Map<K, V> map) {
        if (map == null || map.isEmpty()) {
            return Collections.emptyMap();
        }
        if (map instanceof SequencedMap<K, V> sm) {
            return sm.reversed();
        }
        // JDK 17 fallback
        List<Map.Entry<K, V>> entries = new ArrayList<>(map.entrySet());
        Collections.reverse(entries);
        Map<K, V> reversed = new LinkedHashMap<>();
        for (Map.Entry<K, V> entry : entries) {
            reversed.put(entry.getKey(), entry.getValue());
        }
        return reversed;
    }

    // ==================== SequencedSet 便捷方法 ====================

    /**
     * 创建插入顺序的不可变 Set（兼容 JDK 21 SequencedSet 语义）。
     *
     * @param elements 元素
     * @return 不可变 LinkedHashSet
     * @param <T> 泛型参数类型
     */
    @SafeVarargs
    public static <T> Set<T> of(T... elements) {
        Set<T> set = new LinkedHashSet<>();
        Collections.addAll(set, elements);
        return Collections.unmodifiableSet(set);
    }
}
