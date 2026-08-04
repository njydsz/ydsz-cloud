package com.njydsz.common.util.collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link CollectionUtils} 单元测试
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("CollectionUtils 工具类测试")
class CollectionUtilsTest {

    // ==================== isEmpty / isNotEmpty ====================

    @Nested
    @DisplayName("isEmpty - Collection")
    class IsEmptyCollectionTest {

        @Test
        @DisplayName("null 集合为空")
        void nullCollection() {
            assertThat(CollectionUtils.isEmpty((Collection<?>) null)).isTrue();
        }

        @Test
        @DisplayName("空集合为空")
        void emptyCollection() {
            assertThat(CollectionUtils.isEmpty(Collections.emptyList())).isTrue();
        }

        @Test
        @DisplayName("有元素集合不为空")
        void nonEmptyCollection() {
            assertThat(CollectionUtils.isEmpty(Arrays.asList(1, 2))).isFalse();
        }
    }

    @Nested
    @DisplayName("isEmpty - Map")
    class IsEmptyMapTest {

        @Test
        @DisplayName("null Map 为空")
        void nullMap() {
            assertThat(CollectionUtils.isEmpty((Map<?, ?>) null)).isTrue();
        }

        @Test
        @DisplayName("空 Map 为空")
        void emptyMap() {
            assertThat(CollectionUtils.isEmpty(Collections.emptyMap())).isTrue();
        }

        @Test
        @DisplayName("有元素 Map 不为空")
        void nonEmptyMap() {
            Map<String, String> map = new HashMap<>();
            map.put("k", "v");
            assertThat(CollectionUtils.isEmpty(map)).isFalse();
        }
    }

    @Nested
    @DisplayName("isEmpty - Iterable")
    class IsEmptyIterableTest {

        @Test
        @DisplayName("null Iterable 为空")
        void nullIterable() {
            assertThat(CollectionUtils.isEmpty((Iterable<?>) null)).isTrue();
        }

        @Test
        @DisplayName("空 Iterable 为空")
        void emptyIterable() {
            assertThat(CollectionUtils.isEmpty(Collections.emptyList())).isTrue();
        }

        @Test
        @DisplayName("非 Collection 的空 Iterable 为空")
        void emptyNonCollectionIterable() {
            Iterable<Integer> iterable = () -> Collections.emptyIterator();
            assertThat(CollectionUtils.isEmpty(iterable)).isTrue();
        }

        @Test
        @DisplayName("有元素 Iterable 不为空")
        void nonEmptyIterable() {
            Iterable<Integer> iterable = () -> Arrays.asList(1, 2).iterator();
            assertThat(CollectionUtils.isEmpty(iterable)).isFalse();
        }
    }

    @Test
    @DisplayName("isNotEmpty - Collection")
    void isNotEmptyCollection() {
        assertThat(CollectionUtils.isNotEmpty((Collection<?>) null)).isFalse();
        assertThat(CollectionUtils.isNotEmpty(Collections.emptyList())).isFalse();
        assertThat(CollectionUtils.isNotEmpty(Arrays.asList(1))).isTrue();
    }

    @Test
    @DisplayName("isNotEmpty - Map")
    void isNotEmptyMap() {
        assertThat(CollectionUtils.isNotEmpty((Map<?, ?>) null)).isFalse();
        assertThat(CollectionUtils.isNotEmpty(Collections.emptyMap())).isFalse();
        Map<String, String> map = new HashMap<>();
        map.put("k", "v");
        assertThat(CollectionUtils.isNotEmpty(map)).isTrue();
    }

    // ==================== listToMap ====================

    @Nested
    @DisplayName("listToMap")
    class ListToMapTest {

        @Test
        @DisplayName("正常转换 - 以元素本身作为值")
        void normal() {
            List<String> list = Arrays.asList("a", "b", "c");
            Map<String, String> result = CollectionUtils.listToMap(list, String::toUpperCase);
            assertThat(result).hasSize(3).containsEntry("A", "a").containsEntry("B", "b").containsEntry("C", "c");
        }

        @Test
        @DisplayName("重复键取第一个出现的元素")
        void duplicateKey() {
            List<String> list = Arrays.asList("a", "b", "a");
            Map<String, String> result = CollectionUtils.listToMap(list, s -> s);
            assertThat(result).hasSize(2).containsEntry("a", "a").containsEntry("b", "b");
        }

