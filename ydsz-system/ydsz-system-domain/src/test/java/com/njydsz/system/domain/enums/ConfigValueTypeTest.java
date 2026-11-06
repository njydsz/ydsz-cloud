package com.njydsz.system.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ConfigValueType} 单元测试。
 *
 * <p>覆盖：validate 合法/非法、validateFormat 四种类型格式校验、parseValue 类型转换、括号平衡 JSON 校验。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("配置值类型枚举解析")
class ConfigValueTypeTest {

  @Nested
  @DisplayName("validate 合法值")
  class ValidateValid {

    @ParameterizedTest(name = "validate(\"{0}\") 不抛异常")
    @CsvSource({"STRING", "NUMBER", "BOOLEAN", "JSON"})
    @DisplayName("全量类型均合法")
    void should_accept_all_types(String code) {
      ConfigValueType.validate(code);
    }

    @ParameterizedTest(name = "validate(\"{0}\") 大小写无关")
    @CsvSource({"string", "Number", "boolean", "Json"})
    @DisplayName("大小写无关")
    void should_be_case_insensitive(String code) {
      ConfigValueType.validate(code);
    }
  }

  @Nested
  @DisplayName("validate 非法值")
  class ValidateInvalid {

    @Test
    @DisplayName("null 抛出异常")
    void should_throw_for_null() {
      assertThatThrownBy(() -> ConfigValueType.validate(null))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("空串抛出异常")
    void should_throw_for_empty() {
      assertThatThrownBy(() -> ConfigValueType.validate(""))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("纯空白抛出异常")
    void should_throw_for_blank() {
      assertThatThrownBy(() -> ConfigValueType.validate("   "))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "validate(\"{0}\") 未知类型抛出异常")
    @ValueSource(strings = {"FAKE", "STRINGX", "1", "string_number"})
    @DisplayName("未知类型抛出异常")
    void should_throw_for_unknown(String code) {
      assertThatThrownBy(() -> ConfigValueType.validate(code))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("validateFormat STRING")
  class ValidateFormatString {

    @Test
    @DisplayName("短字符串通过")
    void should_pass_short_string() {
      assertThat(ConfigValueType.validateFormat("STRING", "hello")).isNull();
    }

    @Test
    @DisplayName("超长字符串返回错误")
    void should_fail_too_long_string() {
      String tooLong = "a".repeat(4097);

      assertThat(ConfigValueType.validateFormat("STRING", tooLong)).isNotNull();
    }

    @Test
    @DisplayName("边界长度 4096 通过")
    void should_pass_boundary_length() {
      String boundary = "a".repeat(4096);

      assertThat(ConfigValueType.validateFormat("STRING", boundary)).isNull();
    }
  }

  @Nested
  @DisplayName("validateFormat NUMBER")
  class ValidateFormatNumber {

    @Test
    @DisplayName("合法数值通过")
    void should_pass_valid_number() {
      assertThat(ConfigValueType.validateFormat("NUMBER", "123.45")).isNull();
    }

    @Test
    @DisplayName("整数通过")
    void should_pass_integer() {
      assertThat(ConfigValueType.validateFormat("NUMBER", "100")).isNull();
    }

    @Test
    @DisplayName("负数通过")
    void should_pass_negative() {
      assertThat(ConfigValueType.validateFormat("NUMBER", "-5")).isNull();
    }

    @Test
    @DisplayName("非数值返回错误")
    void should_fail_non_number() {
      assertThat(ConfigValueType.validateFormat("NUMBER", "abc")).isNotNull();
    }

    @Test
    @DisplayName("超出上限返回错误")
    void should_fail_out_of_range() {
      assertThat(ConfigValueType.validateFormat("NUMBER", "1e16")).isNotNull();
    }

    @Test
    @DisplayName("超出下限返回错误")
    void should_fail_below_range() {
      assertThat(ConfigValueType.validateFormat("NUMBER", "-1e16")).isNotNull();
    }
  }

  @Nested
  @DisplayName("validateFormat BOOLEAN")
  class ValidateFormatBoolean {

    @ParameterizedTest(name = "validateFormat(BOOLEAN, \"{0}\") 通过")
    @ValueSource(strings = {"true", "false", "TRUE", "FALSE", "True", "False"})
    @DisplayName("合法布尔值通过")
    void should_pass_valid_boolean(String value) {
      assertThat(ConfigValueType.validateFormat("BOOLEAN", value)).isNull();
    }

    @ParameterizedTest(name = "validateFormat(BOOLEAN, \"{0}\") 返回错误")
    @ValueSource(strings = {"yes", "no", "1", "0", "truth"})
    @DisplayName("非法布尔值返回错误")
    void should_fail_invalid_boolean(String value) {
      assertThat(ConfigValueType.validateFormat("BOOLEAN", value)).isNotNull();
    }
  }

  @Nested
  @DisplayName("validateFormat JSON")
  class ValidateFormatJson {

    @Test
    @DisplayName("合法 JSON 对象通过")
    void should_pass_json_object() {
      assertThat(ConfigValueType.validateFormat("JSON", "{\"a\":1}")).isNull();
    }

    @Test
    @DisplayName("合法 JSON 数组通过")
    void should_pass_json_array() {
      assertThat(ConfigValueType.validateFormat("JSON", "[1,2,3]")).isNull();
    }

    @Test
    @DisplayName("花括号不匹配返回错误")
    void should_fail_unbalanced_braces() {
      assertThat(ConfigValueType.validateFormat("JSON", "{\"a\":1")).isNotNull();
    }

    @Test
    @DisplayName("方括号不匹配返回错误")
    void should_fail_unbalanced_brackets() {
      assertThat(ConfigValueType.validateFormat("JSON", "[1,2,3")).isNotNull();
    }

    @Test
    @DisplayName("非 JSON 起始字符返回错误")
    void should_fail_non_json_start() {
      assertThat(ConfigValueType.validateFormat("JSON", "abc")).isNotNull();
    }

    @Test
    @DisplayName("字符串内括号被忽略")
    void should_ignore_brackets_in_string() {
      assertThat(ConfigValueType.validateFormat("JSON", "{\"a\":\"}\"}")).isNull();
    }

    @Test
    @DisplayName("超长 JSON 返回错误")
    void should_fail_too_long_json() {
      String tooLong = "[" + "1".repeat(65536) + "]";

      assertThat(ConfigValueType.validateFormat("JSON", tooLong)).isNotNull();
    }
  }

  @Nested
  @DisplayName("validateFormat 空值跳过")
  class ValidateFormatSkip {

    @Test
    @DisplayName("valueType 为 null 跳过校验")
    void should_skip_for_null_type() {
      assertThat(ConfigValueType.validateFormat(null, "value")).isNull();
    }

    @Test
    @DisplayName("valueType 为空白跳过校验")
    void should_skip_for_blank_type() {
      assertThat(ConfigValueType.validateFormat("  ", "value")).isNull();
    }

    @Test
    @DisplayName("value 为 null 跳过校验")
    void should_skip_for_null_value() {
      assertThat(ConfigValueType.validateFormat("STRING", null)).isNull();
    }

    @Test
    @DisplayName("未知 valueType 返回错误")
    void should_fail_for_unknown_type() {
      assertThat(ConfigValueType.validateFormat("FAKE", "value")).isNotNull();
    }
  }

  @Nested
  @DisplayName("parseValue")
  class ParseValue {

    @Test
    @DisplayName("STRING 原样返回")
    void should_parse_string() {
      assertThat(ConfigValueType.parseValue("STRING", "hello")).isEqualTo("hello");
    }

    @Test
    @DisplayName("NUMBER 转为 Double")
    void should_parse_number() {
      assertThat(ConfigValueType.parseValue("NUMBER", "123.45")).isEqualTo(123.45d);
    }

    @Test
    @DisplayName("BOOLEAN 转为 Boolean")
    void should_parse_boolean() {
      assertThat(ConfigValueType.parseValue("BOOLEAN", "true")).isEqualTo(Boolean.TRUE);
      assertThat(ConfigValueType.parseValue("BOOLEAN", "FALSE")).isEqualTo(Boolean.FALSE);
    }

    @Test
    @DisplayName("JSON 原样返回")
    void should_parse_json() {
      assertThat(ConfigValueType.parseValue("JSON", "{\"a\":1}")).isEqualTo("{\"a\":1}");
    }

    @Test
    @DisplayName("type 为 null 时原样返回 value")
    void should_return_value_for_null_type() {
      assertThat(ConfigValueType.parseValue(null, "hello")).isEqualTo("hello");
    }

    @Test
    @DisplayName("type 为空白时原样返回 value")
    void should_return_value_for_blank_type() {
      assertThat(ConfigValueType.parseValue("  ", "hello")).isEqualTo("hello");
    }

    @Test
    @DisplayName("value 为 null 时返回 null")
    void should_return_null_for_null_value() {
      assertThat(ConfigValueType.parseValue("STRING", null)).isNull();
    }

    @Test
    @DisplayName("type 大小写无关")
    void should_be_case_insensitive() {
      assertThat(ConfigValueType.parseValue("number", "1")).isEqualTo(1.0d);
    }

    @Test
    @DisplayName("未知 type 抛出异常")
    void should_throw_for_unknown_type() {
      assertThatThrownBy(() -> ConfigValueType.parseValue("FAKE", "value"))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("枚举值总量")
  class Values {

    @Test
    @DisplayName("共 4 个类型")
    void should_have_four_types() {
      assertThat(ConfigValueType.values()).hasSize(4);
    }
  }
}
