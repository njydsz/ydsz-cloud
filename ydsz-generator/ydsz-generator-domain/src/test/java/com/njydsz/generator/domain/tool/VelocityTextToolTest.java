package com.njydsz.generator.domain.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link VelocityTextTool} 单元测试。
 *
 * <p>覆盖：camelCase/pascalCase/uncapitalize/capitalize/snakeCase/abbreviate/repeat/removePrefix/removeSuffix/wrapType。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("Velocity 文本工具")
class VelocityTextToolTest {

  private final VelocityTextTool tool = new VelocityTextTool();

  @Nested
  @DisplayName("camelCase")
  class CamelCase {

    @ParameterizedTest
    @CsvSource({
      "user_name, userName",
      "USER_NAME, userName",
      "userName, userName",
      "User_Name, userName",
      "a_b_c, aBC"
    })
    @DisplayName("下划线转驼峰")
    void converts(String input, String expected) {
      assertThat(tool.camelCase(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("null 返回 null")
    void nullReturnsNull() {
      assertThat(tool.camelCase(null)).isNull();
    }
  }

  @Nested
  @DisplayName("pascalCase")
  class PascalCase {

    @ParameterizedTest
    @CsvSource({
      "user_name, UserName",
      "USER_NAME, UserName",
      "userName, UserName",
      "a_b, AB"
    })
    @DisplayName("下划线转 PascalCase")
    void converts(String input, String expected) {
      assertThat(tool.pascalCase(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("null/空串原样返回")
    void nullAndEmpty(String input) {
      assertThat(tool.pascalCase(input)).isEqualTo(input);
    }
  }

  @Nested
  @DisplayName("uncapitalize")
  class Uncapitalize {

    @Test
    @DisplayName("首字母大写转小写")
    void converts() {
      assertThat(tool.uncapitalize("UserName")).isEqualTo("userName");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("null/空串原样返回")
    void nullAndEmpty(String input) {
      assertThat(tool.uncapitalize(input)).isEqualTo(input);
    }
  }

  @Nested
  @DisplayName("capitalize")
  class Capitalize {

    @Test
    @DisplayName("首字母小写转大写")
    void converts() {
      assertThat(tool.capitalize("userName")).isEqualTo("UserName");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("null/空串原样返回")
    void nullAndEmpty(String input) {
      assertThat(tool.capitalize(input)).isEqualTo(input);
    }
  }

  @Nested
  @DisplayName("snakeCase")
  class SnakeCase {

    @ParameterizedTest
    @CsvSource({
      "userName, user_name",
      "UserName, user_name",
      "ABCDef, a_bc_def",
      "already_snake, already_snake"
    })
    @DisplayName("驼峰转下划线")
    void converts(String input, String expected) {
      assertThat(tool.snakeCase(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("null 返回 null")
    void nullReturnsNull() {
      assertThat(tool.snakeCase(null)).isNull();
    }
  }

  @Nested
  @DisplayName("abbreviate")
  class Abbreviate {

    @ParameterizedTest
    @CsvSource({
      "user_name, un",
      "order_item, oi",
      "a_b_c, abc",
      "single, s"
    })
    @DisplayName("取每段首字母小写")
    void converts(String input, String expected) {
      assertThat(tool.abbreviate(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("null/空串原样返回")
    void nullAndEmpty(String input) {
      assertThat(tool.abbreviate(input)).isEqualTo(input);
    }
  }

  @Nested
  @DisplayName("repeat")
  class Repeat {

    @Test
    @DisplayName("正常重复")
    void repeats() {
      assertThat(tool.repeat("ab", 3)).isEqualTo("ababab");
    }

    @Test
    @DisplayName("times=0 返回空串")
    void zeroTimes() {
      assertThat(tool.repeat("ab", 0)).isEmpty();
    }

    @Test
    @DisplayName("times<0 返回空串")
    void negativeTimes() {
      assertThat(tool.repeat("ab", -1)).isEmpty();
    }

    @Test
    @DisplayName("input=null 返回空串")
    void nullInput() {
      assertThat(tool.repeat(null, 3)).isEmpty();
    }
  }

  @Nested
  @DisplayName("removePrefix")
  class RemovePrefix {

    @Test
    @DisplayName("移除匹配前缀")
    void removes() {
      assertThat(tool.removePrefix("/path/to/file", "/")).isEqualTo("path/to/file");
    }

    @Test
    @DisplayName("不匹配前缀原样返回")
    void noMatch() {
      assertThat(tool.removePrefix("hello", "xyz")).isEqualTo("hello");
    }

    @Test
    @DisplayName("input=null 返回 null")
    void nullInput() {
      assertThat(tool.removePrefix(null, "/")).isNull();
    }
  }

  @Nested
  @DisplayName("removeSuffix")
  class RemoveSuffix {

    @Test
    @DisplayName("移除匹配后缀")
    void removes() {
      assertThat(tool.removeSuffix("file.java", ".java")).isEqualTo("file");
    }

    @Test
    @DisplayName("不匹配后缀原样返回")
    void noMatch() {
      assertThat(tool.removeSuffix("file.java", ".xml")).isEqualTo("file.java");
    }

    @Test
    @DisplayName("input=null 返回 null")
    void nullInput() {
      assertThat(tool.removeSuffix(null, ".java")).isNull();
    }

    @Test
    @DisplayName("suffix=null 原样返回")
    void nullSuffix() {
      assertThat(tool.removeSuffix("file", null)).isEqualTo("file");
    }
  }

  @Nested
  @DisplayName("wrapType")
  class WrapType {

    @ParameterizedTest
    @CsvSource({
      "int, Integer",
      "long, Long",
      "short, Short",
      "byte, Byte",
      "float, Float",
      "double, Double",
      "char, Character",
      "boolean, Boolean",
      "void, Void"
    })
    @DisplayName("基本类型转包装类型")
    void wraps(String input, String expected) {
      assertThat(tool.wrapType(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("非基本类型原样返回")
    void nonPrimitive() {
      assertThat(tool.wrapType("String")).isEqualTo("String");
    }

    @Test
    @DisplayName("null 返回 null")
    void nullReturnsNull() {
      assertThat(tool.wrapType(null)).isNull();
    }
  }
}
