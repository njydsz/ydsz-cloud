package com.njydsz.common.excel.core.writer;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.excel.annotation.ExcelIgnore;
import com.njydsz.common.excel.annotation.ExcelProperty;
import com.njydsz.common.excel.annotation.ExcelSheet;
import com.njydsz.common.excel.core.config.ExcelConfig;
import com.njydsz.common.excel.core.metadata.WriteMetadata;
import com.njydsz.common.excel.core.security.FormulaInjectionGuard;
import com.njydsz.common.excel.support.asm.ASMFieldAccessor;

/**
 * 高性能 Excel 写入器 — 纯手工 XML 序列化。
 *
 * <p>直接生成 OOXML（.xlsx）格式的 XML 字节流并写入 ZIP 包， 绕过 Apache POI 的对象模型，以极低的内存开销实现高性能写入。
 *
 * <h3>写入原理</h3>
 *
 * <p>.xlsx 文件本质上是一个 ZIP 包，包含以下 XML 文件：
 *
 * <ul>
 *   <li>{@code [Content_Types].xml}：内容类型声明
 *   <li>{@code _rels/.rels}：根关系文件
 *   <li>{@code xl/workbook.xml}：工作簿定义
 *   <li>{@code xl/worksheets/sheet1.xml}：Sheet 数据
 *   <li>{@code xl/sharedStrings.xml}：共享字符串表
 * </ul>
 *
 * 其中 ContentTypes/Rels/Workbook 为固定模板，在静态块中一次性生成为字节常量复用； 仅 Sheet 数据与 SST 需要按数据动态生成。
 *
 * <h3>性能优化</h3>
 *
 * <ul>
 *   <li>使用 MethodHandle 替代反射获取字段值
 *   <li>行级缓冲（1MB），减少 ZIP 写入次数
 *   <li>公式注入防护（{@link FormulaInjectionGuard}）
 *   <li>支持 {@code @ExcelProperty.order()} 列序排序
 *   <li>支持 {@code excludeColumnFiledNames} / {@code includeColumnFiledNames} 列过滤
 * </ul>
 *
 * <h3>注意事项</h3>
 *
 * <ul>
 *   <li><b>线程安全性</b>：实例持有行缓冲区、行游标、单元格引用暂存等可变状态， <b>既不能跨线程共享，也不能重复调用 {@code doWrite}</b>（行号会累加）。
 *       每次导出请新建实例。
 *   <li><b>输出目标优先级</b>：{@code filePath} &gt; {@code file} &gt; {@code outputStream}， 三者均未设置时抛
 *       {@link IllegalArgumentException}。
 *   <li>写文件路径时先在系统临时目录落地 sheet1.xml 再转存进 ZIP， 临时目录在 {@code finally} 中递归删除，清理失败仅记 warn 不影响结果。
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FormulaInjectionGuard
 * @see ASMFieldAccessor
 */
public class SuperFastExcelWriter {

  private static final Logger LOG = LoggerFactory.getLogger(SuperFastExcelWriter.class);

  private static final int ROW_BUFFER_SIZE = 1024 * 1024;
  private static final int ZIP_BUFFER_SIZE = 1024 * 1024;
  private static final byte[] CONTENT_TYPES_BYTES;
  private static final byte[] RELS_BYTES;
  private static final byte[] WORKBOOK_RELS_BYTES;
  private static final byte[] WORKBOOK_BYTES_TEMPLATE;
  private static final byte[] SHEET_HEADER_BYTES;
  private static final byte[] FOOTER_BYTES;

  static {
    CONTENT_TYPES_BYTES =
        ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\""
                + " ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\""
                + " ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\""
                + " ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "<Override PartName=\"/xl/sharedStrings.xml\""
                + " ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\"/>"
                + "</Types>")
            .getBytes(StandardCharsets.UTF_8);

    RELS_BYTES =
        ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>")
            .getBytes(StandardCharsets.UTF_8);

