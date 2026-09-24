package com.njydsz.common.util.collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link CollectionUtils} 单元测试。
 *
 * <p>覆盖：判空、转换、过滤、查找、合并、分片、去重、Stream 适配等全路径。
 *
 * @since 26.09.19
 */
@DisplayName("CollectionUtils 测试")
class CollectionUtilsTest {

  @Nested
  @DisplayName("判空方法")
  class IsEmptyTest {

    @Test
    @DisplayName("isEmpty: null 返回 true")
    void isEmpty_nullCollection_returnsTrue() {
      assertThat(CollectionUtils.isEmpty((Collection<?>) null)).isTrue();
    }

    @Test
    @DisplayName("isEmpty: 空集合返回 true")
    void isEmpty_emptyCollection_returnsTrue() {
      assertThat(CollectionUtils.isEmpty(Collections.emptyList())).isTrue();
    }

    @Test
    @DisplayName("isEmpty: 有元素返回 false")
    void isEmpty_nonEmptyCollection_returnsFalse() {
      assertThat(CollectionUtils.isEmpty(List.of("a", "b"))).isFalse();
    }

    @Test
    @DisplayName("isEmpty(Map): null 返回 true")
    void isEmpty_nullMap_returnsTrue() {
      assertThat((CollectionUtils.isEmpty((Map<?, ?>) null))).isTrue();
    }

    @Test
    @DisplayName("isEmpty(Map): 空 Map 返回 true")
    void isEmpty_emptyMap_returnsTrue() {
      assertThat(CollectionUtils.isEmpty(Collections.emptyMap())).isTrue();
    }

    @Test
    @DisplayName("isNotEmpty: null 返回 false")
    void isNotEmpty_null_returnsFalse() {
      assertThat(CollectionUtils.isNotEmpty((Collection<?>) null)).isFalse();
    }
  }

  @Nested
  @DisplayName("类型转换方法")
  class ConvertTest {

