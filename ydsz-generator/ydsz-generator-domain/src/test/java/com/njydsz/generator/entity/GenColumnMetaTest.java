package com.njydsz.generator.entity;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link GenColumnMeta} 单元测试。
 *
 * <p>覆盖：getEnumValueList 解析（空值/正常/含空格/无等号项）、isTsImportRequired 判定。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("列元数据实体")
class GenColumnMetaTest {

  @Nested
  @DisplayName("getEnumValueList")
  class EnumValues {

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("null/空串返回空列表")
    void nullAndEmpty(String input) {
      GenColumnMeta col = new GenColumnMeta();
      col.setEnumValues(input);
      assertThat(col.getEnumValueList()).isEmpty();
    }

    @Test
    @DisplayName("单组键值对解析")
    void singlePair() {
      GenColumnMeta col = new GenColumnMeta();
      col.setEnumValues("0=禁用");
      List<Map.Entry<String, String>> list = col.getEnumValueList();
      assertThat(list).hasSize(1);
      assertThat(list.get(0).getKey()).isEqualTo("0");
      assertThat(list.get(0).getValue()).isEqualTo("禁用");
    }

    @Test
    @DisplayName("多组键值对解析")
    void multiplePairs() {
      GenColumnMeta col = new GenColumnMeta();
      col.setEnumValues("0=禁用,1=启用,2=待审核");
      List<Map.Entry<String, String>> list = col.getEnumValueList();
      assertThat(list).hasSize(3);
      assertThat(list.get(0).getKey()).isEqualTo("0");
      assertThat(list.get(0).getValue()).isEqualTo("禁用");
      assertThat(list.get(1).getKey()).isEqualTo("1");
      assertThat(list.get(1).getValue()).isEqualTo("启用");
      assertThat(list.get(2).getKey()).isEqualTo("2");
      assertThat(list.get(2).getValue()).isEqualTo("待审核");
    }

    @Test
    @DisplayName("含空格被 trim")
    void trimsSpaces() {
      GenColumnMeta col = new GenColumnMeta();
      col.setEnumValues(" 0 = 禁用 , 1 = 启用 ");
      List<Map.Entry<String, String>> list = col.getEnumValueList();
      assertThat(list).hasSize(2);
      assertThat(list.get(0).getKey()).isEqualTo("0");
      assertThat(list.get(0).getValue()).isEqualTo("禁用");
    }

    @Test
    @DisplayName("无等号项被过滤")
    void filtersNoEquals() {
      GenColumnMeta col = new GenColumnMeta();
      col.setEnumValues("0=禁用,INVALID,1=启用");
      List<Map.Entry<String, String>> list = col.getEnumValueList();
      assertThat(list).hasSize(2);
      assertThat(list.get(0).getKey()).isEqualTo("0");
      assertThat(list.get(1).getKey()).isEqualTo("1");
    }

    @Test
    @DisplayName("值中含等号按首个等号分割")
    void valueContainsEquals() {
      GenColumnMeta col = new GenColumnMeta();
      col.setEnumValues("0=a=b");
      List<Map.Entry<String, String>> list = col.getEnumValueList();
      assertThat(list).hasSize(1);
      assertThat(list.get(0).getKey()).isEqualTo("0");
      assertThat(list.get(0).getValue()).isEqualTo("a=b");
    }
  }

  @Nested
  @DisplayName("isTsImportRequired")
  class TsImport {

    @ParameterizedTest
    @CsvSource({
      "LocalDateTime, true",
      "BigDecimal, true",
      "byte[], true",
      "String, false",
      "Integer, false",
      "Boolean, false"
    })
    @DisplayName("按 javaType 判定是否需要额外 TS import")
    void byJavaType(String javaType, boolean expected) {
      GenColumnMeta col = new GenColumnMeta();
      col.setJavaType(javaType);
      assertThat(col.isTsImportRequired()).isEqualTo(expected);
    }

    @Test
    @DisplayName("javaType=null 返回 false")
    void nullJavaType() {
      GenColumnMeta col = new GenColumnMeta();
      col.setJavaType(null);
      assertThat(col.isTsImportRequired()).isFalse();
    }
  }
}