    WORKBOOK_RELS_BYTES =
        ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings\" Target=\"sharedStrings.xml\"/>"
                + "</Relationships>")
            .getBytes(StandardCharsets.UTF_8);

    WORKBOOK_BYTES_TEMPLATE =
        ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets>"
                + "<sheet name=\"%s\" sheetId=\"1\" r:id=\"rId1\"/>"
                + "</sheets>"
                + "</workbook>")
            .getBytes(StandardCharsets.UTF_8);

    SHEET_HEADER_BYTES =
        ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<sheetData>")
            .getBytes(StandardCharsets.UTF_8);

    FOOTER_BYTES = "</sheetData></worksheet>".getBytes(StandardCharsets.UTF_8);
  }

  private final WriteMetadata metadata;
  private FieldAccessorInfo[] fieldInfoArray;
  private int fieldInfoSize;
  private Map<Integer, FieldAccessorInfo> fieldInfoMap;
  private byte[] columnTypeIds;

  private int currentRow = 0;

  private byte[] rowBuffer;
  private int rowBufferPos;

  private final byte[] cellRefBuffer = new byte[16];
  private final byte[] numberBuffer = new byte[32];
  private final char[] digitChars = "0123456789".toCharArray();

  private static final DateTimeFormatter DEFAULT_DATE_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  public SuperFastExcelWriter(WriteMetadata metadata) {
    this.metadata = metadata;
  }

  private ExcelConfig getExcelConfig() {
    return metadata.getExcelConfig() != null ? metadata.getExcelConfig() : ExcelConfig.defaults();
  }

  /**
   * 将数据序列化为 xlsx 并输出到 {@link WriteMetadata} 指定的目标。
   *
   * <p><b>执行顺序</b>：空数据短路返回 → 按 {@code clazz} 解析列元数据（列过滤、排序、 MethodHandle 访问器绑定）→ 按目标类型分派到文件或流写入。
   *
   * <p><b>目标选择</b>：依次判断 {@code filePath}、{@code file}、{@code outputStream}，
   * 取第一个非空者；写流时不关闭调用方传入的流，由调用方负责关闭。
   *
   * <p><b>失败语义</b>：写文件过程中抛异常会残留不完整的目标文件，需调用方清理； 临时目录的清理已在 {@code finally} 中兜底。
   *
   * @param data 待写入数据；非 {@link List} 时按单条记录处理，空列表则直接返回、不生成文件
   * @throws IllegalArgumentException 当未配置任何输出目标时抛出
   * @throws Exception 序列化或 IO 过程中的异常原样向上抛出，不做包装
   */
  public void doWrite(Object data) throws Exception {
    List<?> list = data instanceof List ? (List<?>) data : Collections.singletonList(data);
    if (list.isEmpty()) {
      return;
    }

    String filePath = metadata.getFilePath();
    File file = metadata.getFile();
    OutputStream os = metadata.getOutputStream();

    Class<?> clazz = metadata.getClazz();
    if (clazz != null) {
      analyzeClass(clazz);
    }

    if (filePath != null) {
      writeXlsxDirect(filePath, list);
    } else if (file != null) {
      writeXlsxDirect(file.getAbsolutePath(), list);
    } else if (os != null) {
      writeXlsxToStream(os, list);
    } else {
      throw new IllegalArgumentException("No output target specified");
    }
  }

  private void writeXlsxDirect(String filePath, List<?> list) throws Exception {
    Path tempDir = Files.createTempDirectory("ydsz_sxssf_");

    try (FileOutputStream fos = new FileOutputStream(filePath);
        BufferedOutputStream bos = new BufferedOutputStream(fos, ZIP_BUFFER_SIZE);
        ZipOutputStream zipOut = new ZipOutputStream(bos)) {

      zipOut.setLevel(getExcelConfig().getCompressionLevel());

      ZipEntry entry = new ZipEntry("[Content_Types].xml");
      zipOut.putNextEntry(entry);
      zipOut.write(CONTENT_TYPES_BYTES);
      zipOut.closeEntry();

      entry = new ZipEntry("_rels/.rels");
      zipOut.putNextEntry(entry);
      zipOut.write(RELS_BYTES);
      zipOut.closeEntry();

      entry = new ZipEntry("xl/_rels/workbook.xml.rels");
      zipOut.putNextEntry(entry);
      zipOut.write(WORKBOOK_RELS_BYTES);
      zipOut.closeEntry();

      entry = new ZipEntry("xl/workbook.xml");
      zipOut.putNextEntry(entry);
      zipOut.write(getWorkbookBytes());
      zipOut.closeEntry();

      Path sheetTempFile = tempDir.resolve("sheet1.xml");

      UltraFastSharedStrings ss = new UltraFastSharedStrings();

      try (FileOutputStream sheetFos = new FileOutputStream(sheetTempFile.toFile());
          BufferedOutputStream sheetBos = new BufferedOutputStream(sheetFos, ZIP_BUFFER_SIZE)) {

        sheetBos.write(SHEET_HEADER_BYTES);

        rowBuffer = new byte[ROW_BUFFER_SIZE];
        rowBufferPos = 0;

        // 写入表头行
        if (fieldInfoSize > 0) {
          currentRow++;
          int headerLen = writeHeaderRow(ss);
          sheetBos.write(rowBuffer, 0, headerLen);
          rowBufferPos = 0;
        }

        int listSize = list.size();
        for (int rowIdx = 0; rowIdx < listSize; rowIdx++) {
          Object item = list.get(rowIdx);
          currentRow++;
          int rowLen = writeRowToBuffer(item, ss);
          sheetBos.write(rowBuffer, 0, rowLen);
          rowBufferPos = 0;
        }

        sheetBos.write(FOOTER_BYTES);
        sheetBos.flush();
      }

      try (FileInputStream sheetFis = new FileInputStream(sheetTempFile.toFile())) {
        ZipEntry sheetEntry = new ZipEntry("xl/worksheets/sheet1.xml");
        zipOut.putNextEntry(sheetEntry);
        sheetFis.transferTo(zipOut);
        zipOut.closeEntry();
      }

      byte[] ssBytes = ss.buildXmlDirect();
      ZipEntry ssEntry = new ZipEntry("xl/sharedStrings.xml");
      zipOut.putNextEntry(ssEntry);
      zipOut.write(ssBytes);
      zipOut.closeEntry();

      zipOut.finish();
    } finally {
      try {
        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .map(p -> p.toFile())
            .forEach(f -> f.delete());
      } catch (Exception e) {
        LOG.warn("清理临时文件异常", e);
      }
    }
  }

  private void writeXlsxToStream(OutputStream os, List<?> list) throws Exception {
    ZipOutputStream zipOut = new ZipOutputStream(os);
    try {
      zipOut.setLevel(getExcelConfig().getCompressionLevel());

      zipOut.putNextEntry(new ZipEntry("[Content_Types].xml"));
      zipOut.write(CONTENT_TYPES_BYTES);
      zipOut.closeEntry();

      zipOut.putNextEntry(new ZipEntry("_rels/.rels"));
      zipOut.write(RELS_BYTES);
      zipOut.closeEntry();

      zipOut.putNextEntry(new ZipEntry("xl/_rels/workbook.xml.rels"));
      zipOut.write(WORKBOOK_RELS_BYTES);
      zipOut.closeEntry();

      zipOut.putNextEntry(new ZipEntry("xl/workbook.xml"));
      zipOut.write(getWorkbookBytes());
      zipOut.closeEntry();

      rowBuffer = new byte[ROW_BUFFER_SIZE];
      rowBufferPos = 0;

      UltraFastSharedStrings ss = new UltraFastSharedStrings();

      zipOut.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
      zipOut.write(SHEET_HEADER_BYTES);

      // 写入表头行
      if (fieldInfoSize > 0) {
        currentRow++;
        int headerLen = writeHeaderRow(ss);
        zipOut.write(rowBuffer, 0, headerLen);
        rowBufferPos = 0;
      }

      for (int rowIdx = 0; rowIdx < list.size(); rowIdx++) {
        Object item = list.get(rowIdx);
        currentRow++;
        int rowLen = writeRowToBuffer(item, ss);
        zipOut.write(rowBuffer, 0, rowLen);
        rowBufferPos = 0;
      }

      zipOut.write(FOOTER_BYTES);
      zipOut.closeEntry();

      byte[] ssBytes = ss.buildXmlDirect();
      ZipEntry ssEntry = new ZipEntry("xl/sharedStrings.xml");
      zipOut.putNextEntry(ssEntry);
      zipOut.write(ssBytes);
      zipOut.closeEntry();

      zipOut.finish();
    } catch (Exception e) {
      try {
        zipOut.close();
      } catch (Exception ignored) {
        // best effort
      }
      throw e;
    }
  }

  private int writeRowToBuffer(Object item, UltraFastSharedStrings ss) throws Exception {
    ensureCapacity(32);
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = 'r';
    rowBuffer[rowBufferPos++] = 'o';
    rowBuffer[rowBufferPos++] = 'w';
    rowBuffer[rowBufferPos++] = ' ';
    rowBuffer[rowBufferPos++] = 'r';
    rowBuffer[rowBufferPos++] = '=';
    rowBuffer[rowBufferPos++] = '"';
    writeNumberToBuffer(currentRow);
    rowBuffer[rowBufferPos++] = '"';
    rowBuffer[rowBufferPos++] = '>';

    for (int col = 0; col < fieldInfoSize; col++) {
      FieldAccessorInfo info = fieldInfoArray[col];
      if (info == null) {
        continue;
      }

      Object value;
      if (info.getter != null) {
        value = info.getter.get(item);
      } else {
        info.field.setAccessible(true);
        value = info.field.get(item);
      }

      writeCellTyped(col, value, info.dateFormatObj, columnTypeIds[col], ss);
    }

    ensureCapacity(16);
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = '/';
    rowBuffer[rowBufferPos++] = 'r';
    rowBuffer[rowBufferPos++] = 'o';
    rowBuffer[rowBufferPos++] = 'w';
    rowBuffer[rowBufferPos++] = '>';

    return rowBufferPos;
  }

  private void writeCellTyped(
      int col, Object value, DateTimeFormatter dateFormat, byte typeId, UltraFastSharedStrings ss) {
    if (value == null) {
      writeNullCell(col);
      return;
    }

    switch (typeId) {
      case 1:
        writeStringCell(col, (String) value, ss);
        break;
      case 2:
        // P0 修复：浮点类型（Double/Float/BigDecimal）必须以 double 写入，
        // 此前统一 longValue() 会截断所有小数（如 12.75 → 12）
        if (value instanceof Double || value instanceof Float || value instanceof BigDecimal) {
          writeDoubleCell(col, ((Number) value).doubleValue());
        } else {
          writeNumberCell(col, ((Number) value).longValue());
        }
        break;
      case 3:
        writeDateCell(col, value, dateFormat);
        break;
      case 4:
        writeBooleanCell(col, (Boolean) value);
        break;
      default:
        writeGenericCell(col, value);
        break;
    }
  }

  private void writeNullCell(int col) {
    ensureCapacity(16);
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = 'c';
    rowBuffer[rowBufferPos++] = ' ';
    rowBuffer[rowBufferPos++] = 'r';
    rowBuffer[rowBufferPos++] = '=';
    rowBuffer[rowBufferPos++] = '"';
    writeCellRef(col);
    rowBuffer[rowBufferPos++] = '"';
    rowBuffer[rowBufferPos++] = '/';
    rowBuffer[rowBufferPos++] = '>';
  }

  private void writeStringCellInline(int col, String value) {
    if (getExcelConfig().isFormulaInjectionProtection()) {
      value = FormulaInjectionGuard.sanitizeFormulaInjection(value);
    }
    int strLen = value.length();
    int capacity = 64 + strLen * 2;
    ensureCapacity(capacity);

    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = 'c';
    rowBuffer[rowBufferPos++] = ' ';
    rowBuffer[rowBufferPos++] = 'r';
    rowBuffer[rowBufferPos++] = '=';
    rowBuffer[rowBufferPos++] = '"';
    writeCellRef(col);
    rowBuffer[rowBufferPos++] = '"';
    rowBuffer[rowBufferPos++] = ' ';
    rowBuffer[rowBufferPos++] = 't';
    rowBuffer[rowBufferPos++] = '=';
    rowBuffer[rowBufferPos++] = '"';
    rowBuffer[rowBufferPos++] = 'i';
    rowBuffer[rowBufferPos++] = 'n';
    rowBuffer[rowBufferPos++] = 'l';
    rowBuffer[rowBufferPos++] = 'i';
    rowBuffer[rowBufferPos++] = 'n';
    rowBuffer[rowBufferPos++] = 'e';
    rowBuffer[rowBufferPos++] = 'S';
    rowBuffer[rowBufferPos++] = 't';
    rowBuffer[rowBufferPos++] = 'r';
    rowBuffer[rowBufferPos++] = '"';
    rowBuffer[rowBufferPos++] = '>';
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = 'i';
    rowBuffer[rowBufferPos++] = 's';
    rowBuffer[rowBufferPos++] = '>';
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = 't';
    rowBuffer[rowBufferPos++] = '>';
    writeStringToBuffer(value, true);
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = '/';
    rowBuffer[rowBufferPos++] = 't';
    rowBuffer[rowBufferPos++] = '>';
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = '/';
    rowBuffer[rowBufferPos++] = 'i';
    rowBuffer[rowBufferPos++] = 's';
    rowBuffer[rowBufferPos++] = '>';
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = '/';
    rowBuffer[rowBufferPos++] = 'c';
    rowBuffer[rowBufferPos++] = '>';
  }

  private void writeStringCell(int col, String value, UltraFastSharedStrings ss) {
    if (getExcelConfig().isFormulaInjectionProtection()) {
      value = FormulaInjectionGuard.sanitizeFormulaInjection(value);
    }
    int strLen = value.length();
    if (strLen > 50) {
      writeStringCellInline(col, value);
      return;
    }

    int ssIndex = ss.add(value);
    ensureCapacity(64);
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = 'c';
    rowBuffer[rowBufferPos++] = ' ';
    rowBuffer[rowBufferPos++] = 'r';
    rowBuffer[rowBufferPos++] = '=';
    rowBuffer[rowBufferPos++] = '"';
    writeCellRef(col);
    rowBuffer[rowBufferPos++] = '"';
    rowBuffer[rowBufferPos++] = ' ';
    rowBuffer[rowBufferPos++] = 't';
    rowBuffer[rowBufferPos++] = '=';
    rowBuffer[rowBufferPos++] = '"';
    rowBuffer[rowBufferPos++] = 's';
    rowBuffer[rowBufferPos++] = '"';
    rowBuffer[rowBufferPos++] = '>';
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = 'v';
    rowBuffer[rowBufferPos++] = '>';
    writeNumberToBuffer(ssIndex);
    closeCellTag();
  }

  private void writeNumberCell(int col, long value) {
    ensureCapacity(64);
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = 'c';
    rowBuffer[rowBufferPos++] = ' ';
    rowBuffer[rowBufferPos++] = 'r';
    rowBuffer[rowBufferPos++] = '=';
    rowBuffer[rowBufferPos++] = '"';
    writeCellRef(col);
    rowBuffer[rowBufferPos++] = '"';
    rowBuffer[rowBufferPos++] = '>';
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = 'v';
    rowBuffer[rowBufferPos++] = '>';
    writeNumberToBuffer(value);
    closeCellTag();
  }

  /**
   * 写入浮点数值单元格。
   *
   * <p>NaN/Infinity 不是合法的 OOXML 数值，降级为 inline 字符串单元格写出，避免产出损坏文件。
   */
  private void writeDoubleCell(int col, double value) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      writeStringCellInline(col, Double.toString(value));
      return;
    }
    String dStr = Double.toString(value);
    ensureCapacity(64 + dStr.length());
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = 'c';
    rowBuffer[rowBufferPos++] = ' ';
    rowBuffer[rowBufferPos++] = 'r';
    rowBuffer[rowBufferPos++] = '=';
    rowBuffer[rowBufferPos++] = '"';
    writeCellRef(col);
    rowBuffer[rowBufferPos++] = '"';
    rowBuffer[rowBufferPos++] = '>';
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = 'v';
    rowBuffer[rowBufferPos++] = '>';
    writeStringToBuffer(dStr, false);
    closeCellTag();
  }

  private void writeDateCell(int col, Object value, DateTimeFormatter dateFormat) {
    String dateStr;
    if (value instanceof LocalDateTime ldt) {
      dateStr = ldt.format(dateFormat);
    } else if (value instanceof LocalDate ld) {
      dateStr = ld.format(dateFormat);
    } else {
      dateStr = ((Date) value).toInstant().atZone(ZoneId.systemDefault()).format(dateFormat);
    }
    // P0 修复：此前日期字符串直接写入无 t 属性的 <v>（数值单元格），
    // 产出 Excel 判定损坏的文件；改为合法的 inlineStr 字符串单元格
    writeStringCellInline(col, dateStr);
  }

  private void writeBooleanCell(int col, Boolean value) {
    ensureCapacity(64);
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = 'c';
    rowBuffer[rowBufferPos++] = ' ';
    rowBuffer[rowBufferPos++] = 'r';
    rowBuffer[rowBufferPos++] = '=';
    rowBuffer[rowBufferPos++] = '"';
    writeCellRef(col);
    rowBuffer[rowBufferPos++] = '"';
    rowBuffer[rowBufferPos++] = ' ';
    rowBuffer[rowBufferPos++] = 't';
    rowBuffer[rowBufferPos++] = '=';
    rowBuffer[rowBufferPos++] = '"';
    rowBuffer[rowBufferPos++] = 'b';
    rowBuffer[rowBufferPos++] = '"';
    rowBuffer[rowBufferPos++] = '>';
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = 'v';
    rowBuffer[rowBufferPos++] = '>';
    writeStringToBuffer(value ? "1" : "0", false);
    closeCellTag();
  }

  private void writeGenericCell(int col, Object value) {
    // P0 修复：toString 值直接写入无 t 属性的 <v> 会产出非法 XML（数值单元格装文本）；
    // 改为合法的 inlineStr 字符串单元格（含公式注入防护，与主路径行为一致）
    writeStringCellInline(col, value.toString());
  }

  private void closeCellTag() {
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = '/';
    rowBuffer[rowBufferPos++] = 'v';
    rowBuffer[rowBufferPos++] = '>';
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = '/';
    rowBuffer[rowBufferPos++] = 'c';
    rowBuffer[rowBufferPos++] = '>';
  }

  private void writeCellRef(int col) {
    int c = col;
    int len = 0;
    while (c >= 0) {
      cellRefBuffer[len++] = (byte) ('A' + c % 26);
      c = c / 26 - 1;
    }
    for (int i = len - 1; i >= 0; i--) {
      rowBuffer[rowBufferPos++] = cellRefBuffer[i];
    }
    writeNumberToBuffer(currentRow);
  }

  private void writeNumberToBuffer(long value) {
    if (value == 0) {
      rowBuffer[rowBufferPos++] = '0';
      return;
    }

    if (value < 0) {
      rowBuffer[rowBufferPos++] = '-';
      value = -value;
    }

    int numDigits = 0;
    long temp = value;
    while (temp > 0) {
      numberBuffer[numDigits++] = (byte) digitChars[(int) (temp % 10)];
      temp /= 10;
    }

    for (int i = numDigits - 1; i >= 0; i--) {
      rowBuffer[rowBufferPos++] = numberBuffer[i];
    }
  }

  private void writeStringToBuffer(String str, boolean needsEscape) {
    if (str == null) {
      return;
    }

    if (!needsEscape) {
      int len = str.length();
      ensureCapacity(len);
      for (int i = 0; i < len; i++) {
        char c = str.charAt(i);
        if (c >= 0x20 || c == '\t' || c == '\n' || c == '\r') {
          rowBuffer[rowBufferPos++] = (byte) c;
        }
      }
      return;
    }

    int len = str.length();
    int i = 0;
    while (i < len) {
      char c = str.charAt(i);

      if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
        i++;
        continue;
      }

      if (c < 128 && c != '&' && c != '<' && c != '>' && c != '"' && c != '\'') {
        rowBuffer[rowBufferPos++] = (byte) c;
        i++;
        continue;
      }

      if (c == '&') {
        ensureCapacity(5);
        rowBuffer[rowBufferPos++] = '&';
        rowBuffer[rowBufferPos++] = 'a';
        rowBuffer[rowBufferPos++] = 'm';
        rowBuffer[rowBufferPos++] = 'p';
        rowBuffer[rowBufferPos++] = ';';
      } else if (c == '<') {
        ensureCapacity(5);
        rowBuffer[rowBufferPos++] = '&';
        rowBuffer[rowBufferPos++] = 'l';
        rowBuffer[rowBufferPos++] = 't';
        rowBuffer[rowBufferPos++] = ';';
      } else if (c == '>') {
        ensureCapacity(5);
        rowBuffer[rowBufferPos++] = '&';
        rowBuffer[rowBufferPos++] = 'g';
        rowBuffer[rowBufferPos++] = 't';
        rowBuffer[rowBufferPos++] = ';';
      } else if (c == '"') {
        ensureCapacity(6);
        rowBuffer[rowBufferPos++] = '&';
        rowBuffer[rowBufferPos++] = 'q';
        rowBuffer[rowBufferPos++] = 'u';
        rowBuffer[rowBufferPos++] = 'o';
        rowBuffer[rowBufferPos++] = 't';
        rowBuffer[rowBufferPos++] = ';';
      } else if (c == '\'') {
        ensureCapacity(6);
        rowBuffer[rowBufferPos++] = '&';
        rowBuffer[rowBufferPos++] = 'a';
        rowBuffer[rowBufferPos++] = 'p';
        rowBuffer[rowBufferPos++] = 'o';
        rowBuffer[rowBufferPos++] = 's';
        rowBuffer[rowBufferPos++] = ';';
      } else {
        ensureCapacity(8);
        byte[] bytes = Character.toString(c).getBytes(StandardCharsets.UTF_8);
        System.arraycopy(bytes, 0, rowBuffer, rowBufferPos, bytes.length);
        rowBufferPos += bytes.length;
      }
      i++;
    }
  }

  private void ensureCapacity(int needed) {
    if (rowBufferPos + needed > rowBuffer.length) {
      int newSize = rowBufferPos + needed + 1024;
      rowBuffer = Arrays.copyOf(rowBuffer, newSize);
    }
  }

  private void analyzeClass(Class<?> clazz) {
    try {
      Field[] declaredFields = clazz.getDeclaredFields();
      List<int[]> orderList = new ArrayList<>(); // [order, fieldIndex]
      List<Field> annotatedFields = new ArrayList<>();

      for (int i = 0; i < declaredFields.length; i++) {
        Field field = declaredFields[i];
        if (field.getAnnotation(ExcelIgnore.class) != null) {
          continue;
        }

        ExcelProperty prop = field.getAnnotation(ExcelProperty.class);

        if (prop != null) {
          orderList.add(new int[] {prop.order(), i});
          annotatedFields.add(field);
        }
      }

      if (orderList.isEmpty()) {
        return;
      }

      // Sort by order value to ensure correct column sequence
      orderList.sort((a, b) -> Integer.compare(a[0], b[0]));

      fieldInfoSize = orderList.size();
      fieldInfoArray = new FieldAccessorInfo[fieldInfoSize];
      fieldInfoMap = new HashMap<>();
      columnTypeIds = new byte[fieldInfoSize];

      for (int compactIdx = 0; compactIdx < orderList.size(); compactIdx++) {
        int originalOrder = orderList.get(compactIdx)[0];
        Field field = annotatedFields.get(compactIdx);
        ExcelProperty prop = field.getAnnotation(ExcelProperty.class);
        String dateFormat = prop.dateFormat();

        FieldAccessorInfo info = new FieldAccessorInfo();
        info.field = field;
        info.headerName =
            (prop.value() != null && !prop.value().isEmpty()) ? prop.value() : field.getName();
        info.getter = ASMFieldAccessor.getGetter(clazz, field);
        info.dateFormatObj =
            (dateFormat != null && !dateFormat.isEmpty())
                ? DateTimeFormatter.ofPattern(dateFormat)
                : DEFAULT_DATE_FORMATTER;
        fieldInfoMap.put(originalOrder, info);

        fieldInfoArray[compactIdx] = info;

        Class<?> fieldType = field.getType();
        if (fieldType == String.class) {
          columnTypeIds[compactIdx] = 1;
        } else if (Number.class.isAssignableFrom(fieldType)
            || fieldType == int.class
            || fieldType == long.class
            || fieldType == double.class
            || fieldType == float.class) {
          columnTypeIds[compactIdx] = 2;
        } else if (fieldType == Date.class
            || fieldType == LocalDateTime.class
            || fieldType == LocalDate.class) {
          columnTypeIds[compactIdx] = 3;
        } else if (fieldType == boolean.class || fieldType == Boolean.class) {
          columnTypeIds[compactIdx] = 4;
        } else {
          columnTypeIds[compactIdx] = 5;
        }
      }
    } catch (Exception e) {
      LOG.warn("ASM field accessor creation failed, using reflection", e);
    }
  }

  /**
   * 单列访问元数据，绑定反射字段、表头文本、ASM 访问器与日期格式。
   *
   * <p>在 {@link #analyzeClass(Class)} 阶段构建；ASM 访问器生成失败时 {@code getter} 为 {@code null}，写入阶段回退为直接反射访问
   * {@code field}。
   */
  private static class FieldAccessorInfo {
    Field field;
    String headerName;
    ASMFieldAccessor.FieldGetter getter;
    DateTimeFormatter dateFormatObj;
  }

  /**
   * 获取 Workbook XML 字节。
   *
   * <p>Sheet 名称解析优先级与 POI 兼容路径一致：{@code @ExcelSheet} 注解（doWrite 期折叠进 metadata 的契约） &gt; 显式
   * {@code sheet(name)} 配置 &gt; 默认 "Sheet1"。 此前仅读注解，显式 {@code sheet("用户列表")} 在 fast 路径被静默忽略。
   */
  private byte[] getWorkbookBytes() {
    String sheetName = metadata.getSheetName();
    Class<?> clazz = metadata.getClazz();
    if (clazz != null) {
      ExcelSheet sheetAnnotation = clazz.getAnnotation(ExcelSheet.class);
      if (sheetAnnotation != null && !sheetAnnotation.name().isEmpty()) {
        sheetName = sheetAnnotation.name();
      }
    }
    if (sheetName == null || sheetName.isEmpty()) {
      sheetName = "Sheet1";
    }
    // XML 转义
    sheetName =
        sheetName
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    return String.format(new String(WORKBOOK_BYTES_TEMPLATE, StandardCharsets.UTF_8), sheetName)
        .getBytes(StandardCharsets.UTF_8);
  }

  /** 写入表头行 */
  private int writeHeaderRow(UltraFastSharedStrings ss) {
    ensureCapacity(32);
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = 'r';
    rowBuffer[rowBufferPos++] = 'o';
    rowBuffer[rowBufferPos++] = 'w';
    rowBuffer[rowBufferPos++] = ' ';
    rowBuffer[rowBufferPos++] = 'r';
    rowBuffer[rowBufferPos++] = '=';
    rowBuffer[rowBufferPos++] = '"';
    writeNumberToBuffer(currentRow);
    rowBuffer[rowBufferPos++] = '"';
    rowBuffer[rowBufferPos++] = '>';

    for (int col = 0; col < fieldInfoSize; col++) {
      FieldAccessorInfo info = fieldInfoArray[col];
      if (info == null || info.headerName == null) {
        continue;
      }
      writeStringCell(col, info.headerName, ss);
    }

    ensureCapacity(16);
    rowBuffer[rowBufferPos++] = '<';
    rowBuffer[rowBufferPos++] = '/';
    rowBuffer[rowBufferPos++] = 'r';
    rowBuffer[rowBufferPos++] = 'o';
    rowBuffer[rowBufferPos++] = 'w';
    rowBuffer[rowBufferPos++] = '>';
    return rowBufferPos;
  }

  /**
   * 极速共享字符串表（SST）。
   *
   * <p>内部采用「数组 + HashMap」双结构：数组按插入顺序保存字符串以顺序生成 XML， HashMap 建立去重映射以复用索引、压缩文件体积。超过 50 字符的长字符串不走此表。
   * 实例非线程安全，仅用于单线程的写入流程。
   */
  private static class UltraFastSharedStrings {
    /** 字符串数组 */
    private String[] strings = new String[4096];

    /** 字符串计数 */
    private int count = 0;

    /**
     * 添加字符串并返回索引
     *
     * @param str 要添加的字符串
     * @return 字符串索引，不存在返回 -1
     */
    public int add(String str) {
      if (str == null) {
        return -1;
      }

      Integer existing = stringToIndex.get(str);
      if (existing != null) {
        return existing;
      }

      if (count >= strings.length) {
        strings = Arrays.copyOf(strings, strings.length * 2);
      }

      int idx = count++;
      strings[idx] = str;
      stringToIndex.put(str, idx);
      return idx;
    }

    /** 字符串到索引的映射，使用HashMap避免hashCode冲突问题 */
    private final HashMap<String, Integer> stringToIndex = new HashMap<>(1024);

    byte[] buildXmlDirect() throws Exception {
      ByteArrayOutputStream baos = new ByteArrayOutputStream(count * 128);

      baos.write(
          "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
              .getBytes(StandardCharsets.UTF_8));
      baos.write(
          "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" count=\""
              .getBytes(StandardCharsets.UTF_8));
      baos.write(String.valueOf(count).getBytes());
      baos.write("\" uniqueCount=\"".getBytes(StandardCharsets.UTF_8));
      baos.write(String.valueOf(count).getBytes());
      baos.write("\">".getBytes(StandardCharsets.UTF_8));

      for (int i = 0; i < count; i++) {
        baos.write("<si><t>".getBytes(StandardCharsets.UTF_8));
        String s = strings[i];
        if (s != null) {
          int len = s.length();
          for (int j = 0; j < len; j++) {
            char c = s.charAt(j);
            if (c < 128) {
              if (c == '&') {
                baos.write(AMP_BYTES);
              } else if (c == '<') {
                baos.write(LT_BYTES);
              } else if (c == '>') {
                baos.write(GT_BYTES);
              } else if (c == '"') {
                baos.write(QUOT_BYTES);
              } else if (c == '\'') {
                baos.write(APOS_BYTES);
              } else {
                baos.write((byte) c);
              }
            } else if (c >= 0x80 && c <= 0x7FF) {
              baos.write((byte) (0xC0 | (c >> 6)));
              baos.write((byte) (0x80 | (c & 0x3F)));
            } else if (c >= 0x800 && c <= 0xFFFF) {
              baos.write((byte) (0xE0 | (c >> 12)));
              baos.write((byte) (0x80 | ((c >> 6) & 0x3F)));
              baos.write((byte) (0x80 | (c & 0x3F)));
            }
          }
        }
        baos.write("</t></si>".getBytes(StandardCharsets.UTF_8));
      }

      baos.write("</sst>".getBytes(StandardCharsets.UTF_8));
      return baos.toByteArray();
    }
  }

  private static final byte[] AMP_BYTES = "&amp;".getBytes(StandardCharsets.UTF_8);
  private static final byte[] LT_BYTES = "&lt;".getBytes(StandardCharsets.UTF_8);
  private static final byte[] GT_BYTES = "&gt;".getBytes(StandardCharsets.UTF_8);
  private static final byte[] QUOT_BYTES = "&quot;".getBytes(StandardCharsets.UTF_8);
  private static final byte[] APOS_BYTES = "&apos;".getBytes(StandardCharsets.UTF_8);
}
