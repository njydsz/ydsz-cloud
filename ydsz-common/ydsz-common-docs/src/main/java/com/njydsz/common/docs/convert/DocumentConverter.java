package com.njydsz.common.docs.convert;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.docs.domain.DocumentContent;
import com.njydsz.common.docs.domain.ParseOptions;
import com.njydsz.common.docs.enums.DocumentFormat;
import com.njydsz.common.docs.exception.DocumentException;
import com.njydsz.common.docs.exception.DocumentExceptionCode;
import com.njydsz.common.docs.parser.registry.DocumentParserRegistry;

/**
 * 文档格式转换器
 *
 * <p>将 Office 文档（Word/Excel/PPT）和 PDF 转换为纯文本格式。
 *
 * <p>实现策略：优先委托已注册的 {@link DocumentParser} 获取结构化内容后提取文本， 避免重复实现 POI 解析逻辑；仅当解析器不可用时才使用内置轻量转换。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnClass(name = "org.apache.poi.xwpf.usermodel.XWPFDocument")
public class DocumentConverter {

  private final DocumentParserRegistry parserRegistry;

  public DocumentConverter(DocumentParserRegistry parserRegistry) {
    this.parserRegistry = parserRegistry;
  }

  /**
   * 转换文档格式
   *
   * @param inputStream 原始文档输入流
   * @param fileName 原始文件名
   * @param sourceFormat 源格式
   * @param targetFormat 目标格式
   * @return 转换后的文档字节流
   */
  public byte[] convert(
      InputStream inputStream,
      String fileName,
      DocumentFormat sourceFormat,
      DocumentFormat targetFormat) {
    if (targetFormat == DocumentFormat.TXT) {
      return convertToText(inputStream, fileName, sourceFormat);
    }
    throw new DocumentException(
        DocumentExceptionCode.CONVERT_FAILED, "不支持的目标格式: " + targetFormat + "（当前仅支持转换为 TXT）");
  }

  /**
   * 将文档转换为纯文本字节流。
   *
   * <p>优先使用已注册的解析器获取 {@link DocumentContent#getText()}， 避免重复实现 Office 文档解析逻辑。解析器不可用时降级到内置轻量实现。
   */
  private byte[] convertToText(
      InputStream inputStream, String fileName, DocumentFormat sourceFormat) {
    // 优先委托已注册解析器
    if (parserRegistry.isSupported(sourceFormat)) {
      try {
        var parser = parserRegistry.getParser(sourceFormat);
        DocumentContent content =
            parser.parse(inputStream, fileName, ParseOptions.builder().build());
        String text = content.getText();
        return text != null ? text.getBytes(StandardCharsets.UTF_8) : new byte[0];
      } catch (Exception e) {
        log.warn("[DocumentConverter] 委托解析器失败，降级到内置实现: {}", e.getMessage());
      }
    }

    // 降级：内置轻量实现（仅 Excel 需要特殊处理，Word/PPT/PDF 已有解析器）
    if (sourceFormat == DocumentFormat.XLSX || sourceFormat == DocumentFormat.XLS) {
      return convertExcelToTextFallback(inputStream, fileName);
    }

    throw new DocumentException(DocumentExceptionCode.CONVERT_FAILED, "不支持的源格式: " + sourceFormat);
  }

  /**
   * Excel → 纯文本降级实现（解析器不可用时）。
   *
   * <p>此方法仅在解析器未注册时执行，作为兜底保障。 正常路径应委托 {@link
   * com.njydsz.common.docs.parser.impl.ExcelDocumentParser}。
   */
  private byte[] convertExcelToTextFallback(InputStream inputStream, String fileName) {
    try (ByteArrayOutputStream output = new ByteArrayOutputStream();
        Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {

      var workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(inputStream);
      for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
        var sheet = workbook.getSheetAt(i);
        writer.write("=== ");
        writer.write(sheet.getSheetName());
        writer.write(" ===\n");
        for (var row : sheet) {
          StringBuilder sb = new StringBuilder();
          for (int c = 0; c < row.getLastCellNum(); c++) {
            if (c > 0) {
              sb.append('\t');
            }
            Cell cell = row.getCell(c);
            if (cell != null) {
              sb.append(getCellValueAsString(cell));
            }
          }
          if (!sb.isEmpty()) {
            writer.write(sb.toString());
            writer.write('\n');
          }
        }
      }
      writer.flush();
      return output.toByteArray();
    } catch (IOException e) {
      log.error("[DocumentConverter] Excel 降级转换失败: {}", fileName, e);
      throw new DocumentException(DocumentExceptionCode.CONVERT_FAILED, e);
    }
  }

  /**
   * 将 POI 单元格值转换为字符串。
   *
   * <p>处理常见单元格类型（字符串、数字、布尔、公式）， 日期按 {@code yyyy-MM-dd HH:mm:ss} 格式输出。 数字为整数时去掉小数部分。
   *
   * @param cell POI 单元格对象，为 {@code null} 时返回空串
   * @return 单元格值的字符串表示；永不为 {@code null}
   */
  public static String getCellValueAsString(Cell cell) {
    if (cell == null) {
      return "";
    }
    CellType cellType = cell.getCellType();
    return switch (cellType) {
      case STRING -> cell.getStringCellValue().trim();
      case NUMERIC -> {
        if (DateUtil.isCellDateFormatted(cell)) {
          yield java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
              .format(cell.getLocalDateTimeCellValue());
        }
        double num = cell.getNumericCellValue();
        if (num == Math.floor(num)) {
          yield String.valueOf((long) num);
        }
        yield String.valueOf(num);
      }
      case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
      case FORMULA -> {
        try {
          yield cell.getStringCellValue();
        } catch (Exception e) {
          yield String.valueOf(cell.getNumericCellValue());
        }
      }
      default -> "";
    };
  }
}
