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
import com.njydsz.common.docs.domain.DocumentTable;
import com.njydsz.common.docs.enums.DocumentFormat;
import com.njydsz.common.docs.exception.DocumentException;

/**
 * {@link ExcelDocumentParser} 单元测试。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@DisplayName("ExcelDocumentParser 测试")
class ExcelDocumentParserTest {

  private ExcelDocumentParser parser;

  @BeforeEach
  void setUp() {
    parser = new ExcelDocumentParser();
  }

  @Nested
  @DisplayName("场景：正常解析")
  class WhenParsing {

    @Test
    @DisplayName("含表头和数据的 XLSX 应解析为单张表格")
    void shouldParseWorkbookWithData() throws IOException {
      InputStream stream = TestUtils.minimalXlsxStream();

      DocumentContent result = parser.parse(stream, "test.xlsx", null);

      assertThat(result.getTables()).hasSize(1);
      DocumentTable table = result.getTables().get(0);
      assertThat(table.getCaption()).isEqualTo("Sheet1");
      assertThat(table.getRowCount()).isEqualTo(2);
      assertThat(table.getRows().get(0).get(0)).isEqualTo("header1");
      assertThat(result.getText()).contains("header1");
      assertThat(result.getText()).contains("value1");
    }

    @Test
    @DisplayName("totalPages 应等于 Sheet 数量")
    void shouldSetTotalPagesFromSheetCount() throws IOException {
      InputStream stream = TestUtils.minimalXlsxStream();

      DocumentContent result = parser.parse(stream, "test.xlsx", null);

      assertThat(result.getTotalPages()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("场景：异常与边界")
  class WhenError {

    @Test
    @DisplayName("null 流应抛出 DOCUMENT_EMPTY")
    void shouldThrowWhenStreamIsNull() {
      assertThatThrownBy(() -> parser.parse(null, "null.xlsx", null))
          .isInstanceOf(DocumentException.class);
    }

    @Test
    @DisplayName("损坏的 XLSX 字节流应抛出 PARSE_FAILED")
    void shouldThrowOnCorruptedStream() {
      byte[] corrupted = "this is not xlsx".getBytes();
      InputStream stream = new java.io.ByteArrayInputStream(corrupted);

      assertThatThrownBy(() -> parser.parse(stream, "corrupt.xlsx", null))
          .isInstanceOf(DocumentException.class);
    }
  }

  @Nested
  @DisplayName("场景：格式声明")
  class WhenFormat {

    @Test
    @DisplayName("getSupportedFormat 应返回 XLSX")
    void shouldSupportXlsxFormat() {
      assertThat(parser.getSupportedFormat()).isEqualTo(DocumentFormat.XLSX);
    }

    @Test
    @DisplayName("supports(XLS) 应返回 true（通吃旧版 Excel）")
    void shouldAlsoSupportLegacyXls() {
      assertThat(parser.supports(DocumentFormat.XLS)).isTrue();
    }

    @Test
    @DisplayName("不支持 XLSM（含宏）")
    void shouldNotSupportMacroWorkbook() {
      assertThat(parser.supports(DocumentFormat.XLSM)).isFalse();
    }
  }
}
