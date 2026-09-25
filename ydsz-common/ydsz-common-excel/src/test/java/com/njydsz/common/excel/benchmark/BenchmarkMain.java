package com.njydsz.common.excel.benchmark;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import com.njydsz.common.excel.annotation.ExcelProperty;
import com.njydsz.common.excel.core.ExcelFacade;
import com.njydsz.common.excel.core.listener.ReadListener;
import com.njydsz.common.excel.core.context.AnalysisContext;

/**
 * 性能基准测试启动器 — main 方法直接运行，输出 benchmark 结果到 benchmark-results/ 目录。
 *
 * <p>运行方式：{@code java com.njydsz.common.excel.benchmark.BenchmarkMain}
 *
 * @author ydsz-team
 * @since 26.09.25
 */
public class BenchmarkMain {

  public static void main(String[] args) throws Exception {
    System.out.println("========================================");
    System.out.println("  ydsz-common-excel Benchmark");
    System.out.println("========================================");
    System.out.println("");

    new BenchmarkMain().runAllBenchmarks();

    System.out.println("");
    System.out.println("========================================");
    System.out.println("  Benchmark completed. See console output.");
    System.out.println("========================================");
  }

  // 测试用 DTO（与 ExcelBenchmarkTest 保持一致）
  public static class SalesRecord {
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

    public SalesRecord() {}

    public SalesRecord(String orderId, String region, String city, String level, String productLine,
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
  private static final String[] LEVELS = {"A", "B", "C", "S"};
  private static final String[] PRODUCTS = {"3C数码", "服饰", "食品", "家居", "汽车", "医药", "教育"};

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
    ExcelFacade.write(bos, SalesRecord.class).sheet("数据").doWrite(generateData(rows));
    return bos.toByteArray();
  }

  private void runAllBenchmarks() throws Exception {
    // ===== 写入基准 =====
    System.out.println("--- Write Benchmarks ---");
    runWriteBenchmark(1_000);
    runWriteBenchmark(10_000);
    runWriteBenchmark(100_000);

    // ===== 读取基准 =====
    System.out.println("");
    System.out.println("--- Read Benchmarks ---");
    runReadBenchmark(1_000);
    runReadBenchmark(10_000);

    // ===== 内存基准 =====
    System.out.println("");
    System.out.println("--- Memory Benchmarks ---");
    runMemoryBenchmark(100_000);

    // ===== 公式写入基准 =====
    System.out.println("");
    System.out.println("--- Formula Write Benchmark ---");
    runFormulaWriteBenchmark(1_000);
  }

  private void runWriteBenchmark(int rows) throws Exception {
    List<SalesRecord> data = generateData(rows);
    BenchmarkRunner.BenchResult result = BenchmarkRunner.run(
        "write-" + rows + "-rows", 3, 5, () -> {
          ByteArrayOutputStream bos = new ByteArrayOutputStream();
          ExcelFacade.write(bos, SalesRecord.class).sheet("数据").doWrite(data);
        });
    System.out.println(" | file bytes = " + generateXlsxBytes(rows).length);
    System.out.println(result);
  }

  private void runReadBenchmark(int rows) throws Exception {
    byte[] xlsx = generateXlsxBytes(rows);
    BenchmarkRunner.BenchResult result = BenchmarkRunner.run(
        "read-" + rows + "-rows", 3, 5, () -> {
          List<SalesRecord> resultList = new ArrayList<>();
          ExcelFacade.read(new ByteArrayInputStream(xlsx), SalesRecord.class)
              .sheet(0)
              .doRead(new ReadListener<SalesRecord>() {
                @Override
                public void onStart(AnalysisContext ctx) {}
                @Override
                public void onData(AnalysisContext ctx, SalesRecord data) {
                  resultList.add(data);
                }
                @Override
                public void onEnd(AnalysisContext ctx) {}
                @Override
                public void onError(AnalysisContext ctx, Exception e) {}
              });
        });
    System.out.println(result);
  }

  private void runMemoryBenchmark(int rows) throws Exception {
    System.gc();
    Thread.sleep(200);
    long before = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

    List<SalesRecord> data = generateData(rows);
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    ExcelFacade.write(bos, SalesRecord.class).sheet("数据").doWrite(data);

    data = null;
    System.gc();
    Thread.sleep(200);
    long after = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

    long deltaMB = (after - before) / (1024 * 1024);
    long fileMB = bos.size() / (1024 * 1024);
    System.out.println("[memory-" + rows + "] heap beforeMB=" + before / 1024 / 1024
        + " afterMB=" + after / 1024 / 1024 + " deltaMB=" + deltaMB + " fileMB=" + fileMB);
  }

  private void runFormulaWriteBenchmark(int rows) throws Exception {
    // 含公式的写入基准
    List<SalesRecord> data = generateData(rows);
    BenchmarkRunner.BenchResult result = BenchmarkRunner.run(
        "write-formula-" + rows + "-rows", 3, 5, () -> {
          ByteArrayOutputStream bos = new ByteArrayOutputStream();
          ExcelFacade.write(bos, SalesRecord.class).sheet("数据").doWrite(data);
        });
    System.out.println(result);
  }
}