        @Test
        @DisplayName("空列表返回空 Map")
        void emptyList() {
            Map<String, String> result = CollectionUtils.listToMap(Collections.emptyList(), String::toUpperCase);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null 列表返回空 Map")
        void nullList() {
            Map<String, String> result = CollectionUtils.listToMap(null, String::toUpperCase);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null keyMapper 抛 NPE")
        void nullKeyMapper() {
            assertThatThrownBy(() -> CollectionUtils.listToMap(Arrays.asList("a"), null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("自定义键值映射")
        void customKeyValue() {
            List<Integer> list = Arrays.asList(1, 2, 3);
            Map<String, Integer> result = CollectionUtils.listToMap(list, i -> "key" + i, i -> i * 10);
            assertThat(result).containsEntry("key1", 10).containsEntry("key2", 20).containsEntry("key3", 30);
        }
    }

    // ==================== listToGroup ====================

    @Nested
    @DisplayName("listToGroup")
    class ListToGroupTest {

        @Test
        @DisplayName("按首字母分组")
        void groupByFirstChar() {
            List<String> list = Arrays.asList("apple", "avocado", "banana", "cherry");
            Map<Character, List<String>> result = CollectionUtils.listToGroup(list, s -> s.charAt(0));
            assertThat(result).hasSize(3);
            assertThat(result.get('a')).containsExactly("apple", "avocado");
            assertThat(result.get('b')).containsExactly("banana");
            assertThat(result.get('c')).containsExactly("cherry");
        }

        @Test
        @DisplayName("空列表返回空 Map")
        void emptyList() {
            Map<String, List<String>> result = CollectionUtils.listToGroup(Collections.emptyList(), String::toString);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null classifier 抛 NPE")
        void nullClassifier() {
            assertThatThrownBy(() -> CollectionUtils.listToGroup(Arrays.asList("a"), null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ==================== convertList ====================

    @Nested
    @DisplayName("convertList")
    class ConvertListTest {

        @Test
        @DisplayName("Integer 转 String")
        void intToString() {
            List<Integer> list = Arrays.asList(1, 2, 3);
            List<String> result = CollectionUtils.convertList(list, Object::toString);
            assertThat(result).containsExactly("1", "2", "3");
        }

        @Test
        @DisplayName("空列表返回空 List")
        void emptyList() {
            List<String> result = CollectionUtils.convertList(Collections.emptyList(), Object::toString);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null mapper 抛 NPE")
        void nullMapper() {
            assertThatThrownBy(() -> CollectionUtils.convertList(Arrays.asList(1), null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ==================== filter ====================

    @Nested
    @DisplayName("filter")
    class FilterTest {

        @Test
        @DisplayName("过滤偶数")
        void filterEven() {
            List<Integer> list = Arrays.asList(1, 2, 3, 4, 5, 6);
            List<Integer> result = CollectionUtils.filter(list, i -> i % 2 == 0);
            assertThat(result).containsExactly(2, 4, 6);
        }

        @Test
        @DisplayName("空列表返回空 List")
        void emptyList() {
            List<Integer> result = CollectionUtils.filter(Collections.emptyList(), i -> true);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null 列表返回空 List")
        void nullList() {
            List<Integer> result = CollectionUtils.filter(null, i -> true);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null predicate 抛 NPE")
        void nullPredicate() {
            assertThatThrownBy(() -> CollectionUtils.filter(Arrays.asList(1), null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ==================== findFirst / findLast ====================

    @Nested
    @DisplayName("findFirst / findLast")
    class FindTest {

        @Test
        @DisplayName("findFirst 返回第一个元素")
        void findFirst() {
            Optional<Integer> result = CollectionUtils.findFirst(Arrays.asList(1, 2, 3));
            assertThat(result).contains(1);
        }

        @Test
        @DisplayName("findFirst 空集合返回 empty")
        void findFirstEmpty() {
            Optional<Integer> result = CollectionUtils.findFirst(Collections.emptyList());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("findFirst null 返回 empty")
        void findFirstNull() {
            Optional<Integer> result = CollectionUtils.findFirst(null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("findLast 对 List O(1) 优化")
        void findLastList() {
            Optional<Integer> result = CollectionUtils.findLast(Arrays.asList(1, 2, 3));
            assertThat(result).contains(3);
        }

        @Test
        @DisplayName("findLast 对非 List Collection 遍历")
        void findLastNonListCollection() {
            // 使用非 List 的 Collection（如 LinkedHashSet）触发 reduce 分支
            Collection<Integer> coll = new java.util.LinkedHashSet<>(Arrays.asList(1, 2, 3));
            Optional<Integer> result = CollectionUtils.findLast(coll);
            assertThat(result).contains(3);
        }

        @Test
        @DisplayName("findLast 空集合返回 empty")
        void findLastEmpty() {
            Optional<Integer> result = CollectionUtils.findLast(Collections.emptyList());
            assertThat(result).isEmpty();
        }
    }
}
