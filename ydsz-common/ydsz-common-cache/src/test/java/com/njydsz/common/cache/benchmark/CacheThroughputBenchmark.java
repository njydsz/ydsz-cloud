package com.njydsz.common.cache.benchmark;

import java.util.Random;

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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheType;

import java.util.concurrent.TimeUnit;

/**
 * 本地缓存吞吐基准（当前自研实现 vs Caffeine 对照）。
 *
 * <p>用途：为"Caffeine 内核替换"决策（报告 A1）留存换内核前的对照数据。 test scope 引入 Caffeine，
 * 主代码零第三方依赖约束（L1 纯度）不变。
 *
 * <p>基准场景（对标 Caffeine 官方基准的关键路径）：
 *
 * <ul>
 *   <li>readHit：100% 命中读（getIfPresent）—— Caffeine 最强项（无锁读 + 条带环缓冲记录访问）
 *   <li>mixedWriteRead：约 6% 写 / 94% 读混合负载
 *   <li>computeIfAbsentMiss：未命中加载路径（单飞语义下的并发回源）
 * </ul>
 *
 * <p>参数：容量 10,000；key 均匀随机分布（固定种子可复现）；命中读序列预生成共享只读数组， 每线程独立游标（Scope.Thread），避免共享计数器把并发度串行化。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class CacheThroughputBenchmark {

  /** 缓存容量（同时是命中读 key 空间上界） */
  static final int SIZE = 10_000;

  /** 预生成的均匀随机访问序列长度（共享只读） */
  static final int SEQUENCE_LENGTH = 1 << 20;

  /** 写概率掩码：约 1/16 的访问为写 */
  static final int WRITE_MASK = 15;

  /** 未命中 key 起始偏移（在容量之外，保证 miss 语义） */
  static final int MISS_KEY_BASE = SIZE;

  /** 每线程未命中 key 独立段宽度（线程间 key 空间隔离，避免互相"命中"） */
  static final int MISS_KEY_SEGMENT = 1_000_000;

  /** 基准实现选择 */
  @Param({"TINYLFU", "STRIPED", "CAFFEINE"})
  public String impl;

  private Cache<Integer, Integer> ydszCache;

  private com.github.benmanes.caffeine.cache.Cache<Integer, Integer> caffeineCache;

  /** 共享只读访问序列（均匀随机，固定种子） */
  private int[] readSequence;

  /** 初始化基准状态：构建缓存并预填充至容量 */
  @Setup
  public void setup() {
    Random random = new Random(42);
    readSequence = new int[SEQUENCE_LENGTH];
    for (int i = 0; i < SEQUENCE_LENGTH; i++) {
      readSequence[i] = random.nextInt(SIZE);
    }
    if ("CAFFEINE".equals(impl)) {
      caffeineCache = Caffeine.newBuilder().maximumSize(SIZE).build();
      for (int i = 0; i < SIZE; i++) {
        caffeineCache.put(i, i);
      }
    } else {
      ydszCache =
          YdszCache.<Integer, Integer>newBuilder()
              .type("STRIPED".equals(impl) ? CacheType.STRIPED : CacheType.TINYLFU)
              .maximumSize(SIZE)
              .build();
      for (int i = 0; i < SIZE; i++) {
        ydszCache.put(i, i);
      }
    }
  }

  /** 每线程独立游标（访问序列下标与未命中 key 生成器），避免共享计数器串行化并发 */
  @State(Scope.Thread)
  public static class Cursor {
    /** 访问序列游标 */
    int idx;

    /** 未命中 key 生成器（独立段起始） */
    long missKey =
        MISS_KEY_BASE
            + (Thread.currentThread().getId() % 1024) * (long) MISS_KEY_SEGMENT;

    /** 读取下一个命中读 key */
    int nextHitKey(int[] sequence) {
      int i = idx;
      idx = i + 1 >= SEQUENCE_LENGTH ? 0 : i + 1;
      return sequence[i];
    }
  }

  /** 100% 命中读吞吐（ops/ms） */
  @Benchmark
  public Integer readHit(Cursor cursor) {
    int key = cursor.nextHitKey(readSequence);
    if (ydszCache != null) {
      return ydszCache.getIfPresent(key);
    }
    return caffeineCache.getIfPresent(key);
  }

  /** 混合读写吞吐（约 6% 写 / 94% 读） */
  @Benchmark
  public Integer mixedWriteRead(Cursor cursor) {
    int key = cursor.nextHitKey(readSequence);
    if ((key & WRITE_MASK) == 0) {
      if (ydszCache != null) {
        ydszCache.put(key, key);
      } else {
        caffeineCache.put(key, key);
      }
      return key;
    }
    if (ydszCache != null) {
      return ydszCache.getIfPresent(key);
    }
    return caffeineCache.getIfPresent(key);
  }

  /** 未命中加载路径吞吐（computeIfAbsent 单飞 + 写入 + 驱逐开销） */
  @Benchmark
  public Integer computeIfAbsentMiss(Cursor cursor) {
    int key = (int) cursor.missKey++;
    if (ydszCache != null) {
      return ydszCache.computeIfAbsent(key, k -> k);
    }
    return caffeineCache.get(key, k -> k);
  }
}
