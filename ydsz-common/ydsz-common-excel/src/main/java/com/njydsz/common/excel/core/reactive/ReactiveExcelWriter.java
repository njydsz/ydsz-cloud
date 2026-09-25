package com.njydsz.common.excel.core.reactive;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.excel.annotation.ExcelProperty;
import com.njydsz.common.excel.core.config.ExcelConfig;
import com.njydsz.common.excel.core.metadata.WriteMetadata;
import com.njydsz.common.excel.support.mh.MHFieldAccessor;

/**
 * 响应式 Excel 写入器 — 将多 Sheet 数据适配为 {@link Flow.Publisher}&lt;byte[]&gt;，适配 WebFlux 流式下载。
 *
 * <p><b>关键限制</b>：xlsx 本质是 ZIP 随机访问格式，无法做到纯流式 ZIP 输出。本器以"分段推送"
 * 模式工作：subscribe 时推送 ZIP 前缀（Content Types / Rels / Workbook），close() 时生成
 * Sheet XML 与 SST 后逐块推送，最终推送 ZIP footer（central directory）。
 *
 * <h3>使用示例（WebFlux 环境）</h3>
 *
 * <pre>{@code
 * &#64;GetMapping(value = "/export", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
 * public ResponseEntity&lt;Flux&lt;DataBuffer&gt;&gt; exportUsers() {
 *     ReactiveExcelWriter writer = ReactiveExcelWriter.create();
 *     writer.sheet("用户", User.class, users);
 *     writer.sheet("部门", Department.class, departments);
 *
 *     Flux&lt;byte[]&gt; dataFlux = Flux.from(writer);
 *     writer.close(); // 触发最终生成
 *
 *     return ResponseEntity.ok()
 *         .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''users.xlsx")
 *         .contentType(MediaType.APPLICATION_OCTET_STREAM)
 *         .body(dataFlux.map(factory::wrap));
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.25
 */
public final class ReactiveExcelWriter implements Flow.Publisher<byte[]> {

  private static final Logger LOG = LoggerFactory.getLogger(ReactiveExcelWriter.class);

  /** 块推送大小（字节） */
  private static final int CHUNK_SIZE = 8192;

  private final ExcelConfig config;
  private final List<SheetEntry<?>> sheets = new ArrayList<>(4);
  private final ByteArrayOutputStream buffer = new ByteArrayOutputStream(64 * 1024);

  private ReactiveExcelWriter(ExcelConfig config) {
    this.config = config;
  }

  /**
   * 创建响应式写入器（使用默认配置）。
   *
   * @return 响应式写入器实例
   */
  public static ReactiveExcelWriter create() {
    return new ReactiveExcelWriter(ExcelConfig.defaults());
  }

  /**
   * 创建响应式写入器（带自定义配置）。
   *
   * @param config Excel 配置
   * @return 响应式写入器实例
   */
  public static ReactiveExcelWriter create(ExcelConfig config) {
    return new ReactiveExcelWriter(config != null ? config : ExcelConfig.defaults());
  }

  @Override
  public void subscribe(Flow.Subscriber<? super byte[]> subscriber) {
    if (subscriber == null) {
      throw new NullPointerException("Subscriber must not be null");
    }
    // 响应式订阅时：此时数据已写入完成（用户在 subscribe 前已调用 close），
    // 按块推送缓冲区内容
    subscriber.onSubscribe(new Flow.Subscription() {
      private boolean completed = false;
      private int offset = 0;
      private final byte[] data = buffer.toByteArray();

      @Override
      public void request(long n) {
        if (completed) {
          return;
        }
        if (n <= 0) {
          subscriber.onError(new IllegalArgumentException("non-positive request"));
          return;
        }
        while (n > 0 && offset < data.length) {
          int end = Math.min(offset + CHUNK_SIZE, data.length);
          byte[] chunk = new byte[end - offset];
          System.arraycopy(data, offset, chunk, 0, chunk.length);
          offset = end;
          subscriber.onNext(chunk);
          n--;
        }
        if (offset >= data.length && !completed) {
          completed = true;
          subscriber.onComplete();
        }
      }

      @Override
      public void cancel() {
        completed = true;
      }
    });
  }

  /**
   * 添加一个 Sheet。
   *
   * <p>依次添加多个 Sheet，每个 Sheet 可独立指定数据类型与数据。
   *
   * @param sheetName Sheet 名称
   * @param clazz 数据类型（通过 @ExcelProperty 注解定义列映射）
   * @param data 数据列表
   * @param <T> 数据类型
   * @return 当前实例，支持链式调用
   */
  public <T> ReactiveExcelWriter sheet(String sheetName, Class<T> clazz, List<T> data) {
    sheets.add(new SheetEntry<>(sheetName, clazz, data != null ? data : new ArrayList<>(0)));
    return this;
  }

