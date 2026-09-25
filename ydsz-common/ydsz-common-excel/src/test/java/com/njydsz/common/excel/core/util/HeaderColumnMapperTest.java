package com.njydsz.common.excel.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.common.excel.annotation.ExcelProperty;
import com.njydsz.common.excel.core.util.HeaderColumnMapper.MatchMode;

/**
 * {@link HeaderColumnMapper} 单元测试。
 */
class HeaderColumnMapperTest {

  static class SimpleDto {
    @ExcelProperty(value = "Name")
    String name;

    @ExcelProperty(value = "Age")
    Integer age;

    @ExcelProperty(value = "Email")
    String email;
  }

  @Test
  @DisplayName("EXACT 模式：完全匹配")
  void exactModeShouldMatch() {
    List<String> headers = Arrays.asList("Name", "Age", "Email");
    int[] mapping = HeaderColumnMapper.map(SimpleDto.class, headers, MatchMode.EXACT);
    assertEquals(3, mapping.length);
    assertEquals(0, mapping[0]); // Name -> col 0
    assertEquals(1, mapping[1]); // Age -> col 1
    assertEquals(2, mapping[2]); // Email -> col 2
  }

  @Test
  @DisplayName("EXACT 模式：大小写不匹配应返回 -1")
  void exactModeShouldNotMatchDifferentCase() {
    List<String> headers = Arrays.asList("name", "age", "email");
    int[] mapping = HeaderColumnMapper.map(SimpleDto.class, headers, MatchMode.EXACT);
    // EXACT 模式区分大小写
    assertEquals(-1, mapping[0]);
    assertEquals(-1, mapping[1]);
    assertEquals(-1, mapping[2]);
  }

  @Test
  @DisplayName("CASE_INSENSITIVE 模式：忽略大小写")
  void caseInsensitiveModeShouldMatch() {
    List<String> headers = Arrays.asList("NAME", "AGE", "EMAIL");
    int[] mapping = HeaderColumnMapper.map(SimpleDto.class, headers, MatchMode.CASE_INSENSITIVE);
    assertEquals(0, mapping[0]);
    assertEquals(1, mapping[1]);
    assertEquals(2, mapping[2]);
  }

  @Test
  @DisplayName("WHITESPACE_NORMALIZED 模式：空格处理后匹配")
  void whitespaceNormalizedModeShouldMatch() {
    List<String> headers = Arrays.asList(" Name ", "  Age", "Email  ");
    int[] mapping = HeaderColumnMapper.map(SimpleDto.class, headers, MatchMode.WHITESPACE_NORMALIZED);
    assertEquals(0, mapping[0]);
    assertEquals(1, mapping[1]);
    assertEquals(2, mapping[2]);
  }

  @Test
  @DisplayName("LENIENT 模式：全半角转换后匹配")
  void lenientModeShouldHandleFullWidth() {
    // 全角 "Ｎａｍｅ"（但不转换字母全角，因为这里用 ASCII 全角，而代码只转换 ASCII 全角区段）
    // 测试：带括号的情况
    List<String> headers = Arrays.asList("（Name）", "Age", "Email");
    int[] mapping = HeaderColumnMapper.map(SimpleDto.class, headers, MatchMode.LENIENT);
    assertEquals(0, mapping[0]); // 去除全角括号后匹配
    assertEquals(1, mapping[1]);
    assertEquals(2, mapping[2]);
  }

  @Test
  @DisplayName("列数不一致：Excel 列少于字段时返回 -1")
  void shouldHandleMissingColumns() {
    List<String> headers = Arrays.asList("Name", "Age");
    int[] mapping = HeaderColumnMapper.map(SimpleDto.class, headers, MatchMode.EXACT);
    assertEquals(3, mapping.length);
    assertEquals(0, mapping[0]);
    assertEquals(1, mapping[1]);
    assertEquals(-1, mapping[2]); // Email 列不存在
  }

  @Test
  @DisplayName("列数不一致：Excel 列多于字段时忽略")
  void shouldHandleExtraColumns() {
    List<String> headers = Arrays.asList("Name", "Age", "Email", "Extra");
    int[] mapping = HeaderColumnMapper.map(SimpleDto.class, headers, MatchMode.EXACT);
    assertEquals(3, mapping.length); // 只有 3 个字段
    assertEquals(0, mapping[0]);
    assertEquals(1, mapping[1]);
    assertEquals(2, mapping[2]);
  }

  @Test
  @DisplayName("重复 header：首次匹配优先（先到先得）")
  void shouldPrioritizeFirstMatch() {
    List<String> headers = Arrays.asList("Name", "Name", "Email");
    int[] mapping = HeaderColumnMapper.map(SimpleDto.class, headers, MatchMode.EXACT);
    // Name 匹配第一个；Age 匹配不到（-1）；Email 匹配 col 2
    assertEquals(0, mapping[0]); // Name -> col 0
    assertEquals(-1, mapping[1]); // Age -> 无匹配
    assertEquals(2, mapping[2]); // Email -> col 2
  }

  @Test
  @DisplayName("空 header 列表返回零长度数组")
  void shouldReturnEmptyForEmptyHeaders() {
    int[] mapping = HeaderColumnMapper.map(SimpleDto.class, Arrays.asList(), MatchMode.EXACT);
    assertEquals(0, mapping.length);
  }

  @Test
  @DisplayName("null 输入返回零长度数组")
  void shouldReturnEmptyForNull() {
    int[] mapping = HeaderColumnMapper.map(null, null, MatchMode.EXACT);
    assertEquals(0, mapping.length);
  }

  @Test
  @DisplayName("toColumnOrderArray 按 Excel 列序输出")
  void shouldProduceColumnOrderedArray() {
    List<String> headers = Arrays.asList("Name", "Age", "Email");
    int[] mapping = HeaderColumnMapper.map(SimpleDto.class, headers, MatchMode.EXACT);
    String[] ordered = HeaderColumnMapper.toColumnOrderArray(mapping, headers);
    assertEquals(3, ordered.length);
    assertEquals("Name", ordered[0]);
    assertEquals("Age", ordered[1]);
    assertEquals("Email", ordered[2]);
  }
}
