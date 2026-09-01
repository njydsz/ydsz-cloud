package com.njydsz.common.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.njydsz.common.excel.annotation.ContentStyle;
import com.njydsz.common.excel.annotation.ExcelProperty;
import com.njydsz.common.excel.core.ExcelWriter;
import com.njydsz.common.excel.core.config.ExcelConfig;
import com.njydsz.common.excel.core.listener.WriteLifecycleHandler;
import com.njydsz.common.excel.core.metadata.WriteMetadata;

/**
 * 深度完善（方案 B）回归测试集 — fast 写引擎显式降级。
 *
 * <p>fast 写引擎（SuperFastExcelWriter）不触发 WriteLifecycleHandler 回调、不应用
 * 样式注解。此前该能力差异仅 javadoc 标注——用户在 fast 配置下注册回调或使用样式
 * 注解时被静默忽略。现在门面 {@code ExcelWriter.doWrite} 检测到这两类配置即自动
 * 回落 POI 路径（回调与样式全生效），消除静默失效。
 *
 * <p>断言策略：以"回调被实际触发 / 样式被实际写入文件"为证（POI 直读验证），而非
 * 仅断言走了哪个分支。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class FastWriterGracefulFallbackTest {

  @TempDir File tempDir;

  // ==================== 回调注册触发降级 ====================

  @Test
  void registeredLifecycleCallbackForcesPoiPathAndFires() throws IOException {
    File out = new File(tempDir, "callback.xlsx");
    AtomicInteger afterRowWrites = new AtomicInteger();
    AtomicInteger headerWrites = new AtomicInteger();

    WriteMetadata metadata = new WriteMetadata();
    metadata.setClazz(SimpleRow.class);
    metadata.setFilePath(out.getAbsolutePath());
    metadata.setExcelConfig(ExcelConfig.builder().useFastWriter(true).build());

    ExcelWriter writer = new ExcelWriter(metadata);
    writer.registerWriteHandler(
        new WriteLifecycleHandler() {
          @Override
          public void afterHeaderWrite(Sheet sheet, int headerRow) {
            headerWrites.incrementAndGet();
          }

          @Override
          public void afterRowWrite(Row row, Object rowData, int rowIndex) {
            afterRowWrites.incrementAndGet();
          }
        });
    List<SimpleRow> data = List.of(new SimpleRow("A"), new SimpleRow("B"));
    writer.doWrite(data);
    writer.finish();

    // fast 引擎不触发回调——回调计数 > 0 证明实际走了 POI 路径（降级生效）
    assertEquals(1, headerWrites.get());
    assertEquals(2, afterRowWrites.get());
    assertTrue(out.exists() && out.length() > 0);
  }

  // ==================== 样式注解触发降级 ====================

  @Test
  void styleAnnotatedDtoForcesPoiPathAndAppliesStyle() throws IOException {
    File out = new File(tempDir, "styled.xlsx");

    WriteMetadata metadata = new WriteMetadata();
    metadata.setClazz(StyledRow.class);
    metadata.setFilePath(out.getAbsolutePath());
    metadata.setExcelConfig(ExcelConfig.builder().useFastWriter(true).build());

    new ExcelWriter(metadata).doWrite(List.of(new StyledRow("加粗列"))).finish();

    // fast 引擎不写样式——POI 直读发现非默认加粗/字重即证明降级后样式生效
    try (Workbook wb = WorkbookFactory.create(out, null, true)) {
      Sheet sheet = wb.getSheetAt(0);
      Row header = sheet.getRow(0);
      Cell cell = header.getCell(0);
      boolean bold = cell.getCellStyle().getFont().getBold();
      assertTrue(bold, "样式注解在 fast 配置下应经 POI 降级路径生效（加粗）");
      assertEquals("名称", cell.getStringCellValue());
    }
  }

  // ==================== 无回调无样式仍走 fast（回归） ====================

  @Test
  void plainDtoStillUsesFastPath() throws IOException {
    // 无回调、无样式注解：fast 路径不受降级逻辑影响（正常写出 + 数据可读回）
    File out = new File(tempDir, "plain.xlsx");

    WriteMetadata metadata = new WriteMetadata();
    metadata.setClazz(SimpleRow.class);
    metadata.setFilePath(out.getAbsolutePath());
    metadata.setExcelConfig(ExcelConfig.builder().useFastWriter(true).build());

    new ExcelWriter(metadata).doWrite(List.of(new SimpleRow("A"), new SimpleRow("B"))).finish();

    try (Workbook wb = WorkbookFactory.create(out, null, true)) {
      Sheet sheet = wb.getSheetAt(0);
      assertEquals("名称", sheet.getRow(0).getCell(0).getStringCellValue());
      assertEquals("A", sheet.getRow(1).getCell(0).getStringCellValue());
      assertEquals("B", sheet.getRow(2).getCell(0).getStringCellValue());
    }
  }

  // ==================== DTO ====================

  /** 普通行 DTO（无样式注解，fast 路径可用） */
  public static class SimpleRow {
    @ExcelProperty("名称")
    private String name;

    public SimpleRow() {}

    public SimpleRow(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  /** 带样式注解的行 DTO（触发 POI 降级） */
  public static class StyledRow {
    @ExcelProperty(value = "名称")
    @ContentStyle(bold = true)
    private String name;

    public StyledRow() {}

    public StyledRow(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }
}
