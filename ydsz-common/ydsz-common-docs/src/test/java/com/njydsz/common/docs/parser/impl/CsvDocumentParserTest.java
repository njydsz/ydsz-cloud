package com.njydsz.common.docs.parser.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.common.docs.domain.DocumentContent;
import com.njydsz.common.docs.domain.DocumentTable;
import com.njydsz.common.docs.enums.DocumentFormat;
import com.njydsz.common.docs.exception.DocumentException;

/**
 * {@link CsvDocumentParser} 单元测试。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@DisplayName("CsvDocumentParser 测试")
class CsvDocumentParserTest {

  private CsvDocumentParser parser;

  @BeforeEach
  void setUp() {
    parser = new CsvDocumentParser();
  }

  @Nested
  @DisplayName("场景：正常 CSV 解析")
  class WhenParsing {

    @Test
    @DisplayName("标准多行 CSV 应产出完整 rows")
    void shouldParseMultiRowCsv() throws IOException {
      String csv = "name,age,city\nAlice,30,Beijing\nBob,25,Shanghai";
      InputStream stream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

      DocumentContent result = parser.parse(stream, "test.csv", null);

      assertThat(result.getTables()).hasSize(1);
      DocumentTable table = result.getTables().get(0);
      assertThat(table.getRowCount()).isEqualTo(3);
      assertThat(table.getRows().get(0).get(0)).isEqualTo("name");
      assertThat(table.getRows().get(1).get(0)).isEqualTo("Alice");
      assertThat(table.getRows().get(2).get(2)).isEqualTo("Shanghai");
    }

    @Test
    @DisplayName("单行 CSV 不报错")
    void shouldParseSingleRowCsv() throws IOException {
      String csv = "only,one,row";
      InputStream stream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

      DocumentContent result = parser.parse(stream, "single.csv", null);

      assertThat(result.getTables()).hasSize(1);
      assertThat(result.getTables().get(0).getRowCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("CSV 文本输出含制表符连接各行")
    void shouldJoinCellWithTabs() throws IOException {
      String csv = "a,b\nc,d";
      InputStream stream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

      DocumentContent result = parser.parse(stream, "test.csv", null);

      assertThat(result.getText()).contains("a\tb");
      assertThat(result.getText()).contains("c\td");
    }
  }

  @Nested
  @DisplayName("场景：边界条件")
  class WhenBoundary {

    @Test
    @DisplayName("不含换行的单个字段也能解析")
    void shouldParseSingleField() throws IOException {
      String csv = "single_value";
      InputStream stream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

      DocumentContent result = parser.parse(stream, "one.csv", null);

      assertThat(result.getTables()).hasSize(1);
      assertThat(result.getTables().get(0).getRowCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("空 CSV 文件（零字节）应返回空 table")
    void shouldHandleEmptyCsv() throws IOException {
      InputStream stream = new ByteArrayInputStream(new byte[0]);

      DocumentContent result = parser.parse(stream, "empty.csv", null);

      assertThat(result.getTables()).hasSize(1);
      assertThat(result.getTables().get(0).getRowCount()).isEqualTo(0);
    }
  }

  @Nested
  @DisplayName("场景：异常分支")
  class WhenError {

    @Test
    @DisplayName("null 流应抛出 DOCUMENT_EMPTY")
    void shouldThrowWhenStreamIsNull() {
      assertThatThrownBy(() -> parser.parse(null, "null.csv", null))
          .isInstanceOf(DocumentException.class);
    }
  }

  @Nested
  @DisplayName("场景：格式声明")
  class WhenFormat {

    @Test
    @DisplayName("getSupportedFormat 应返回 CSV")
    void shouldSupportCsvFormat() {
      assertThat(parser.getSupportedFormat()).isEqualTo(DocumentFormat.CSV);
    }

    @Test
    @DisplayName("仅支持 CSV（不含 TSV）")
    void shouldNotSupportOtherFormats() {
      assertThat(parser.supports(DocumentFormat.TXT)).isFalse();
      assertThat(parser.supports(DocumentFormat.XLSX)).isFalse();
    }
  }
}
