package com.njydsz.common.excel.core.writer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.njydsz.common.excel.annotation.ExcelProperty;
import com.njydsz.common.excel.support.asm.ASMFieldAccessor;

/**
 * 多 Sheet 快速写入器 — 一次生成包含多个 Sheet 的 xlsx 工作簿。
 *
 * <p>与单 Sheet 的 {@link SuperFastExcelWriter} 相比，本类额外处理多 Sheet 共享 SST、workbook.xml
 * 多 sheet 声明、rels 与 Content_Types 的多 Override。每 Sheet 可独立指定数据类型。
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * ExcelFacade.writeMultiSheet(outputStream)
 *     .sheet("用户", User.class, users)
 *     .sheet("部门", Department.class, departments)
 *     .finish();
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.10.01
 */
public class MultiSheetFastWriter {

  private static final byte[] SHEET_HEADER = (
      "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
          + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
          + "<sheetData>")
      .getBytes(StandardCharsets.UTF_8);
  private static final byte[] SHEET_FOOTER =
      "</sheetData></worksheet>".getBytes(StandardCharsets.UTF_8);
  private static final byte[] RELS = (
      "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
          + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
          + "<Relationship Id=\"rId1\" "
          + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" "
          + "Target=\"xl/workbook.xml\"/>"
          + "</Relationships>")
      .getBytes(StandardCharsets.UTF_8);
  private static final byte[] THEME = (
      "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
          + "<a:theme xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" name=\"Office Theme\">"
          + "<a:themeElements><a:clrScheme name=\"Office\"><a:dk1><a:sysClr val=\"windowText\" lastClr=\"000000\"/>"
          + "</a:dk1><a:lt1><a:sysClr val=\"window\" lastClr=\"FFFFFF\"/></a:lt1><a:dk2><a:srgbClr val=\"44546A\"/>"
          + "</a:dk2><a:lt2><a:srgbClr val=\"E7E6E6\"/></a:lt2><a:accent1><a:srgbClr val=\"4472C4\"/></a:accent1>"
          + "<a:accent2><a:srgbClr val=\"ED7D31\"/></a:accent2><a:accent3><a:srgbClr val=\"A5A5A5\"/></a:accent3>"
          + "<a:accent4><a:srgbClr val=\"FFC000\"/></a:accent4><a:accent5><a:srgbClr val=\"5B9BD5\"/></a:accent5>"
          + "<a:accent6><a:srgbClr val=\"70AD47\"/></a:accent6><a:hlink><a:srgbClr val=\"0563C1\"/></a:hlink>"
          + "<a:folHlink><a:srgbClr val=\"954F72\"/></a:folHlink></a:clrScheme><a:fontScheme name=\"Office\">"
          + "<a:majorFont><a:latin typeface=\"Calibri Light\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/>"
          + "</a:majorFont><a:minorFont><a:latin typeface=\"Calibri\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/>"
          + "</a:minorFont></a:fontScheme><a:fmtScheme name=\"Office\"><a:fillStyleLst>"
          + "<a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:solidFill><a:schemeClr val=\"phClr\"/>"
          + "</a:solidFill><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:fillStyleLst>"
          + "<a:lnStyleLst><a:ln/><a:ln/><a:ln/></a:lnStyleLst><a:effectStyleLst><a:effectStyle><a:effectLst/>"
          + "</a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/>"
          + "</a:effectStyle></a:effectStyleLst><a:bgFillStyleLst><a:solidFill><a:schemeClr val=\"phClr\"/>"
          + "</a:solidFill><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:solidFill><a:schemeClr val=\"phClr\"/>"
          + "</a:solidFill></a:bgFillStyleLst></a:fmtScheme></a:themeElements></a:theme>")
      .getBytes(StandardCharsets.UTF_8);
  private static final byte[] STYLES = (
      "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
          + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
          + "<numFmts count=\"0\"/><fonts count=\"1\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
          + "<fills count=\"1\"><fill><patternFill patternType=\"none\"/></fill></fills>"
          + "<borders count=\"1\"><border><left/><right/><top/><bottom/></border></borders>"
          + "<cellStyleXfs count=\"1\"><xf/></cellStyleXfs>"
          + "<cellXfs count=\"1\"><xf/></cellXfs>"
          + "</styleSheet>")
      .getBytes(StandardCharsets.UTF_8);