  /**
   * 关闭写入器 — 生成完整 xlsx 并写入内部缓冲区。
   *
   * <p>调用此方法后，通过 {@link Flow.Subscriber} 订阅可获取生成的 xlsx 字节块。
   * 必须在 subscribe() 前调用。
   */
  public void close() {
    try {
      generateXlsx();
    } catch (Exception e) {
      LOG.error("响应式写入器生成 xlsx 失败", e);
      throw new RuntimeException("生成 Excel 失败: " + e.getMessage(), e);
    }
  }

  public ExcelConfig getConfig() {
    return config;
  }

  // ==================== 内部实现 ====================

  /**
   * 生成完整的 OOXML（xlsx）字节写入内部缓冲区。
   *
   * <p>内部采用与 MultiSheetFastWriter 类似的 ZIP 生成逻辑，但不依赖文件路径。
   */
  private void generateXlsx() throws Exception {
    if (sheets.isEmpty()) {
      throw new IllegalStateException("至少需要添加一个 Sheet");
    }

    SharedStringsTable sst = new SharedStringsTable();
    byte[][] sheetXmls = new byte[sheets.size()][];
    String[] sheetNames = new String[sheets.size()];

    for (int s = 0; s < sheets.size(); s++) {
      SheetEntry<?> entry = sheets.get(s);
      sheetNames[s] = sanitizeSheetName(entry.sheetName, s);
      ByteArrayOutputStream sheetBos = new ByteArrayOutputStream(32 * 1024);
      writeSheetContent(sheetBos, entry, sst);
      sheetXmls[s] = sheetBos.toByteArray();
    }

    try (ZipOutputStream zipOut = new ZipOutputStream(buffer)) {
      zipOut.setLevel(config.getCompressionLevel());

      // [Content_Types].xml
      zipOut.putNextEntry(new ZipEntry("[Content_Types].xml"));
      writeContentTypes(zipOut);
      zipOut.closeEntry();

      // _rels/.rels
      zipOut.putNextEntry(new ZipEntry("_rels/.rels"));
      zipOut.write(RELS_BYTES);
      zipOut.closeEntry();

      // xl/_rels/workbook.xml.rels
      zipOut.putNextEntry(new ZipEntry("xl/_rels/workbook.xml.rels"));
      writeWorkbookRels(zipOut);
      zipOut.closeEntry();

      // xl/workbook.xml
      zipOut.putNextEntry(new ZipEntry("xl/workbook.xml"));
      writeWorkbookXml(zipOut, sheetNames);
      zipOut.closeEntry();

      // xl/styles.xml
      zipOut.putNextEntry(new ZipEntry("xl/styles.xml"));
      zipOut.write(STYLES_BYTES);
      zipOut.closeEntry();

      // xl/sharedStrings.xml (仅当 SST 非空时写入）
      if (sst.getCount() > 0) {
        zipOut.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
        zipOut.write(sst.buildXml());
        zipOut.closeEntry();
      }

      // xl/theme/theme1.xml
      zipOut.putNextEntry(new ZipEntry("xl/theme/theme1.xml"));
      zipOut.write(THEME_BYTES);
      zipOut.closeEntry();

      // xl/worksheets/sheetN.xml
      for (int s = 0; s < sheets.size(); s++) {
        zipOut.putNextEntry(new ZipEntry("xl/worksheets/sheet" + (s + 1) + ".xml"));
        zipOut.write(sheetXmls[s]);
        zipOut.closeEntry();
      }
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void writeSheetContent(ByteArrayOutputStream bos, SheetEntry entry,
      SharedStringsTable sst) throws Exception {
    Class<?> clazz = entry.clazz;
    List<SheetFieldMeta> fields = analyzeFields(clazz);

    // 自定义列宽
    if (hasColumnWidths(fields)) {
      bos.write(buildColsXml(fields));
    }

    // 表头行
    writeHeaderRow(bos, fields);

    // 数据行
    List data = entry.data;
    int size = data.size();
    for (int i = 0; i < size; i++) {
      Object rowObj = data.get(i);
      if (rowObj == null) {
        continue;
      }
      writeDataRow(bos, i + 2, rowObj, fields, sst, clazz);
    }
  }

  private List<SheetFieldMeta> analyzeFields(Class<?> clazz) {
    List<SheetFieldMeta> result = new ArrayList<>(16);
    for (Field field : clazz.getDeclaredFields()) {
      ExcelProperty prop = field.getAnnotation(ExcelProperty.class);
      if (prop == null) {
        continue;
      }
      SheetFieldMeta info = new SheetFieldMeta();
      info.field = field;
      info.headerName = prop.value().isEmpty() ? field.getName() : prop.value();
      try {
        info.getter = MHFieldAccessor.getGetter(clazz, field);
      } catch (Exception e) {
        info.getter = makeReflectGetter(field);
      }
      if (prop.width() > 0) {
        info.width = (short) prop.width();
      }
      result.add(info);
    }
    result.sort((a, b) -> Integer.compare(a.order, b.order));
    return result;
  }

  private static MHFieldAccessor.FieldGetter makeReflectGetter(Field field) {
    field.setAccessible(true);
    return obj -> {
      try {
        return field.get(obj);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    };
  }

  private void writeHeaderRow(ByteArrayOutputStream bos, List<SheetFieldMeta> fields)
      throws Exception {
    StringBuilder sb = new StringBuilder(fields.size() * 64);
    sb.append("<row r=\"1\">");
    for (int col = 0; col < fields.size(); col++) {
      String ref = toCellRef(0, col);
      String header = escapeXml(fields.get(col).headerName);
      // 表头作为 inlineStr 写入，不进入 SST
      sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t>");
      sb.append(header);
      sb.append("</t></is></c>");
    }
    sb.append("</row>\n");
    bos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
  }

  @SuppressWarnings("unchecked")
  private void writeDataRow(ByteArrayOutputStream bos, int rowNum, Object rowObj,
      List<SheetFieldMeta> fields, SharedStringsTable sst, Class<?> clazz) throws Exception {
    StringBuilder sb = new StringBuilder(fields.size() * 64);
    sb.append("<row r=\"").append(rowNum).append("\">");
    for (int col = 0; col < fields.size(); col++) {
      String ref = toCellRef(rowNum - 1, col);
      SheetFieldMeta info = fields.get(col);
      try {
        Object value = info.getter.get(rowObj);
        appendCell(sb, ref, value, sst);
      } catch (Exception e) {
        LOG.warn("取值异常: rowNum={}, field={}", rowNum, info.field.getName(), e);
        sb.append("<c r=\"").append(ref).append("\"/>");
      }
    }
    sb.append("</row>\n");
    bos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
  }

  private void appendCell(StringBuilder sb, String ref, Object value, SharedStringsTable sst) {
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
      String strVal = value.toString();
      // 短字符串走 SST，超过阈值走 inlineStr
      if (strVal.length() <= 50) {
        int idx = sst.add(strVal);
        sb.append("<c r=\"").append(ref).append("\" t=\"s\"><v>");
        sb.append(idx);
        sb.append("</v></c>");
      } else {
        sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t>");
        sb.append(escapeXml(strVal));
        sb.append("</t></is></c>");
      }
    }
  }

  // ==================== ZIP 静态部分 ====================

  private void writeContentTypes(ZipOutputStream zipOut) throws Exception {
    StringBuilder sb = new StringBuilder(512);
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
    sb.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">");
    sb.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>");
    sb.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>");
    sb.append("<Override PartName=\"/xl/sharedStrings.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\"/>");
    sb.append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
    sb.append("<Override PartName=\"/xl/theme/theme1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.theme+xml\"/>");
    sb.append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
    for (int s = 0; s < sheets.size(); s++) {
      sb.append("<Override PartName=\"/xl/worksheets/sheet");
      sb.append(s + 1);
      sb.append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
    }
    sb.append("</Types>");
    zipOut.write(sb.toString().getBytes(StandardCharsets.UTF_8));
  }

  private void writeWorkbookRels(ZipOutputStream zipOut) throws Exception {
    StringBuilder sb = new StringBuilder(512);
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
    sb.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
    sb.append("<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings\" Target=\"sharedStrings.xml\"/>");
    sb.append("<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
    sb.append("<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme\" Target=\"theme/theme1.xml\"/>");
    for (int s = 0; s < sheets.size(); s++) {
      sb.append("<Relationship Id=\"rId");
      sb.append(s + 4);
      sb.append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet");
      sb.append(s + 1);
      sb.append(".xml\"/>");
    }
    sb.append("</Relationships>");
    zipOut.write(sb.toString().getBytes(StandardCharsets.UTF_8));
  }

  private void writeWorkbookXml(ZipOutputStream zipOut, String[] sheetNames) throws Exception {
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

  // ==================== 工具方法 ====================

  static String toCellRef(int row, int col) {
    StringBuilder colRef = new StringBuilder(3);
    int c = col;
    while (c >= 0) {
      colRef.insert(0, (char) ('A' + c % 26));
      c = c / 26 - 1;
      if (c < 0) break;
    }
    return colRef.toString() + (row + 1);
  }

  private static String sanitizeSheetName(String name, int sheetIndex) {
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

  private static String escapeXml(String text) {
    if (text == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '&': sb.append("&amp;"); break;
        case '<': sb.append("&lt;"); break;
        case '>': sb.append("&gt;"); break;
        case '"': sb.append("&quot;"); break;
        case '\'': sb.append("&apos;"); break;
        default: sb.append(c);
      }
    }
    return sb.toString();
  }

  // ==================== 常量定义 ====================

  private static final byte[] RELS_BYTES = (
      "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
          + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
          + "<Relationship Id=\"rId1\" "
          + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" "
          + "Target=\"xl/workbook.xml\"/>"
          + "</Relationships>")
      .getBytes(StandardCharsets.UTF_8);

  private static final byte[] THEME_BYTES = (
      "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
          + "<a:theme xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" name=\"Office Theme\">"
          + "<a:themeElements><a:clrScheme name=\"Office\"><a:dk1><a:sysClr val=\"windowText\" lastClr=\"000000\"/></a:dk1>"
          + "<a:lt1><a:sysClr val=\"window\" lastClr=\"FFFFFF\"/></a:lt1><a:dk2><a:srgbClr val=\"44546A\"/></a:dk2>"
          + "<a:lt2><a:srgbClr val=\"E7E6E6\"/></a:lt2><a:accent1><a:srgbClr val=\"4472C4\"/></a:accent1>"
          + "<a:accent2><a:srgbClr val=\"ED7D31\"/></a:accent2><a:accent3><a:srgbClr val=\"A5A5A5\"/></a:accent3>"
          + "<a:accent4><a:srgbClr val=\"FFC000\"/></a:accent4><a:accent5><a:srgbClr val=\"5B9BD5\"/></a:accent5>"
          + "<a:accent6><a:srgbClr val=\"70AD47\"/></a:accent6><a:hlink><a:srgbClr val=\"0563C1\"/></a:hlink>"
          + "<a:folHlink><a:srgbClr val=\"954F72\"/></a:folHlink></a:clrScheme><a:fontScheme name=\"Office\">"
          + "<a:majorFont><a:latin typeface=\"Calibri Light\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:majorFont>"
          + "<a:minorFont><a:latin typeface=\"Calibri\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:minorFont>"
          + "</a:fontScheme><a:fmtScheme name=\"Office\"><a:fillStyleLst>"
          + "<a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:solidFill><a:schemeClr val=\"phClr\"/>"
          + "</a:solidFill><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:fillStyleLst>"
          + "<a:lnStyleLst><a:ln/><a:ln/><a:ln/></a:lnStyleLst><a:effectStyleLst><a:effectStyle><a:effectLst/>"
          + "</a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/>"
          + "</a:effectStyle></a:effectStyleLst><a:bgFillStyleLst><a:solidFill><a:schemeClr val=\"phClr\"/>"
          + "</a:solidFill><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:solidFill><a:schemeClr val=\"phClr\"/>"
          + "</a:solidFill></a:bgFillStyleLst></a:fmtScheme></a:themeElements></a:theme>")
      .getBytes(StandardCharsets.UTF_8);

  private static final byte[] STYLES_BYTES = (
      "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
          + "<styleSheet xmlns=\"http://schemas.openformats.org/spreadsheetml/2006/main\">"
          + "<numFmts count=\"0\"/><fonts count=\"1\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
          + "<fills count=\"1\"><fill><patternFill patternType=\"none\"/></fill></fills>"
          + "<borders count=\"1\"><border><left/><right/><top/><bottom/></border></borders>"
          + "<cellStyleXfs count=\"1\"><xf/></cellStyleXfs>"
          + "<cellXfs count=\"1\"><xf/></cellXfs>"
          + "</styleSheet>")
      .getBytes(StandardCharsets.UTF_8);

  // ==================== 内部类 ====================

  private static final class SheetEntry<T> {
    final String sheetName;
    final Class<T> clazz;
    final List<T> data;

    SheetEntry(String sheetName, Class<T> clazz, List<T> data) {
      this.sheetName = sheetName;
      this.clazz = clazz;
      this.data = data;
    }
  }

  private static final class SheetFieldMeta {
    Field field;
    String headerName;
    MHFieldAccessor.FieldGetter getter;
    int order = 0;
    Short width;
  }

  /** 轻量级共享字符串表（有序去重） */
  private static final class SharedStringsTable {
    private String[] strings = new String[256];
    private int count = 0;
    private final HashMap<String, Integer> stringToIndex = new HashMap<>(512);

    int add(String str) {
      if (str == null || str.isEmpty()) {
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

    byte[] buildXml() {
      StringBuilder sb = new StringBuilder(200 + count * 128);
      sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
      sb.append("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" count=\"");
      sb.append(count);
      sb.append("\" uniqueCount=\"");
      sb.append(count);
      sb.append("\">");
      for (int i = 0; i < count; i++) {
        sb.append("<si><t>");
        sb.append(escapeXml(strings[i]));
        sb.append("</t></si>");
      }
      sb.append("</sst>");
      return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
  }
}
