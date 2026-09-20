package com.njydsz.common.docs.parser.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.common.docs.TestUtils;
import com.njydsz.common.docs.domain.DocumentContent;
import com.njydsz.common.docs.domain.DocumentSection;
import com.njydsz.common.docs.enums.DocumentFormat;
import com.njydsz.common.docs.exception.DocumentException;

/**
 * {@link WordDocumentParser} 单元测试。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@DisplayName("WordDocumentParser 测试")
class WordDocumentParserTest {

  private WordDocumentParser parser;

  @BeforeEach
  void setUp() {
    parser = new WordDocumentParser();
  }

  @Nested
  @DisplayName("场景：段落解析")
  class WhenParsingParagraphs {

    @Test
    @DisplayName("纯段落应被解析为 paragraph 类型")
    void shouldParseParagraphs() throws IOException {
      InputStream stream = TestUtils.minimalDocxStream(null, "Hello World", "Second Para");

      DocumentContent result = parser.parse(stream, "test.docx", null);

      assertThat(result.getSections()).hasSize(2);
      assertThat(result.getSections().get(0).getType()).isEqualTo("paragraph");
      assertThat(result.getSections().get(0).getContent()).isEqualTo("Hello World");
      assertThat(result.getText()).contains("Hello World");
    }

    @Test
    @DisplayName("Heading1 样式段落应被识别为 heading + 层级 1")
    void shouldRecognizeHeading1() throws IOException {
      InputStream stream = TestUtils.minimalDocxStream("Heading1", "This Is A Heading");

      DocumentContent result = parser.parse(stream, "heading.docx", null);

      assertThat(result.getSections()).hasSize(1);
      DocumentSection section = result.getSections().get(0);
      assertThat(section.getType()).isEqualTo("heading");
      assertThat(section.getHeadingLevel()).isEqualTo(1);
      assertThat(section.getContent()).isEqualTo("This Is A Heading");
    }

    @Test
    @DisplayName("Heading2 样式段落应被识别为 heading + 层级 2")
    void shouldRecognizeHeading2() throws IOException {
      InputStream stream = TestUtils.minimalDocxStream("Heading2", "Sub Heading");

      DocumentContent result = parser.parse(stream, "heading2.docx", null);

      assertThat(result.getSections().get(0).getHeadingLevel()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("场景：元数据与完整性")
  class WhenMetadata {

    @Test
    @DisplayName("应正确填充 metadata 与 totalChars")
    void shouldFillMetadata() throws IOException {
      InputStream stream = TestUtils.minimalDocxStream(null, "hello metadata");

      DocumentContent result = parser.parse(stream, "meta.docx", null);

      assertThat(result.getMetadata()).isNotNull();
      assertThat(result.getMetadata().getTitle()).isEqualTo("meta.docx");
      assertThat(result.getTotalChars()).isGreaterThan(0);
    }
  }

  @Nested
  @DisplayName("场景：异常与边界")
  class WhenError {

    @Test
    @DisplayName("null 流应抛出 DOCUMENT_EMPTY")
    void shouldThrowWhenStreamIsNull() {
      assertThatThrownBy(() -> parser.parse(null, "null.docx", null))
          .isInstanceOf(DocumentException.class);
    }

    @Test
    @DisplayName("空 DOCX（无段落）应返回零 sections")
    void shouldHandleEmptyDocx() throws IOException {
      InputStream stream = TestUtils.minimalDocxStream((String) null);

      DocumentContent result = parser.parse(stream, "empty.docx", null);

      assertThat(result.getSections()).isEmpty();
    }
  }

  @Nested
  @DisplayName("场景：格式声明")
  class WhenFormat {

    @Test
    @DisplayName("getSupportedFormat 应返回 DOCX")
    void shouldSupportDocxFormat() {
      assertThat(parser.getSupportedFormat()).isEqualTo(DocumentFormat.DOCX);
    }

    @Test
    @DisplayName("不支持旧版 .doc 格式")
    void shouldNotSupportLegacyDoc() {
      assertThat(parser.supports(DocumentFormat.DOC)).isFalse();
    }
  }
}
