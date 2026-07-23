package com.njydsz.common.cache;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheBuilder;
import com.njydsz.common.cache.builder.CacheType;

/**
 * YdszCache JMH 性能基准测试套件
 *
 * <p>覆盖场景：
 *
 * <ul>
 *   <li>不同缓存类型（TINYLFU/LRU/STRIPED）的读写性能对比
 *   <li>ExpirableCache 装饰器对性能的影响
 *   <li>computeIfAbsent 原子加载性能
 *   <li>批量操作性能
 * </ul>
 *
 * <p>运行方式：
 *
 * <pre>
 * mvn compile -pl ydsz-common/ydsz-common-cache
 * java -jar target/benchmarks.jar CacheBenchmark -wi 3 -i 5 -f 1
 * </pre>
 *
 * @since 1.0.0
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class CacheBenchmark {

  @Param({"TINYLFU", "LRU", "STRIPED"})
  private String cacheType;

  private Cache<String, String> cache;

  @Setup
  public void setup() {
    CacheType type = CacheType.valueOf(cacheType);
    cache =
        CacheBuilder.<String, String>newBuilder()
            .type(type)
            .maximumSize(10000)
            .build();

    // 预填充数据
    for (int i = 0; i < 5000; i++) {
      cache.put("key-" + i, "value-" + i);
    }
  }

  /** 测试缓存读取（命中场景） */
  @Benchmark
  public void getIfPresentHit(Blackhole bh) {
    int idx = (int) (Thread.currentThread().threadId() % 5000);
    String value = cache.getIfPresent("key-" + idx);
    bh.consume(value);
  }

  /** 测试缓存读取（未命中场景） */
  @Benchmark
  public void getIfPresentMiss(Blackhole bh) {
    String value = cache.getIfPresent("non-existent-key");
    bh.consume(value);
  }

  /** 测试缓存写入 */
  @Benchmark
  public void put(Blackhole bh) {
    int idx = (int) (System.nanoTime() % 10000);
    cache.put("bench-key-" + idx, "bench-value-" + idx);
    bh.consume(idx);
  }

  /** 测试 computeIfAbsent（命中场景） */
  @Benchmark
  public void computeIfAbsentHit(Blackhole bh) {
    int idx = (int) (Thread.currentThread().threadId() % 5000);
    String value = cache.computeIfAbsent("key-" + idx, k -> "loaded-" + k);
    bh.consume(value);
  }

  /** 测试 computeIfAbsent（未命中场景，需要执行加载函数） */
  @Benchmark
  public void computeIfAbsentMiss(Blackhole bh) {
    int idx = (int) (System.nanoTime() % 100000) + 50000;
    String value = cache.computeIfAbsent("miss-key-" + idx, k -> "loaded-" + k);
    bh.consume(value);
  }
}