  private final OutputStream out;
  private final List<SheetData<?>> sheets = new ArrayList<>(4);

  public MultiSheetFastWriter(OutputStream out) {
    this.out = out;
  }

  /**
   * 添加一个 Sheet。
   *
   * @param sheetName Sheet 名称
   * @param clazz 数据类型
   * @param data 数据列表
   * @param <T> 数据类型
   * @return 当前实例，支持链式调用
   */
  public <T> MultiSheetFastWriter sheet(String sheetName, Class<T> clazz, List<T> data) {
    sheets.add(SheetData.of(sheetName, clazz, data != null ? data : new ArrayList<>(0)));
    return this;
  }

  /**
   * 执行写入。
   *
   * @throws IOException 写入异常
   */
  public void finish() throws IOException {
    if (sheets.isEmpty()) {
      throw new IllegalStateException("至少需要添加一个 Sheet");
    }
    writeAllSheets();
  }

  private void writeAllSheets() throws IOException {
    int sheetCount = sheets.size();
    SharedStringsTable sst = new SharedStringsTable();
    byte[][] sheetXmls = new byte[sheetCount][];
    String[] sheetNames = new String[sheetCount];

    for (int s = 0; s < sheetCount; s++) {
      SheetData<?> sheetData = sheets.get(s);
      sheetNames[s] = sanitizeSheetName(sheetData.getSheetName(), s);
      ByteArrayOutputStream sheetBos = new ByteArrayOutputStream(64 * 1024);
      sheetBos.write(SHEET_HEADER);
      writeSheetContent(sheetBos, sheetData, sst);
      sheetBos.write(SHEET_FOOTER);
      sheetXmls[s] = sheetBos.toByteArray();
    }

    try (ZipOutputStream zipOut = new ZipOutputStream(out)) {
      zipOut.putNextEntry(new ZipEntry("[Content_Types].xml"));
      writeContentTypes(zipOut, sheetCount);

      zipOut.putNextEntry(new ZipEntry("_rels/.rels"));
      zipOut.write(RELS);

      zipOut.putNextEntry(new ZipEntry("xl/_rels/workbook.xml.rels"));
      writeWorkbookRels(zipOut, sheetCount);

      zipOut.putNextEntry(new ZipEntry("xl/workbook.xml"));
      writeWorkbookXml(zipOut, sheetNames);

      zipOut.putNextEntry(new ZipEntry("xl/styles.xml"));
      zipOut.write(STYLES);

      if (sst.getCount() > 0) {
        zipOut.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
        zipOut.write(sst.buildXml());
      }

      zipOut.putNextEntry(new ZipEntry("xl/theme/theme1.xml"));
      zipOut.write(THEME);

      for (int s = 0; s < sheetCount; s++) {
        zipOut.putNextEntry(new ZipEntry("xl/worksheets/sheet" + (s + 1) + ".xml"));
        zipOut.write(sheetXmls[s]);
      }
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void writeSheetContent(ByteArrayOutputStream bos, SheetData sheetData,
      SharedStringsTable sst) throws IOException {
    Class<?> clazz = sheetData.getClazz();
    List<SheetFieldMeta> fields = analyzeFields(clazz);

    if (hasColumnWidths(fields)) {
      bos.write(buildColsXml(fields));
    }

    writeHeaderRow(bos, fields);

    List data = sheetData.getData();
    int size = data.size();
    for (int i = 0; i < size; i++) {
      Object rowObj = data.get(i);
      if (rowObj == null) {
        continue;
      }
      writeDataRow(bos, i + 2, rowObj, fields);
    }
  }

  private List<SheetFieldMeta> analyzeFields(Class<?> clazz) {
    List<SheetFieldMeta> result = new ArrayList<>(16);

    int declOrder = 0;
    for (Field field : clazz.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      ExcelProperty prop = field.getAnnotation(ExcelProperty.class);
      if (prop == null) {
        continue;
      }

      SheetFieldMeta info = new SheetFieldMeta();
      info.field = field;

      int idx = prop.index();
      info.originalOrder = (idx >= 0) ? idx : declOrder;

      String value = prop.value();
      info.headerName = (value != null && !value.isEmpty()) ? value : field.getName();

      try {
        info.getter = ASMFieldAccessor.getGetter(clazz, field);
      } catch (Exception e) {
        info.getter = field::get;
        field.setAccessible(true);
      }

      if (prop.width() > 0) {
        info.width = (short) prop.width();
      }

      result.add(info);
      declOrder++;
    }

    result.sort((a, b) -> {
      boolean aIndexed = a.originalOrder <= 10000;
      boolean bIndexed = b.originalOrder <= 10000;
      if (aIndexed && bIndexed) {
        return Integer.compare(a.originalOrder, b.originalOrder);
      }
      if (aIndexed != bIndexed) {
        return aIndexed ? -1 : 1;
      }
      return 0;
    });

    return result;
  }

  private void writeHeaderRow(ByteArrayOutputStream bos, List<SheetFieldMeta> fields)
      throws IOException {
    StringBuilder sb = new StringBuilder(fields.size() * 64);
    sb.append("<row r=\"1\">");
    for (int col = 0; col < fields.size(); col++) {
      String ref = convertToCellRef(0, col);
      String header = escapeXml(fields.get(col).headerName);
      sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t>");
      sb.append(header);
      sb.append("</t></is></c>");
    }
    sb.append("</row>\n");
    bos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
  }

  private void writeDataRow(ByteArrayOutputStream bos, int rowNum, Object rowObj,
      List<SheetFieldMeta> fields) throws IOException {
    StringBuilder sb = new StringBuilder(fields.size() * 64);
    sb.append("<row r=\"").append(rowNum).append("\">");
    for (int col = 0; col < fields.size(); col++) {
      String ref = convertToCellRef(rowNum - 1, col);
      SheetFieldMeta info = fields.get(col);
      try {
        Object value = info.getter.get(rowObj);
        appendCell(sb, ref, value);
      } catch (Exception e) {
        sb.append("<c r=\"").append(ref).append("\"/>");
      }
    }
    sb.append("</row>\n");
    bos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
  }

  private void appendCell(StringBuilder sb, String ref, Object value) {
    if (value == null) {
      sb.append("<c r=\"").append(ref).append("\"/>");
      return;
    }

    if (value instanceof Number) {
      sb.append("<c r=\"").append(ref).append("\"><v>");
      sb.append(value.toString());
      sb.append("</v></c>");
    } else if (value instanceof Boolean) {
      sb.append("<c r=\"").append(ref).append("\" t=\"b\"><v>");
      sb.append(((Boolean) value) ? "1" : "0");
      sb.append("</v></c>");
    } else {
      sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t>");
      sb.append(escapeXml(value.toString()));
      sb.append("</t></is></c>");
    }
  }

  private String sanitizeSheetName(String name, int sheetIndex) {
    if (name == null || name.trim().isEmpty()) {
      return "Sheet" + (sheetIndex + 1);
    }
    String cleaned = name.replaceAll("[/\\\\?*\\[\\]]", "_");
    if (cleaned.length() > 31) {
      cleaned = cleaned.substring(0, 31);
    }
    return cleaned.isEmpty() ? "Sheet" + (sheetIndex + 1) : cleaned;
  }

  private boolean hasColumnWidths(List<SheetFieldMeta> fields) {
    for (SheetFieldMeta info : fields) {
      if (info.width != null) {
        return true;
      }
    }
    return false;
  }

  private byte[] buildColsXml(List<SheetFieldMeta> fields) {
    StringBuilder sb = new StringBuilder(fields.size() * 48);
    sb.append("<cols>");
    for (int col = 0; col < fields.size(); col++) {
      if (fields.get(col).width == null) {
        continue;
      }
      int one = col + 1;
      sb.append("<col min=\"").append(one)
          .append("\" max=\"").append(one)
          .append("\" width=\"").append(fields.get(col).width)
          .append("\" customWidth=\"1\"/>");
    }
    sb.append("</cols>");
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  private void writeContentTypes(ZipOutputStream zipOut, int sheetCount) throws IOException {
    StringBuilder sb = new StringBuilder(512);
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
    sb.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">");
    sb.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>");
    sb.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>");
    sb.append("<Override PartName=\"/xl/sharedStrings.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\"/>");
    sb.append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
    sb.append("<Override PartName=\"/xl/theme/theme1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.theme+xml\"/>");
    sb.append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
    for (int s = 0; s < sheetCount; s++) {
      sb.append("<Override PartName=\"/xl/worksheets/sheet").append(s + 1);
      sb.append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
    }
    sb.append("</Types>");
    zipOut.write(sb.toString().getBytes(StandardCharsets.UTF_8));
  }

  private void writeWorkbookRels(ZipOutputStream zipOut, int sheetCount) throws IOException {
    StringBuilder sb = new StringBuilder(512);
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
    sb.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
    sb.append("<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings\" Target=\"sharedStrings.xml\"/>");
    sb.append("<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
    sb.append("<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme\" Target=\"theme/theme1.xml\"/>");
    for (int s = 0; s < sheetCount; s++) {
      sb.append("<Relationship Id=\"rId").append(s + 4);
      sb.append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet").append(s + 1).append(".xml\"/>");
    }
    sb.append("</Relationships>");
    zipOut.write(sb.toString().getBytes(StandardCharsets.UTF_8));
  }

  private void writeWorkbookXml(ZipOutputStream zipOut, String[] sheetNames) throws IOException {
    StringBuilder sb = new StringBuilder(256 + sheetNames.length * 64);
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
    sb.append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">");
    sb.append("<sheets>");
    for (int s = 0; s < sheetNames.length; s++) {
      sb.append("<sheet name=\"").append(escapeXml(sheetNames[s]));
      sb.append("\" sheetId=\"").append(s + 1);
      sb.append("\" r:id=\"rId").append(s + 4).append("\"/>");
    }
    sb.append("</sheets>");
    sb.append("</workbook>");
    zipOut.write(sb.toString().getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Excel 列序号到列名的转换（0 → A, 25 → Z, 26 → AA）。
   *
   * @param row 行号（0-based）
   * @param col 列号（0-based）
   * @return Excel 列引用（如 A1, B2, AA10）
   */
  private String convertToCellRef(int row, int col) {
    StringBuilder colRef = new StringBuilder(3);
    int c = col;
    while (c >= 0) {
      colRef.insert(0, (char) ('A' + c % 26));
      c = c / 26 - 1;
      if (c < 0) {
        break;
      }
    }
    return colRef.toString() + (row + 1);
  }

  private static String escapeXml(String text) {
    if (text == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '&':
          sb.append("&amp;");
          break;
        case '<':
          sb.append("&lt;");
          break;
        case '>':
          sb.append("&gt;");
          break;
        case '"':
          sb.append("&quot;");
          break;
        case '\'':
          sb.append("&apos;");
          break;
        default:
          sb.append(c);
      }
    }
    return sb.toString();
  }

  /** Sheet 字段元数据 */
  static class SheetFieldMeta {
    Field field;
    String headerName;
    ASMFieldAccessor.FieldGetter getter;
    int originalOrder;
    Short width;
  }

  /** 共享字符串表（有序去重） */
  static class SharedStringsTable {
    private static final int INITIAL_CAPACITY = 256;
    private String[] strings = new String[INITIAL_CAPACITY];
    private int count = 0;
    private final HashMap<String, Integer> stringToIndex = new HashMap<>(512);

    int add(String str) {
      if (str == null || str.isEmpty()) {
        return -1;
      }
      if (str.length() > 50) {
        return -1;
      }
      Integer existing = stringToIndex.get(str);
      if (existing != null) {
        return existing;
      }
      if (count >= strings.length) {
        String[] newArr = new String[strings.length * 2];
        System.arraycopy(strings, 0, newArr, 0, strings.length);
        strings = newArr;
      }
      int idx = count++;
      strings[idx] = str;
      stringToIndex.put(str, idx);
      return idx;
    }

    int getCount() {
      return count;
    }

    byte[] buildXml() throws IOException {
      ByteArrayOutputStream baos = new ByteArrayOutputStream(count * 128);
      baos.write(("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
          + "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" count=\""
          + count + "\" uniqueCount=\"" + count + "\">").getBytes(StandardCharsets.UTF_8));
      for (int i = 0; i < count; i++) {
        baos.write(("<si><t>" + escapeXml(strings[i]) + "</t></si>").getBytes(StandardCharsets.UTF_8));
      }
      baos.write("</sst>".getBytes(StandardCharsets.UTF_8));
      return baos.toByteArray();
    }
  }
}
