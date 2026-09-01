package com.njydsz.common.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.njydsz.common.excel.annotation.ExcelIgnore;
import com.njydsz.common.excel.annotation.ExcelProperty;
import com.njydsz.common.excel.core.ExcelFacade;
import com.njydsz.common.excel.core.ExcelReader;
import com.njydsz.common.excel.core.ExcelWriter;
import com.njydsz.common.excel.core.RawSheetData;
import com.njydsz.common.excel.core.config.ExcelConfig;
import com.njydsz.common.excel.core.metadata.ReadMetadata;
import com.njydsz.common.excel.core.metadata.WriteMetadata;

/**
 * P0 回归测试集 — 双引擎最小 round-trip 验证。
 *
 * <p>覆盖五个 P0 修复的行为回归：
 *
 * <ol>
 *   <li>P0-0 fast 引擎默认关闭（默认配置走 POI 兼容路径）
 *   <li>P0-1 InputStream + fastReader 显式开启时回退 POI 路径，不再 NPE
 *   <li>P0-2 fast 读取路径列映射生效（表头收集 + 元数据工厂惰性构建）
 *   <li>P0-3 fast 写入路径小数不截断（Double/BigDecimal 走 double 分支）
 *   <li>P0-5 headRowNumber 双引擎语义一致（1-based 表头行号）
 * </ol>
 *
 * <p>已知限制（P1，非本测试集断言目标）：fast 读取引擎对"数值型日期单元格"不识别（SimpleCell
 * 无样式信息，日期序列号被当普通数字），日期列应以字符串 + dateFormat 写入。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class ExcelRoundTripTest {

  @TempDir Path tempDir;

  private File employeeFile;

  /** 标题行在表头之上的文件（headRowNumber=2 场景） */
  private File employeeFileWithTitleRow;

  private File dateFormattedFile;

  private static final String[] HEADERS = {"姓名", "年龄", "薪资", "金额", "入职日期", "备注", "秘密"};

  /** 测试数据：覆盖整数、小数、负小数、高精度小数、字符串、空值、公式样式字符串 */
  private static final Object[][] DATA = {
    {"张三", 30, 12345.67, 99.95, "2024-03-15", "=SUM(A1:A2)", "s1"},
    {"李四", 25, 0.5, -0.01, "2023-12-31", null, "s2"},
    {"王五", 41, 99999.99, 12345.6789, "2020-01-01", "普通备注", "s3"},
  };

  @BeforeEach
  void setUp() throws IOException {
    employeeFile = createEmployeeFile(tempDir.resolve("employee.xlsx").toFile(), false);
    employeeFileWithTitleRow =
        createEmployeeFile(tempDir.resolve("employee-title.xlsx").toFile(), true);
    dateFormattedFile = createDateFormattedFile(tempDir.resolve("date.xlsx").toFile());
  }

  // ==================== POI 兼容路径（默认配置） ====================

  @Test
  void poiTypedReadAllFields() {
    List<EmployeeRow> rows =
        ExcelFacade.read(employeeFile.getAbsolutePath(), EmployeeRow.class).doReadAll();

    assertEquals(3, rows.size());
    EmployeeRow first = rows.get(0);
    assertEquals("张三", first.getName());
    assertEquals(30, first.getAge());
    assertEquals("李四", rows.get(1).getName());
    assertEquals(41, rows.get(2).getAge());
  }

  @Test
  void poiDecimalPrecisionPreserved() {
    List<EmployeeRow> rows =
        ExcelFacade.read(employeeFile.getAbsolutePath(), EmployeeRow.class).doReadAll();

    assertEquals(12345.67, rows.get(0).getSalary(), 1e-9);
    assertEquals(0.5, rows.get(1).getSalary(), 1e-9);
    assertEquals(0, rows.get(0).getAmount().compareTo(new BigDecimal("99.95")));
    assertEquals(0, rows.get(1).getAmount().compareTo(new BigDecimal("-0.01")));
    assertEquals(0, rows.get(2).getAmount().compareTo(new BigDecimal("12345.6789")));
  }

  @Test
  void poiDateStringParsedWithDateFormat() {
    List<EmployeeRow> rows =
        ExcelFacade.read(employeeFile.getAbsolutePath(), EmployeeRow.class).doReadAll();

    assertEquals(LocalDate.of(2024, 3, 15), rows.get(0).getHireDate());
    assertEquals(LocalDate.of(2023, 12, 31), rows.get(1).getHireDate());
  }

  @Test
  void poiHeadRowNumber2SkipsTitleRow() {
    List<EmployeeRow> rows =
        ExcelFacade.read(employeeFileWithTitleRow.getAbsolutePath(), EmployeeRow.class)
            .headRowNumber(2)
            .doReadAll();

    assertEquals(3, rows.size());
    assertEquals("张三", rows.get(0).getName());
  }

  @Test
  void poiExcelIgnoreFieldNotMapped() {
    List<EmployeeRow> rows =
        ExcelFacade.read(employeeFile.getAbsolutePath(), EmployeeRow.class).doReadAll();

    // @ExcelIgnore 标注字段不参与映射（P0-4：注解已可标注到 FIELD）
    assertNull(rows.get(0).getSecret());
    // 公式样式字符串按原样读入
    assertEquals("=SUM(A1:A2)", rows.get(0).getRemark());
    // 空单元格映射为 null
    assertNull(rows.get(1).getRemark());
  }

  @Test
  void poiDateFormattedCellParsed() throws Exception {
    List<DateRow> rows;
    try (InputStream in = new java.io.FileInputStream(dateFormattedFile)) {
      rows = ExcelFacade.read(in, DateRow.class).doReadAll();
    }

    assertEquals(1, rows.size());
    assertNotNull(rows.get(0).getTime());
    String actual = new SimpleDateFormat("yyyy-MM-dd").format(rows.get(0).getTime());
    assertEquals("2024-06-01", actual);
  }

  @Test
  void inputStreamWithDefaultConfigUsesPoiPath() throws Exception {
    // P0-0 回归：默认配置 fast 引擎关闭，InputStream 输入正常读取
    List<EmployeeRow> rows;
    try (InputStream in = new java.io.FileInputStream(employeeFile)) {
      rows = ExcelFacade.read(in, EmployeeRow.class).doReadAll();
    }
    assertEquals(3, rows.size());
    assertEquals("张三", rows.get(0).getName());
  }

  // ==================== fast 读取引擎（显式 opt-in） ====================

  @Test
  void fastTypedReadAllFields() {
    List<EmployeeRow> rows = readWithFastEngine(employeeFile, null);

    // P0-2 回归：此前 fast 路径列元数据恒为 null，全部字段为 null / 整行丢弃
    assertEquals(3, rows.size());
    assertEquals("张三", rows.get(0).getName());
    assertEquals(30, rows.get(0).getAge());
    assertEquals("李四", rows.get(1).getName());
    assertNull(rows.get(1).getRemark());
    assertEquals("普通备注", rows.get(2).getRemark());
  }

  @Test
  void fastDecimalPrecisionPreserved() {
    List<EmployeeRow> rows = readWithFastEngine(employeeFile, null);

    assertEquals(12345.67, rows.get(0).getSalary(), 1e-9);
    assertEquals(0, rows.get(0).getAmount().compareTo(new BigDecimal("99.95")));
    assertEquals(0, rows.get(1).getAmount().compareTo(new BigDecimal("-0.01")));
    assertEquals(0, rows.get(2).getAmount().compareTo(new BigDecimal("12345.6789")));
  }

  @Test
  void fastDateStringParsedWithDateFormat() {
    List<EmployeeRow> rows = readWithFastEngine(employeeFile, null);

    assertEquals(LocalDate.of(2024, 3, 15), rows.get(0).getHireDate());
    assertEquals(LocalDate.of(2020, 1, 1), rows.get(2).getHireDate());
  }

  @Test
  void fastHeadRowNumber2SkipsTitleRow() {
    List<EmployeeRow> rows = readWithFastEngine(employeeFileWithTitleRow, 2);

    // P0-5 回归：fast 引擎接收 1-based 表头行号，语义与 POI 路径一致
    assertEquals(3, rows.size());
    assertEquals("张三", rows.get(0).getName());
  }

  @Test
  void fastInputStreamFallsBackToPoiWithoutNpe() throws Exception {
    // P0-1 回归：fastReader=true + InputStream 输入不再 NPE，回退 POI 路径正常读取
    ReadMetadata metadata = new ReadMetadata();
    metadata.setClazz(EmployeeRow.class);
    metadata.setInputStream(new java.io.FileInputStream(employeeFile));
    metadata.setExcelConfig(ExcelConfig.builder().useFastReader(true).build());

    List<EmployeeRow> rows = new ExcelReader(metadata).doReadAll();
    assertEquals(3, rows.size());
    assertEquals("张三", rows.get(0).getName());
  }

  @Test
  void fastMaxRowsLimitsRowCount() {
    ReadMetadata metadata = new ReadMetadata();
    metadata.setClazz(EmployeeRow.class);
    metadata.setFilePath(employeeFile.getAbsolutePath());
    metadata.setExcelConfig(ExcelConfig.builder().useFastReader(true).build());
    metadata.setMaxRows(2);

    List<EmployeeRow> rows = new ExcelReader(metadata).doReadAll();
    assertEquals(2, rows.size());
  }

  @Test
  void fastNumericDateCellIsConverted() {
    // 深度完善（方案 B）：fast 引擎解析 styles.xml numFmt 判定日期样式，
    // 数值序列值转换为 Date——原"已知限制"（被当纯数字、Date 字段 null）已解除。
    List<DateRow> rows = readDateRowsWithFastEngine(dateFormattedFile);
    assertEquals(1, rows.size());
    assertEquals(
        LocalDate.of(2024, 6, 1),
        rows.get(0).getTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
  }

  // ==================== fast 写入引擎 round-trip ====================

  @Test
  void fastWriterRoundTripPreservesDecimalsAndDates() {
    File out = tempDir.resolve("fast-write.xlsx").toFile();
    writeWithEngine(out, /* useFastWriter = */ true);

    // 用 POI 兼容路径读回，验证 fast 写出的文件可被标准引擎解析
    List<EmployeeRow> rows =
        ExcelFacade.read(out.getAbsolutePath(), EmployeeRow.class).doReadAll();

    assertEquals(3, rows.size());
    // P0-3 回归：此前 fast 写路径统一 longValue() 导致 12345.67 → 12345
    assertEquals(12345.67, rows.get(0).getSalary(), 1e-9);
    assertEquals(0.5, rows.get(1).getSalary(), 1e-9);
    assertEquals(0, rows.get(2).getAmount().compareTo(new BigDecimal("12345.6789")));
    // 日期字符串 round-trip
    assertEquals(LocalDate.of(2024, 3, 15), rows.get(0).getHireDate());
    assertEquals("张三", rows.get(0).getName());
  }

  @Test
  void fastWriterProducesReadableSheetNameAndHeaders() throws Exception {
    File out = tempDir.resolve("fast-write-sheet.xlsx").toFile();
    writeWithEngine(out, /* useFastWriter = */ true);

    List<RawSheetData> sheets;
    try (InputStream in = new java.io.FileInputStream(out)) {
      sheets = ExcelFacade.readAllSheets(in);
    }

    assertEquals(1, sheets.size());
    assertEquals("员工列表", sheets.get(0).sheetName());
    assertTrue(sheets.get(0).headers().contains("姓名"));
    assertEquals(3, sheets.get(0).rows().size());
  }

  @Test
  void poiWriterRoundTripAsControl() {
    File out = tempDir.resolve("poi-write.xlsx").toFile();
    writeWithEngine(out, /* useFastWriter = */ false);

    List<EmployeeRow> rows =
        ExcelFacade.read(out.getAbsolutePath(), EmployeeRow.class).doReadAll();

    assertEquals(3, rows.size());
    assertEquals(12345.67, rows.get(0).getSalary(), 1e-9);
    assertEquals(LocalDate.of(2024, 3, 15), rows.get(0).getHireDate());
  }

  // ==================== 测试基础设施 ====================

  /** 以 fast 读取引擎读取员工文件（headRowNumber 为 null 时用默认值 1） */
  private List<EmployeeRow> readWithFastEngine(File file, Integer headRowNumber) {
    ReadMetadata metadata = new ReadMetadata();
    metadata.setClazz(EmployeeRow.class);
    metadata.setFilePath(file.getAbsolutePath());
    metadata.setExcelConfig(ExcelConfig.builder().useFastReader(true).build());
    if (headRowNumber != null) {
      metadata.setHeadRowNumber(headRowNumber);
    }
    return new ExcelReader(metadata).doReadAll();
  }

  /** 以 fast 读取引擎读取数值日期文件 */
  private List<DateRow> readDateRowsWithFastEngine(File file) {
    ReadMetadata metadata = new ReadMetadata();
    metadata.setClazz(DateRow.class);
    metadata.setFilePath(file.getAbsolutePath());
    metadata.setExcelConfig(ExcelConfig.builder().useFastReader(true).build());
    return new ExcelReader(metadata).doReadAll();
  }

  /** 用指定引擎写入样例数据（useFastWriter=true 走 SuperFastExcelWriter） */
  private void writeWithEngine(File file, boolean useFastWriter) {
    WriteMetadata metadata = new WriteMetadata();
    metadata.setClazz(EmployeeRow.class);
    metadata.setFilePath(file.getAbsolutePath());
    metadata.setExcelConfig(ExcelConfig.builder().useFastWriter(useFastWriter).build());

    ExcelWriter writer = new ExcelWriter(metadata);
    writer.sheet("员工列表").doWrite(sampleRows());
  }

  private static List<EmployeeRow> sampleRows() {
    List<EmployeeRow> rows = new ArrayList<>();
    rows.add(row("张三", 30, 12345.67, "99.95", "2024-03-15", "备注甲"));
    rows.add(row("李四", 25, 0.5, "-0.01", "2023-12-31", null));
    rows.add(row("王五", 41, 99999.99, "12345.6789", "2020-01-01", "备注丙"));
    return rows;
  }

  private static EmployeeRow row(
      String name, int age, double salary, String amount, String hireDate, String remark) {
    EmployeeRow r = new EmployeeRow();
    r.setName(name);
    r.setAge(age);
    r.setSalary(salary);
    r.setAmount(new BigDecimal(amount));
    r.setHireDate(LocalDate.parse(hireDate));
    r.setRemark(remark);
    return r;
  }

  /** 生成员工测试文件（POI 原生写出，作为独立于被测写引擎的数据源） */
  private static File createEmployeeFile(File file, boolean withTitleRow) throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook();
        FileOutputStream fos = new FileOutputStream(file)) {
      Sheet sheet = wb.createSheet("员工");
      int rowIdx = 0;
      if (withTitleRow) {
        Row title = sheet.createRow(rowIdx++);
        title.createCell(0).setCellValue("员工清单（标题行）");
      }
      Row header = sheet.createRow(rowIdx++);
      for (int i = 0; i < HEADERS.length; i++) {
        header.createCell(i).setCellValue(HEADERS[i]);
      }
      for (Object[] data : DATA) {
        Row row = sheet.createRow(rowIdx++);
        for (int i = 0; i < data.length; i++) {
          if (data[i] == null) {
            continue;
          }
          Cell cell = row.createCell(i);
          if (data[i] instanceof Number) {
            cell.setCellValue(((Number) data[i]).doubleValue());
          } else {
            cell.setCellValue((String) data[i]);
          }
        }
      }
      wb.write(fos);
    }
    return file;
  }

  /** 生成数值型日期单元格文件（日期样式 + 数值存储，POI 路径可识别，fast 引擎为已知限制） */
  private static File createDateFormattedFile(File file) throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook();
        FileOutputStream fos = new FileOutputStream(file)) {
      Sheet sheet = wb.createSheet("日期");
      Row header = sheet.createRow(0);
      header.createCell(0).setCellValue("时间");
      Row row = sheet.createRow(1);
      Cell cell = row.createCell(0);
      CellStyle style = wb.createCellStyle();
      style.setDataFormat(wb.createDataFormat().getFormat("yyyy-mm-dd"));
      cell.setCellStyle(style);
      cell.setCellValue(LocalDate.of(2024, 6, 1));
      wb.write(fos);
    }
    return file;
  }

  /** 员工行 DTO — 覆盖字符串/整数/浮点/BigDecimal/日期（字符串格式）类型 */
  public static class EmployeeRow {
    @ExcelProperty("姓名")
    private String name;

    @ExcelProperty("年龄")
    private Integer age;

    @ExcelProperty("薪资")
    private Double salary;

    @ExcelProperty("金额")
    private BigDecimal amount;

    @ExcelProperty(value = "入职日期", dateFormat = "yyyy-MM-dd")
    private LocalDate hireDate;

    @ExcelProperty("备注")
    private String remark;

    @ExcelIgnore
    private String secret;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public Integer getAge() {
      return age;
    }

    public void setAge(Integer age) {
      this.age = age;
    }

    public Double getSalary() {
      return salary;
    }

    public void setSalary(Double salary) {
      this.salary = salary;
    }

    public BigDecimal getAmount() {
      return amount;
    }

    public void setAmount(BigDecimal amount) {
      this.amount = amount;
    }

    public LocalDate getHireDate() {
      return hireDate;
    }

    public void setHireDate(LocalDate hireDate) {
      this.hireDate = hireDate;
    }

    public String getRemark() {
      return remark;
    }

    public void setRemark(String remark) {
      this.remark = remark;
    }

    public String getSecret() {
      return secret;
    }

    public void setSecret(String secret) {
      this.secret = secret;
    }
  }

  /** 数值日期 DTO */
  public static class DateRow {
    @ExcelProperty("时间")
    private Date time;

    public Date getTime() {
      return time;
    }

    public void setTime(Date time) {
      this.time = time;
    }
  }
}
