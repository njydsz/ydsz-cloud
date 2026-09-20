package com.njydsz.common.docs.parser.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.common.docs.domain.DocumentContent;
import com.njydsz.common.docs.domain.DocumentSection;
import com.njydsz.common.docs.domain.ParseOptions;
import com.njydsz.common.docs.enums.DocumentFormat;
import com.njydsz.common.docs.exception.DocumentException;

/**
 * {@link TxtDocumentParser} 单元测试。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@DisplayName("TxtDocumentParser 测试")
class TxtDocumentParserTest {

  private TxtDocumentParser parser;

  @BeforeEach
  void setUp() {
    parser = new TxtDocumentParser();
  }

  @Nested
  @DisplayName("场景：正常解析")
  class WhenParsing {

    @Test
    @DisplayName("多行文本应拆为多个段落分节")
    void shouldSplitMultiLineIntoSections() throws IOException {
      String content = "第一行\n第二行\n第三行";
      InputStream stream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

      DocumentContent result = parser.parse(stream, "test.txt", ParseOptions.builder().build());

      assertThat(result.getText()).contains("第一行", "第二行", "第三行");
      assertThat(result.getSections()).hasSize(3);
      assertThat(result.getSections().get(0).getType()).isEqualTo("paragraph");
      assertThat(result.getSections().get(0).getContent()).isEqualTo("第一行");
    }

    @Test
    @DisplayName("空行应被过滤不生成分节")
    void shouldSkipBlankLines() throws IOException {
      String content = "line1\n\n\nline2";
      InputStream stream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

      DocumentContent result = parser.parse(stream, "test.txt", ParseOptions.builder().build());

      assertThat(result.getSections()).hasSize(2);
    }

    @Test
    @DisplayName("单行无换行文本也能解析")
    void shouldParseSingleLine() throws IOException {
      String content = "only one line";
      InputStream stream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

      DocumentContent result = parser.parse(stream, "test.txt", ParseOptions.builder().build());

      assertThat(result.getText()).isEqualTo("only one line");
      assertThat(result.getSections()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("场景：边界条件")
  class WhenBoundary {

    @Test
    @DisplayName("全部为空行时 sections 为空、text 为空")
    void shouldHandleAllBlankLines() throws IOException {
      String content = "\n\n\n";
      InputStream stream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

      DocumentContent result = parser.parse(stream, "blank.txt", ParseOptions.builder().build());

      assertThat(result.getSections()).isEmpty();
      assertThat(result.getText()).isEmpty();
    }

    @Test
    @DisplayName("空内容时 sections 为空、text 为空字符串")
    void shouldHandleEmptyContent() throws IOException {
      InputStream stream = new ByteArrayInputStream(new byte[0]);

      DocumentContent result =
          parser.parse(stream, "empty.txt", ParseOptions.builder().build());

      assertThat(result.getSections()).isEmpty();
      assertThat(result.getText()).isEmpty();
    }
  }

  @Nested
  @DisplayName("场景：异常分支")
  class WhenError {

    @Test
    @DisplayName("传入 null 流应抛出 DOCUMENT_EMPTY")
    void shouldThrowWhenStreamIsNull() {
      assertThatThrownBy(
              () -> parser.parse(null, "null.txt", ParseOptions.builder().build()))
          .isInstanceOf(DocumentException.class);
    }

    @Test
    @DisplayName("传入 null options 时仍能正常解析（按 UTF-8 处理）")
    void shouldParseWithNullOptions() throws IOException {
      String content = "hello world";
      InputStream stream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

      DocumentContent result = parser.parse(stream, "test.txt", null);

      assertThat(result.getText()).isEqualTo("hello world");
    }
  }

  @Nested
  @DisplayName("场景：格式声明")
  class WhenFormat {

    @Test
    @DisplayName("getSupportedFormat 应返回 TXT")
    void shouldSupportTxtFormat() {
      assertThat(parser.getSupportedFormat()).isEqualTo(DocumentFormat.TXT);
    }

    @Test
    @DisplayName("supports(TXT) 应返回 true")
    void shouldReturnTrueForTxt() {
      assertThat(parser.supports(DocumentFormat.TXT)).isTrue();
    }

    @Test
    @DisplayName("supports(CSV) 应返回 false")
    void shouldReturnFalseForCsv() {
      assertThat(parser.supports(DocumentFormat.CSV)).isFalse();
    }
  }
}
