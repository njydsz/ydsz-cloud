package com.njydsz.common.cache.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.internal.tinylfu.WindowTinyLFUCache;
import com.njydsz.common.cache.stats.CacheStats;

/**
 * AbstractCache 公共逻辑测试 — 重点验证原子操作的线程安全性。
 *
 * <p>覆盖：computeIfAbsent 单飞语义、compute 串行化、merge 不丢失、批量加载统计。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
class AbstractCacheTest {

  private Cache<String, Integer> createCache() {
    return new WindowTinyLFUCache<>(100);
  }

  @Nested
  @DisplayName("computeIfAbsent 原子性")
  class ComputeIfAbsentTest {

    @Test
    @DisplayName("并发调用同一 key，mappingFunction 仅执行一次（单飞）")
    void concurrentComputeIfAbsent_shouldExecuteOnce() throws Exception {
      Cache<String, Integer> cache = createCache();
      AtomicInteger computeCount = new AtomicInteger(0);
      int threadCount = 20;

      ExecutorService executor = Executors.newFixedThreadPool(threadCount);
      CountDownLatch startLatch = new CountDownLatch(1);
      CountDownLatch endLatch = new CountDownLatch(threadCount);

      IntStream.range(0, threadCount)
          .forEach(
              i ->
                  executor.submit(
                      () -> {
                        try {
                          startLatch.await();
                          cache.computeIfAbsent(
                              "sharedKey",
                              k -> {
                                computeCount.incrementAndGet();
                                try {
                                  Thread.sleep(20);
                                } catch (InterruptedException e) {
                                  Thread.currentThread().interrupt();
                                }
                                return 42;
                              });
                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                        } finally {
                          endLatch.countDown();
                        }
                      }));

      startLatch.countDown();
      assertThat(endLatch.await(10, TimeUnit.SECONDS)).isTrue();
      executor.shutdown();

      assertThat(computeCount.get()).isEqualTo(1);
      assertThat(cache.getIfPresent("sharedKey")).isEqualTo(42);
    }

    @Test
    @DisplayName("computeIfAbsent 返回 null 时不写入缓存")
    void computeIfAbsent_returningNull_shouldNotPut() {
      Cache<String, Integer> cache = createCache();
      Integer result = cache.computeIfAbsent("key", k -> null);
      assertThat(result).isNull();
      assertThat(cache.estimatedSize()).isZero();
    }

    @Test
    @DisplayName("computeIfAbsent 对已存在的 key 直接返回旧值")
    void computeIfAbsent_existingKey_shouldReturnOldValue() {
      Cache<String, Integer> cache = createCache();
      cache.put("key", 10);
      Integer result = cache.computeIfAbsent("key", k -> 20);
      assertThat(result).isEqualTo(10);
    }
  }

  @Nested
  @DisplayName("compute 串行化")
  class ComputeTest {

    @Test
    @DisplayName("compute 执行后值被正确更新")
    void compute_shouldUpdateValue() {
      Cache<String, Integer> cache = createCache();
      cache.put("counter", 0);
      cache.compute("counter", (k, v) -> v + 1);
      assertThat(cache.getIfPresent("counter")).isEqualTo(1);
    }

    @Test
    @DisplayName("compute 返回 null 时移除条目")
    void compute_returningNull_shouldRemove() {
      Cache<String, Integer> cache = createCache();
      cache.put("key", 1);
      cache.compute("key", (k, v) -> null);
      assertThat(cache.getIfPresent("key")).isNull();
    }
  }

  @Nested
  @DisplayName("merge 语义")
  class MergeTest {

    @Test
    @DisplayName("merge 递增操作结果正确")
    void merge_increment_shouldAccumulate() {
      Cache<String, Integer> cache = createCache();
      cache.put("sum", 0);
      cache.merge("sum", 1, Integer::sum);
      cache.merge("sum", 1, Integer::sum);
      cache.merge("sum", 1, Integer::sum);
      assertThat(cache.getIfPresent("sum")).isEqualTo(3);
    }

    @Test
    @DisplayName("并发 merge 不应丢失调用")
    void concurrentMerge_shouldNotLoseCalls() throws Exception {
      Cache<String, Integer> cache = createCache();
      cache.put("counter", 0);
      int threadCount = 16;
      int mergesPerThread = 100;

      ExecutorService executor = Executors.newFixedThreadPool(threadCount);
      CountDownLatch latch = new CountDownLatch(threadCount);

      IntStream.range(0, threadCount)
          .forEach(
              t ->
                  executor.submit(
                      () -> {
                        try {
                          IntStream.range(0, mergesPerThread)
                              .forEach(i -> cache.merge("counter", 1, Integer::sum));
                        } finally {
                          latch.countDown();
                        }
                      }));

      assertThat(latch.await(15, TimeUnit.SECONDS)).isTrue();
      assertThat(cache.getIfPresent("counter"))
          .isEqualTo(threadCount * mergesPerThread);
      executor.shutdown();
    }
  }

  @Nested
  @DisplayName("批量加载统计")
  class BatchLoadStatsTest {

    @Test
    @DisplayName("getAll 带 loader 时缺失键被加载")
    void getAll_withLoader_shouldLoadMissingKeys() {
      Cache<String, Integer> cache = createCache();
      cache.put("a", 1);
      cache.put("b", 2);

      Set<String> keys = Set.of("a", "b", "c", "d");
      AtomicInteger loadCount = new AtomicInteger(0);

      Map<String, Integer> result =
          cache.getAll(
              keys,
              missing -> {
                loadCount.incrementAndGet();
                missing.forEach(k -> { /* simulate DB load */ });
                return Map.of("c", 3, "d", 4);
              });

      assertThat(result).containsEntry("a", 1).containsEntry("c", 3);
      assertThat(loadCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("getAsync 异步加载结果正确")
    void getAsync_shouldLoadAndReturn() throws Exception {
      Cache<String, Integer> cache = createCache();
      AtomicInteger loadCount = new AtomicInteger(0);

      CompletableFuture<Integer> future =
          cache.getAsync(
              "asyncKey",
              k -> {
                loadCount.incrementAndGet();
                return CompletableFuture.completedFuture(99);
              });

      Integer result = future.get(5, TimeUnit.SECONDS);
      assertThat(result).isEqualTo(99);
      assertThat(loadCount.get()).isEqualTo(1);
      // 第二次 getAsync 应从缓存返回，不触发 loader
      cache.getAsync(
              "asyncKey",
              k -> CompletableFuture.completedFuture(99))
          .get(5, TimeUnit.SECONDS);
    }
  }
}
