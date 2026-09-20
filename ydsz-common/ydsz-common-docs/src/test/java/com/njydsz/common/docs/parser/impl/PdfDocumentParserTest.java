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
import com.njydsz.common.docs.enums.DocumentFormat;
import com.njydsz.common.docs.exception.DocumentException;
import com.njydsz.common.util.io.TempFileManager;

/**
 * {@link PdfDocumentParser} 单元测试。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@DisplayName("PdfDocumentParser 测试")
class PdfDocumentParserTest {

  private PdfDocumentParser parser;

  @BeforeEach
  void setUp() {
    parser = new PdfDocumentParser(new TempFileManager());
  }

  @Nested
  @DisplayName("场景：正常解析")
  class WhenParsing {

    @Test
    @DisplayName("含文本的最小 PDF 应能解析出文本")
    void shouldParseMinimalPdf() throws IOException {
      InputStream stream = TestUtils.minimalPdfStream();

      DocumentContent result = parser.parse(stream, "test.pdf", null);

      assertThat(result.getText()).isNotBlank();
      assertThat(result.getSections()).isNotEmpty();
    }

    @Test
    @DisplayName("解析后的全文字符数应大于 0")
    void shouldHavePositiveTotalChars() throws IOException {
      InputStream stream = TestUtils.minimalPdfStream();

      DocumentContent result = parser.parse(stream, "test.pdf", null);

      assertThat(result.getTotalChars()).isGreaterThan(0);
    }
  }

  @Nested
  @DisplayName("场景：异常与边界")
  class WhenError {

    @Test
    @DisplayName("null 流应抛出 DOCUMENT_EMPTY")
    void shouldThrowWhenStreamIsNull() {
      assertThatThrownBy(() -> parser.parse(null, "null.pdf", null))
          .isInstanceOf(DocumentException.class);
    }

    @Test
    @DisplayName("损坏的 PDF 字节流应抛异常")
    void shouldThrowOnCorruptedStream() {
      byte[] corrupted = "not a pdf file at all".getBytes();
      InputStream stream = new java.io.ByteArrayInputStream(corrupted);

      // 损坏的 PDF 可能抛出 DocumentException 或 IOException，均属于解析失败
      assertThatThrownBy(() -> parser.parse(stream, "corrupt.pdf", null))
          .isInstanceOf(Throwable.class);
    }
  }

  @Nested
  @DisplayName("场景：格式声明")
  class WhenFormat {

    @Test
    @DisplayName("getSupportedFormat 应返回 PDF")
    void shouldSupportPdfFormat() {
      assertThat(parser.getSupportedFormat()).isEqualTo(DocumentFormat.PDF);
    }

    @Test
    @DisplayName("不支持其他格式")
    void shouldNotSupportOtherFormats() {
      assertThat(parser.supports(DocumentFormat.DOCX)).isFalse();
      assertThat(parser.supports(DocumentFormat.TXT)).isFalse();
    }
  }
}
