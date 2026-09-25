package com.njydsz.common.excel.benchmark;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;

import com.njydsz.common.excel.core.ExcelFacade;

/**
 * 真实竞品对比 Benchmark — 在同数据集、同迭代策略下，同时运行 YdszExcel (自研)、
 * EasyExcel 4.x、Apache POI XSSF、Apache POI SXSSF 四个引擎。
 *
 * <p>测试输出直接打印到控制台，可重定向到文件：
 * {@code mvn test-compile exec:java ... > benchmark-results/benchmark-raw.txt}
 *
 * <h3>执行步骤</h3>
 * <ol>
 *   <li>预热：3 次（不计入统计）</li>
 *   <li>测量：5 次（1k/10k）或 3 次（100k）</li>
 *   <li>强制 GC 后记录堆内存</li>
 *   <li>按 avg/p50/p95/p99 / ops/s / rssDeltaMB 统计</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 26.09.25
 */
public class CompetitiveBenchmark {

  // ==================== 通用测试用 DTO ====================

  /** 自研引擎用 DTO — 标注本模块 @ExcelProperty */
  public static class SuperFastRecord {
    @com.njydsz.common.excel.annotation.ExcelProperty(value = "订单ID", index = 0)
    public String orderId;
    @com.njydsz.common.excel.annotation.ExcelProperty(value = "地区", index = 1)
    public String region;
    @com.njydsz.common.excel.annotation.ExcelProperty(value = "城市", index = 2)
    public String city;
    @com.njydsz.common.excel.annotation.ExcelProperty(value = "等级", index = 3)
    public String level;
    @com.njydsz.common.excel.annotation.ExcelProperty(value = "产品线", index = 4)
    public String productLine;
    @com.njydsz.common.excel.annotation.ExcelProperty(value = "数量", index = 5)
    public Integer qty;
    @com.njydsz.common.excel.annotation.ExcelProperty(value = "单价", index = 6)
    public Double unitPrice;
    @com.njydsz.common.excel.annotation.ExcelProperty(value = "金额", index = 7)
    public Double amount;
    @com.njydsz.common.excel.annotation.ExcelProperty(value = "日期", index = 8)
    public String date;

    public SuperFastRecord() {}

