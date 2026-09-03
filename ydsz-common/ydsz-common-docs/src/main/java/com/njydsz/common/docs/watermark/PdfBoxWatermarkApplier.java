package com.njydsz.common.docs.watermark;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import com.njydsz.common.docs.exception.DocumentException;
import com.njydsz.common.docs.exception.DocumentExceptionCode;

/**
 * 基于 Apache PDFBox 的 PDF 水印能力提供者。
 *
 * <p>能力提供者实现：在 common-docs 内部封装 PDFBox，为业务模块提供 PDF 水印叠加能力。
 * 本类对第三方 SDK 的引用属于 common-docs 能力封装（checkstyle 豁免清单覆盖），
 * 业务模块不得直接 import PDFBox，必须通过 {@link PdfWatermarkApplier} 使用。
 *
 * <p><b>装配控制：</b>运行时无 PDFBox 依赖时本 Bean 不装配，业务方可降级返回原文件，
 * 避免硬依赖。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
@ConditionalOnClass(name = "org.apache.pdfbox.Loader")
  // CHECKSTYLE.ON: RegexpSinglelineJava
public class PdfBoxWatermarkApplier implements PdfWatermarkApplier {

  /** 水印字体大小 */
  private static final float WATERMARK_FONT_SIZE = 20F;

  /** 水印颜色 RGB 分量（浅灰） */
  private static final int WATERMARK_COLOR_RGB = 200;

  /** 水印起始 X 偏移 */
  private static final float WATERMARK_X_OFFSET = 100F;

  /** 水印起始 Y 偏移系数（相对页面高度） */
  private static final float WATERMARK_Y_RATIO = 3F;

  /**
   * 为 PDF 文档叠加文字水印。
   *
   * <p>对每一页以 45 度旋转排版浅灰色文字水印，使用 {@code AppendMode.APPEND} 追加内容流，
   * 不修改原始页面内容。
   *
   * @param pdfBytes 原始 PDF 字节
   * @param watermarkText 水印文本
   * @return 叠加水印后的 PDF 字节
   * @throws DocumentException 水印叠加失败（文档损坏 / IO 异常）
   */
  @Override
  public byte[] applyWatermark(byte[] pdfBytes, String watermarkText) {
    if (pdfBytes == null || pdfBytes.length == 0) {
      return pdfBytes;
    }
    if (watermarkText == null || watermarkText.isEmpty()) {
      return pdfBytes;
    }
    try (PDDocument document = Loader.loadPDF(pdfBytes);
        ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
      for (PDPage page : document.getPages()) {
        addPdfWatermarkPage(document, page, watermarkText);
      }
      document.save(bos);
      return bos.toByteArray();
    } catch (IOException e) {
      log.error("[PdfBoxWatermarkApplier] PDF 水印叠加失败: err={}", e.getMessage(), e);
      throw new DocumentException(DocumentExceptionCode.PARSE_FAILED, "PDF 水印叠加失败", e);
    }
  }

  /**
   * 为 PDF 单页添加文字水印。
   *
   * @param document PDF 文档
   * @param page PDF 页面
   * @param watermarkText 水印文本
   * @throws IOException PDF 处理异常
   */
  private void addPdfWatermarkPage(PDDocument document, PDPage page, String watermarkText)
      throws IOException {
    try (PDPageContentStream contentStream =
        new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true)) {
      contentStream.setFont(
          new PDType1Font(Standard14Fonts.FontName.HELVETICA), WATERMARK_FONT_SIZE);
      float pageSize = page.getMediaBox().getHeight();
      contentStream.setNonStrokingColor(WATERMARK_COLOR_RGB, WATERMARK_COLOR_RGB, WATERMARK_COLOR_RGB);
      contentStream.beginText();
      contentStream.newLineAtOffset(WATERMARK_X_OFFSET, pageSize / WATERMARK_Y_RATIO);
      contentStream.showText(watermarkText);
      contentStream.endText();
    }
  }
}
