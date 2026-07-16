package com.njydsz.common.util.merge;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
/**
 * 合并操作函数式接口
 *
 * <p>用于定义两个对象之间的合并逻辑，支持多种合并策略。
 * 设计灵感来源于 Apache Commons Collections、Google Guava 的合并思想，
 * 并进行了增强和优化。</p>
 *
 * <p><b>主要特性：</b>
 * <ul>
 *   <li>支持双向合并：R 对象接收 T 对象的数据</li>
 *   <li>支持返回值合并：通过 apply 方法返回合并结果</li>
 *   <li>支持批量合并：结合 Compare 接口实现条件合并</li>
 *   <li>支持链式调用：提供 default 方法增强</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>
 * // 简单合并：将 B 的属性复制到 A
 * Merged&lt;User, UserVO&gt; merger = (a, b) -&gt; {
 *     a.setName(b.getName());
 *     a.setAge(b.getAge());
 * };
 *
 * // 批量合并：根据 ID 匹配并合并
 * MergeUtils.merge(users, userVOs,
 *     (u, v) -&gt; Objects.equals(u.getId(), v.getId()),
 *     (u, v) -&gt; u.setName(v.getName())
 * );
 *
 * // 使用返回值合并
 * List&lt;User&gt; result = Merged.applyMerge(users, userVOs,
 *     (u, v) -&gt; Objects.equals(u.getId(), v.getId()),
 *     (u, v) -&gt; { u.setName(v.getName()); return u; }
 * );
 * </pre>
 *
 * @param <R> 返回值的对象类型（目标对象类型）
 * @param <T> 需要被合并的类型（源对象类型）
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 * @see MergeUtils
 * @see BiConsumer
 * @see BiFunction
 */
@FunctionalInterface
public interface Merged<R, T> {

    /**
     * 合并两个对象
     *
     * <p>将源对象 {@code t} 的数据合并到目标对象 {@code r} 中。
     * 该方法应该修改 {@code r} 的状态以包含 {@code t} 的数据。</p>
     *
     * @param r 目标对象（将被修改）
     * @param t 源对象（提供数据）
     * @throws NullPointerException 如果 r 为 null（t 可以为 null，具体实现决定）
     */
    void merge(R r, T t);

    /**
     * 批量应用合并操作
     *
     * <p>遍历两个集合，根据比较器结果有条件地执行合并操作。
     * 时间复杂度：O(n*m)，其中 n 和 m 分别是两个集合的大小。</p>
     *
     * <p><b>注意：</b>对于大数据集，建议使用 {@link MergeUtils#mergeByKey} 等
     * 基于 Map 的优化方法，时间复杂度可降至 O(n+m)。</p>
     *
     * @param listA   目标对象集合
     * @param listB   源对象集合
     * @param compare 比较器，用于判断是否应该合并
     * @throws NullPointerException 如果 listA、listB 或 compare 为 null
     * @see MergeUtils#mergeByKey
     * @see MergeUtils#mergeWithStrategy
     */
    default void apply(List<R> listA, List<T> listB, Compare<R, T> compare) {
        Objects.requireNonNull(listA, "listA cannot be null");
        Objects.requireNonNull(listB, "listB cannot be null");
        Objects.requireNonNull(compare, "compare cannot be null");
        
        listA.forEach(a -> listB.forEach(b -> {
            if (compare.compare(a, b)) {
                merge(a, b);
            }
        }));
    }

    /**
     * 带返回值的合并操作（静态方法）
     *
     * <p>创建一个新的结果对象，而不是修改原对象。
     * 适用于不可变对象或需要保留原对象的场景。</p>
     *
     * @param r       目标对象
     * @param t       源对象
     * @param merger  合并函数
     * @param <R>     目标类型
     * @param <T>     源类型
     * @return 合并后的新对象
     */
    static <R, T> R applyMerge(R r, T t, BiFunction<R, T, R> merger) {
        Objects.requireNonNull(r, "r cannot be null");
        Objects.requireNonNull(t, "t cannot be null");
        Objects.requireNonNull(merger, "merger cannot be null");
        return merger.apply(r, t);
    }

