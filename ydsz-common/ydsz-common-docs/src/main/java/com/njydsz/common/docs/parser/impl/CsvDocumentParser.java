package com.njydsz.common.docs.parser.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.docs.domain.DocumentContent;
import com.njydsz.common.docs.domain.DocumentMetadata;
import com.njydsz.common.docs.domain.DocumentSection;
import com.njydsz.common.docs.domain.DocumentTable;
import com.njydsz.common.docs.domain.ParseOptions;
import com.njydsz.common.docs.enums.DocumentFormat;
import com.njydsz.common.docs.exception.DocumentException;
import com.njydsz.common.docs.exception.DocumentExceptionCode;
import com.njydsz.common.docs.parser.DocumentParser;

/**
 * CSV 文档解析器
 *
 * <p>解析 CSV 文件，将其转换为结构化表格和文本内容。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnClass(name = "org.apache.commons.csv.CSVParser")
public class CsvDocumentParser implements DocumentParser {

  /**
   * 将 CSV 逐行解析为表格模型与制表符分隔的纯文本双份表示。
   *
   * <p>采用 {@link CSVFormat#DEFAULT} 逗号分隔规则，<b>不做表头识别</b>—— 首行与数据行一视同仁地存入 {@code
   * rows}，是否含表头由调用方自行判断。 输出同时包含结构化 {@link DocumentTable}（供表格渲染）和纯文本 （供全文检索与 PII 扫描），文本形态下单元格以
   * {@code \t} 连接、行以 {@code \n} 分隔， 因此原始单元格内含制表符时会与分隔符混淆，重建表格请以 {@code tables} 为准。
   *
   * <p><b>列数口径不一致：</b>{@code colCount} 取首行列数，而非全表最大列数， 遇到锯齿形（每行列数不等）的 CSV 时该值会偏小，仅作参考。
   *
   * <p>整份 CSV 会全量驻留内存，超大文件请由上层先行按 {@code maxFileSizeMb} 拦截。
   *
   * @param inputStream CSV 字节流，由调用方负责关闭；为 {@code null} 时视为空文档
   * @param fileName 原始文件名，同时用作表格标题与元数据标题，仅用于展示与排障
   * @param options 解析选项，此处仅取 {@code charset} 字段；传 {@code null} 按 UTF-8 处理
   * @return 文档内容，含单个 table 类型分节、单张表格；页数恒为 1
   * @throws DocumentException 入参流为 {@code null} 时错误码 {@code DOCUMENT_EMPTY}； 读取或 CSV 语法解析失败时错误码
   *     {@code PARSE_FAILED}； {@code options.charset} 指定了 JVM 不支持的编码名时， 抛出 {@link
   *     java.nio.charset.UnsupportedCharsetException}
   */
  @Override
  public DocumentContent parse(InputStream inputStream, String fileName, ParseOptions options) {
    if (inputStream == null) {
      throw new DocumentException(DocumentExceptionCode.DOCUMENT_EMPTY);
    }

    Charset charset = resolveCharset(options);
    List<List<String>> rows = new ArrayList<>();
    StringBuilder fullText = new StringBuilder();

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, charset));
        CSVParser parser = CSVFormat.DEFAULT.parse(reader)) {

      int colCount = 0;
      for (CSVRecord record : parser) {
        List<String> row = new ArrayList<>();
        for (int i = 0; i < record.size(); i++) {
          row.add(record.get(i));
        }
        rows.add(row);
        colCount = Math.max(colCount, record.size());
      }
    } catch (IOException e) {
      log.error("[CsvDocumentParser] 解析失败: {}", fileName, e);
      throw new DocumentException(DocumentExceptionCode.PARSE_FAILED, e);
    }

    // 构建纯文本
    for (List<String> row : rows) {
      fullText.append(String.join("\t", row)).append('\n');
    }

    String text = fullText.toString();
    DocumentTable table =
        DocumentTable.builder()
            .caption(fileName)
            .pageNumber(1)
            .rowCount(rows.size())
            .colCount(rows.isEmpty() ? 0 : rows.get(0).size())
            .rows(rows)
            .build();

    return DocumentContent.builder()
        .text(text)
        .sections(
            List.of(DocumentSection.builder().type("table").content(text).pageNumber(1).build()))
        .tables(List.of(table))
        .metadata(DocumentMetadata.builder().title(fileName).charCount(text.length()).build())
        .totalChars(text.length())
        .totalPages(1)
        .build();
  }

  /**
   * 声明本解析器在注册中心占据的格式槽位。
   *
   * <p>本类未覆写 {@code supports}，故仅精确匹配 CSV 一种格式； 制表符分隔的 TSV 不在受理范围内。
   *
   * @return 恒为 {@link DocumentFormat#CSV}
   */
  @Override
  public DocumentFormat getSupportedFormat() {
    return DocumentFormat.CSV;
  }

  /**
   * 确定读取 CSV 所用字符集，未显式配置时回退 UTF-8。
   *
   * <p>CSV 不像 XML 那样自带编码声明，无法从内容推断，因此把选择权交给调用方； 国产办公软件导出的 GBK 文件必须显式指定 {@code charset}，否则中文将乱码。
   *
   * @param options 解析选项，可为 {@code null}
   * @return 解析出的字符集；未配置或配置为空白串时返回 {@link StandardCharsets#UTF_8}
   */
  private Charset resolveCharset(ParseOptions options) {
    if (options != null && options.getCharset() != null && !options.getCharset().isBlank()) {
      return Charset.forName(options.getCharset());
    }
    return StandardCharsets.UTF_8;
  }
}
