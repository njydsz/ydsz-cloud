package com.njydsz.common.config.hotreload;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link ConfigMergeUtils} 单元测试。
 *
 * <p>覆盖：RFC 7396 Merge Patch 核心语义（字段覆盖、null 删除、嵌套递归、缺省保留）、
 * 空输入降级、多层合并优先级、非法 JSON 异常降级。
 *
 * @since 26.09.20
 */
@DisplayName("ConfigMergeUtils 测试")
class ConfigMergeUtilsTest {

  @Nested
  @DisplayName("merge: 单层合并")
  class MergeTest {

    @Test
    @DisplayName("基本覆盖：patch 字段覆盖 target 同名字段")
    void merge_patchOverridesTarget() {
      String base = "{\"timeout\":30,\"retry\":3}";
      String patch = "{\"retry\":5}";
      assertThat(ConfigMergeUtils.merge(base, patch)).isEqualTo("{\"timeout\":30,\"retry\":5}");
    }

    @Test
    @DisplayName("null 值删除：patch 中 null 字段从 target 中移除")
    void merge_nullValue_removesField() {
      String base = "{\"name\":\"John\",\"age\":30}";
      String patch = "{\"age\":null}";
      String result = ConfigMergeUtils.merge(base, patch);
      assertThat(result).contains("\"name\":\"John\"");
      assertThat(result).doesNotContain("age");
    }

    @Test
    @DisplayName("嵌套递归：patch 中的对象与 target 对应对象递归合并（非整体替换）")
    void merge_nestedObject_recursiveMerge() {
      String base = "{\"pool\":{\"min\":1,\"max\":10},\"timeout\":30}";
      String patch = "{\"pool\":{\"max\":20}}";
      String result = ConfigMergeUtils.merge(base, patch);
      assertThat(result).contains("\"min\":1");
      assertThat(result).contains("\"max\":20");
      assertThat(result).contains("\"timeout\":30");
    }

    @Test
    @DisplayName("缺省保留：patch 中不存在的字段保留 target 原值")
    void merge_missingInPatch_retainsTargetValue() {
      String base = "{\"a\":1,\"b\":2,\"c\":3}";
      String patch = "{\"b\":99}";
      String result = ConfigMergeUtils.merge(base, patch);
      assertThat(result).contains("\"a\":1");
      assertThat(result).contains("\"b\":99");
      assertThat(result).contains("\"c\":3");
    }

    @Test
    @DisplayName("新增字段：patch 中 target 不存在的字段被添加")
    void merge_newFieldInPatch_added() {
      String base = "{\"a\":1}";
      String patch = "{\"b\":2}";
      String result = ConfigMergeUtils.merge(base, patch);
      assertThat(result).contains("\"a\":1");
      assertThat(result).contains("\"b\":2");
    }

    @Test
    @DisplayName("空 base：返回 override 原值")
    void merge_nullBase_returnsOverride() {
      assertThat(ConfigMergeUtils.merge(null, "{\"a\":1}")).isEqualTo("{\"a\":1}");
    }

    @Test
    @DisplayName("空 override：返回 base 原值")
    void merge_nullOverride_returnsBase() {
      assertThat(ConfigMergeUtils.merge("{\"a\":1}", null)).isEqualTo("{\"a\":1}");
    }

    @Test
    @DisplayName("空白字符串 override：返回 base 原值")
    void merge_blankOverride_returnsBase() {
      assertThat(ConfigMergeUtils.merge("{\"a\":1}", "   ")).isEqualTo("{\"a\":1}");
    }

    @Test
    @DisplayName("非法 JSON base：降级返回 override")
    void merge_invalidJsonBase_fallbackToOverride() {
      String malformed = "{not-valid-json}";
      String override = "{\"fallback\":true}";
      assertThat(ConfigMergeUtils.merge(malformed, override)).isEqualTo(override);
    }

    @Test
    @DisplayName("数组替换：patch 中数组替换 target 中数组（非递归合并）")
    void merge_arrayInPatch_replacesTargetArray() {
      String base = "{\"items\":[1,2,3],\"name\":\"list\"}";
      String patch = "{\"items\":[4,5]}";
      String result = ConfigMergeUtils.merge(base, patch);
      assertThat(result).contains("\"items\":[4,5]");
      assertThat(result).contains("\"name\":\"list\"");
    }
  }

  @Nested
  @DisplayName("mergeLayers: 多层合并")
  class MergeLayersTest {

    @Test
    @DisplayName("双层合并：后者覆盖前者")
    void mergeLayers_twoLayers() {
      String defaults = "{\"timeout\":30,\"retry\":3}";
      String env = "{\"retry\":5}";
      String result = ConfigMergeUtils.mergeLayers(defaults, env);
      assertThat(result).contains("\"timeout\":30");
      assertThat(result).contains("\"retry\":5");
    }

    @Test
    @DisplayName("三层合并：按优先级从低到高依次覆盖")
    void mergeLayers_threeLayers() {
      String defaults = "{\"a\":1,\"b\":2,\"c\":3}";
      String env = "{\"b\":20}";
      String tenant = "{\"c\":300}";
      String result = ConfigMergeUtils.mergeLayers(defaults, env, tenant);
      assertThat(result).contains("\"a\":1");
      assertThat(result).contains("\"b\":20");
      assertThat(result).contains("\"c\":300");
    }

    @Test
    @DisplayName("单层返回第一个配置")
    void mergeLayers_singleLayer_returnsFirst() {
      assertThat(ConfigMergeUtils.mergeLayers("{\"a\":1}")).isEqualTo("{\"a\":1}");
    }

    @Test
    @DisplayName("空输入返回 null")
    void mergeLayers_nullInput_returnsNull() {
      assertThat(ConfigMergeUtils.mergeLayers((String[]) null)).isNull();
    }

    @Test
    @DisplayName("空数组返回 null")
    void mergeLayers_emptyArray_returnsNull() {
      assertThat(ConfigMergeUtils.mergeLayers(new String[0])).isNull();
    }

    @Test
    @DisplayName("中间层空值跳过（处理后继层）")
    void mergeLayers_middleNull_skipped() {
      String defaults = "{\"a\":1}";
      String tenant = "{\"a\":99}";
      String result = ConfigMergeUtils.mergeLayers(defaults, null, tenant);
      assertThat(result).contains("\"a\":99");
    }
  }

  @Nested
  @DisplayName("使用示例（对齐 Javadoc 文档）")
  class DocumentationExampleTest {

    @Test
    @DisplayName("Javadoc 示例：合并结果与文档一致")
    void javadocExample_matchesExpected() {
      String baseConfig = "{\"timeout\":30,\"retry\":3,\"pool\":{\"min\":1,\"max\":10}}";
      String override = "{\"retry\":5,\"pool\":{\"max\":20}}";
      String merged = ConfigMergeUtils.merge(baseConfig, override);

      assertThat(merged).contains("\"timeout\":30");
      assertThat(merged).contains("\"retry\":5");
      assertThat(merged).contains("\"min\":1");
      assertThat(merged).contains("\"max\":20");
    }
  }
}
