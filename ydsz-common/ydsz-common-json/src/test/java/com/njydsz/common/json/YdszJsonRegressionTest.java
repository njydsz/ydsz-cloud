package com.njydsz.common.json;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.common.json.annotation.JsonClass;
import com.njydsz.common.json.annotation.JsonProperty;

/**
 * P0 缺陷回归测试集（2026-09-01 审计修复配套）。
 *
 * <p>覆盖本轮实跑复现的 5 个 P0 场景与序列化正确性基线：
 *
 * <ul>
 *   <li>P0-1 fastWriterPool ThreadLocal 重入（容器内嵌无注解 Bean 输出损坏 JSON）
 *   <li>P0-2 长字符串字段容量保障（8KB 字段曾抛 Range out of bounds）
 *   <li>P0-3 包装类型 0/false 值静默丢失
 *   <li>P0-4 DAG 共享引用输出损坏
 *   <li>P0-5 NaN/Infinity 输出非法 JSON 字面量
 *   <li>P0-6 @JsonProperty.access 声明支持实则零实现（WRITE_ONLY 密码泄漏）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
class YdszJsonRegressionTest {

  /** 无注解 Bean（走 BeanSerializer 快速路径） */
  static class PlainBean {
    private Integer count = 0;
    private Boolean active = false;
    private String name = "x";

    Integer getCount() {
      return count;
    }

    void setCount(Integer count) {
      this.count = count;
    }

    Boolean getActive() {
      return active;
    }

    void setActive(Boolean active) {
      this.active = active;
    }

    String getName() {
      return name;
    }

    void setName(String name) {
      this.name = name;
    }
  }

  /** @JsonClass 注解 Bean（走 ValueWriter 注解路径） */
  @JsonClass
  static class AnnotatedBean {
    private Integer count = 0;
    private String name = "x";

    Integer getCount() {
      return count;
    }

    void setCount(Integer count) {
      this.count = count;
    }

    String getName() {
      return name;
    }

    void setName(String name) {
      this.name = name;
    }
  }

  /** 嵌套容器结构（验证深层重入） */
  static class OrderDto {
    private Long id = 1L;
    private List<PlainBean> items = new ArrayList<>();

    Long getId() {
      return id;
    }

    void setId(Long id) {
      this.id = id;
    }

    List<PlainBean> getItems() {
      return items;
    }

    void setItems(List<PlainBean> items) {
      this.items = items;
    }
  }

  /** access 注解 Bean（安全语义验证） */
  static class UserDto {
    private String username = "alice";

    @JsonProperty(value = "password", access = JsonProperty.Access.WRITE_ONLY)
    private String password = "secret123";

    @JsonProperty(value = "createdAt", access = JsonProperty.Access.READ_ONLY)
    private String createdAt = "2026-09-01";

    String getUsername() {
      return username;
    }

    void setUsername(String username) {
      this.username = username;
    }

    String getPassword() {
      return password;
    }

    void setPassword(String password) {
      this.password = password;
    }

    String getCreatedAt() {
      return createdAt;
    }

    void setCreatedAt(String createdAt) {
      this.createdAt = createdAt;
    }
  }

  // ==================== P0-1/P0-2 重入防护 ====================

  @Test
  @DisplayName("P0: List<无注解Bean> 输出合法 JSON（fastWriterPool 重入修复）")
  void listOfPlainBeanProducesValidJson() {
    List<PlainBean> list = new ArrayList<>();
    list.add(new PlainBean());
    list.add(new PlainBean());

    String json = YdszJson.toJson(list);

    assertTrue(json.startsWith("["), "应以 [ 开头: " + json);
    assertTrue(json.endsWith("]"), "应以 ] 结尾: " + json);
    assertEquals(2, countOccurrences(json, "name"), "应包含 2 个 name 字段: " + json);
    assertDoesNotThrow(() -> YdszJson.fromJson(json, List.class, PlainBean.class));
  }

  @Test
  @DisplayName("P0: Map<String,无注Bean> 输出合法 JSON（键名不被清空）")
  void mapOfPlainBeanProducesValidJson() {
    Map<String, PlainBean> map = new LinkedHashMap<>();
    map.put("a", new PlainBean());
    map.put("b", new PlainBean());

    String json = YdszJson.toJson(map);

    assertTrue(json.contains("\"a\":"), "应包含键 a: " + json);
    assertTrue(json.contains("\"b\":"), "应包含键 b: " + json);
    assertTrue(YdszJson.isValidJson(json), "输出应为合法 JSON: " + json);
  }

  @Test
  @DisplayName("P0: Bean 内 List<Bean> 字段（深层嵌套重入）")
  void beanWithBeanCollectionField() {
    OrderDto order = new OrderDto();
    order.getItems().add(new PlainBean());
    order.getItems().add(new PlainBean());

    String json = YdszJson.toJson(order);

    assertTrue(YdszJson.isValidJson(json), "输出应为合法 JSON: " + json);
    assertTrue(json.contains("\"items\":"), "应包含 items 字段: " + json);
  }

  @Test
  @DisplayName("P0: toJsonBytes 走 HTTP 转换器同路径（serializeToBytes）")
  void toJsonBytesWithPlainBeanList() {
    List<PlainBean> list = new ArrayList<>();
    list.add(new PlainBean());

    String json = new String(YdszJson.toJsonBytes(list), java.nio.charset.StandardCharsets.UTF_8);

    assertTrue(json.startsWith("["), "字节路径同样应输出合法 JSON: " + json);
    assertTrue(YdszJson.isValidJson(json), "字节路径输出应为合法 JSON: " + json);
  }

  @Test
  @DisplayName("P0: Map 内嵌套 Bean 与普通值混合（外层键名不丢失）")
  void mapWithNestedBeanAndScalars() {
    Map<String, Object> outer = new LinkedHashMap<>();
    outer.put("inner", new PlainBean());
    outer.put("tail", "T");

    String json = YdszJson.toJson(outer);

    assertTrue(json.contains("\"inner\":"), "嵌套键名不应丢失: " + json);
    assertTrue(json.endsWith("}"), "应以 } 结尾: " + json);
  }

  // ==================== P0-4 DAG 共享引用 ====================

  @Test
  @DisplayName("P0: DAG 共享引用（非循环）输出完整且合法")
  void dagSharedReferenceNotCorrupted() {
    PlainBean shared = new PlainBean();
    Map<String, PlainBean> holder1 = new LinkedHashMap<>();
    Map<String, PlainBean> holder2 = new LinkedHashMap<>();
    holder1.put("a", shared);
    holder2.put("a", shared);
    List<Map<String, PlainBean>> dag = new ArrayList<>();
    dag.add(holder1);
    dag.add(holder2);

    String json = YdszJson.toJson(dag);

    assertTrue(YdszJson.isValidJson(json), "输出应为合法 JSON: " + json);
    assertEquals(2, countOccurrences(json, "\"a\""), "共享引用应完整输出两次: " + json);
  }

  // ==================== P0-3 包装类型 0/false ====================

  @Test
  @DisplayName("P0: 无注解 Bean 的 Integer=0 / Boolean=false 不被静默吞掉")
  void wrapperZeroAndFalseAreSerialized() {
    String json = YdszJson.toJson(new PlainBean());

    assertTrue(json.contains("\"count\":0"), "Integer=0 应输出: " + json);
    assertTrue(json.contains("\"active\":false"), "Boolean=false 应输出: " + json);
  }

  @Test
  @DisplayName("P0: 包装类型 null 值仍被跳过（与 Jackson ALWAYS+非 null 语义一致）")
  void wrapperNullStillSkipped() {
    PlainBean bean = new PlainBean();
    bean.setCount(null);
    bean.setActive(null);

    String json = YdszJson.toJson(bean);

    assertFalse(json.contains("count"), "null 值应跳过: " + json);
    assertFalse(json.contains("active"), "null 值应跳过: " + json);
  }

  @Test
  @DisplayName("注解路径与快速路径 0 值行为一致（双路径一致性）")
  void annotatedAndPlainPathConsistent() {
    assertTrue(YdszJson.toJson(new AnnotatedBean()).contains("\"count\":0"));
    assertTrue(YdszJson.toJson(new PlainBean()).contains("\"count\":0"));
  }

  // ==================== P0-2 长字符串容量 ====================

  @Test
  @DisplayName("P0: 8KB String 字段不崩溃且内容完整")
  void longStringFieldDoesNotCrash() {
    PlainBean bean = new PlainBean();
    bean.setName("A".repeat(8000));

    String json = assertDoesNotThrow(() -> YdszJson.toJson(bean));

    assertTrue(json.contains("\"name\":\"AAAA"), "长字段应完整输出");
    assertTrue(json.endsWith("\"}"));
  }

  @Test
  @DisplayName("长字符串作为 List 元素与 Map 键值不崩溃（writeStringDirectNoCheck 容量）")
  void longStringInContainerDoesNotCrash() {
    String longStr = "B".repeat(100000);
    List<String> list = new ArrayList<>(List.of(longStr, "tail"));
    Map<String, String> map = new LinkedHashMap<>();
    map.put(longStr, longStr);

    assertDoesNotThrow(() -> YdszJson.toJson(list));
    assertDoesNotThrow(() -> YdszJson.toJson(map));
    assertTrue(YdszJson.toJson(list).contains("tail"));
  }

  @Test
  @DisplayName("转义字符串字段（引号/反斜杠/控制字符）不崩溃且输出合法")
  void escapedStringFields() {
    PlainBean bean = new PlainBean();
    bean.setName("a\"b\\c\nd\te");

    String json = assertDoesNotThrow(() -> YdszJson.toJson(bean));

    assertTrue(YdszJson.isValidJson(json), "转义输出应为合法 JSON: " + json);
    PlainBean back = YdszJson.fromJson(json, PlainBean.class);
    assertEquals("a\"b\\c\nd\te", back.getName(), "转义 round-trip 应还原");
  }

  // ==================== P0-5 NaN/Infinity ====================

  @Test
  @DisplayName("P0: NaN/Infinity 统一输出 null（全路径合法 JSON）")
  void nanAndInfinityOutputNull() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("nan", Double.NaN);
    m.put("pinf", Double.POSITIVE_INFINITY);
    m.put("ninf", Double.NEGATIVE_INFINITY);
    m.put("fnan", Float.NaN);

    String json = YdszJson.toJson(m);

    assertTrue(YdszJson.isValidJson(json), "输出应为合法 JSON: " + json);
    assertFalse(json.contains("NaN"), "不得出现 NaN 字面量: " + json);
    assertFalse(json.contains("Infinity"), "不得出现 Infinity 字面量: " + json);
  }

  // ==================== P0-6 @JsonProperty.access ====================

  @Test
  @DisplayName("P0: WRITE_ONLY 密码字段不被序列化输出（安全）")
  void writeOnlyFieldNotSerialized() {
    String json = YdszJson.toJson(new UserDto());

    assertFalse(json.contains("secret123"), "密码不得泄漏: " + json);
    assertFalse(json.contains("password"), "WRITE_ONLY 字段不应出现在输出: " + json);
    assertTrue(json.contains("username"), "普通字段应正常输出: " + json);
  }

  @Test
  @DisplayName("P0: WRITE_ONLY 字段可反序列化写入（登录密码接收场景）")
  void writeOnlyFieldDeserialized() {
    UserDto dto = YdszJson.fromJson("{\"password\":\"newpass\"}", UserDto.class);

    assertEquals("newpass", dto.getPassword(), "WRITE_ONLY 字段应可反序列化");
  }

  @Test
  @DisplayName("P0: READ_ONLY 字段反序列化忽略（服务端生成字段不被外部注入）")
  void readOnlyFieldIgnoredOnDeserialize() {
    UserDto dto = YdszJson.fromJson("{\"createdAt\":\"1999-01-01\"}", UserDto.class);

    assertEquals("2026-09-01", dto.getCreatedAt(), "READ_ONLY 字段应保持原值，不接受注入");
  }

  // ==================== round-trip 基线 ====================

  @Test
  @DisplayName("round-trip: 嵌套 Bean + 集合字段 + 泛型列表")
  void roundTripNestedAndGeneric() {
    OrderDto order = new OrderDto();
    order.setId(42L);
    PlainBean item = new PlainBean();
    item.setName("item-1");
    order.getItems().add(item);

    String json = YdszJson.toJson(order);
    Map<String, Object> parsed = YdszJson.parseMap(json);

    assertEquals(42, ((Number) parsed.get("id")).intValue());
    assertNotNull(parsed.get("items"));
  }

  @Test
  @DisplayName("round-trip: 数值精度（long 超 int 范围 / BigDecimal / double）")
  void roundTripNumericPrecision() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("big", 9007199254740993L);
    m.put("dec", new BigDecimal("123.45678901234567890123456789"));
    m.put("dbl", 0.1 + 0.2);

    String json = YdszJson.toJson(m);

    assertTrue(json.contains("9007199254740993"), "long 精度不得丢失: " + json);
    assertTrue(json.contains("123.45678901234567890123456789"), "BigDecimal 精度不得丢失: " + json);
    Map<String, Object> back = YdszJson.parseMap(json);
    assertEquals(0.30000000000000004, (Double) back.get("dbl"), 1e-15, "double round-trip 精度");
  }

  @Test
  @DisplayName("round-trip: 日期 / 枚举 / Unicode 代理对")
  void roundTripDateEnumUnicode() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("time", LocalDateTime.of(2026, 9, 1, 10, 0, 0));
    m.put("emoji", "🚀中");

    String json = YdszJson.toJson(m);

    assertTrue(json.contains("2026-09-01T10:00"), "LocalDateTime 应输出: " + json);
    assertTrue(json.contains("🚀"), "Unicode 代理对应完整输出: " + json);
  }

  @Test
  @DisplayName("fromJson(List.class, X.class) 泛型反序列化")
  void genericListDeserialization() {
    List<AnnotatedBean> beans = YdszJson.fromJson(
        "[{\"count\":5,\"name\":\"a\"},{\"count\":6,\"name\":\"b\"}]", List.class, AnnotatedBean.class);

    assertEquals(2, beans.size());
    assertEquals(5, beans.get(0).getCount());
    assertEquals("b", beans.get(1).getName());
  }

  private static int countOccurrences(String haystack, String needle) {
    int count = 0;
    int idx = 0;
    while ((idx = haystack.indexOf(needle, idx)) != -1) {
      count++;
      idx += needle.length();
    }
    return count;
  }
}
