package com.njydsz.common.excel.benchmark;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.njydsz.common.excel.annotation.ExcelProperty;
import com.njydsz.common.excel.core.ExcelFacade;
import com.njydsz.common.excel.core.listener.ReadListener;
import com.njydsz.common.excel.core.context.AnalysisContext;

/**
 * Excel 读写性能基准测试。
 *
 * <p>注意：涉及 IO + 内存统计的基准测试需要较长的运行时间，默认标记为 {@link Disabled}。
 * 需手动启用。
 *
 * <h3>测试环境要求</h3>
 *
 * <ul>
 *   <li>Java 21+、JDK 启用 {@code --enable-preview}（仅部分 API 需要，可不启用）</li>
 *   <li>独立环境运行，避免 CI 超时（无时间限制时优先 full run）</li>
 * </ul>
 *
 * <h3>竞品对标</h3>
 *
 * <ul>
 *   <li><b>EasyExcel</b>：阿里开源的基于 SAX 流式读取框架（核心卖点是低内存）</li>
 *   <li><b>Apache POI SXSSF</b>: 滑动窗口式写大文件</li>
 *   <li><b>Apache POI XSSF</b>: DOM 方式全量加载（内存峰值高）</li>
 *   <li><b>本引擎（SuperFast）</b>: 字节流直接 output + MethodHandle 字段访问</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.25
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Disabled("手动启用 — 基准测试耗时较长")
class ExcelBenchmarkTest {

  // ==================== 测试用 DTO ====================
  static class SalesRecord {
    @ExcelProperty(value = "订单ID", index = 0)
    String orderId;

    @ExcelProperty(value = "地区", index = 1)
    String region;

    @ExcelProperty(value = "城市", index = 2)
    String city;

    @ExcelProperty(value = "客户等级", index = 3)
    String level;

    @ExcelProperty(value = "产品线", index = 4)
    String productLine;

    @ExcelProperty(value = "数量", index = 5)
    Integer qty;

    @ExcelProperty(value = "单价", index = 6)
    Double unitPrice;

    @ExcelProperty(value = "金额", index = 7)
    Double amount;

    @ExcelProperty(value = "日期", index = 8)
    String date;

    SalesRecord() {}

    SalesRecord(String orderId, String region, String city, String level, String productLine,
        Integer qty, Double unitPrice, Double amount, String date) {
      this.orderId = orderId;
      this.region = region;
      this.city = city;
      this.level = level;
      this.productLine = productLine;
      this.qty = qty;
      this.unitPrice = unitPrice;
      this.amount = amount;
      this.date = date;
    }
  }

  private static final String[] REGIONS = {"华东", "华南", "华北", "华中", "西南", "西北", "东北"};
  private static final String[] CITIES = {"上海", "杭州", "南京", "广州", "深圳", "北京", "成都", "武汉", "西安", "沈阳"};
  private static final String[] LEVELS = {"A", "B", "C", ""};
  private static final String[] PRODUCTS = {"3C数码", "服饰", "食品", "家居", "汽车", "医药", "教育"};

  // ==================== 数据准备 ====================

  private List<SalesRecord> generateData(int rows) {
    List<SalesRecord> list = new ArrayList<>(rows);
    for (int i = 0; i < rows; i++) {
      list.add(new SalesRecord(
          "ORD-" + (100000 + i),
          REGIONS[i % REGIONS.length],
          CITIES[i % CITIES.length],
          LEVELS[i % LEVELS.length],
          PRODUCTS[i % PRODUCTS.length],
          1 + (i % 100),
          10.0 + (i % 1000),
          (1 + (i % 100)) * (10.0 + (i % 1000)),
          "2026-09-" + (1 + (i % 28))
      ));
    }
    return list;
  }

  private byte[] generateXlsxBytes(int rows) throws Exception {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    ExcelFacade.write(bos, SalesRecord.class)
        .sheet("数据")
        .doWrite(generateData(rows));
    return bos.toByteArray();
  }

  // ==================== 写入基准测试 ====================

  @Test
  @DisplayName("写入基准 - 1000 行")
  void benchmark_write_1k() throws Exception {
    List<SalesRecord> data = generateData(1000);
    BenchmarkRunner.BenchResult result = BenchmarkRunner.run(
        "write-1k-rows", 3, 10, () -> {
          ByteArrayOutputStream bos = new ByteArrayOutputStream();
          ExcelFacade.write(bos, SalesRecord.class)
              .sheet("数据")
              .doWrite(data);
          assertNotNull(bos);
        });
    System.out.println(" | output bytes/sample = " + (generateXlsxBytes(1000).length));
  }

  @Test
  @DisplayName("写入基准 - 10000 行")
  void benchmark_write_10k() throws Exception {
    List<SalesRecord> data = generateData(10000);
    BenchmarkRunner.BenchResult result = BenchmarkRunner.run(
        "write-10k-rows", 3, 5, () -> {
          ByteArrayOutputStream bos = new ByteArrayOutputStream();
          ExcelFacade.write(bos, SalesRecord.class)
              .sheet("数据")
              .doWrite(data);
          assertNotNull(bos);
        });
    System.out.println(" | output bytes/sample = " + (generateXlsxBytes(10000).length));
  }

  @Test
  @DisplayName("写入基准 - 100000 行")
  void benchmark_write_100k() throws Exception {
    List<SalesRecord> data = generateData(100000);
    BenchmarkRunner.run("write-100k-rows", 1, 3, () -> {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      ExcelFacade.write(bos, SalesRecord.class)
          .sheet("数据")
          .doWrite(data);
    });
  }

  // ==================== 读取基准测试 ====================

  @Test
  @DisplayName("读取基准 - 1000 行")
  void benchmark_read_1k() throws Exception {
    byte[] xlsx = generateXlsxBytes(1000);
    BenchmarkRunner.run("read-1k-rows", 3, 10, () -> {
      List<SalesRecord> result = new ArrayList<>();
      ExcelFacade.read(new ByteArrayInputStream(xlsx), SalesRecord.class)
          .sheet(0)
          .doRead(new ReadListener<SalesRecord>() {
            @Override
            public void onData(AnalysisContext ctx, SalesRecord data) {
              result.add(data);
            }
            @Override
            public void onEnd(AnalysisContext ctx) {}
            @Override
            public void onError(AnalysisContext ctx, Exception e) {}
          });
      assertNotNull(result);
    });
  }

  @Test
  @DisplayName("读取基准 - 10000 行")
  void benchmark_read_10k() throws Exception {
    byte[] xlsx = generateXlsxBytes(10000);
    BenchmarkRunner.run("read-10k-rows", 3, 5, () -> {
      List<SalesRecord> result = new ArrayList<>();
      ExcelFacade.read(new ByteArrayInputStream(xlsx), SalesRecord.class)
          .sheet(0)
          .doRead(new ReadListener<SalesRecord>() {
            @Override
            public void onData(AnalysisContext ctx, SalesRecord data) {
              result.add(data);
            }
            @Override
            public void onEnd(AnalysisContext ctx) {}
            @Override
            public void onError(AnalysisContext ctx, Exception e) {}
          });
      assertNotNull(result);
    });
  }

  // ==================== 内存基准测试 ====================

  @Test
  @DisplayName("内存占用 - 写 100k 行后检查 RSS")
  void benchmark_memory_100k_write() throws Exception {
    System.gc();
    Thread.sleep(200);
    long before = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

    List<SalesRecord> data = generateData(100000);
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    ExcelFacade.write(bos, SalesRecord.class)
        .sheet("数据")
        .doWrite(data);

    data = null; // 释放引用
    System.gc();
    Thread.sleep(200);
    long after = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

    long delta = (after - before) / (1024 * 1024);
    System.out.println("[memory-100k-write] beforeMB=" + before / 1024 / 1024
        + " afterMB=" + after / 1024 / 1024 + " deltaMB=" + delta);
    System.out.println("[memory-100k-write] fileMB=" + bos.size() / 1024 / 1024);
  }
}
