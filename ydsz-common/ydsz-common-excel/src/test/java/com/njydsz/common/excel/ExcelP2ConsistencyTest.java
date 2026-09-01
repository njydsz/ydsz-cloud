package com.njydsz.common.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.njydsz.common.excel.annotation.ExcelProperty;
import com.njydsz.common.excel.core.ExcelWriter;
import com.njydsz.common.excel.core.config.ExcelConfig;
import com.njydsz.common.excel.core.listener.WriteHandler;
import com.njydsz.common.excel.core.metadata.WriteMetadata;
import com.njydsz.common.excel.csv.DefaultAnnotationRowMapper;

/**
 * P2-12 回归测试集 — 库内一致性修复。
 *
 * <p>覆盖六项修复的行为回归：
 *
 * <ol>
 *   <li>WriteHandler.applyDataValidation 按 validationType 分派（此前一律公式列表约束，
 *       下拉失效、数值/日期验证全部退化）
 *   <li>writeBatch 注入防护与 doWrite 对齐 + 独立调用可初始化（此前 NPE）
 *   <li>DefaultAnnotationRowMapper 尊重 @ExcelProperty.dateFormat（此前一律 ISO 格式）；
 *       Date 不可解析时抛带上下文异常（此前静默兜底 epoch 毫秒产出错误数据）
 *   <li>ExcelProperties 补绑定 use1904Windowing / validationMode / maxReadCacheSize
 *       （由 ExcelSpringWiringTest 覆盖）
 *   <li>fast 写路径回调限制 / ASMFieldAccessor 命名澄清（javadoc 标注，无行为断言）
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class ExcelP2ConsistencyTest {

  @TempDir File tempDir;

  // ==================== WriteHandler.applyDataValidation 分派 ====================

  @Test
  void listValidationProducesExplicitListConstraint() throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet sheet = wb.createSheet("S");
      // P2-12 回归：此前 createListValidation 的候选值被当公式（下拉失效）
      WriteHandler.applyDataValidation(
          sheet, 1, 10, 0, 0, WriteHandler.createListValidation("是,否,待定"));

      for (DataValidationConstraint constraint : readBackConstraints(wb)) {
        assertEquals(
            DataValidationConstraint.ValidationType.LIST, constraint.getValidationType());
        assertTrue(constraint.getFormula1().contains("是"));
        assertTrue(constraint.getFormula1().contains("待定"));
      }
    }
  }

  @Test
  void numberBetweenValidationProducesDecimalConstraint() throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet sheet = wb.createSheet("S");
      // P2-12 回归：此前 DECIMAL 类型也被构造为公式列表约束（类型 + 公式全错）
      WriteHandler.applyDataValidation(
          sheet, 1, 10, 1, 1, WriteHandler.createNumberBetweenValidation(0, 100));

      List<DataValidationConstraint> constraints = readBackConstraints(wb);
      assertEquals(1, constraints.size());
      DataValidationConstraint constraint = constraints.get(0);
      assertEquals(
          DataValidationConstraint.ValidationType.DECIMAL, constraint.getValidationType());
      // POI 数值约束规范化为 double 字面量（0 → "0.0"）
      assertEquals(0.0, Double.parseDouble(constraint.getFormula1()), 1e-9);
      assertEquals(100.0, Double.parseDouble(constraint.getFormula2()), 1e-9);
    }
  }

  /** 内存 round-trip：写出再读回，取 Sheet 上的数据验证约束（XML 序列化后仍成立才算数）。 */
  private static List<DataValidationConstraint> readBackConstraints(XSSFWorkbook wb)
      throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    wb.write(baos);
    try (XSSFWorkbook reread =
        new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
      XSSFSheet sheet = (XSSFSheet) reread.getSheetAt(0);
      List<DataValidationConstraint> result = new ArrayList<>();
      sheet
          .getDataValidations()
          .forEach(dv -> result.add(dv.getValidationConstraint()));
      return result;
    }
  }

  // ==================== writeBatch 防护对齐 + 独立调用 ====================

  @Test
  void writeBatchStandaloneWorksAndAppliesInjectionSanitization() throws Exception {
    File out = new File(tempDir, "batch.xlsx");
    WriteMetadata metadata = new WriteMetadata();
    metadata.setClazz(BatchRow.class);
    metadata.setFilePath(out.getAbsolutePath());
    metadata.setExcelConfig(ExcelConfig.defaults());

    // P2-12 回归①：独立调用 writeBatch（未经 doWrite）此前 NPE——sheet 未初始化
    // P2-12 回归②：注入防护与 doWrite 对齐（typed 路径经 UltraFastCellWriter 消毒）
    ExcelWriter writer = new ExcelWriter(metadata);
    List<BatchRow> rows = new ArrayList<>();
    rows.add(new BatchRow("=1+1"));
    rows.add(new BatchRow("普通值"));
    writer.sheet("批量").writeBatch(rows);
    writer.finish();

    // 用 POI 直读（ExcelFacade.read 默认 automaticTrim=true 会裁掉消毒前导空格，无法观测）
    try (XSSFWorkbook wb = new XSSFWorkbook(out)) {
      Sheet sheet = wb.getSheetAt(0);
      // 表头在首行（ensureInitializedForBatch 已写表头），数据从第二行起
      assertEquals("备注", sheet.getRow(0).getCell(0).getStringCellValue());
      // XLSX 路径消毒：前导空格阻断"另存 CSV 二次求值"链
      assertEquals(" =1+1", sheet.getRow(1).getCell(0).getStringCellValue());
      assertEquals("普通值", sheet.getRow(2).getCell(0).getStringCellValue());
    }
  }

  // ==================== DefaultAnnotationRowMapper ====================

  @Test
  void rowMapperRespectsDateFormatInBothDirections() {
    DefaultAnnotationRowMapper<MapperRow> mapper =
        new DefaultAnnotationRowMapper<>(MapperRow.class);

    MapperRow row =
        mapper.toRow(new String[] {"2024/03/15", "2024/03/15 08:30", "2024-06-01 12:00:00"});
    assertEquals(LocalDate.of(2024, 3, 15), row.getDate());
    assertEquals(LocalDateTime.of(2024, 3, 15, 8, 30), row.getDateTime());
    assertNotNull(row.getBirthday());

    // fromRow 按同一 dateFormat 输出（此前一律 toString 的 ISO 格式）
    String[] out = mapper.fromRow(row);
    assertEquals("2024/03/15", out[0]);
    assertEquals("2024/03/15 08:30", out[1]);
    assertEquals("2024-06-01 12:00:00", out[2]);
  }

  @Test
  void rowMapperUnparsableDateFailsFastInsteadOfEpochFallback() {
    DefaultAnnotationRowMapper<MapperRow> mapper =
        new DefaultAnnotationRowMapper<>(MapperRow.class);

    // P2-12 回归：此前 "not-a-date" 走 Long.parseLong → NumberFormatException 被吞后
    // 或纯数字日期串被静默解析为 epoch（产出 1970 附近的错误数据）
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> mapper.toRow(new String[] {"2024/03/15", "2024/03/15 08:30", "not-a-date"}));
    assertTrue(ex.getMessage().contains("not-a-date"));
  }

  @Test
  void rowMapperNumericEpochMillisStillAcceptedForDate() {
    DefaultAnnotationRowMapper<MapperRow> mapper =
        new DefaultAnnotationRowMapper<>(MapperRow.class);

    // 纯数字字符串按 epoch 毫秒解释（保留原语义）
    MapperRow row =
        mapper.toRow(new String[] {"2024/03/15", "2024/03/15 08:30", "1717200000000"});
    assertEquals(1717200000000L, row.getBirthday().getTime());
  }

  // ==================== 测试 DTO ====================

  /** writeBatch 用 DTO */
  public static class BatchRow {
    @ExcelProperty("备注")
    private String remark;

    public BatchRow() {}

    public BatchRow(String remark) {
      this.remark = remark;
    }

    public String getRemark() {
      return remark;
    }

    public void setRemark(String remark) {
      this.remark = remark;
    }
  }

  /** RowMapper 用 DTO：dateFormat 覆盖 LocalDate/LocalDateTime，Date 用默认格式 */
  public static class MapperRow {
    @ExcelProperty(value = "日期", order = 1, dateFormat = "yyyy/MM/dd")
    private LocalDate date;

    @ExcelProperty(value = "时间", order = 2, dateFormat = "yyyy/MM/dd HH:mm")
    private LocalDateTime dateTime;

    @ExcelProperty(value = "生日", order = 3)
    private Date birthday;

    public LocalDate getDate() {
      return date;
    }

    public void setDate(LocalDate date) {
      this.date = date;
    }

    public LocalDateTime getDateTime() {
      return dateTime;
    }

    public void setDateTime(LocalDateTime dateTime) {
      this.dateTime = dateTime;
    }

    public Date getBirthday() {
      return birthday;
    }

    public void setBirthday(Date birthday) {
      this.birthday = birthday;
    }
  }
}
