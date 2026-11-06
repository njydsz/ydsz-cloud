package com.njydsz.agent.domain.document;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DocumentFormat} 单元测试。
 *
 * <p>覆盖：扩展名解析（含去点、大小写）、未知/null 兜底、扩展名与 MIME 字段。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("DocumentFormat 文档格式枚举")
class DocumentFormatTest {

  @Nested
  @DisplayName("扩展名解析")
  class FromExtensionTest {

    @ParameterizedTest(name = "fromExtension(\"{0}\") 应返回 {1}")
    @CsvSource({
      "md, MARKDOWN",
      "docx, DOCX",
      "pdf, PDF",
      "html, HTML",
      "txt, TXT",
      "MD, MARKDOWN",
      ".pdf, PDF",
      ".md, MARKDOWN",
    })
    @DisplayName("扩展名解析应成功（含去点与大小写）")
    void shouldParseExtension(String ext, DocumentFormat expected) {
      assertThat(DocumentFormat.fromExtension(ext)).isEqualTo(expected);
    }

    @Test
    @DisplayName("null 应返回 null")
    void shouldReturnNullForNull() {
      assertThat(DocumentFormat.fromExtension(null)).isNull();
    }

    @ParameterizedTest(name = "fromExtension(\"{0}\") 应返回 null")
    @ValueSource(strings = {"xlsx", "pptx", "csv", ""})
    @DisplayName("未知扩展名应返回 null")
    void shouldReturnNullForUnknown(String ext) {
      assertThat(DocumentFormat.fromExtension(ext)).isNull();
    }
  }

  @Nested
  @DisplayName("元数据")
  class MetaTest {

    @Test
    @DisplayName("扩展名应为小写且唯一")
    void shouldHaveLowercaseUniqueExtensions() {
      long distinct = Arrays.stream(DocumentFormat.values())
          .map(DocumentFormat::getExtension)
          .distinct()
          .count();
      assertThat(distinct).isEqualTo(DocumentFormat.values().length);
      for (DocumentFormat format : DocumentFormat.values()) {
        assertThat(format.getExtension()).isEqualTo(format.getExtension().toLowerCase());
      }
    }

    @Test
    @DisplayName("MIME 类型应非空")
    void shouldHaveMimeType() {
      for (DocumentFormat format : DocumentFormat.values()) {
        assertThat(format.getMimeType()).isNotBlank();
      }
    }
  }
}