    @Test
    @DisplayName("listToMap: 正常转换取第一个")
    void listToMap_normalCase_takesFirstOnDuplicate() {
      Map<Integer, String> result = CollectionUtils.listToMap(
          List.of("aa", "bb", "cc"), String::length);
      assertThat(result).containsEntry(2, "aa");
      // 全部长度都是 2，重复键只保留第一个
      assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("listToMap: 空集返回空 Map")
    void listToMap_empty_returnsEmptyMap() {
      Map<Integer, String> result = CollectionUtils.listToMap(
          Collections.<String>emptyList(), String::length);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("listToGroup: 正常分组")
    void listToGroup_normalCase_groupsCorrectly() {
      Map<Integer, List<String>> grouped = CollectionUtils.listToGroup(
          List.of("a", "bb", "ccc", "dd"), String::length);
      assertThat(grouped).containsEntry(1, List.of("a"))
          .containsEntry(2, List.of("bb", "dd"))
          .containsEntry(3, List.of("ccc"));
    }

    @Test
    @DisplayName("convertList: 正常转换")
    void convertList_normalCase() {
      List<Integer> result = CollectionUtils.convertList(
          List.of("a", "bb"), String::length);
      assertThat(result).containsExactly(1, 2);
    }
  }

  @Nested
  @DisplayName("查找方法")
  class FindTest {

    @Test
    @DisplayName("findFirst: 非空集合返回第一个")
    void findFirst_nonEmpty_returnsFirst() {
      assertThat(CollectionUtils.findFirst(List.of("a", "b", "c"))).contains("a");
    }

    @Test
    @DisplayName("findFirst: 空集合返回 empty")
    void findFirst_empty_returnsEmpty() {
      assertThat(CollectionUtils.findFirst(Collections.emptyList())).isEmpty();
    }

    @Test
    @DisplayName("findLast: List 类型 O(1) 返回最后一个")
    void findLast_listType_returnsLast() {
      assertThat(CollectionUtils.findLast(List.of("a", "b", "c"))).contains("c");
    }

    @Test
    @DisplayName("findLast: Set 类型返回最后一个迭代元素")
    void findLast_setType_returnsLast() {
      LinkedHashSet<String> set = new LinkedHashSet<>();
      set.add("x");
      set.add("y");
      assertThat(CollectionUtils.findLast(set)).contains("y");
    }
  }

  @Nested
  @DisplayName("合并方法")
  class ConcatTest {

    @Test
    @DisplayName("concat: 跳过 null 元素")
    void concat_skipsNullElements() {
      List<Integer> result = CollectionUtils.concat(
          List.of(1, 2), null, List.of(3));
      assertThat(result).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("concat: 空数组返回空 List")
    void concat_emptyVarargs_returnsEmptyList() {
      // YDIZ-WARN-001 允许保留：测试用例强制转换原始集合，运行时类型安全由 fixture 保证
      @SuppressWarnings("unchecked")
      List<Integer> result = CollectionUtils.concat(new Collection[0]);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("flatten: 嵌套集合展平")
    void flatten_nestedCollections_flattens() {
      List<Integer> result = CollectionUtils.flatten(
          List.of(List.of(1, 2), List.of(3), List.of()));
      assertThat(result).containsExactly(1, 2, 3);
    }
  }

  @Nested
  @DisplayName("分片方法")
  class PartitionTest {

    @Test
    @DisplayName("partition: 正常分片")
    void partition_normalCase() {
      List<List<Integer>> result = CollectionUtils.partition(
          List.of(1, 2, 3, 4, 5), 2);
      assertThat(result).hasSize(3);
      assertThat(result.get(0)).containsExactly(1, 2);
      assertThat(result.get(1)).containsExactly(3, 4);
      assertThat(result.get(2)).containsExactly(5);
    }

    @Test
    @DisplayName("partition: batchSize < 1 抛出异常")
    void partition_invalidBatchSize_throws() {
      assertThatThrownBy(() -> CollectionUtils.partition(List.of(1), 0))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("去重方法")
  class DistinctTest {

    @Test
    @DisplayName("distinct: 保持顺序去重")
    void distinct_preservesOrder() {
      List<Integer> result = CollectionUtils.distinct(List.of(1, 2, 2, 3, 1));
      assertThat(result).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("distinctBy: 按键去重保留首次")
    void distinctBy_keepsFirstByKey() {
      List<String> result = CollectionUtils.distinctBy(
          List.of("aa", "bb", "cc"), String::length);
      assertThat(result).containsExactly("aa");
    }
  }

  @Nested
  @DisplayName("Stream / Optional 适配")
  class StreamOptionalTest {

    @Test
    @DisplayName("safeStream: null 集合返回空流")
    void safeStream_null_returnsEmptyStream() {
      Stream<String> stream = CollectionUtils.safeStream(null);
      assertThat(stream).isEmpty();
    }

    @Test
    @DisplayName("safeStream: 非空集合返回流")
    void safeStream_nonEmpty_returnsStream() {
      Stream<String> stream = CollectionUtils.safeStream(List.of("a"));
      assertThat(stream).containsExactly("a");
    }

    @Test
    @DisplayName("fromOptional: 有值时转为单元素 List")
    void fromOptional_present_returnsSingletonList() {
      List<String> result = CollectionUtils.fromOptional(Optional.of("hello"));
      assertThat(result).containsExactly("hello");
    }

    @Test
    @DisplayName("fromOptional: 空时返回空 List")
    void fromOptional_empty_returnsEmptyList() {
      List<String> result = CollectionUtils.fromOptional(Optional.empty());
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("filterNullValues: 过滤 null 值")
    void filterNullValues_removesNulls() {
      Map<String, String> source = new HashMap<>();
      source.put("k1", "v1");
      source.put("k2", null);
      source.put("k3", "v3");

      Map<String, String> result = CollectionUtils.filterNullValues(source);
      assertThat(result).containsEntry("k1", "v1")
          .containsEntry("k3", "v3")
          .doesNotContainKey("k2");
    }

    @Test
    @DisplayName("filterNullValues: source 为 null 抛出 NPE")
    void filterNullValues_nullSource_throwsNpe() {
      assertThatThrownBy(() -> CollectionUtils.filterNullValues(null))
          .isInstanceOf(NullPointerException.class);
    }
  }
}
