package com.njydsz.common.cache.benchmark;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

/**
 * 基准运行入口（手动触发，不走 surefire）。
 *
 * <p>固定跑 1/4/16 三档线程 × 三实现 × 三场景。总耗时约 5 分钟。 结果输出到控制台（吞吐 ops/ms，含置信区间），
 * 由执行者摘录至分析报告 §5 执行记录。
 *
 * <p>运行方式：test-compile 后以 test classpath 执行本类 main（详见报告执行记录）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class BenchmarkRunner {

  /** 线程档位（单线程 / 中并发 / 高并发） */
  private static final int[] THREAD_LEVELS = {1, 4, 16};

  /** 预热轮数 */
  private static final int WARMUP_ITERATIONS = 3;

  /** 测量轮数 */
  private static final int MEASUREMENT_ITERATIONS = 5;

  /** 单轮时长（秒） */
  private static final int ITERATION_SECONDS = 1;

  private BenchmarkRunner() {}

  /**
   * 基准主入口。
   *
   * @param args 未使用
   * @throws Exception JMH 运行异常
   */
  public static void main(String[] args) throws Exception {
    for (int threads : THREAD_LEVELS) {
      Options options =
          new OptionsBuilder()
              .include(CacheThroughputBenchmark.class.getSimpleName())
              .threads(threads)
              .forks(1)
              .warmupIterations(WARMUP_ITERATIONS)
              .warmupTime(TimeValue.seconds(ITERATION_SECONDS))
              .measurementIterations(MEASUREMENT_ITERATIONS)
              .measurementTime(TimeValue.seconds(ITERATION_SECONDS))
              .timeUnit(TimeUnit.MILLISECONDS)
              .shouldFailOnError(true)
              .build();
      System.out.println("======== threads = " + threads + " ========");
      new Runner(options).run();
    }
  }
}
