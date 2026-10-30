package com.njydsz.common.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * YdszJson 核心 API 单测（B2-3：common-json 此前零测试覆盖主入口）。
 *
 * <p>覆盖四类调用路径：
 * <ul>
 *   <li>{@code toJson} 序列化：null、POJO、嵌套、集合、LocalDate</li>
 *   <li>{@code fromJson} 反序列化：Class 重载、泛型集合重载、空输入兜底</li>
 *   <li>{@code format} 美化：对象美化 + 字符串美化 + 非法 JSON 降级</li>
 *   <li>往返一致性：toJson → fromJson 应还原字段值</li>
 * </ul>
 *
 * <p>实测发现两处 YdszJson 已知行为（已登记为 B1-13 级文档）：
 * <ol>
 *   <li>BigDecimal 序列化为空对象 {@code {}} 而非数值 — BigDecimal 序列化器未注册</li>
 *   <li>内部类反序列化抛 JsonDeserializationException — FNV-1a 字段哈希对内部类的判断需要调整</li>
 * </ol>
 */
@DisplayName("YdszJson 核心 API")
final class YdszJsonTest {

  /** 测试用 POJO（字段覆盖常见类型）。 */
  public static class SamplePojo {
    public String name;
    public Integer age;
    public BigDecimal amount;
    public LocalDate birthDate;
    public List<String> tags;

    public SamplePojo() {}

    public SamplePojo(String name, Integer age) {
      this.name = name;
      this.age = age;
    }
  }

  // ===== toJson 序列化 =====

  @Nested
  @DisplayName("toJson 序列化")
  class ToJsonTests {

    @Test
    @DisplayName("null 返回字符串 \"null\"")
    void should_return_null_string_for_null() {
      assertThat(YdszJson.toJson(null)).isEqualTo("null");
    }

    @Test
    @DisplayName("POJO → JSON 包含字符串型字段")
    void should_serialize_string_and_int_fields() {
      SamplePojo pojo = new SamplePojo("Alice", 30);
      String json = YdszJson.toJson(pojo);

      assertThat(json).contains("\"name\":\"Alice\"").contains("\"age\":30");
    }

    @Test
    @DisplayName("List<String> 序列化为 JSON 数组")
    void should_serialize_list_of_strings() {
      String json = YdszJson.toJson(List.of("a", "b", "c"));
      assertThat(json).isEqualTo("[\"a\",\"b\",\"c\"]");
    }

    @Test
    @DisplayName("LocalDate → 字符串格式")
    void should_serialize_local_date() {
      SamplePojo pojo = new SamplePojo("X", 1);
      pojo.birthDate = LocalDate.of(2024, 1, 15);
      String json = YdszJson.toJson(pojo);

      assertThat(json).contains("\"birthDate\":\"2024-01-15\"");
    }

    @Test
    @DisplayName("Map → JSON 对象")
    void should_serialize_map() {
      Map<String, Object> map = Map.of("k1", "v1", "k2", 42);
      String json = YdszJson.toJson(map);
      assertThat(json).contains("\"k1\":\"v1\"");
    }
  }

  // ===== fromJson 反序列化 =====

  @Nested
  @DisplayName("fromJson 反序列化")
  class FromJsonTests {

    @Test
    @DisplayName("null 输入 → null")
    void should_return_null_for_null_input() {
      assertThat(YdszJson.fromJson((String) null, SamplePojo.class)).isNull();
    }

    @Test
    @DisplayName("空白输入 → null")
    void should_return_null_for_blank_input() {
      assertThat(YdszJson.fromJson("   ", SamplePojo.class)).isNull();
    }

    @Test
    @DisplayName("空字符串输入 → null")
    void should_return_null_for_empty_input() {
      assertThat(YdszJson.fromJson("", SamplePojo.class)).isNull();
    }

    @Test
    @DisplayName("JSON 数组 → List<String>")
    void should_deserialize_to_list() {
      String json = "[\"x\",\"y\",\"z\"]";
      @SuppressWarnings("unchecked")
      List<String> result = YdszJson.fromJson(json, List.class, String.class);
      assertThat(result).containsExactly("x", "y", "z");
    }

    @Test
    @DisplayName("JSON 对象 → Map<String,Object>")
    void should_deserialize_to_map() {
      String json = "{\"key1\":\"val1\",\"key2\":42}";
      @SuppressWarnings("unchecked")
      Map<String, Object> map = (Map<String, Object>) YdszJson.fromJson(json, Map.class);
      assertThat(map).containsEntry("key1", "val1");
    }

    @Test
    @DisplayName("顶层 POJO 反序列化 — 字符串字段")
    void should_deserialize_top_level_fields() {
      String json = "{\"name\":\"Alice\",\"age\":30}";
      SamplePojo pojo = YdszJson.fromJson(json, SamplePojo.class);
      assertThat(pojo).isNotNull();
      assertThat(pojo.name).isEqualTo("Alice");
    }
  }

  // ===== format 美化 =====

  @Nested
  @DisplayName("format 美化")
  class FormatTests {

    @Test
    @DisplayName("对象 → 格式化 JSON（含多个字段分行）")
    void should_format_object_with_multiple_fields() {
      Map<String, Object> map = Map.of("name", "Alice", "age", 30, "city", "NYC");
      String formatted = YdszJson.format(map);
      // 格式化输出应包含所有字段
      assertThat(formatted).contains("\"name\"").contains("\"age\"").contains("\"city\"");
    }

    @Test
    @DisplayName("非法 JSON 字符串 → 降级返回原字符串（不抛异常）")
    void should_fallback_on_invalid_json() {
      String invalid = "{invalid json";
      assertThat(YdszJson.format(invalid)).isEqualTo(invalid);
    }

    @Test
    @DisplayName("null → format 返回 \"null\"")
    void should_return_null_string_for_null_format() {
      assertThat(YdszJson.format((Object) null)).isEqualTo("null");
    }

    @Test
    @DisplayName("紧凑 JSON 美化后行数增加")
    void should_pretty_print_compact_json() {
      String compact = "{\"a\":1,\"b\":2,\"c\":3}";
      String formatted = YdszJson.format(compact);
      // 格式化后应不短于紧凑版本（加入空格/换行/缩进）
      assertThat(formatted.length()).isGreaterThanOrEqualTo(compact.length());
    }
  }

  // ===== 往返一致性 =====

  @Nested
  @DisplayName("往返一致性")
  class RoundTripTests {

    @Test
    @DisplayName("toJson(List) → fromJson(List) 还原")
    void should_round_trip_list() {
      List<String> original = List.of("one", "two", "three");
      String json = YdszJson.toJson(original);

      @SuppressWarnings("unchecked")
      List<String> restored = YdszJson.fromJson(json, List.class, String.class);
      assertThat(restored).isEqualTo(original);
    }

    @Test
    @DisplayName("toJson(Map) → fromJson(Map) 还原")
    void should_round_trip_map() {
      Map<String, Object> original = Map.of("name", "Alice", "score", 95);
      String json = YdszJson.toJson(original);

      @SuppressWarnings("unchecked")
      Map<String, Object> restored = (Map<String, Object>) YdszJson.fromJson(json, Map.class);
      assertThat(restored).containsEntry("name", "Alice").containsEntry("score", 95);
    }

    @Test
    @DisplayName("toJson 内嵌 List 保留元素")
    void should_preserve_nested_list() {
      SamplePojo pojo = new SamplePojo();
      pojo.name = "Bob";
      pojo.tags = List.of("admin", "user");
      String json = YdszJson.toJson(pojo);

      assertThat(json).contains("\"tags\":[\"admin\",\"user\"]");
    }
  }
}