    /**
     * 批量带返回值的合并操作（静态方法）
     *
     * <p>根据比较器条件，批量合并两个集合并返回结果列表。</p>
     *
     * @param listA   目标对象集合
     * @param listB   源对象集合
     * @param compare 比较器
     * @param merger  合并函数
     * @param <R>     目标类型
     * @param <T>     源类型
     * @return 合并后的结果列表
     */
    static <R, T> List<R> applyMerge(
            List<R> listA,
            List<T> listB,
            Compare<R, T> compare,
            BiFunction<R, T, R> merger) {
        Objects.requireNonNull(listA, "listA cannot be null");
        Objects.requireNonNull(listB, "listB cannot be null");
        Objects.requireNonNull(compare, "compare cannot be null");
        Objects.requireNonNull(merger, "merger cannot be null");
        
        return listA.stream()
                .map(a -> {
                    for (T b : listB) {
                        if (compare.compare(a, b)) {
                            return merger.apply(a, b);
                        }
                    }
                    return a;
                })
                .collect(Collectors.toList());
    }

    /**
     * 基于 key 提取器的合并操作（静态方法）
     *
     * <p>通过 key 提取器快速匹配两个集合中的对象并执行合并。
     * 使用 Map 实现，时间复杂度 O(n+m)，性能优于嵌套循环。</p>
     *
     * @param listA        目标对象集合
     * @param listB        源对象集合
     * @param keyExtractor key 提取函数
     * @param merger       合并操作
     * @param <K>          key 的类型
     * @param <R>          目标类型
     * @param <T>          源类型
     * @return 合并后的结果列表
     */
    static <K, R, T> List<R> mergeByKey(
            List<R> listA,
            List<T> listB,
            Function<? super R, ? extends K> keyExtractor,
            Function<? super T, ? extends K> keyExtractorB,
            Merged<R, T> merger) {
        Objects.requireNonNull(listA, "listA cannot be null");
        Objects.requireNonNull(listB, "listB cannot be null");
        Objects.requireNonNull(keyExtractor, "keyExtractor cannot be null");
        Objects.requireNonNull(keyExtractorB, "keyExtractorB cannot be null");
        Objects.requireNonNull(merger, "merger cannot be null");
        
        if (listA.isEmpty() || listB.isEmpty()) {
            return new ArrayList<>(listA);
        }
        
        Map<K, T> mapB = new HashMap<>();
        for (T t : listB) {
            K key = keyExtractorB.apply(t);
            if (key != null) {
                mapB.put(key, t);
            }
        }
        
        List<R> result = new ArrayList<>(listA.size());
        for (R r : listA) {
            K key = keyExtractor.apply(r);
            if (key != null && mapB.containsKey(key)) {
                R copy = copyObject(r);
                merger.merge(copy, mapB.get(key));
                result.add(copy);
            } else {
                result.add(copyObject(r));
            }
        }
        return result;
    }
    
    
    private static <T> T copyObject(T obj) {
        if (obj instanceof Serializable) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos);
                oos.writeObject(obj);
                oos.close();

                ObjectInputStream ois = new ObjectInputStream(
                    new ByteArrayInputStream(baos.toByteArray()));
                T copy = Merged.castObject(ois.readObject());
                ois.close();
                return copy;
            } catch (Exception e) {
                return obj;
            }
        }
        return obj;
    }

    /** 内部辅助方法：安全转换反序列化对象到泛型类型 T */
    private static <T> T castObject(Object obj) {
        return (T) obj;
    }

    /**
     * 比较器函数式接口
     *
     * <p>用于判断两个对象是否满足合并条件。
     * 通常用于基于业务 key（如 ID、编码等）的匹配。</p>
     *
     * @param <R> 目标对象类型
     * @param <T> 源对象类型
     */
    @FunctionalInterface
    interface Compare<R, T> {
        /**
         * 比较两个对象
         *
         * @param r 目标对象
         * @param t 源对象
         * @return 如果满足合并条件返回 true，否则返回 false
         */
        boolean compare(R r, T t);

        /**
         * 基于 key 比较的默认实现
         *
         * @param keyExtractorR R 的 key 提取器
         * @param keyExtractorT T 的 key 提取器
         * @param <K>           key 的类型
         * @return Compare 实例
         */
        static <K, R, T> Compare<R, T> of(
                Function<? super R, ? extends K> keyExtractorR,
                Function<? super T, ? extends K> keyExtractorT) {
            Objects.requireNonNull(keyExtractorR, "keyExtractorR cannot be null");
            Objects.requireNonNull(keyExtractorT, "keyExtractorT cannot be null");
            return (r, t) -> Objects.equals(keyExtractorR.apply(r), keyExtractorT.apply(t));
        }
    }
}
