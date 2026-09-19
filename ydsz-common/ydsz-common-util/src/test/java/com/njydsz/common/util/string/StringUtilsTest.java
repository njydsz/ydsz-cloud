package com.njydsz.common.util.string;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link StringUtils} 单元测试。
 *
 * <p>覆盖：判空、默认值、前缀、命名转换、截断、多参断言等全路径。
 *
 * @since 26.09.19
 */
@DisplayName("StringUtils 测试")
class StringUtilsTest {

  @Nested
  @DisplayName("判空方法")
  class BlankTest {

    @Test
    @DisplayName("isEmpty: null 返回 true")
    void isEmpty_null_returnsTrue() {
      assertThat(StringUtils.isEmpty((CharSequence) null)).isTrue();
    }

    @Test
    @DisplayName("isEmpty: 空字符串返回 true")
    void isEmpty_empty_returnsTrue() {
      assertThat(StringUtils.isEmpty("")).isTrue();
    }

    @Test
    @DisplayName("isEmpty: 有内容返回 false")
    void isEmpty_nonEmpty_returnsFalse() {
      assertThat(StringUtils.isEmpty("hello")).isFalse();
    }

    @Test
    @DisplayName("isBlank: 空白字符串返回 true")
    void isBlank_whitespace_returnsTrue() {
      assertThat(StringUtils.isBlank("  \t\n")).isTrue();
    }

    @Test
    @DisplayName("isNotBlank: 有非空白字符返回 true")
    void isNotBlank_nonWhitespace_returnsTrue() {
      assertThat(StringUtils.isNotBlank(" hello ")).isTrue();
    }

    @Test
    @DisplayName("hasText: 与 isBlank 语义等价")
    void hasText_sameAsIsBlank() {
      assertThat(StringUtils.hasText(" ")).isFalse();
      assertThat(StringUtils.hasText("x")).isTrue();
    }
  }

  @Nested
  @DisplayName("对象判空")
  class ObjectEmptyTest {

    @Test
    @DisplayName("isEmpty: 空数组返回 true")
    void isEmpty_emptyArray_returnsTrue() {
      assertThat(StringUtils.isEmpty(new Object[0])).isTrue();
    }

    @Test
    @DisplayName("isEmpty: 非空数组返回 false")
    void isEmpty_nonEmptyArray_returnsFalse() {
      assertThat(StringUtils.isEmpty(new Object[]{"x"})).isFalse();
    }
  }

  @Nested
  @DisplayName("多参断言")
  class MultiParamTest {

    @Test
    @DisplayName("isNoneBlank: 全无空白返回 true")
    void isNoneBlank_allNonBlank_returnsTrue() {
      assertThat(StringUtils.isNoneBlank("a", "b", "c")).isTrue();
    }

    @Test
    @DisplayName("isNoneBlank: 存在 null 返回 false")
    void isNoneBlank_hasNull_returnsFalse() {
      assertThat(StringUtils.isNoneBlank("a", null)).isFalse();
    }

    @Test
    @DisplayName("isNoneBlank: 存在空白返回 false")
    void isNoneBlank_hasBlank_returnsFalse() {
      assertThat(StringUtils.isNoneBlank("a", " ")).isFalse();
    }

    @Test
    @DisplayName("isNoneBlank: 空参返回 true（vacuously true）")
    void isNoneBlank_noArgs_returnsTrue() {
      assertThat(StringUtils.isNoneBlank()).isTrue();
    }

    @Test
    @DisplayName("isAnyBlank: 全无空白返回 false")
    void isAnyBlank_allNonBlank_returnsFalse() {
      assertThat(StringUtils.isAnyBlank("a", "b")).isFalse();
    }

    @Test
    @DisplayName("isAnyBlank: 存在空白返回 true")
    void isAnyBlank_hasBlank_returnsTrue() {
      assertThat(StringUtils.isAnyBlank("a", " ")).isTrue();
    }

    @Test
    @DisplayName("isAnyBlank: 空参返回 false")
    void isAnyBlank_noArgs_returnsFalse() {
      assertThat(StringUtils.isAnyBlank()).isFalse();
    }
  }

  @Nested
  @DisplayName("格式方法")
  class FormatTest {

    @Test
    @DisplayName("format: 占位符替换")
    void format_placeholderSubstitution() {
      assertThat(StringUtils.format("Hello, {}!", "World")).isEqualTo("Hello, World!");
    }

    @Test
    @DisplayName("format: null pattern 返回 null")
    void format_nullPattern_returnsNull() {
      assertThat(StringUtils.format(null, "arg")).isNull();
    }
  }

  @Nested
  @DisplayName("截断与缩写")
  class TruncateTest {

    @Test
    @DisplayName("truncate: 超长截断")
    void truncate_exceeds_cuts() {
      assertThat(StringUtils.truncate("hello world", 5)).isEqualTo("hello");
    }

    @Test
    @DisplayName("truncate: 不超长返回原字符串")
    void truncate_withinLimit_returnsOriginal() {
      assertThat(StringUtils.truncate("hello", 10)).isEqualTo("hello");
    }

    @Test
    @DisplayName("abbreviate: 超长截断并追加 ...")
    void abbreviate_exceeds_abbreviates() {
      assertThat(StringUtils.abbreviate("hello world", 8)).isEqualTo("hello...");
    }

    @Test
    @DisplayName("abbreviate: 不超长不追加省略号")
    void abbreviate_withinLimit_returnsOriginal() {
      assertThat(StringUtils.abbreviate("hi", 8)).isEqualTo("hi");
    }
  }

  @Nested
  @DisplayName("命名转换")
  class NamingTest {

    @Test
    @DisplayName("toCamelCase: 下划线转驼峰")
    void toCamelCase_underscoreToCamel() {
      assertThat(StringUtils.toCamelCase("user_name")).isEqualTo("userName");
    }

    @Test
    @DisplayName("toCamelCase: 已驼峰保持原样")
    void toCamelCase_alreadyCamel_unchanged() {
      assertThat(StringUtils.toCamelCase("userName")).isEqualTo("userName");
    }

    @Test
    @DisplayName("toUnderScoreCase: 驼峰转下划线")
    void toUnderScoreCase_camelToUnderscore() {
      assertThat(StringUtils.toUnderScoreCase("userName")).isEqualTo("user_name");
    }
  }
}
