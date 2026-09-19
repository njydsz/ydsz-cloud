package com.njydsz.common.cache.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.internal.concurrent.StripedConcurrentCache;
import com.njydsz.common.cache.support.CacheLoader;

/**
 * StripedConcurrentCache 分段锁缓存测试。
 *
 * <p>覆盖：基本操作、容量淘汰、并发安全。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
class StripedConcurrentCacheTest {

  private Cache<String, String> cache;

  @BeforeEach
  void setUp() {
    cache = new StripedConcurrentCache<>(100);
  }

  @Nested
  @DisplayName("基本操作")
  class BasicTest {

    @Test
    @DisplayName("put/get/remove 正常")
    void putGetRemove_shouldWork() {
      cache.put("k1", "v1");
      assertThat(cache.getIfPresent("k1")).isEqualTo("v1");
      cache.remove("k1");
      assertThat(cache.getIfPresent("k1")).isNull();
    }

    @Test
    @DisplayName("超出容量触发淘汰")
    void exceedingCapacity_shouldEvict() {
      Cache<String, String> smallCache = new StripedConcurrentCache<>(5);
      IntStream.range(0, 15).forEach(i -> smallCache.put("k" + i, "v" + i));
      assertThat(smallCache.estimatedSize()).isLessThanOrEqualTo(5);
    }
  }

  @Nested
  @DisplayName("并发安全")
  class ConcurrencyTest {

    @Test
    @DisplayName("并发 put 最终计数不超过容量")
    void concurrentPut_sizeShouldNotExceedCapacity() throws Exception {
      Cache<String, String> smallCache = new StripedConcurrentCache<>(10);
      int threadCount = 8;
      int itemsPerThread = 50;

      ExecutorService executor = Executors.newFixedThreadPool(threadCount);
      CountDownLatch latch = new CountDownLatch(threadCount);

      IntStream.range(0, threadCount)
          .forEach(
              t ->
                  executor.submit(
                      () -> {
                        try {
                          IntStream.range(0, itemsPerThread)
                              .forEach(i -> smallCache.put("t" + t + ":k" + i, "v"));
                        } finally {
                          latch.countDown();
                        }
                      }));

      assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
      executor.shutdown();

      assertThat(smallCache.estimatedSize()).isLessThanOrEqualTo(10);
    }
  }
}
