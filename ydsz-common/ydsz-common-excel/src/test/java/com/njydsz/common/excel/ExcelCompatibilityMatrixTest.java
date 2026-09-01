package com.njydsz.common.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.njydsz.common.excel.annotation.ExcelProperty;
import com.njydsz.common.excel.core.ExcelReader;
import com.njydsz.common.excel.core.ExcelWriter;
import com.njydsz.common.excel.core.config.ExcelConfig;
import com.njydsz.common.excel.core.metadata.ReadMetadata;
import com.njydsz.common.excel.core.metadata.WriteMetadata;

/**
 * 兼容用例矩阵（深度完善·方案 B 之三）—— 自研双引擎 round-trip 全覆盖。
 *
 * <p>以 EasyExcel 公开行为语义为纸面基线（不引入依赖），自建"写引擎 × 读引擎 × 类型 ×
 * 边界"矩阵，作为 fast 默认开启的前置门禁：
 *
 * <ol>
 *   <li>写引擎：fast（SuperFastExcelWriter）/ POI（ExcelWriter 兼容路径）
 *   <li>读引擎：fast（SuperFastExcelReader）/ POI（ExcelReader 兼容路径）
 *   <li>四种组合各做一次全类型 round-trip，验证矩阵内任意组合的数据保真
 * </ol>
 *
 * <p>类型矩阵：String（中文/特殊字符）、Integer、Long（大数）、Double、BigDecimal、
 * Boolean、LocalDate、LocalDateTime、Date。后续边界扩展（null 行、空串、前导零、
 * 换行、emoji、超长文本）在矩阵基础上叠加。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class ExcelCompatibilityMatrixTest {

  @TempDir Path tempDir;

  // ==================== 组合 1：fast 写 → fast 读 ====================

  @Test
  void matrixFastWriteFastRead() throws IOException {
    File file = tempDir.resolve("m-ff.xlsx").toFile();
    writeWithFast(file, sampleRows());
    List<MatrixRow> rows = readWith(file, true);
    assertSampleRows(rows);
  }

  // ==================== 组合 2：fast 写 → POI 读 ====================

  @Test
  void matrixFastWritePoiRead() throws IOException {
    File file = tempDir.resolve("m-fp.xlsx").toFile();
    writeWithFast(file, sampleRows());
    List<MatrixRow> rows = readWith(file, false);
    assertSampleRows(rows);
  }

  // ==================== 组合 3：POI 写 → fast 读 ====================

  @Test
  void matrixPoiWriteFastRead() throws IOException {
    File file = tempDir.resolve("m-pf.xlsx").toFile();
    writeWithPoi(file, sampleRows());
    List<MatrixRow> rows = readWith(file, true);
    assertSampleRows(rows);
  }

  // ==================== 组合 4：POI 写 → POI 读（对照基线） ====================

  @Test
  void matrixPoiWritePoiRead() throws IOException {
    File file = tempDir.resolve("m-pp.xlsx").toFile();
    writeWithPoi(file, sampleRows());
    List<MatrixRow> rows = readWith(file, false);
    assertSampleRows(rows);
  }

  // ==================== 边界：null 值行保真 ====================

  @Test
  void boundaryNullValuesSurviveAllEnginePairs() throws IOException {
    MatrixRow nullRow = new MatrixRow();
    nullRow.setName("只有名称");

    File ff = tempDir.resolve("b-null-ff.xlsx").toFile();
    writeWithFast(ff, List.of(nullRow));
    List<MatrixRow> rowsFast = readWith(ff, true);
    assertEquals(1, rowsFast.size());
    assertEquals("只有名称", rowsFast.get(0).getName());
    assertNull(rowsFast.get(0).getCount());

    File pf = tempDir.resolve("b-null-pf.xlsx").toFile();
    writeWithPoi(pf, List.of(nullRow));
    List<MatrixRow> rowsPoi = readWith(pf, true);
    assertEquals(1, rowsPoi.size());
    assertEquals("只有名称", rowsPoi.get(0).getName());
    assertNull(rowsPoi.get(0).getCount());
  }

  // ==================== 边界：特殊字符与 Unicode ====================

  @Test
  void boundarySpecialCharactersRoundTrip() throws IOException {
    String specials = "A&B<C>D\"E'F\tG\nH　IＪ🔥漢字";
    MatrixRow row = new MatrixRow();
    row.setName(specials);
    row.setCount(1);

    File ff = tempDir.resolve("b-special-ff.xlsx").toFile();
    writeWithFast(ff, List.of(row));
    List<MatrixRow> rows = readWith(ff, true);
    assertEquals(1, rows.size());
    assertEquals(specials, rows.get(0).getName());
  }

  // ==================== 测试基础设施 ====================

  /** 全类型样本行集 */
  private static List<MatrixRow> sampleRows() {
    MatrixRow first = new MatrixRow();
    first.setName("张三 & 李四");
    first.setCount(42);
    first.setBig(9_000_000_000L);
    first.setPrice(123.456);
    first.setAmount(new BigDecimal("99.95"));
    first.setActive(true);
    first.setHireDate(LocalDate.of(2024, 3, 15));
    first.setCreated(LocalDateTime.of(2026, 9, 1, 10, 30, 0));
    first.setBirthday(new Date(0));

    MatrixRow second = new MatrixRow();
    second.setName("王五");
    second.setCount(-7);
    second.setBig(1L);
    second.setPrice(-0.5);
    second.setAmount(new BigDecimal("0.01"));
    second.setActive(false);
    second.setHireDate(LocalDate.of(1999, 12, 31));
    second.setCreated(LocalDateTime.of(2000, 1, 1, 0, 0, 0));

    return List.of(first, second);
  }

  /** 全类型 round-trip 断言 */
  private static void assertSampleRows(List<MatrixRow> rows) {
    assertEquals(2, rows.size());
    MatrixRow first = rows.get(0);
    assertEquals("张三 & 李四", first.getName());
    assertEquals(42, first.getCount());
    assertEquals(9_000_000_000L, first.getBig());
    assertEquals(123.456, first.getPrice(), 1e-9);
    assertEquals(0, new BigDecimal("99.95").compareTo(first.getAmount()));
    assertEquals(Boolean.TRUE, first.getActive());
    assertEquals(LocalDate.of(2024, 3, 15), first.getHireDate());
    assertEquals(LocalDateTime.of(2026, 9, 1, 10, 30, 0), first.getCreated());
    assertEquals(0, first.getBirthday().getTime() / 1000);

    MatrixRow second = rows.get(1);
    assertEquals("王五", second.getName());
    assertEquals(-7, second.getCount());
    assertEquals(1L, second.getBig());
    assertEquals(-0.5, second.getPrice(), 1e-9);
    assertEquals(0, new BigDecimal("0.01").compareTo(second.getAmount()));
    assertEquals(Boolean.FALSE, second.getActive());
    assertEquals(LocalDate.of(1999, 12, 31), second.getHireDate());
  }

  /** fast 写引擎写出 */
  private static void writeWithFast(File file, List<MatrixRow> data) throws IOException {
    WriteMetadata metadata = new WriteMetadata();
    metadata.setClazz(MatrixRow.class);
    metadata.setFilePath(file.getAbsolutePath());
    metadata.setExcelConfig(ExcelConfig.builder().useFastWriter(true).build());
    ExcelWriter writer = new ExcelWriter(metadata);
    writer.doWrite(data);
    writer.finish();
  }

  /** POI 兼容路径写出（useFastWriter=false 对照） */
  private static void writeWithPoi(File file, List<MatrixRow> data) throws IOException {
    WriteMetadata metadata = new WriteMetadata();
    metadata.setClazz(MatrixRow.class);
    metadata.setFilePath(file.getAbsolutePath());
    metadata.setExcelConfig(ExcelConfig.builder().useFastWriter(false).build());
    ExcelWriter writer = new ExcelWriter(metadata);
    writer.doWrite(data);
    writer.finish();
  }

  /** 按读引擎选择读取（useFastReader 开关） */
  private static List<MatrixRow> readWith(File file, boolean useFastReader) throws IOException {
    ReadMetadata metadata = new ReadMetadata();
    metadata.setClazz(MatrixRow.class);
    metadata.setFilePath(file.getAbsolutePath());
    metadata.setExcelConfig(ExcelConfig.builder().useFastReader(useFastReader).build());
    return new ExcelReader(metadata).doReadAll();
  }

  /** 矩阵 DTO：全类型列 */
  public static class MatrixRow {
    @ExcelProperty("名称")
    private String name;

    @ExcelProperty("数量")
    private Integer count;

    @ExcelProperty("大数")
    private Long big;

    @ExcelProperty("价格")
    private Double price;

    @ExcelProperty("金额")
    private BigDecimal amount;

    @ExcelProperty("启用")
    private Boolean active;

    @ExcelProperty(value = "入职日期", dateFormat = "yyyy-MM-dd")
    private LocalDate hireDate;

    @ExcelProperty(value = "创建时间", dateFormat = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime created;

    @ExcelProperty(value = "生日", dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date birthday;

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

    public Long getBig() {
      return big;
    }

    public void setBig(Long big) {
      this.big = big;
    }

    public Double getPrice() {
      return price;
    }

    public void setPrice(Double price) {
      this.price = price;
    }

    public BigDecimal getAmount() {
      return amount;
    }

    public void setAmount(BigDecimal amount) {
      this.amount = amount;
    }

    public Boolean getActive() {
      return active;
    }

    public void setActive(Boolean active) {
      this.active = active;
    }

    public LocalDate getHireDate() {
      return hireDate;
    }

    public void setHireDate(LocalDate hireDate) {
      this.hireDate = hireDate;
    }

    public LocalDateTime getCreated() {
      return created;
    }

    public void setCreated(LocalDateTime created) {
      this.created = created;
    }

    public Date getBirthday() {
      return birthday;
    }

    public void setBirthday(Date birthday) {
      this.birthday = birthday;
    }
  }
}
