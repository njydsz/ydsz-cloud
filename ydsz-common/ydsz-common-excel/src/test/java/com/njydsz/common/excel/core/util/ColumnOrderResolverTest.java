package com.njydsz.common.excel.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.common.excel.annotation.ExcelIgnore;
import com.njydsz.common.excel.annotation.ExcelProperty;

/**
 * {@link ColumnOrderResolver} 单元测试。
 */
class ColumnOrderResolverTest {

  // 测试用 DTO
  static class BasicDto {
    @ExcelProperty(value = "姓名", order = 2)
    String name;

    @ExcelProperty(value = "年龄", order = 1)
    Integer age;

    @ExcelProperty(value = "邮箱", order = 3)
    String email;
  }

  // 显式 index 优先
  static class IndexedDto {
    @ExcelProperty(value = "A", index = 2)
    String fieldA;

    @ExcelProperty(value = "B", index = 0)
    String fieldB;

    @ExcelProperty(value = "C", index = 1)
    String fieldC;
  }

  // 混合模式
  static class MixedDto {
    @ExcelProperty(value = "NoIndex1", order = 5)
    String f1; // 无 index

    @ExcelProperty(value = "HasIndex", index = 0)
    String f2; // 显式 index=0

    @ExcelProperty(value = "NoIndex2", order = 1)
    String f3; // 无 index
  }

  // @ExcelIgnore 字段
  static class WithIgnoreDto {
    @ExcelProperty(value = "ID")
    String id;

    @ExcelIgnore
    String internalField;

    @ExcelProperty(value = "Name")
    String name;
  }

  // 无注解字段（应忽略）
  static class SomeAnnotatedDto {
    @ExcelProperty(value = "HasAnn")
    String annotated;

    String notAnnotated; // 应被过滤
  }

  @Test
  @DisplayName("纯 order 排序：按 order 值升序")
  void shouldOrderByAnnotation() {
    List<Field> fields = ColumnOrderResolver.resolveOrderedFields(BasicDto.class);
    assertEquals(3, fields.size());
    assertEquals("age", fields.get(0).getName());   // order=1
    assertEquals("name", fields.get(1).getName());  // order=2
    assertEquals("email", fields.get(2).getName()); // order=3
  }

  @Test
  @DisplayName("index 优先：显式 index 字段排在最前面并按 index 排序")
  void shouldPrioritizeIndex() {
    List<Field> fields = ColumnOrderResolver.resolveOrderedFields(IndexedDto.class);
    assertEquals(3, fields.size());
    assertEquals("fieldB", fields.get(0).getName()); // index=0
    assertEquals("fieldC", fields.get(1).getName()); // index=1
    assertEquals("fieldA", fields.get(2).getName()); // index=2
  }

  @Test
  @DisplayName("混合模式：显式 index 字段优先于纯 order 字段")
  void shouldMixIndexAndOrder() {
    List<Field> fields = ColumnOrderResolver.resolveOrderedFields(MixedDto.class);
    assertEquals(3, fields.size());
    // HasIndex (index=0) 排第一
    assertEquals("f2", fields.get(0).getName());
    // 纯 order 字段：f3(order=1) < f1(order=5)
    assertEquals("f3", fields.get(1).getName());
    assertEquals("f1", fields.get(2).getName());
  }

  @Test
  @DisplayName("@ExcelIgnore 字段应被过滤")
  void shouldFilterIgnoredFields() {
    List<Field> fields = ColumnOrderResolver.resolveOrderedFields(WithIgnoreDto.class);
    assertEquals(2, fields.size());
    for (Field f : fields) {
      assertTrue(!f.getName().equals("internalField"));
    }
  }

  @Test
  @DisplayName("无 @ExcelProperty 注解的字段应被过滤")
  void shouldFilterUnannotatedFields() {
    List<Field> fields = ColumnOrderResolver.resolveOrderedFields(SomeAnnotatedDto.class);
    assertEquals(1, fields.size());
    assertEquals("annotated", fields.get(0).getName());
  }

  @Test
  @DisplayName("空类应返回空列表")
  void shouldReturnEmptyForNoAnnotatedFields() {
    List<Field> fields = ColumnOrderResolver.resolveOrderedFields(String.class);
    assertTrue(fields.isEmpty());
  }

  @Test
  @DisplayName("null 应返回空列表")
  void shouldReturnEmptyForNull() {
    List<Field> fields = ColumnOrderResolver.resolveOrderedFields(null);
    assertTrue(fields.isEmpty());
  }

  @Test
  @DisplayName("getColumnPriority：显式 index 返回 index 值")
  void shouldReturnIndexPriority() {
    try {
      Field f = IndexedDto.class.getDeclaredField("fieldB");
      ExcelProperty prop = f.getAnnotation(ExcelProperty.class);
      int priority = ColumnOrderResolver.getColumnPriority(prop, 5);
      assertEquals(0, priority);
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  @DisplayName("getColumnPriority：有 order 无 index 返回中间范围值")
  void shouldReturnFallbackPriority() {
    try {
      Field f1 = MixedDto.class.getDeclaredField("f1");
      ExcelProperty prop = f1.getAnnotation(ExcelProperty.class);
      int priority = ColumnOrderResolver.getColumnPriority(prop, 0);
      assertTrue(priority > Integer.MAX_VALUE / 2);
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  @DisplayName("getColumnPriority：null 注解返回兜底值")
  void shouldReturnMaxForNullAnnotation() {
    int priority = ColumnOrderResolver.getColumnPriority(null, 10);
    assertTrue(priority > Integer.MAX_VALUE - 100);
  }
}
