package com.njydsz.common.cache.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.builder.CacheType;

/**
 * 原子操作语义并发测试（对标 Caffeine / ConcurrentHashMap）。
 *
 * <p>锁定的不变量：
 *
 * <ul>
 *   <li>computeIfAbsent：同一 key 并发下 mappingFunction 仅执行一次，结果对全部等待者可见
 *   <li>putIfAbsent：并发下仅一个线程完成写入
 *   <li>merge：并发计数叠加无丢失（串行化）
 *   <li>computeIfAbsent：mapping 抛异常时异常传播给所有等待者（不重复执行）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
class AtomicOperationsTest {

  /** 并发线程数 */
  private static final int THREADS = 16;

  @Test
  @DisplayName("computeIfAbsent 原子性：16 线程并发，mappingFunction 仅执行一次")
  void computeIfAbsentShouldExecuteMappingOnceUnderConcurrency() throws Exception {
    com.njydsz.common.cache.api.Cache<String, String> cache =
        YdszCache.<String, String>newBuilder().build();
    AtomicInteger mappingCount = new AtomicInteger();
    CountDownLatch ready = new CountDownLatch(THREADS);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(THREADS);

    ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    try {
      for (int i = 0; i < THREADS; i++) {
        pool.submit(
            () -> {
              ready.countDown();
              try {
                start.await();
                cache.computeIfAbsent(
                    "atomic-key",
                    k -> {
                      mappingCount.incrementAndGet();
                      sleepQuietly(50);
                      return "computed";
                    });
              } catch (Exception ignored) {
                // 计数不受个别线程异常影响
              } finally {
                done.countDown();
              }
            });
      }
      ready.await();
      start.countDown();
      assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
    } finally {
      pool.shutdownNow();
    }

    assertThat(mappingCount.get()).as("并发下 mappingFunction 应仅执行一次").isEqualTo(1);
    assertThat(cache.getIfPresent("atomic-key")).isEqualTo("computed");
  }

  @Test
  @DisplayName("putIfAbsent 原子性：并发下仅一个线程写入成功，其余读到既有值")
  void putIfAbsentShouldAllowSingleWriterUnderConcurrency() throws Exception {
    com.njydsz.common.cache.api.Cache<String, Integer> cache =
        YdszCache.<String, Integer>newBuilder().type(CacheType.STRIPED).build();
    AtomicInteger nullReturns = new AtomicInteger();
    CountDownLatch ready = new CountDownLatch(THREADS);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(THREADS);

    ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    try {
      for (int i = 0; i < THREADS; i++) {
        final int idx = i;
        pool.submit(
            () -> {
              ready.countDown();
              try {
                start.await();
                Integer previous = cache.putIfAbsent("race-key", idx);
                if (previous == null) {
                  nullReturns.incrementAndGet();
                }
              } catch (Exception ignored) {
                // 计数不受个别线程异常影响
              } finally {
                done.countDown();
              }
            });
      }
      ready.await();
      start.countDown();
      assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
    } finally {
      pool.shutdownNow();
    }

    assertThat(nullReturns.get()).as("putIfAbsent 并发下应仅一个线程拿到 null（写入成功）").isEqualTo(1);
    assertThat(cache.getIfPresent("race-key")).isNotNull();
  }

  @Test
  @DisplayName("merge 原子性：16 线程并发叠加计数无丢失")
  void mergeShouldSerializeConcurrentUpdates() throws Exception {
    com.njydsz.common.cache.api.Cache<String, Long> cache =
        YdszCache.<String, Long>newBuilder().build();
    CountDownLatch ready = new CountDownLatch(THREADS);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(THREADS);

    ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    try {
      for (int i = 0; i < THREADS; i++) {
        pool.submit(
            () -> {
              ready.countDown();
              try {
                start.await();
                cache.merge("counter-key", 1L, Long::sum);
              } catch (Exception ignored) {
                // 计数不受个别线程异常影响
              } finally {
                done.countDown();
              }
            });
      }
      ready.await();
      start.countDown();
      assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
    } finally {
      pool.shutdownNow();
    }

    // 旧 default 实现为 check-then-act，并发下必然丢失更新（< THREADS）；
    // 原子实现应恰好等于 THREADS
    assertThat(cache.getIfPresent("counter-key"))
        .as("merge 并发叠加应无丢失")
        .isEqualTo((long) THREADS);
  }

  @Test
  @DisplayName("computeIfAbsent 失败传播：mapping 异常直达所有等待者，mapping 仅执行一次")
  void computeIfAbsentFailureShouldPropagateWithoutReExecution() throws Exception {
    com.njydsz.common.cache.api.Cache<String, String> cache =
        YdszCache.<String, String>newBuilder().build();
    AtomicInteger mappingCount = new AtomicInteger();
    AtomicInteger failureCount = new AtomicInteger();
    CountDownLatch ready = new CountDownLatch(THREADS);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(THREADS);

    ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    try {
      for (int i = 0; i < THREADS; i++) {
        pool.submit(
            () -> {
              ready.countDown();
              try {
                start.await();
                cache.computeIfAbsent(
                    "failing-key",
                    k -> {
                      mappingCount.incrementAndGet();
                      sleepQuietly(50);
                      throw new IllegalStateException("mapping failed");
                    });
              } catch (Exception e) {
                if (e instanceof IllegalStateException && "mapping failed".equals(e.getMessage())) {
                  failureCount.incrementAndGet();
                }
              } finally {
                done.countDown();
              }
            });
      }
      ready.await();
      start.countDown();
      assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
    } finally {
      pool.shutdownNow();
    }

    assertThat(mappingCount.get()).as("失败不得引发 mapping 重复执行").isEqualTo(1);
    assertThat(failureCount.get()).as("异常应传播给全部调用线程").isEqualTo(THREADS);
  }

  @Test
  @DisplayName("getAsync 单飞（P1 修复）：16 线程并发异步加载，loader 仅执行一次，结果共享")
  void getAsyncShouldShareSingleLoadUnderConcurrency() throws Exception {
    com.njydsz.common.cache.api.Cache<String, String> cache =
        YdszCache.<String, String>newBuilder().build();
    AtomicInteger loadCount = new AtomicInteger();
    java.util.concurrent.atomic.AtomicReference<String> firstResult =
        new java.util.concurrent.atomic.AtomicReference<>();
    CountDownLatch ready = new CountDownLatch(THREADS);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(THREADS);

    ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    try {
      for (int i = 0; i < THREADS; i++) {
        pool.submit(
            () -> {
              ready.countDown();
              try {
                start.await();
                cache
                    .getAsync(
                        "async-key",
                        k -> {
                          loadCount.incrementAndGet();
                          sleepQuietly(50);
                          return CompletableFuture.completedFuture("async-loaded");
                        })
                    .whenComplete(
                        (v, e) -> {
                          if (e == null && v != null) {
                            firstResult.compareAndSet(null, v);
                          }
                          done.countDown();
                        });
              } catch (Exception ignored) {
                done.countDown();
              }
            });
      }
      ready.await();
      start.countDown();
      assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
    } finally {
      pool.shutdownNow();
    }

    // 旧实现每个并发调用方各自执行 loader（loadCount == THREADS）；
    // 单飞修复后应仅执行一次，全部等待方共享同一结果
    assertThat(loadCount.get()).as("并发异步加载应共享单次回源").isEqualTo(1);
    assertThat(cache.getIfPresent("async-key")).isEqualTo("async-loaded");
    assertThat(firstResult.get()).isEqualTo("async-loaded");
  }

  @Test
  @DisplayName("null 键统计口径统一：返回 null 且不计 miss（TINYLFU/STRIPED/Expirable 一致）")
  void nullKeyShouldNotCountAsMissAcrossImplementations() {
    // TINYLFU（默认类型）
    com.njydsz.common.cache.api.Cache<String, String> tinylfu =
        YdszCache.<String, String>newBuilder().build();
    assertThat(tinylfu.getIfPresent(null)).isNull();
    assertThat(tinylfu.getStats().getMissCount())
        .as("TINYLFU: null 键不应计入 miss")
        .isZero();

    // STRIPED
    com.njydsz.common.cache.api.Cache<String, String> striped =
        YdszCache.<String, String>newBuilder().type(CacheType.STRIPED).build();
    assertThat(striped.getIfPresent(null)).isNull();
    assertThat(striped.getStats().getMissCount())
        .as("STRIPED: null 键不应计入 miss")
        .isZero();

    // Expirable 装饰器（TTL 路径，旧实现计 miss——修复对象）
    com.njydsz.common.cache.api.Cache<String, String> expirable =
        YdszCache.<String, String>newBuilder().expireAfterWrite(60, TimeUnit.SECONDS).build();
    assertThat(expirable.getIfPresent(null)).isNull();
    assertThat(expirable.getStats().getMissCount())
        .as("Expirable: null 键不应计入 miss")
        .isZero();
  }

  /** 静默休眠（测试辅助，不抛检查异常） */
  private static void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
