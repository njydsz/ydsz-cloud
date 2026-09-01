package com.njydsz.common.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.njydsz.common.excel.annotation.ExcelProperty;
import com.njydsz.common.excel.core.ExcelReader;
import com.njydsz.common.excel.core.config.ExcelConfig;
import com.njydsz.common.excel.core.metadata.ReadMetadata;
import com.njydsz.common.excel.exception.ExcelReadException;

/**
 * P1-4 回归测试集 — fast 读取引擎 Sheet 选择 + zip bomb 膨胀比防护。
 *
 * <p>覆盖两个修复点的行为回归：
 *
 * <ol>
 *   <li>Sheet 选择：{@code read(Path)} 经 ZipFile 随机访问解析 workbook.xml 与 rels，
 *       支持 sheetName（精确匹配，未命中即失败——与 POI 路径语义对齐）与 sheetIndex
 *       （声明顺序 0-based，越界回落第一个）。此前 fast 引擎固定读取 zip 中第一个 sheet
 *       entry，忽略调用方的 Sheet 选择配置。
 *   <li>zip bomb 防护：所有解压读取（sheet XML / sharedStrings / InputStream 落盘）经
 *       BoundedInputStream 限流，解压后累计超过 maxReadFileSizeMB 即中断。此前依赖
 *       ZipEntry.getSize()（zip 头可伪造）事后检查，防护形同虚设。
 *   <li>config 接线：ExcelConfig.maxReadFileSizeMB 经 ExcelReader 传递至 fast 引擎
 *       （此前 fast 引擎内部恒用 defaults()）。
 * </ol>
 *
 * <p>zip bomb 用例以手工 ZipOutputStream 构造（压缩后 KB 级、解压后 50MB），绕过
 * ExcelReader 基于压缩文件体积的预检查——这正是膨胀攻击的实战形态。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class FastReaderSheetSelectionAndZipBombTest {

  @TempDir Path tempDir;

  private File multiSheetFile;

  @BeforeEach
  void setUp() throws IOException {
    multiSheetFile = createMultiSheetFile(tempDir.resolve("multi.xlsx").toFile());
  }

  // ==================== Sheet 选择 ====================

  @Test
  void fastReaderSelectsSheetByName() {
    // P1-4 回归：此前 fast 引擎忽略 sheetName，固定读第一个 sheet（汇总），明细数据全部丢失
    List<CountRow> rows = readWithFastEngine(multiSheetFile, "明细", null);

    assertEquals(2, rows.size());
    assertEquals("明细A", rows.get(0).getName());
    assertEquals(Integer.valueOf(1), rows.get(0).getCount());
    assertEquals("明细B", rows.get(1).getName());
  }

  @Test
  void fastReaderSelectsSheetByIndex() {
    // sheetIndex=1（workbook 声明顺序，0-based）定位第二个 sheet
    List<CountRow> rows = readWithFastEngine(multiSheetFile, null, 1);

    assertEquals(2, rows.size());
    assertEquals("明细A", rows.get(0).getName());
  }

  @Test
  void fastReaderDefaultsToFirstSheet() {
    // 无选择时默认第一个 sheet（汇总）
    List<CountRow> rows = readWithFastEngine(multiSheetFile, null, null);

    assertEquals(1, rows.size());
    assertEquals("合计", rows.get(0).getName());
    assertEquals(Integer.valueOf(100), rows.get(0).getCount());
  }

  @Test
  void fastReaderUnknownSheetNameFails() {
    // 对齐 POI 路径语义：按名未命中即失败，而非静默回落第一个 sheet
    ExcelReadException ex =
        assertThrows(
            ExcelReadException.class,
            () -> readWithFastEngine(multiSheetFile, "不存在的Sheet", null));

    assertTrue(ex.getMessage().contains("Sheet不存在"));
  }

  @Test
  void fastReaderSheetIndexOutOfBoundsFallsBackToFirst() {
    // 越界回落第一个 sheet——与 POI 路径 getSheet 语义对齐
    List<CountRow> rows = readWithFastEngine(multiSheetFile, null, 99);

    assertEquals(1, rows.size());
    assertEquals("合计", rows.get(0).getName());
  }

  // ==================== zip bomb 膨胀比防护 ====================

  @Test
  void zipBombInflatedSheetBlocked() throws IOException {
    // sheet entry 压缩后 KB 级、解压后 50MB；maxReadFileSizeMB=1 时必须在解压阶段中断
    File bomb = createZipBombFile(
        tempDir.resolve("bomb-sheet.xlsx").toFile(), "xl/worksheets/sheet1.xml");

    ExcelReadException ex =
        assertThrows(ExcelReadException.class, () -> readBombWithFastEngine(bomb));

    assertTrue(ex.getMessage().contains("超过安全上限"));
  }

  @Test
  void zipBombInflatedSharedStringsBlocked() throws IOException {
    // SST 部件膨胀攻击：sharedStrings.xml 解压后 50MB，sheet 本身正常
    File bomb = createZipBombFile(
        tempDir.resolve("bomb-sst.xlsx").toFile(), "xl/sharedStrings.xml");

    ExcelReadException ex =
        assertThrows(ExcelReadException.class, () -> readBombWithFastEngine(bomb));

    assertTrue(ex.getMessage().contains("超过安全上限"));
  }

  // ==================== 测试基础设施 ====================

  /** 以 fast 读取引擎读取（可选 sheetName / sheetIndex），maxReadFileSizeMB 用默认值 */
  private List<CountRow> readWithFastEngine(File file, String sheetName, Integer sheetIndex) {
    ReadMetadata metadata = new ReadMetadata();
    metadata.setClazz(CountRow.class);
    metadata.setFilePath(file.getAbsolutePath());
    metadata.setExcelConfig(ExcelConfig.builder().useFastReader(true).build());
    if (sheetName != null) {
      metadata.setSheetName(sheetName);
    }
    if (sheetIndex != null) {
      metadata.setSheetIndex(sheetIndex);
    }
    return new ExcelReader(metadata).doReadAll();
  }

  /** 以 fast 读取引擎 + maxReadFileSizeMB=1 读取（zip bomb 断言用） */
  private List<CountRow> readBombWithFastEngine(File file) {
    ReadMetadata metadata = new ReadMetadata();
    metadata.setClazz(CountRow.class);
    metadata.setFilePath(file.getAbsolutePath());
    metadata.setExcelConfig(
        ExcelConfig.builder().useFastReader(true).maxReadFileSizeMB(1).build());
    return new ExcelReader(metadata).doReadAll();
  }

  /** 生成双 Sheet 文件：第一个"汇总"（1 行），第二个"明细"（2 行）——两 Sheet 数据可区分 */
  private static File createMultiSheetFile(File file) throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook();
        FileOutputStream fos = new FileOutputStream(file)) {
      Sheet summary = wb.createSheet("汇总");
      writeHeaderAndRows(summary, new Object[][] {{"合计", 100}});

      Sheet detail = wb.createSheet("明细");
      writeHeaderAndRows(detail, new Object[][] {{"明细A", 1}, {"明细B", 2}});

      wb.write(fos);
    }
    return file;
  }

  private static void writeHeaderAndRows(Sheet sheet, Object[][] dataRows) {
    Row header = sheet.createRow(0);
    header.createCell(0).setCellValue("名称");
    header.createCell(1).setCellValue("数量");
    int rowIdx = 1;
    for (Object[] data : dataRows) {
      Row row = sheet.createRow(rowIdx++);
      row.createCell(0).setCellValue((String) data[0]);
      row.createCell(1).setCellValue((Integer) data[1]);
    }
  }

  /**
   * 构造 zip bomb 形态的伪 xlsx：完整 workbook.xml + rels（sheet 选择链路可走通），
   * 指定部件解压后 50MB（压缩后 KB 级）。
   *
   * <p>压缩文件本身极小，绕过 ExcelReader 基于压缩体积的预检查——验证的是解压阶段的
   * BoundedInputStream 限流，而非预检查。
   */
  private static File createZipBombFile(File file, String bombEntryName) throws IOException {
    String workbookXml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
            + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
            + "<sheets><sheet name=\"Bomb\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>";
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

      // 非炸弹部件给最小合法内容
      if ("xl/sharedStrings.xml".equals(bombEntryName)) {
        zos.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
        zos.write("<worksheet/>".getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
      }

      // 炸弹部件：解压后 50MB 的重复字符
      zos.putNextEntry(new ZipEntry(bombEntryName));
      byte[] chunk = new byte[8192];
      Arrays.fill(chunk, (byte) 'A');
      long written = 0;
      long target = 50L * 1024 * 1024;
      while (written < target) {
        zos.write(chunk);
        written += chunk.length;
      }
      zos.closeEntry();
    }
    return file;
  }

  /** 计数行 DTO */
  public static class CountRow {
    @ExcelProperty("名称")
    private String name;

    @ExcelProperty("数量")
    private Integer count;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public Integer getCount() {
      return count;
    }

    public void setCount(Integer count) {
      this.count = count;
    }
  }
}
