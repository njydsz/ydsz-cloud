package com.njydsz.common.docs;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * 测试辅助工具类
 *
 * <p>提供各类测试文档的工厂方法，避免测试用例中重复编写构造逻辑。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class TestUtils {

  private TestUtils() {
    // 工具类禁止实例化
  }

  /**
   * 根据字符串内容构造 UTF-8 输入流。
   *
   * @param content 文本内容
   * @return UTF-8 字节输入流
   */
  public static InputStream streamOf(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * 生成最小可解析的 PDF 字节流（含 1 页 + 1 行文本）。
   *
   * @return PDF 字节输入流
   * @throws IOException PDF 生成失败
   */
  public static InputStream minimalPdfStream() throws IOException {
    try (PDDocument doc = new PDDocument()) {
      PDPage page = new PDPage();
      doc.addPage(page);
      try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
        content.beginText();
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        content.newLineAtOffset(72, 700);
        content.showText("hello pdf content");
        content.endText();
      }
      return savePdfToInputStream(doc);
    }
  }

  /**
   * 生成最小可解析的 DOCX 字节流（含 1 段落 + 可选标题）。
   *
   * @param titleStyle 标题样式（Heading1 等），可为 null（纯段落）
   * @param texts 段落文本数组
   * @return DOCX 字节输入流
   * @throws IOException DOCX 生成失败
   */
  public static InputStream minimalDocxStream(String titleStyle, String... texts)
      throws IOException {
    try (XWPFDocument doc = new XWPFDocument()) {
      for (String text : texts) {
        XWPFParagraph para = doc.createParagraph();
        if (titleStyle != null) {
          para.setStyle(titleStyle);
        }
        para.createRun().setText(text);
      }
      return savePoiToInputStream(doc);
    }
  }

  /**
   * 生成最小可解析的 XLSX 字节流（含 1 Sheet + 1 行数据）。
   *
   * @return XLSX 字节输入流
   * @throws IOException XLSX 生成失败
   */
  public static InputStream minimalXlsxStream() throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      XSSFSheet sheet = wb.createSheet("Sheet1");
      XSSFRow row = sheet.createRow(0);
      row.createCell(0).setCellValue("header1");
      row.createCell(1).setCellValue("value1");
      XSSFRow row2 = sheet.createRow(1);
      row2.createCell(0).setCellValue("row2col1");
      row2.createCell(1).setCellValue("100");
      return savePoiToInputStream(wb);
    }
  }

  private static InputStream savePoiToInputStream(
      org.apache.poi.POIXMLDocument doc) throws IOException {
    Path temp = Files.createTempFile("ydsz-docs-test-", ".tmp");
    try (var out = Files.newOutputStream(temp)) {
      doc.write(out);
    }
    byte[] bytes = Files.readAllBytes(temp);
    Files.deleteIfExists(temp);
    return new ByteArrayInputStream(bytes);
  }

  private static InputStream savePdfToInputStream(PDDocument doc) throws IOException {
    Path temp = Files.createTempFile("ydsz-docs-test-", ".tmp");
    doc.save(temp.toFile());
    byte[] bytes = Files.readAllBytes(temp);
    Files.deleteIfExists(temp);
    return new ByteArrayInputStream(bytes);
  }
}
