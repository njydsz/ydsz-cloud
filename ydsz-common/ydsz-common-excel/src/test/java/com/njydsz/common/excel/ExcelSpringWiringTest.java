package com.njydsz.common.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.njydsz.common.excel.annotation.ExcelProperty;
import com.njydsz.common.excel.api.validator.DataValidator.ValidationMode;
import com.njydsz.common.excel.core.config.ExcelConfig;
import com.njydsz.common.excel.helper.ExcelExportHelper;
import com.njydsz.common.excel.spring.ExcelAutoConfiguration;
import com.njydsz.common.excel.spring.ExcelHealthIndicator;
import com.njydsz.common.excel.spring.ExcelTemplate;

/**
 * P1-2 回归测试集 — Spring 配置接线端到端验证。
 *
 * <p>修复前四处断线：
 *
 * <ol>
 *   <li>ExcelTemplate 持有 config 但委托 ExcelFacade 静态门面（恒为 defaults）
 *   <li>ExcelExportHelper 无 config 入口（恒为 defaults）
 *   <li>ExcelWriter 的 ValueFormatter 构造时固化 defaults，链式 config() 不重建
 *   <li>ExcelHealthIndicator 从未注册为 Bean
 * </ol>
 *
 * <p>断言策略：以"配置属性 → 行为差异"为证（属性可观测地改变输出文件）， 而非仅断言 Bean 存在——防止接线修复只到
 * Bean 层未到执行层。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class ExcelSpringWiringTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ExcelAutoConfiguration.class));

  // ==================== 配置属性 → ExcelConfig Bean ====================

  @Test
  void propertiesBindToExcelConfigBean() {
    runner.withPropertyValues(
            "ydsz.excel.default-date-format=yyyy/MM/dd",
            "ydsz.excel.max-read-file-size-mb=7",
            "ydsz.excel.use-fast-reader=true")
        .run(
            context -> {
              org.assertj.core.api.Assertions.assertThat(context)
                  .hasSingleBean(ExcelConfig.class);
              ExcelConfig config = context.getBean(ExcelConfig.class);
              assertEquals("yyyy/MM/dd", config.getDefaultDateFormat());
              assertEquals(7, config.getMaxReadFileSizeMB());
              assertEquals(true, config.isUseFastReader());
            });
  }

  @Test
  void reservedPropertiesBindToExcelConfigBean() {
    // P2-12 回归：use1904-windowing / validation-mode / max-read-cache-size 三个预留配置
    // 此前 ExcelProperties 未声明，配置了不生效
    runner.withPropertyValues(
            "ydsz.excel.use-1904-windowing=true",
            "ydsz.excel.validation-mode=COLLECT_ALL",
            "ydsz.excel.max-read-cache-size=2048")
        .run(
            context -> {
              ExcelConfig config = context.getBean(ExcelConfig.class);
              assertEquals(true, config.isUse1904Windowing());
              assertEquals(ValidationMode.COLLECT_ALL, config.getValidationMode());
              assertEquals(2048, config.getMaxReadCacheSize());
            });
  }

  // ==================== ExcelTemplate 写路径接线（属性可观测） ====================

  @Test
  void templateWriteAppliesWiredDateFormatAndDefaultProtection() {
    // default-date-format 覆盖为 yyyy/MM/dd；公式注入防护保持默认开启
    runner.withPropertyValues("ydsz.excel.default-date-format=yyyy/MM/dd")
        .run(
            context -> {
              ExcelTemplate template = context.getBean(ExcelTemplate.class);
              ByteArrayOutputStream out = new ByteArrayOutputStream();
              template.write(
                  out,
                  WireRow.class,
                  List.of(new WireRow(LocalDateTime.of(2024, 6, 1, 10, 30), "=1+1")));

              try (XSSFWorkbook workbook =
                  new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
                Sheet sheet = workbook.getSheetAt(0);
                Row dataRow = sheet.getRow(1);
                // 日期字段无注解 dateFormat → 应回退到配置的 default-date-format（修复前恒为默认 yyyy-MM-dd HH:mm:ss）
                assertEquals("2024/06/01", dataRow.getCell(0).getStringCellValue());
                // 防护默认开启 → XLSX 空格前缀（修复前 ValueFormatter 恒用 defaults，本条恰好相同，交叉验证见下一用例）
                assertEquals(" =1+1", dataRow.getCell(1).getStringCellValue());
              }
            });
  }

  // ==================== ExcelExportHelper 接线（关闭防护的行为差异为证） ====================

  @Test
  void exportHelperAppliesWiredFormulaInjectionSwitch() {
    // 显式关闭公式注入防护 → 输出应为原始 "=1+1"（无空格前缀）
    // 修复前：ExcelExportHelper 不接收 config，ValueFormatter 恒用 defaults（防护恒开），此断言必失败
    runner.withPropertyValues("ydsz.excel.formula-injection-protection=false")
        .run(
            context -> {
              ExcelExportHelper helper = context.getBean(ExcelExportHelper.class);
              byte[] bytes =
                  helper.export(
                      WireRow.class,
                      List.of(new WireRow(LocalDateTime.of(2024, 6, 1, 10, 30), "=1+1")));

              try (XSSFWorkbook workbook =
                  new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                Sheet sheet = workbook.getSheetAt(0);
                Row dataRow = sheet.getRow(1);
                assertEquals("=1+1", dataRow.getCell(1).getStringCellValue());
              }
            });
  }

  @Test
  void exportHelperDynamicExportKeepsHeaderAtFirstRow() {
    // 动态导出（自定义表头）：headRowNumber 0→1 语义修正后，表头仍应在第一行、数据从第二行起
    runner.run(
        context -> {
          ExcelExportHelper helper = context.getBean(ExcelExportHelper.class);
          byte[] bytes =
              helper.export(
                  "动态表", List.of("列A", "列B"), List.of(List.of("=1+1", "x")));

          try (XSSFWorkbook workbook =
              new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("列A", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals(" =1+1", sheet.getRow(1).getCell(0).getStringCellValue());
          }
        });
  }

  // ==================== 健康检查指示器注册 ====================

  @Test
  void healthIndicatorRegisteredAndReportsUpWithDetails() {
    runner.withPropertyValues("ydsz.excel.use-fast-reader=true")
        .run(
            context -> {
              org.assertj.core.api.Assertions.assertThat(context)
                  .hasSingleBean(ExcelHealthIndicator.class);
              ExcelHealthIndicator indicator = context.getBean(ExcelHealthIndicator.class);
              assertNotNull(indicator.health());
              assertEquals("UP", indicator.health().getStatus().getCode());
              assertEquals(true, indicator.health().getDetails().get("fastReader"));
              assertEquals(true, indicator.health().getDetails().containsKey("maxReadMb"));
              // P2：真实探测明细——临时目录可写（fast 引擎流式解析/落盘的硬依赖）
              assertEquals(true, indicator.health().getDetails().get("tempDirWritable"));
              assertEquals(true, indicator.health().getDetails().containsKey("tempDir"));
            });
  }

  /** 接线验证 DTO：LocalDateTime（无注解 dateFormat，回退全局配置）+ 危险前缀字符串 */
  public static class WireRow {

    @ExcelProperty("时间")
    private LocalDateTime time;

    @ExcelProperty("备注")
    private String remark;

    public WireRow() {}

    public WireRow(LocalDateTime time, String remark) {
      this.time = time;
      this.remark = remark;
    }

    public LocalDateTime getTime() {
      return time;
    }

    public void setTime(LocalDateTime time) {
      this.time = time;
    }

    public String getRemark() {
      return remark;
    }

    public void setRemark(String remark) {
      this.remark = remark;
    }
  }
}
