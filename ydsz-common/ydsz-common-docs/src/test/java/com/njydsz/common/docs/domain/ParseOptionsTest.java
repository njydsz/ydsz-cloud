package com.njydsz.common.docs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.common.docs.enums.ParseMode;
import com.njydsz.common.docs.enums.ParseProfile;

/**
 * {@link ParseOptions} 语义化工厂方法测试。
 *
 * @author ydsz-team
 * @since 26.09.20
 */
@DisplayName("ParseOptions 工厂方法测试")
class ParseOptionsTest {

  @Nested
  @DisplayName("defaults()")
  class Defaults {

    @Test
    @DisplayName("应启用全部默认选项")
    void shouldEnableAllByDefault() {
      ParseOptions options = ParseOptions.defaults();

      assertThat(options.isExtractTables()).isTrue();
      assertThat(options.isExtractImages()).isTrue();
      assertThat(options.isExtractMetadata()).isTrue();
      assertThat(options.getMode()).isEqualTo(ParseMode.FULL);
      assertThat(options.getProfile()).isEqualTo(ParseProfile.STRUCTURED);
    }
  }

  @Nested
  @DisplayName("textOnly()")
  class TextOnly {

    @Test
    @DisplayName("应关闭表格 / 图片 / 元数据")
    void shouldDisableNonTextOptions() {
      ParseOptions options = ParseOptions.textOnly();

      assertThat(options.isExtractTables()).isFalse();
      assertThat(options.isExtractImages()).isFalse();
      assertThat(options.isExtractMetadata()).isFalse();
    }
  }

  @Nested
  @DisplayName("forRag()")
  class ForRag {

    @Test
    @DisplayName("应保留表格，关闭图片")
    void shouldKeepTablesDropImages() {
      ParseOptions options = ParseOptions.forRag(1000, 200);

      assertThat(options.isExtractTables()).isTrue();
      assertThat(options.isExtractImages()).isFalse();
      assertThat(options.isExtractMetadata()).isTrue();
    }
  }

  @Nested
  @DisplayName("fast()")
  class Fast {

    @Test
    @DisplayName("应使用 FAST 模式并关闭表格 / 图片")
    void shouldUseFastMode() {
      ParseOptions options = ParseOptions.fast();

      assertThat(options.getMode()).isEqualTo(ParseMode.FAST);
      assertThat(options.isExtractTables()).isFalse();
      assertThat(options.isExtractImages()).isFalse();
    }
  }
}
