package com.njydsz.common.json;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.common.json.exception.JsonException;

/**
 * JsonPatch RFC 6902 / RFC 7396 合规回归测试（P1 修复配套）。
 *
 * <p>覆盖本轮修复的合规缺口：
 *
 * <ul>
 *   <li>test 操作数值等价（RFC 6902 §4.6：1 与 1.0 与 1L 等价）
 *   <li>test 操作路径缺失必须失败（区分"路径缺失"与"显式 null 值"）
 *   <li>整文档路径（path = ""）的 ADD/REPLACE 支持
 *   <li>ADD 不再自动创建中间节点（父路径必须存在）
 *   <li>JSON Pointer "/" 指向空键成员而非整文档（RFC 6901）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
class JsonPatchComplianceTest {

  @Test
  @DisplayName("RFC 6902 §4.6: test 数值按值比较（1 与 1.0 与 1L 等价）")
  void testNumericValueEquality() {
    assertDoesNotThrow(() ->
        YdszJson.patch("{\"count\":1}", "[{\"op\":\"test\",\"path\":\"/count\",\"value\":1.0}]"));
    assertDoesNotThrow(() ->
        YdszJson.patch("{\"count\":1}", "[{\"op\":\"test\",\"path\":\"/count\",\"value\":1}]"));
    // 不等数值必须失败
    assertThrows(JsonException.class, () ->
        YdszJson.patch("{\"count\":1}", "[{\"op\":\"test\",\"path\":\"/count\",\"value\":2}]"));
  }

  @Test
  @DisplayName("RFC 6902: test 路径缺失必须失败（即使 value 为 null）")
  void testMissingPathFailsEvenWithNullValue() {
    assertThrows(JsonException.class, () ->
        YdszJson.patch("{\"a\":1}", "[{\"op\":\"test\",\"path\":\"/missing\",\"value\":null}]"));
  }

  @Test
  @DisplayName("RFC 6902: 整文档路径 path=\"\" 的 ADD 替换整棵文档")
  void rootPathAddReplacesDocument() {
    String result = YdszJson.patch(
        "{\"a\":1,\"b\":2}",
        "[{\"op\":\"add\",\"path\":\"\",\"value\":{\"x\":9}}]");

    assertTrue(result.contains("\"x\""), "整文档替换应生效: " + result);
    assertTrue(!result.contains("\"a\""), "旧字段应被替换掉: " + result);
  }

  @Test
  @DisplayName("RFC 6902: 整文档路径 path=\"\" 的 REPLACE 替换整棵文档")
  void rootPathReplaceReplacesDocument() {
    String result = YdszJson.patch(
        "{\"a\":1}",
        "[{\"op\":\"replace\",\"path\":\"\",\"value\":{\"y\":8}}]");

    assertTrue(result.contains("\"y\""), "整文档替换应生效: " + result);
  }

  @Test
  @DisplayName("RFC 6902: ADD 父路径缺失时必须失败（不再静默创建中间节点）")
  void addDoesNotCreateIntermediateNodes() {
    assertThrows(JsonException.class, () ->
        YdszJson.patch(
            "{\"a\":{}}",
            "[{\"op\":\"add\",\"path\":\"/a/missing/inner\",\"value\":1}]"));
  }

  @Test
  @DisplayName("RFC 6901: 路径 \"/\" 指向空键成员而非整文档")
  void slashPathTargetsEmptyKey() {
    // "/" 指向键为空字符串的成员，replace 应作用于该成员而非整文档
    String result = YdszJson.patch(
        "{\"\":\"old\",\"k\":1}",
        "[{\"op\":\"replace\",\"path\":\"/\",\"value\":\"new\"}]");

    assertTrue(result.contains("\"new\""), "空键成员应被替换: " + result);
    assertTrue(result.contains("\"k\":1"), "其他字段不受影响: " + result);
  }

  @Test
  @DisplayName("RFC 6902: 常规 patch 操作回归（replace/add/remove 组合）")
  void regularPatchOperationsStillWork() {
    String result = YdszJson.patch(
        "{\"name\":\"old\",\"age\":25}",
        "[{\"op\":\"replace\",\"path\":\"/name\",\"value\":\"new\"},"
            + "{\"op\":\"add\",\"path\":\"/email\",\"value\":\"a@b.c\"},"
            + "{\"op\":\"remove\",\"path\":\"/age\"}]");

    assertTrue(result.contains("\"name\":\"new\""), "replace 应生效: " + result);
    assertTrue(result.contains("a@b.c"), "add 应生效: " + result);
    assertTrue(!result.contains("age"), "remove 应生效: " + result);
  }

  @Test
  @DisplayName("RFC 7396: Merge Patch 常规回归（null 删除 / 嵌套合并）")
  void mergePatchRegression() {
    String result = YdszJson.mergePatch(
        "{\"a\":1,\"b\":{\"c\":2,\"d\":3}}",
        "{\"b\":{\"c\":null,\"e\":4}}");

    assertTrue(result.contains("\"a\":1"), "未提及字段保持不变: " + result);
    assertTrue(result.contains("\"e\":4"), "新增字段应生效: " + result);
    assertTrue(!result.contains("\"c\""), "null 应删除字段: " + result);
    assertTrue(result.contains("\"d\":3"), "嵌套未提及字段保持不变: " + result);
  }

  @Test
  @DisplayName("RFC 6902: test 显式 null 值与 null 字段匹配（合法场景）")
  void testExplicitNullValueMatchesNullField() {
    assertDoesNotThrow(() ->
        YdszJson.patch("{\"a\":null}", "[{\"op\":\"test\",\"path\":\"/a\",\"value\":null}]"));
  }

  @Test
  @DisplayName("RFC 6902: 数组路径操作回归（索引插入）")
  void arrayPathOperations() {
    String result = YdszJson.patch(
        "{\"list\":[1,2,4]}",
        "[{\"op\":\"add\",\"path\":\"/list/2\",\"value\":3}]");

    Object list = YdszJson.parseMap(result).get("list");
    assertEquals("[1,2,3,4]", YdszJson.toJson(list), "索引插入应生效");
  }
}
