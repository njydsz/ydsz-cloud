package com.njydsz.common.excel.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.njydsz.common.excel.core.context.AnalysisContext;
import com.njydsz.common.excel.core.listener.ReadListener;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ExcelReader} 批量模式测试（26.11.06 性能优化新增能力）。
 *
 * <p><b>背景</b>：历史实现 {@code doReadAll()} 全量读入内存，10 万行 ≈ 300-500MB 堆占用。
 * 新增 {@code batchSize(N)} 后，读取器按批回调 {@code onBatchData}，单批峰值内存可控。
 * 本次优化同时补齐了 {@code installBatchListenerIfEnabled}——历史 {@code batchSize} 字段
 * 是死代码（{@code batchBuffer} 从不填充，{@code onBatchData} 永不触发）。
 *
 * <p>本测试覆盖批量模式的核心分支：
 *
 * <ul>
 *   <li>开启 batchSize → 数据按批回调，批大小符合配置，尾部不足一批的数据由 onEnd 冲刷
 *   <li>未开启 batchSize（=0）→ 保持单行 onData 语义（向后兼容）
 *   <li>batchSize 大于总行数 → 单批回调
 * </ul>
 *
 * <p>测试数据用 POI 生成标准 xlsx（与 EasyExcel 生成的文件在读取器侧行为一致）。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
class ExcelReaderBatchModeTest {

  /** 测试用 VO（EasyExcel 反射映射）。 */
  public static class Row {

    private String name;

    private Integer age;

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
  }

  @Test
  @DisplayName("开启 batchSize：数据按批回调且批大小符合配置")
  void batchModeShouldCallbackInBatches() {
    List<Integer> batchSizes = new ArrayList<>();
    List<Integer> singleRows = new ArrayList<>();

    ExcelFacade.read(new ByteArrayInputStream(buildXlsx(7)), Row.class)
        .sheet(0)
        .headRowNumber(1)
        .batchSize(3)
        .doRead(
            listener(batchSizes, singleRows));

    assertEquals(List.of(3, 3, 1), batchSizes, "7 行按 batchSize=3 应分为 3 批：3+3+1");
    assertTrue(singleRows.isEmpty(), "批量模式下 onData 不应被调用");
  }

  @Test
  @DisplayName("未开启 batchSize：保持单行 onData 语义（向后兼容）")
  void withoutBatchSizeShouldUseSingleRowCallback() {
    List<Integer> batchSizes = new ArrayList<>();
    List<Integer> singleRows = new ArrayList<>();

    ExcelFacade.read(new ByteArrayInputStream(buildXlsx(4)), Row.class)
        .sheet(0)
        .headRowNumber(1)
        .doRead(listener(batchSizes, singleRows));

    assertEquals(4, singleRows.size(), "未开启批量模式应逐行回调 4 次");
    assertTrue(batchSizes.isEmpty(), "未开启批量模式不应触发 onBatchData");
  }

  @Test
  @DisplayName("batchSize 大于总行数：单批回调全部数据")
  void batchSizeLargerThanRowsShouldCallbackOnce() {
    List<Integer> batchSizes = new ArrayList<>();

    ExcelFacade.read(new ByteArrayInputStream(buildXlsx(5)), Row.class)
        .sheet(0)
        .headRowNumber(1)
        .batchSize(100)
        .doRead(listener(batchSizes, new ArrayList<>()));

    assertEquals(List.of(5), batchSizes, "5 行 + batchSize=100 应为单批 5 行");
  }

  /**
   * 构造同时记录单行回调与批量回调的监听器。
   *
   * @param batchSizes 批量回调大小收集器
   * @param singleRows 单行回调计数收集器
   * @return 监听器
   */
  private ReadListener<Row> listener(List<Integer> batchSizes, List<Integer> singleRows) {
    return new ReadListener<Row>() {
      @Override
      public void onStart(AnalysisContext context) {}

      @Override
      public void onData(AnalysisContext context, Row data) {
        singleRows.add(1);
      }

      @Override
      public void onEnd(AnalysisContext context) {}

      @Override
      public void onBatchData(AnalysisContext context, List<Row> batch) {
        batchSizes.add(batch.size());
      }
    };
  }

  /**
   * 生成含指定数据行数的 xlsx 字节流（1 行表头 + N 行数据）。
   *
   * @param dataRows 数据行数
   * @return xlsx 字节流
   */
  private byte[] buildXlsx(int dataRows) {
    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet("sheet1");
      org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
      header.createCell(0).setCellValue("name");
      header.createCell(1).setCellValue("age");
      for (int i = 0; i < dataRows; i++) {
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(i + 1);
        row.createCell(0).setCellValue("user-" + i);
        row.createCell(1).setCellValue(20 + i);
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      workbook.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("生成测试 xlsx 失败", e);
    }
  }
}
