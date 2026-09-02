package com.njydsz.common.docs.ocr;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import com.njydsz.common.docs.domain.DocumentContent;
import com.njydsz.common.docs.domain.DocumentMetadata;
import com.njydsz.common.docs.domain.DocumentSection;
import com.njydsz.common.docs.exception.DocumentException;
import com.njydsz.common.docs.exception.DocumentExceptionCode;
import com.njydsz.common.util.io.TempFileManager;

/**
 * OCR 服务提供者
 *
 * <p>P2 功能：将扫描版 PDF 页面渲染为图片，为后续 OCR 识别做准备。
 *
 * <p>当前实现仅支持页面渲染，实际的 OCR 识别需要业务方对接外部 OCR 服务 （如阿里云 OCR / 百度 OCR / Tesseract），通过实现 {@link OcrEngine}
 * 接口集成。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
@ConditionalOnClass(name = "org.apache.pdfbox.rendering.PDFRenderer")
  // CHECKSTYLE.ON: RegexpSinglelineJava
public class OcrProvider {

  /** 默认渲染 DPI */
  private static final int DEFAULT_DPI = 150;

  /** 默认最大渲染页数 */
  private static final int DEFAULT_MAX_PAGES = 20;

  private final TempFileManager tempFileManager;

  public OcrProvider(TempFileManager tempFileManager) {
    this.tempFileManager = tempFileManager;
  }

  /**
   * 渲染 PDF 页面为图片字节列表
   *
   * @param inputStream PDF 输入流
   * @param fileName 文件名
   * @return 每页的 PNG 图片字节流列表
   */
  public List<byte[]> renderPages(InputStream inputStream, String fileName) {
    return renderPages(inputStream, fileName, DEFAULT_DPI, DEFAULT_MAX_PAGES);
  }

  /**
   * 渲染 PDF 页面为图片字节列表
   *
   * @param inputStream PDF 输入流
   * @param fileName 文件名
   * @param dpi 渲染 DPI
   * @param maxPages 最大渲染页数
   * @return 每页的 PNG 图片字节流列表
   */
  public List<byte[]> renderPages(InputStream inputStream, String fileName, int dpi, int maxPages) {
    Path tempFile = null;
    List<byte[]> images = new ArrayList<>(16);

    try {
      tempFile = tempFileManager.createAndWrite("ydsz-docs-ocr-", ".pdf", inputStream);

      try (PDDocument document = Loader.loadPDF(tempFile.toFile())) {
        PDFRenderer renderer = new PDFRenderer(document);
        int pageCount = Math.min(document.getNumberOfPages(), maxPages);

        for (int i = 0; i < pageCount; i++) {
          BufferedImage image = renderer.renderImageWithDPI(i, dpi);
          try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            images.add(baos.toByteArray());
          }
        }
      }

      log.info("[OcrProvider] 已渲染 {} 页图片: {}", images.size(), fileName);
      return images;

    } catch (IOException e) {
      log.error("[OcrProvider] 渲染失败: {}", fileName, e);
      throw new DocumentException(DocumentExceptionCode.PARSE_FAILED, e);
    } finally {
      tempFileManager.deleteTracked(tempFile);
    }
  }

  /**
   * 对扫描版 PDF 进行 OCR 识别
   *
   * <p>需要业务方提供 {@link OcrEngine} 实现，否则返回空结果。
   *
   * @param inputStream PDF 输入流
   * @param fileName 文件名
   * @param engine OCR 引擎实现（可为 null，为 null 时仅渲染图片不识别）
   * @return OCR 识别后的文档内容
   */
  public DocumentContent ocrScan(InputStream inputStream, String fileName, OcrEngine engine) {
    List<byte[]> pageImages = renderPages(inputStream, fileName);

    if (engine == null) {
      log.warn("[OcrProvider] 未提供 OcrEngine 实现，跳过 OCR 识别");
      return DocumentContent.builder()
          .text("")
          .sections(List.of())
          .metadata(DocumentMetadata.builder().title(fileName).pageCount(pageImages.size()).build())
          .totalChars(0)
          .totalPages(pageImages.size())
          .build();
    }

    List<DocumentSection> sections = new ArrayList<>(16);
    StringBuilder fullText = new StringBuilder();

    for (int i = 0; i < pageImages.size(); i++) {
      try {
        String pageText = engine.recognize(pageImages.get(i), i + 1);
        if (pageText != null && !pageText.isBlank()) {
          sections.add(
              DocumentSection.builder()
                  .type("paragraph")
                  .content(pageText.trim())
                  .pageNumber(i + 1)
                  .build());
          fullText.append(pageText);
        }
      } catch (Exception e) {
        log.error("[OcrProvider] 第 {} 页 OCR 识别失败", i + 1, e);
      }
    }

    String text = fullText.toString();
    return DocumentContent.builder()
        .text(text)
        .sections(sections)
        .metadata(
            DocumentMetadata.builder()
                .title(fileName)
                .pageCount(pageImages.size())
                .charCount(text.length())
                .build())
        .totalChars(text.length())
        .totalPages(pageImages.size())
        .build();
  }
}
