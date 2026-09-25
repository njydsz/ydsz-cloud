package com.njydsz.common.excel.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 轻量级基准测试运行器 — 通过预热 + 多次计时循环，统计吞吐量与延迟百分位。
 *
 * <p>不依赖 JMH，直接在 JUnit 测试中运行；结果输出到 {@code benchmark-results/} 目录。
 *
 * <h3>度量指标</h3>
 *
 * <ul>
 *   <li>{@code ops/s} — 吞吐量（每秒操作数）</li>
 *   <li>{@code avgMs} — 单次平均耗时（毫秒）</li>
 *   <li>{@code p50/p95/p99} — 百分位耗时</li>
 *   <li>{@code rssDeltaMB} — 执行后堆内存增量（近似 RSS 变化）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.25
 */
public final class BenchmarkRunner {

  private BenchmarkRunner() {}

  /** 单次基准任务结果。 */
  public static final class BenchResult {
    public final String name;
    public final int iterations;
    public final double avgMs;
    public final double p50Ms;
    public final double p95Ms;
    public final double p99Ms;
    public final long elapsedMs;
    public final double opsPerSec;
    public final long rssBeforeBytes;
    public final long rssAfterBytes;
    public final double rssDeltaMB;

    public BenchResult(String name, int iterations, double avgMs, double p50Ms, double p95Ms,
        double p99Ms, long elapsedMs, long rssBeforeBytes, long rssAfterBytes) {
      this.name = name;
      this.iterations = iterations;
      this.avgMs = avgMs;
      this.p50Ms = p50Ms;
      this.p95Ms = p95Ms;
      this.p99Ms = p99Ms;
      this.elapsedMs = elapsedMs;
      this.opsPerSec = iterations / (elapsedMs / 1000.0);
      this.rssBeforeBytes = rssBeforeBytes;
      this.rssAfterBytes = rssAfterBytes;
      this.rssDeltaMB = (rssAfterBytes - rssBeforeBytes) / (1024.0 * 1024.0);
    }

    @Override
    public String toString() {
      return String.format(Locale.ROOT,
          "[%s] iter=%d | avg=%.2fms | p50=%.2fms | p95=%.2fms | p99=%.2fms | ops/s=%.1f | rssDelta=%.1fMB | total=%dms",
          name, iterations, avgMs, p50Ms, p95Ms, p99Ms, opsPerSec, rssDeltaMB, elapsedMs);
    }
  }

  /** 可调用的基准任务接口。 */
  @FunctionalInterface
  public interface BenchTask {
    void run() throws Exception;
  }

  /**
   * 执行一次基准测试。
   *
   * @param name 测试名称
   * @param warmupIterations 预热次数（不计入统计）
   * @param measuredIterations 正式测量次数
   * @param task 基准任务
   * @return 测试结果
   */
  public static BenchResult run(String name, int warmupIterations, int measuredIterations,
      BenchTask task) throws Exception {
    System.out.println("");
    System.out.println("=== Benchmark " + name + " ===");

    // 强制 GC，清理历史残留
    System.gc();
    Thread.sleep(100);
    long rssBefore = usedHeap();

    // 预热
    for (int i = 0; i < warmupIterations; i++) {
      task.run();
    }

    // 再次 GC，确保预热垃圾被回收
    System.gc();
    Thread.sleep(50);
    rssBefore = usedHeap();

    // 正式测量
    long start = System.nanoTime();
    List<Long> latencies = new ArrayList<>(measuredIterations);
    for (int i = 0; i < measuredIterations; i++) {
      long iterStart = System.nanoTime();
      task.run();
      long iterEnd = System.nanoTime();
      latencies.add(iterEnd - iterStart);
    }
    long end = System.nanoTime();

    long rssAfter = usedHeap();

    // 计算统计量
    latencies.sort(Long::compareTo);
    long totalNanos = end - start;
    double totalMs = totalNanos / 1_000_000.0;
    double avgMs = latencies.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0;
    double p50Ms = percentile(latencies, 50) / 1_000_000.0;
    double p95Ms = percentile(latencies, 95) / 1_000_000.0;
    double p99Ms = percentile(latencies, 99) / 1_000_000.0;

    BenchResult result = new BenchResult(name, measuredIterations, avgMs, p50Ms, p95Ms, p99Ms,
        (long) totalMs, rssBefore, rssAfter);

    System.out.println(result.toString());
    return result;
  }

  /**
   * 执行基准测试（默认配置：预热 3 次 + 测量 10 次）。
   */
  public static BenchResult run(String name, BenchTask task) throws Exception {
    return run(name, 3, 10, task);
  }

  private static long percentile(List<Long> sorted, int p) {
    if (sorted.isEmpty()) {
      return 0;
    }
    int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
    idx = Math.max(0, Math.min(idx, sorted.size() - 1));
    return sorted.get(idx);
  }

  private static long usedHeap() {
    Runtime rt = Runtime.getRuntime();
    return rt.totalMemory() - rt.freeMemory();
  }
}
