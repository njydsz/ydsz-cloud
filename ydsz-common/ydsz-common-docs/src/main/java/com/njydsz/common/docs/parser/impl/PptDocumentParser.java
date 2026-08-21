package com.njydsz.common.docs.parser.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
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
 * PowerPoint 文档解析器（.pptx）
 *
 * <p>基于 Apache POI XSLF 解析 PowerPoint 文档，提取每页幻灯片的文本内容和表格。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
@ConditionalOnClass(name = "org.apache.poi.xslf.usermodel.XMLSlideShow")
  // CHECKSTYLE.ON: RegexpSinglelineJava
public class PptDocumentParser implements DocumentParser {

  /**
   * 遍历每页幻灯片的形状树，抽取文本框内容与表格。
   *
   * <p>以幻灯片序号（从 1 起）作为 {@code pageNumber}，使正文与表格都能回溯到具体页， 这是演示文稿最自然的定位单位。
   *
   * <p><b>抽取范围仅限当前页形状：</b>母版（Slide Master）、版式（Layout）、 备注页（Notes）以及组合形状（{@code
   * XSLFGroupShape}）内的嵌套子形状<b>均不递归处理</b>， 图表、SmartArt、图片中的文字同样不会被提取。 因此对于内容主要放在组合形状或备注中的
   * PPT，抽取结果会明显偏少。
   *
   * <p>形状的遍历顺序取自 OOXML 中的 z-order（叠放次序）而非视觉阅读顺序， 故同一页内标题与正文的先后可能与肉眼所见不符。
   * 表格行按实际行数遍历，缺失单元格补空串，保证每行列数对齐。
   *
   * @param inputStream PPTX 字节流，由调用方负责关闭；为 {@code null} 时视为空文档
   * @param fileName 原始文件名，仅写入元数据标题；不读取 PPT 内嵌的文档属性
   * @param options 解析选项，本实现未使用（表格恒抽取，不受 {@code extractTables} 控制），可传 {@code null}
   * @return 文档内容，含各页文本分节与表格；{@code totalPages} 为幻灯片总数
   * @throws DocumentException 入参流为 {@code null} 时错误码 {@code DOCUMENT_EMPTY}； 读取失败或非 OOXML 容器时错误码
   *     {@code PARSE_FAILED}
   */
  @Override
  public DocumentContent parse(InputStream inputStream, String fileName, ParseOptions options) {
    if (inputStream == null) {
      throw new DocumentException(DocumentExceptionCode.DOCUMENT_EMPTY);
    }

    try (XMLSlideShow ppt = new XMLSlideShow(inputStream)) {
      List<DocumentSection> sections = new ArrayList<>();
      List<DocumentTable> tables = new ArrayList<>();
      StringBuilder fullText = new StringBuilder();

      List<XSLFSlide> slides = ppt.getSlides();
      int slideCount = slides.size();

      for (int i = 0; i < slideCount; i++) {
        XSLFSlide slide = slides.get(i);
        int pageNum = i + 1;

        for (XSLFShape shape : slide.getShapes()) {
          // 文本形状
          if (shape instanceof XSLFTextShape textShape) {
            String text = textShape.getText();
            if (text != null && !text.isBlank()) {
              sections.add(
                  DocumentSection.builder()
                      .type("paragraph")
                      .content(text.trim())
                      .pageNumber(pageNum)
                      .build());
              fullText.append(text).append('\n');
            }
          }
          // 表格形状
          else if (shape instanceof XSLFTable tableShape) {
            List<List<String>> rows = new ArrayList<>();
            int numRows = tableShape.getNumberOfRows();
            int maxCols = 0;
            for (int r = 0; r < numRows; r++) {
              List<String> cells = new ArrayList<>();
              int numCols = tableShape.getNumberOfColumns();
              maxCols = Math.max(maxCols, numCols);
              for (int c = 0; c < numCols; c++) {
                XSLFTableCell cell = tableShape.getCell(r, c);
                cells.add(cell != null ? cell.getText() : "");
              }
              rows.add(cells);
            }
            if (!rows.isEmpty()) {
              tables.add(
                  DocumentTable.builder()
                      .pageNumber(pageNum)
                      .rowCount(rows.size())
                      .colCount(maxCols)
                      .rows(rows)
                      .build());
            }
          }
        }
      }

      String text = fullText.toString();
      return DocumentContent.builder()
          .text(text)
          .sections(sections)
          .tables(tables)
          .metadata(
              DocumentMetadata.builder()
                  .title(fileName)
                  .pageCount(slideCount)
                  .charCount(text.length())
                  .build())
          .totalChars(text.length())
          .totalPages(slideCount)
          .build();

    } catch (IOException e) {
      log.error("[PptDocumentParser] 解析失败: {}", fileName, e);
      throw new DocumentException(DocumentExceptionCode.PARSE_FAILED, e);
    }
  }

  /**
   * 声明本解析器在注册中心占据的格式槽位。
   *
   * <p>本类未覆写 {@code supports}，仅受理 OOXML 版 PPTX。 旧版二进制 PPT 需要 POI 的 HSLF 支持，当前无对应实现，
   * 会在注册中心路由阶段以"不支持的格式"失败。
   *
   * @return 恒为 {@link DocumentFormat#PPTX}
   */
  @Override
  public DocumentFormat getSupportedFormat() {
    return DocumentFormat.PPTX;
  }
}