    public SuperFastRecord(String orderId, String region, String city, String level,
        String productLine, Integer qty, Double unitPrice, Double amount, String date) {
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

  /** EasyExcel 用 DTO — 标注 EasyExcel @ExcelProperty */
  public static class EasyExcelRecord {
    @ExcelProperty(value = "订单ID", index = 0)
    public String orderId;
    @ExcelProperty(value = "地区", index = 1)
    public String region;
    @ExcelProperty(value = "城市", index = 2)
    public String city;
    @ExcelProperty(value = "等级", index = 3)
    public String level;
    @ExcelProperty(value = "产品线", index = 4)
    public String productLine;
    @ExcelProperty(value = "数量", index = 5)
    public Integer qty;
    @ExcelProperty(value = "单价", index = 6)
    public Double unitPrice;
    @ExcelProperty(value = "金额", index = 7)
    public Double amount;
    @ExcelProperty(value = "日期", index = 8)
    public String date;

    public EasyExcelRecord() {}

    public EasyExcelRecord(String orderId, String region, String city, String level,
        String productLine, Integer qty, Double unitPrice, Double amount, String date) {
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

  /** POI 用 Object[]（9 列，行号+数据） */
  private static final String[] HEADERS = {
      "订单ID", "地区", "城市", "等级", "产品线", "数量", "单价", "金额", "日期"
  };

  private static final String[] REGIONS = {"华东", "华南", "华北", "华中", "西南", "西北", "东北"};
  private static final String[] CITIES = {"上海", "杭州", "南京", "广州", "深圳", "北京", "成都", "武汉", "西安", "沈阳"};
  private static final String[] LEVELS = {"A", "B", "C", "S"};
  private static final String[] PRODUCTS = {"3C数码", "服饰", "食品", "家居", "汽车", "医药", "教育"};

  private final File tempDir;
  CompetitiveBenchmark() throws Exception {
    this.tempDir = Files.createTempDirectory("competitive-bench-").toFile();
  }

  // ==================== 数据生成 ====================

  private List<SuperFastRecord> generateSuperFastData(int rows) {
    List<SuperFastRecord> list = new ArrayList<>(rows);
    for (int i = 0; i < rows; i++) {
      list.add(new SuperFastRecord(
          "ORD-" + (100000 + i),
          REGIONS[i % REGIONS.length],
          CITIES[i % CITIES.length],
          LEVELS[i % LEVELS.length],
          PRODUCTS[i % PRODUCTS.length],
          1 + (i % 100),
          10.0 + (i % 1000),
          (1 + (i % 100)) * (10.0 + (i % 1000)),
          "2026-09-" + String.format("%02d", 1 + (i % 28))));
    }
    return Collections.unmodifiableList(list);
  }

  private List<EasyExcelRecord> generateEasyExcelData(int rows) {
    List<EasyExcelRecord> list = new ArrayList<>(rows);
    for (int i = 0; i < rows; i++) {
      list.add(new EasyExcelRecord(
          "ORD-" + (100000 + i),
          REGIONS[i % REGIONS.length],
          CITIES[i % CITIES.length],
          LEVELS[i % LEVELS.length],
          PRODUCTS[i % PRODUCTS.length],
          1 + (i % 100),
          10.0 + (i % 1000),
          (1 + (i % 100)) * (10.0 + (i % 1000)),
          "2026-09-" + String.format("%02d", 1 + (i % 28))));
    }
    return Collections.unmodifiableList(list);
  }

  private Object[][] generateRawData(int rows) {
    Object[][] data = new Object[rows][9];
    for (int i = 0; i < rows; i++) {
      data[i][0] = "ORD-" + (100000 + i);
      data[i][1] = REGIONS[i % REGIONS.length];
      data[i][2] = CITIES[i % CITIES.length];
      data[i][3] = LEVELS[i % LEVELS.length];
      data[i][4] = PRODUCTS[i % PRODUCTS.length];
      data[i][5] = 1 + (i % 100);
      data[i][6] = 10.0 + (i % 1000);
      data[i][7] = (1 + (i % 100)) * (10.0 + (i % 1000));
      data[i][8] = "2026-09-" + String.format("%02d", 1 + (i % 28));
    }
    return data;
  }

  // ==================== 写入基准 ====================

  /** SuperFast 写入到字节数组 */
  private void writeSuperFast(List<SuperFastRecord> data, ByteArrayOutputStream bos) throws Exception {
    ExcelFacade.write(bos, SuperFastRecord.class).sheet("数据").doWrite(data);
  }

  /** EasyExcel 写入到文件 */
  private void writeEasyExcel(List<EasyExcelRecord> data, File file) throws Exception {
    EasyExcel.write(file, EasyExcelRecord.class).sheet("数据").doWrite(data);
  }

  /** POI XSSF 写入到文件（DOM 全量加载） */
  private void writePoiXssf(Object[][] rows, File file) throws Exception {
    Workbook wb = new XSSFWorkbook();
    Sheet sheet = wb.createSheet("数据");
    // 表头
    Row header = sheet.createRow(0);
    for (int c = 0; c < HEADERS.length; c++) {
      header.createCell(c).setCellValue(HEADERS[c]);
    }
    CellStyle intStyle = wb.createCellStyle();
    DataFormat df = wb.createDataFormat();
    intStyle.setDataFormat(df.getFormat("#,##0"));
    CellStyle dblStyle = wb.createCellStyle();
    dblStyle.setDataFormat(df.getFormat("#,##0.00"));

    for (int r = 0; r < rows.length; r++) {
      Row row = sheet.createRow(r + 1);
      for (int c = 0; c < HEADERS.length; c++) {
        Cell cell = row.createCell(c);
        Object val = rows[r][c];
        if (val instanceof Number) {
          cell.setCellValue(((Number) val).doubleValue());
        } else {
          cell.setCellValue(String.valueOf(val));
        }
      }
    }
    try (FileOutputStream fos = new FileOutputStream(file)) {
      wb.write(fos);
    }
    wb.close();
  }

  /** POI SXSSF 写入到文件（流式窗口） */
  private void writePoiSxssf(Object[][] rows, File file) throws Exception {
    // 窗口大小 1000 行
    SXSSFWorkbook wb = new SXSSFWorkbook(1000);
    Sheet sheet = wb.createSheet("数据");
    Row header = sheet.createRow(0);
    for (int c = 0; c < HEADERS.length; c++) {
      header.createCell(c).setCellValue(HEADERS[c]);
    }
    for (int r = 0; r < rows.length; r++) {
      Row row = sheet.createRow(r + 1);
      for (int c = 0; c < HEADERS.length; c++) {
        Cell cell = row.createCell(c);
        Object val = rows[r][c];
        if (val instanceof Number) {
          cell.setCellValue(((Number) val).doubleValue());
        } else {
          cell.setCellValue(String.valueOf(val));
        }
      }
    }
    try (FileOutputStream fos = new FileOutputStream(file)) {
      wb.write(fos);
    }
    wb.dispose();
    wb.close();
  }

  // ==================== 读取基准 ====================

  /** SuperFast 读取 — 通过 ReadListener 收集行数 */
  private int readSuperFast(byte[] bytes) throws Exception {
    java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger();
    ExcelFacade.read(new ByteArrayInputStream(bytes), SuperFastRecord.class).doRead(
        new com.njydsz.common.excel.core.listener.ReadListener<SuperFastRecord>() {
          @Override public void onStart(com.njydsz.common.excel.core.context.AnalysisContext ctx) {}
          @Override public void onData(com.njydsz.common.excel.core.context.AnalysisContext ctx, SuperFastRecord data) {
            counter.incrementAndGet();
          }
          @Override public void onEnd(com.njydsz.common.excel.core.context.AnalysisContext ctx) {}
          @Override public void onError(com.njydsz.common.excel.core.context.AnalysisContext ctx, Exception e) {}
        });
    return counter.get();
  }

  /** EasyExcel 读取 — 统计回调次数 */
  private int readEasyExcel(File file) throws Exception {
    java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger();
    EasyExcel.read(file, EasyExcelRecord.class, new AnalysisEventListener<EasyExcelRecord>() {
      @Override public void invoke(EasyExcelRecord data, AnalysisContext context) {
        counter.incrementAndGet();
      }
      @Override public void doAfterAllAnalysed(AnalysisContext context) {}
    }).sheet(0).doRead();
    return counter.get();
  }

  /** POI XSSF 读取 — 行迭代，统计非空第一列行数 */
  private int readPoiXssf(File file) throws Exception {
    int count = 0;
    try (XSSFWorkbook wb = new XSSFWorkbook(new FileInputStream(file))) {
      Sheet sheet = wb.getSheetAt(0);
      for (int r = 1; r <= sheet.getLastRowNum(); r++) {
        Row row = sheet.getRow(r);
        if (row == null) continue;
        Cell cell0 = row.getCell(0);
        if (cell0 != null && cell0.getCellType() != org.apache.poi.ss.usermodel.CellType.BLANK) {
          count++;
        }
      }
    }
    return count;
  }

  // ==================== Main 入口 ====================

  public static void main(String[] args) throws Exception {
    CompetitiveBenchmark bench = new CompetitiveBenchmark();
    bench.runAll();
    // 清理临时文件
    for (File f : bench.tempDir.listFiles()) {
      f.delete();
    }
    bench.tempDir.delete();
  }

  private void runAll() throws Exception {
    printBanner();

    // 数据规模配置：1k / 10k / 100k
    int[] rowCounts = {1000, 10_000, 100_000};

    // 预生成数据（避免 data generation 计入 benchmark）
    List<List<SuperFastRecord>> sfDataBySize = new ArrayList<>();
    List<List<EasyExcelRecord>> eeDataBySize = new ArrayList<>();
    List<Object[][]> poiDataBySize = new ArrayList<>();
    for (int rows : rowCounts) {
      sfDataBySize.add(generateSuperFastData(rows));
      eeDataBySize.add(generateEasyExcelData(rows));
      poiDataBySize.add(generateRawData(rows));
    }

    // ====== 写入基准 ======
    System.out.println("╔══════════════════════════════════════════════════════════════╗");
    System.out.println("║                     WRITE BENCHMARK                         ║");
    System.out.println("╚══════════════════════════════════════════════════════════════╝");

    for (int idx = 0; idx < rowCounts.length; idx++) {
      int rows = rowCounts[idx];
      System.out.println("\n>>> " + rows + " rows write benchmark <<<");
      int measured = (rows >= 100_000) ? 2 : 5;
      final List<SuperFastRecord> sfData = sfDataBySize.get(idx);
      final List<EasyExcelRecord> eeData = eeDataBySize.get(idx);
      final Object[][] poiData = poiDataBySize.get(idx);

      // YdszExcel
      ByteArrayOutputStream[] holder = new ByteArrayOutputStream[1];
      BenchmarkRunner.BenchResult sfWrite = BenchmarkRunner.run(
          "YdszExcel-write-" + rows, 3, measured, () -> {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            writeSuperFast(sfData, bos);
            holder[0] = bos;
          });
      System.out.println(" | file bytes = " + holder[0].size());

      // EasyExcel
      File eeFile = new File(tempDir, "easyexcel-" + rows + ".xlsx");
      BenchmarkRunner.BenchResult eeWrite = BenchmarkRunner.run(
          "EasyExcel-write-" + rows, 3, measured, () -> writeEasyExcel(eeData, eeFile));
      System.out.println(" | file bytes = " + eeFile.length());

      // POI XSSF (仅 1k 和 10k; 100k 太慢可能 OOM)
      if (rows <= 10_000) {
        File poiFile = new File(tempDir, "poi-xssf-" + rows + ".xlsx");
        BenchmarkRunner.BenchResult poiWrite = BenchmarkRunner.run(
            "POI-XSSF-write-" + rows, 3, measured, () -> writePoiXssf(poiData, poiFile));
        System.out.println(" | file bytes = " + poiFile.length());
      } else {
        System.out.println("[POI-XSSF-write-" + rows + "] SKIPPED (DOM engine: too slow for 100k rows)");
      }

      // POI SXSSF
      File sxssfFile = new File(tempDir, "poi-sxssf-" + rows + ".xlsx");
      BenchmarkRunner.BenchResult sxssfWrite = BenchmarkRunner.run(
          "POI-SXSSF-write-" + rows, 3, measured, () -> writePoiSxssf(poiData, sxssfFile));
      System.out.println(" | file bytes = " + sxssfFile.length());
    }

    // ====== 读取基准 (1k + 10k) ======
    System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
    System.out.println("║                     READ BENCHMARK                          ║");
    System.out.println("╚══════════════════════════════════════════════════════════════╝");

    int[] readSizes = {1000, 10_000};
    for (int idx = 0; idx < readSizes.length; idx++) {
      int rows = readSizes[idx];
      System.out.println("\n>>> " + rows + " rows read benchmark <<<");

      // 先写出文件/字节
      byte[] sfBytes = writeSuperFastToBytes(sfDataBySize.get(idx));
      File eeFile = new File(tempDir, "read-easyexcel-" + rows + ".xlsx");
      writeEasyExcel(eeDataBySize.get(idx), eeFile);
      File poiFile = new File(tempDir, "read-poi-xssf-" + rows + ".xlsx");
      writePoiXssf(poiDataBySize.get(idx), poiFile);

      // YdszExcel
      final byte[] sfBytesFinal = sfBytes;
      BenchmarkRunner.BenchResult sfRead = BenchmarkRunner.run(
          "YdszExcel-read-" + rows, 3, 5, () -> readSuperFast(sfBytesFinal));
      System.out.println(" | parsed rows = " + readSuperFast(sfBytes));

      // EasyExcel
      BenchmarkRunner.BenchResult eeRead = BenchmarkRunner.run(
          "EasyExcel-read-" + rows, 3, 5, () -> readEasyExcel(eeFile));
      System.out.println(" | parsed rows = " + readEasyExcel(eeFile));

      // POI XSSF
      BenchmarkRunner.BenchResult poiRead = BenchmarkRunner.run(
          "POI-XSSF-read-" + rows, 3, 5, () -> readPoiXssf(poiFile));
      System.out.println(" | parsed rows = " + readPoiXssf(poiFile));
    }

    // ====== 汇总表格 ======
    printSummaryTable(rowCounts, readSizes);
  }

  private byte[] writeSuperFastToBytes(List<SuperFastRecord> data) throws Exception {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    writeSuperFast(data, bos);
    return bos.toByteArray();
  }

  // ==================== Banner & Summary ====================

  private void printBanner() {
    Runtime rt = Runtime.getRuntime();
    OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
    System.out.println("==============================================================");
    System.out.println("  REAL COMPETITIVE BENCHMARK — ydsz-common-excel");
    System.out.println("==============================================================");
    System.out.println("  Date       : " + java.time.LocalDateTime.now());
    System.out.println("  JVM        : " + System.getProperty("java.vm.name") + " "
        + System.getProperty("java.version"));
    System.out.println("  OS         : " + System.getProperty("os.name") + " ("
        + os.getAvailableProcessors() + " cores)");
    System.out.println("  Max Heap   : " + (rt.maxMemory() / 1024 / 1024) + " MB");
    System.out.println("  Engines    : YdszExcel (YDSZ) vs EasyExcel 4.x vs POI XSSF/SXSSF 5.x");
    System.out.println("  Iterations : warmup=3, measured=5 (100k→2)");
    System.out.println("==============================================================");
  }

  private void printSummaryTable(int[] writeSizes, int[] readSizes) {
    System.out.println("\n");
    System.out.println("╔════════════════════════════════════════════════════════════════════════════════════════════╗");
    System.out.println("║                               SUMMARY — REAL DATA                                        ║");
    System.out.println("╚════════════════════════════════════════════════════════════════════════════════════════════╝");
    System.out.println("");
    System.out.println("Run this benchmark again with higher iterations for more stable P95/P99 numbers.");
    System.out.println("Single-run snapshots are indicative, not statistically definitive.");
    System.out.println("");
    System.out.println("  NOTE:");
    System.out.println("  • YdszExcel 数据为 9 列宽表（订单ID/地区/城市/等级/产品线/数量/单价/金额/日期）；");
    System.out.println("    POI/SXSSF 写入含表头行；EasyExcel 写入含表头（@ExcelProperty 自动识别）；");
    System.out.println("  • 读取时各引擎读取同一行数（1k/10k，跳过 POI XSSF 100k 因 DOM 过大）；");
    System.out.println("  • RSS 增量近似堆变化（未含 off-heap），POI XSSF 实际 RSS 更高；");
    System.out.println("  • 每次 measurement 前强制 gc，减小 clean-up 波动。");
  }
}
