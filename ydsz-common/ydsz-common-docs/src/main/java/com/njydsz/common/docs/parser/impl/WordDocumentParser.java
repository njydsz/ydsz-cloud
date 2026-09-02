package com.njydsz.common.docs.parser.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

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
 * Word 文档解析器（.docx）
 *
 * <p>基于 Apache POI XWPF 解析 Word OOXML 文档，提取段落、标题层级和表格。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
@ConditionalOnClass(name = "org.apache.poi.xwpf.usermodel.XWPFDocument")
  // CHECKSTYLE.ON: RegexpSinglelineJava
public class WordDocumentParser implements DocumentParser {

  /**
   * 抽取 Word 正文段落与表格，并依据样式名还原标题层级。
   *
   * <p>标题层级来自段落<b>样式名</b>（如 {@code Heading1}）而非视觉字号， 因此仅靠手动放大字体、加粗模拟出的"标题"会被识别为普通段落。 这是可接受的取舍：样式名是
   * Word 中唯一可靠的结构化信号。
   *
   * <p><b>抽取范围限于主文档正文：</b>页眉、页脚、脚注、尾注、批注、文本框 以及表格单元格内的嵌套表格<b>均不处理</b>。 另外段落与表格分两轮遍历（先全部段落、后全部表格），
   * 因此表格在原文中的位置信息丢失，无法还原图文混排的原始次序。
   *
   * <p>Word 分页由渲染引擎动态决定，解析阶段无法获知，故 {@code pageNumber} 与 {@code totalPages} 统一填 1。图片抽取未实现，{@code
   * images} 恒为空列表。
   *
   * @param inputStream DOCX 字节流，由调用方负责关闭；为 {@code null} 时视为空文档
   * @param fileName 原始文件名，仅写入元数据标题；不读取 Word 内嵌文档属性
   * @param options 解析选项，仅读取 {@code extractTables} 开关；传 {@code null} 时视为开启
   * @return 文档内容，含 heading/paragraph 分节与表格；页数恒为 1
   * @throws DocumentException 入参流为 {@code null} 时错误码 {@code DOCUMENT_EMPTY}； 读取失败或非 OOXML 容器（如旧版
   *     .doc）时错误码 {@code PARSE_FAILED}
   */
  @Override
  public DocumentContent parse(InputStream inputStream, String fileName, ParseOptions options) {
    if (inputStream == null) {
      throw new DocumentException(DocumentExceptionCode.DOCUMENT_EMPTY);
    }

    try (XWPFDocument document = new XWPFDocument(inputStream)) {
      List<DocumentSection> sections = new ArrayList<>(16);
      List<DocumentTable> tables = new ArrayList<>(16);
      StringBuilder fullText = new StringBuilder();

      // 解析段落
      for (XWPFParagraph paragraph : document.getParagraphs()) {
        String text = paragraph.getText();
        if (text == null || text.isBlank()) {
          continue;
        }

        String style = paragraph.getStyle();
        int headingLevel = extractHeadingLevel(style);

        sections.add(
            DocumentSection.builder()
                .type(headingLevel > 0 ? "heading" : "paragraph")
                .headingLevel(headingLevel > 0 ? headingLevel : null)
                .content(text.trim())
                .pageNumber(1)
                .build());
        fullText.append(text).append('\n');
      }

      // 解析表格
      if (options == null || options.isExtractTables()) {
        for (XWPFTable table : document.getTables()) {
          List<List<String>> rows = new ArrayList<>(16);
          for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>(16);
            for (XWPFTableCell cell : row.getTableCells()) {
              cells.add(cell.getText() != null ? cell.getText().trim() : "");
            }
            rows.add(cells);
          }
          if (!rows.isEmpty()) {
            tables.add(
                DocumentTable.builder()
                    .pageNumber(1)
                    .rowCount(rows.size())
                    .colCount(rows.get(0).size())
                    .rows(rows)
                    .build());
          }
        }
      }

      String text = fullText.toString();
      return DocumentContent.builder()
          .text(text)
          .sections(sections)
          .tables(tables)
          .images(List.of())
          .metadata(DocumentMetadata.builder().title(fileName).charCount(text.length()).build())
          .totalChars(text.length())
          .totalPages(1)
          .build();

    } catch (IOException e) {
      log.error("[WordDocumentParser] 解析失败: {}", fileName, e);
      throw new DocumentException(DocumentExceptionCode.PARSE_FAILED, e);
    }
  }

  /**
   * 声明本解析器在注册中心占据的格式槽位。
   *
   * <p>本类未覆写 {@code supports}，仅受理 OOXML 版 DOCX。 旧版二进制 DOC 需要 POI 的 HWPF 支持，当前无对应实现。
   *
   * @return 恒为 {@link DocumentFormat#DOCX}
   */
  @Override
  public DocumentFormat getSupportedFormat() {
    return DocumentFormat.DOCX;
  }

  /**
   * 从段落样式名中解析出标题层级。
   *
   * <p>做法是剥离样式名中的所有非数字字符后取整。这种宽松解析是为了兼容 Word 不同语言版本与自定义样式的命名差异（如 {@code Heading1}、{@code heading
   * 2}）。
   *
   * <p><b>降级为 0（普通段落）的情形：</b>样式名为 {@code null}、 不以 Heading/heading 开头、数字部分为空或无法解析、层级超出 1~9 合法区间。
   * 全程不抛异常，保证单个异常样式不会中断整篇文档解析。
   *
   * @param style 段落样式名，可为 {@code null}
   * @return 标题层级 1~9；非标题或无法识别时返回 0
   */
  private int extractHeadingLevel(String style) {
    if (style == null) {
      return 0;
    }
    if (style.startsWith("Heading") || style.startsWith("heading")) {
      String numPart = style.replaceAll("[^0-9]", "");
      try {
        int level = Integer.parseInt(numPart);
        return (level >= 1 && level <= 9) ? level : 0;
      } catch (NumberFormatException e) {
        return 0;
      }
    }
    return 0;
  }
}
