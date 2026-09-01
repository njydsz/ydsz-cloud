package com.njydsz.common.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.njydsz.common.excel.annotation.ExcelProperty;
import com.njydsz.common.excel.core.ExcelReader;
import com.njydsz.common.excel.core.config.ExcelConfig;
import com.njydsz.common.excel.core.metadata.ReadMetadata;

/**
 * 深度完善（方案 B）回归测试集 — fast 读取引擎 XLSX 规范覆盖缺口补齐。
 *
 * <p>覆盖四个能力缺口的解除验证：
 *
 * <ol>
 *   <li>rich text 多 run：SST 条目 {@code <si><r><t>run1</t></r><r><t>run2</t></r></si>}
 *       此前只取第一个 run，静默丢失后续内容（POI RichTextString、Excel 分段着色场景）
 *   <li>phonetic（注音）过滤：{@code <rPh>} 区间内的 {@code <t>} 不参与拼接
 *   <li>数值型日期识别：{@code <c s="N">} 样式索引经 styles.xml numFmt 判定为日期格式
 *       时按窗口转换（fastNumericDateCellIsKnownLimitation 存档项解除）
 *   <li>1904 日期窗口：use1904Windowing=true 时序列值按 1904 窗口转换
 *   <li>inlineStr 富文本：多 run 拼接（手工 zip 构造——POI 不产 inlineStr）
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class FastReaderXlsxSpecComplianceTest {

  @TempDir Path tempDir;

  // ==================== rich text 多 run（SST） ====================

  @Test
  void richTextSstRunsAreFullyConcatenated() throws IOException {
    // POI RichTextString 分段着色产生多 run：加粗(0-2) + 普通(2-4) + 斜体(4-6)
    File file = tempDir.resolve("richtext.xlsx").toFile();
    try (XSSFWorkbook wb = new XSSFWorkbook();
        FileOutputStream fos = new FileOutputStream(file)) {
      Sheet sheet = wb.createSheet("S1");
      Row header = sheet.createRow(0);
      header.createCell(0).setCellValue("名称");

      Row data = sheet.createRow(1);
      XSSFRichTextString rt = new XSSFRichTextString("加粗普通斜体");
      XSSFFont bold = wb.createFont();
      bold.setBold(true);
      XSSFFont italic = wb.createFont();
      italic.setItalic(true);
      rt.applyFont(0, 2, bold);
      rt.applyFont(4, 6, italic);
      Cell cell = data.createCell(0);
      cell.setCellValue(rt);

      wb.write(fos);
    }

    List<NameRow> rows = readNameRowsWithFastEngine(file);

    // 此前 fast 引擎只取第一个 run："加粗"；修复后拼接全部 run
    assertEquals(1, rows.size());
    assertEquals("加粗普通斜体", rows.get(0).getName());
  }

  // ==================== phonetic 过滤 + 多 run（手工 SST） ====================

  @Test
  void sstPhoneticAndMultiRunAreHandled() throws IOException {
    // 手工构造 SST：条目0 为多 run + 注音（rPh 在 run 之后），条目1 为纯文本 + 注音
    String sstXml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
            + " count=\"2\" uniqueCount=\"2\">"
            + "<si><r><rPr><b/></rPr><t>東</t></r><r><t>京</t></r>"
            + "<rPh sb=\"0\" eb=\"2\"><t>とうきょう</t></rPh></si>"
            + "<si><t>大阪</t><rPh sb=\"0\" eb=\"2\"><t>おおさか</t></rPh></si>"
            + "</sst>";
    String sheetXml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
            + "<sheetData>"
            + "<row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>名称</t></is></c></row>"
            + "<row r=\"2\"><c r=\"A2\" t=\"s\"><v>0</v></c></row>"
            + "<row r=\"3\"><c r=\"A3\" t=\"s\"><v>1</v></c></row>"
            + "</sheetData></worksheet>";
    File file = createManualXlsx(tempDir.resolve("phonetic.xlsx").toFile(), sstXml, sheetXml);

    List<NameRow> rows = readNameRowsWithFastEngine(file);

    // 多 run 拼接为"東京"，注音"とうきょう"不混入；纯文本条目同理
    assertEquals(2, rows.size());
    assertEquals("東京", rows.get(0).getName());
    assertEquals("大阪", rows.get(1).getName());
  }

  // ==================== 数值型日期识别 ====================

  @Test
  void numericDateCellIsConvertedToDate() throws IOException {
    File file = tempDir.resolve("date.xlsx").toFile();
    Calendar cal = Calendar.getInstance();
    cal.clear();
    cal.set(2026, Calendar.JANUARY, 15, 10, 30, 0);
    Date written = cal.getTime();
    try (XSSFWorkbook wb = new XSSFWorkbook();
        FileOutputStream fos = new FileOutputStream(file)) {
      Sheet sheet = wb.createSheet("S1");
      Row header = sheet.createRow(0);
      header.createCell(0).setCellValue("名称");
      header.createCell(1).setCellValue("日期");
      header.createCell(2).setCellValue("数量");

      Row data = sheet.createRow(1);
      data.createCell(0).setCellValue("A");
      CellStyle dateStyle = wb.createCellStyle();
      dateStyle.setDataFormat(
          wb.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
      Cell dateCell = data.createCell(1);
      dateCell.setCellValue(written);
      dateCell.setCellStyle(dateStyle);
      // 同行普通数字（General 样式）不受日期识别影响
      data.createCell(2).setCellValue(42);

      wb.write(fos);
    }

    List<DateRow> rows = readDateRowsWithFastEngine(file);

    assertEquals(1, rows.size());
    // 此前 fast 引擎不解析 styles.xml，日期序列值 46037.4375 被当纯数字读入
    assertEquals(
        "2026-01-15", new SimpleDateFormat("yyyy-MM-dd").format(rows.get(0).getDate()));
    assertEquals(Integer.valueOf(42), rows.get(0).getCount());
  }

  // ==================== 1904 日期窗口 ====================

  @Test
  void date1904WindowingIsHonored() throws IOException {
    File file = tempDir.resolve("date1904.xlsx").toFile();
    Calendar cal = Calendar.getInstance();
    cal.clear();
    cal.set(2026, Calendar.JANUARY, 15, 0, 0, 0);
    Date written = cal.getTime();
    try (XSSFWorkbook wb = new XSSFWorkbook();
        FileOutputStream fos = new FileOutputStream(file)) {
      // 1904 窗口 workbook 声明（序列值起点为 1904-01-01，与 1900 窗口差 1462 天）
      wb.getCTWorkbook().addNewWorkbookPr().setDate1904(true);
      Sheet sheet = wb.createSheet("S1");
      Row header = sheet.createRow(0);
      header.createCell(0).setCellValue("名称");
      header.createCell(1).setCellValue("日期");

      Row data = sheet.createRow(1);
      data.createCell(0).setCellValue("A");
      CellStyle dateStyle = wb.createCellStyle();
      dateStyle.setDataFormat(
          wb.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
      Cell dateCell = data.createCell(1);
      // 显式按 1904 窗口计算序列值写入（POI setCellValue(Date) 的窗口取数存在版本差异，
      // 测试以 DateUtil.getExcelDate(date, true) 锁定写入端语义）
      dateCell.setCellValue(DateUtil.getExcelDate(written, true));
      dateCell.setCellStyle(dateStyle);

      wb.write(fos);
    }

    // use1904Windowing=true：1904 序列值还原为写入的日期（默认 1900 解读会偏 4 年）
    ReadMetadata metadata = new ReadMetadata();
    metadata.setClazz(DateRow.class);
    metadata.setFilePath(file.getAbsolutePath());
    metadata.setExcelConfig(ExcelConfig.builder().useFastReader(true).use1904Windowing(true).build());
    List<DateRow> rows = new ExcelReader(metadata).doReadAll();

    assertEquals(1, rows.size());
    assertEquals(
        "2026-01-15", new SimpleDateFormat("yyyy-MM-dd").format(rows.get(0).getDate()));
  }

  // ==================== inlineStr 富文本（手工构造） ====================

  @Test
  void inlineRichTextRunsAreConcatenated() throws IOException {
    // POI 总以 SST 写字符串，inlineStr 需手工构造（部分第三方生成器使用该形态）
    String sheetXml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
            + "<sheetData>"
            + "<row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>名称</t></is></c></row>"
            + "<row r=\"2\"><c r=\"A2\" t=\"inlineStr\"><is>"
            + "<r><rPr><b/></rPr><t>加粗</t></r><r><t>普通</t></r>"
            + "<r><rPr><i/></rPr><t>斜体</t></r>"
            + "</is></c></row>"
            + "</sheetData></worksheet>";
    File file = createManualXlsx(tempDir.resolve("inline.xlsx").toFile(), null, sheetXml);

    List<NameRow> rows = readNameRowsWithFastEngine(file);

    // 此前 inlineStr 只取第一个 run："加粗"；修复后拼接全部 run（含 XML 实体解码路径）
    assertEquals(1, rows.size());
    assertEquals("加粗普通斜体", rows.get(0).getName());
  }

  // ==================== 测试基础设施 ====================

  /** 以 fast 读取引擎读取 NameRow（1900 窗口默认配置）。 */
  private List<NameRow> readNameRowsWithFastEngine(File file) {
    ReadMetadata metadata = new ReadMetadata();
    metadata.setClazz(NameRow.class);
    metadata.setFilePath(file.getAbsolutePath());
    metadata.setExcelConfig(ExcelConfig.builder().useFastReader(true).build());
    return new ExcelReader(metadata).doReadAll();
  }

  /** 以 fast 读取引擎读取 DateRow（1900 窗口默认配置）。 */
  private List<DateRow> readDateRowsWithFastEngine(File file) {
    ReadMetadata metadata = new ReadMetadata();
    metadata.setClazz(DateRow.class);
    metadata.setFilePath(file.getAbsolutePath());
    metadata.setExcelConfig(ExcelConfig.builder().useFastReader(true).build());
    return new ExcelReader(metadata).doReadAll();
  }

  /**
   * 手工构造最小 xlsx（ZipOutputStream）：workbook.xml + rels + 可选 sharedStrings.xml +
   * sheet1.xml。SuperFastExcelReader 只消费这几个部件，无需 [Content_Types].xml。
   *
   * @param file 目标文件
   * @param sstXml sharedStrings.xml 内容；null 表示不写入该部件
   * @param sheetXml sheet1.xml 内容
   */
  private static File createManualXlsx(File file, String sstXml, String sheetXml)
      throws IOException {
    String workbookXml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
            + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
            + "<sheets><sheet name=\"S1\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>";
    String relsXml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\""
            + " Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\""
            + " Target=\"worksheets/sheet1.xml\"/></Relationships>";

    try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file))) {
      zos.putNextEntry(new ZipEntry("xl/workbook.xml"));
      zos.write(workbookXml.getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();

      zos.putNextEntry(new ZipEntry("xl/_rels/workbook.xml.rels"));
      zos.write(relsXml.getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();

      if (sstXml != null) {
        zos.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
        zos.write(sstXml.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
      }

      zos.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
      zos.write(sheetXml.getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();
    }
    return file;
  }

  /** 名称行 DTO（rich text / phonetic / inlineStr 用例） */
  public static class NameRow {
    @ExcelProperty("名称")
    private String name;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  /** 日期行 DTO（数值日期 / 1904 窗口用例） */
  public static class DateRow {
    @ExcelProperty("名称")
    private String name;

    @ExcelProperty("日期")
    private Date date;

    @ExcelProperty("数量")
    private Integer count;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public Date getDate() {
      return date;
    }

    public void setDate(Date date) {
      this.date = date;
    }

    public Integer getCount() {
      return count;
    }

    public void setCount(Integer count) {
      this.count = count;
    }
  }
}
