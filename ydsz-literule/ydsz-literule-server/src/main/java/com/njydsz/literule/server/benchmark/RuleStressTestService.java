package com.njydsz.literule.server.benchmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.thread.util.ExecutorUtils;
import com.njydsz.literule.domain.api.RuleResult;
import com.njydsz.literule.server.config.RuleAdminService;

/**
 * 规则压测服务
 *
 * <p>使用固定线程池并发执行 Dry-run 仿真，测量 QPS、P50/P95/P99 耗时与错误率， 用于规则上线前的性能基线评估。
 * 压测走 dry-run 通道：不发布事件、不记录统计、不影响线上指标。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class RuleStressTestService {

  /** 压测整体超时时间（分钟） */
  private static final long AWAIT_TIMEOUT_MINUTES = 5L;

  /** 毫秒转秒系数 */
  private static final double MILLIS_PER_SECOND = 1000.0;

  /** P50 分位 */
  private static final int PERCENTILE_P50 = 50;

  /** P95 分位 */
  private static final int PERCENTILE_P95 = 95;

  /** P99 分位 */
  private static final int PERCENTILE_P99 = 99;

  /** 纳秒到毫秒换算系数 */
  private static final long NANOS_PER_MILLI = 1_000_000L;

  /** 规则管理服务（提供 dry-run 通道） */
  private final RuleAdminService ruleAdminService;

  /**
   * 构造压测服务
   *
   * @param ruleAdminService 规则管理服务
   */
  public RuleStressTestService(RuleAdminService ruleAdminService) {
    this.ruleAdminService = ruleAdminService;
  }

  /**
   * 执行压测
   *
   * @param ruleCode 规则编码（null 表示全部规则）
   * @param factsList 事实样本池
   * @param threads 并发线程数
   * @param iterations 压测迭代次数
   * @param warmupIterations 预热迭代次数（不计入结果）
   * @return 压测结果
   */
  public StressTestResult run(
      String ruleCode,
      List<Map<String, Object>> factsList,
      int threads,
      int iterations,
      int warmupIterations) {
    if (factsList == null || factsList.isEmpty()) {
      throw new IllegalArgumentException("factsList 不能为空");
    }
    int safeThreads = Math.max(1, threads);
    int safeIterations = Math.max(1, iterations);
    int safeWarmup = Math.max(0, warmupIterations);

    // 预热：触发类加载与表达式编译缓存填充
    if (safeWarmup > 0) {
      runInternal(ruleCode, factsList, safeThreads, safeWarmup);
    }

    StressTestResult result = runInternal(ruleCode, factsList, safeThreads, safeIterations);
    log.info(
        "[LiteRule-Benchmark] 压测完成: ruleCode={}, threads={}, iterations={}, qps={}, "
            + "p50={}ms, p95={}ms, p99={}ms, errorRate={}",
        ruleCode, safeThreads, safeIterations,
        result.getQps(), result.getP50Ms(), result.getP95Ms(), result.getP99Ms(),
        result.getErrorRate());
    return result;
  }

  /**
   * 内部压测执行（固定线程池 + 每个线程按样本池随机选取 facts）
   *
   * @param ruleCode 规则编码
   * @param factsList 事实样本池
   * @param threads 并发线程数
   * @param iterations 迭代次数
   * @return 压测结果
   */
  private StressTestResult runInternal(
      String ruleCode, List<Map<String, Object>> factsList, int threads, int iterations) {
    // CHECKSTYLE.OFF: RegexpSinglelineJava - 压测服务需要短生命周期并发线程池，经 common-thread ExecutorUtils 创建
    ExecutorService executor = ExecutorUtils.newFixedThreadPool(threads, "literule-stress");
    // CHECKSTYLE.ON: RegexpSinglelineJava
    try {
      int perThread = Math.max(1, iterations / threads);
      int remainder = iterations % threads;
      List<Long> latencies = Collections.synchronizedList(new ArrayList<>(iterations));
      LongAdder successCount = new LongAdder();
      LongAdder errorCount = new LongAdder();
      CountDownLatch latch = new CountDownLatch(threads);
      long startNanos = System.nanoTime();

      for (int t = 0; t < threads; t++) {
        int count = perThread + (t < remainder ? 1 : 0);
        executor.submit(
            () -> {
              try {
                for (int i = 0; i < count; i++) {
                  Map<String, Object> facts =
                      factsList.get(ThreadLocalRandom.current().nextInt(factsList.size()));
                  long begin = System.nanoTime();
                  try {
                    List<RuleResult> results = ruleAdminService.dryRun(ruleCode, facts);
                    if (results == null) {
                      errorCount.increment();
                    } else {
                      successCount.increment();
                    }
                  } catch (Exception e) {
                    errorCount.increment();
                  } finally {
                    latencies.add((System.nanoTime() - begin) / NANOS_PER_MILLI);
                  }
                }
              } finally {
                latch.countDown();
              }
            });
      }
      latch.await(AWAIT_TIMEOUT_MINUTES, TimeUnit.MINUTES);
      long durationMs = Math.max(1, (System.nanoTime() - startNanos) / NANOS_PER_MILLI);
      return computeResult(
          ruleCode, latencies, successCount.sum(), errorCount.sum(), durationMs, threads);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("压测被中断", e);
    } finally {
      executor.shutdown();
    }
  }

  /**
   * 汇总压测指标
   *
   * @param ruleCode 规则编码
   * @param latencies 耗时样本（毫秒）
   * @param success 成功次数
   * @param errors 失败次数
   * @param durationMs 总耗时（毫秒）
   * @param threads 并发线程数
   * @return 压测结果
   */
  private StressTestResult computeResult(
      String ruleCode,
      List<Long> latencies,
      long success,
      long errors,
      long durationMs,
      int threads) {
    long total = success + errors;
    double errorRate = total == 0 ? 0.0 : (double) errors / total;
    double qps = durationMs == 0 ? 0.0 : total * MILLIS_PER_SECOND / durationMs;
    List<Long> sorted = new ArrayList<>(latencies);
    Collections.sort(sorted);
    return StressTestResult.builder()
        .ruleCode(ruleCode)
        .total(total)
        .success(success)
        .failed(errors)
        .errorRate(errorRate)
        .qps(qps)
        .p50Ms(percentile(sorted, PERCENTILE_P50))
        .p95Ms(percentile(sorted, PERCENTILE_P95))
        .p99Ms(percentile(sorted, PERCENTILE_P99))
        .durationMs(durationMs)
        .threads(threads)
        .build();
  }

  /**
   * 计算分位数（毫秒）
   *
   * @param sorted 升序耗时样本
   * @param percentile 分位（0-100）
   * @return 分位耗时；样本为空时返回 0
   */
  private long percentile(List<Long> sorted, int percentile) {
    if (sorted == null || sorted.isEmpty()) {
      return 0L;
    }
    int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
    return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
  }

  /**
   * 压测结果（字段语义见字段注释）。
   */
  @Builder
  @Data
  public static class StressTestResult {
    /** 规则编码 */
    private String ruleCode;
    /** 总执行次数 */
    private long total;
    /** 成功次数 */
    private long success;
    /** 失败次数 */
    private long failed;
    /** 错误率（0-1） */
    private double errorRate;
    /** 每秒执行次数 */
    private double qps;
    /** P50 耗时（毫秒） */
    private long p50Ms;
    /** P95 耗时（毫秒） */
    private long p95Ms;
    /** P99 耗时（毫秒） */
    private long p99Ms;
    /** 总耗时（毫秒） */
    private long durationMs;
    /** 并发线程数 */
    private int threads;
  }
}
