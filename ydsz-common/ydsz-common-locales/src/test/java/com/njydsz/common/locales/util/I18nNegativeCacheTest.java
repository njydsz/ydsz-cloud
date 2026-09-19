package com.njydsz.common.locales.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * {@link I18nNegativeCache} 单元测试（包级访问，直接验证 LRU 负缓存行为）
 *
 * <p>覆盖：基础 isMissing/markMissing、LRU 淘汰容量、并发读写安全。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
class I18nNegativeCacheTest {

  @Test
  void isMissing_initially_returnsFalse() {
    I18nNegativeCache cache = new I18nNegativeCache(10);
    assertFalse(cache.isMissing("key1", Locale.US));
  }

  @Test
  void markMissing_thenIsMissing_returnsTrue() {
    I18nNegativeCache cache = new I18nNegativeCache(10);
    cache.markMissing("missing.key", Locale.US);
    assertTrue(cache.isMissing("missing.key", Locale.US));
  }

  @Test
  void markMissing_differentLocale_notInterferes() {
    I18nNegativeCache cache = new I18nNegativeCache(10);
    cache.markMissing("key.same", Locale.US);
    assertTrue(cache.isMissing("key.same", Locale.US));
    assertFalse(cache.isMissing("key.same", Locale.CHINA));
  }

  @Test
  void size_reflectsEntries() {
    I18nNegativeCache cache = new I18nNegativeCache(10);
    cache.markMissing("k1", Locale.US);
    cache.markMissing("k2", Locale.US);
    cache.markMissing("k3", Locale.CHINA);
    assertEquals(3, cache.size());
  }

  @Test
  void clear_resetsCache() {
    I18nNegativeCache cache = new I18nNegativeCache(10);
    cache.markMissing("k1", Locale.US);
    cache.markMissing("k2", Locale.US);
    cache.clear();
    assertEquals(0, cache.size());
    assertFalse(cache.isMissing("k1", Locale.US));
  }

  @Test
  void lruEviction_whenExceedingCapacity() {
    I18nNegativeCache cache = new I18nNegativeCache(3);
    cache.markMissing("k1", Locale.US);
    cache.markMissing("k2", Locale.US);
    cache.markMissing("k3", Locale.US);
    assertEquals(3, cache.size());

    // 新增第 4 个，最久未访问的 k1 应被驱逐
    cache.markMissing("k4", Locale.US);
    assertEquals(3, cache.size());
    // k2/k3 仍然存在（最近未访问顺序在 k4 之前）
    assertTrue(cache.isMissing("k2", Locale.US));
    assertTrue(cache.isMissing("k3", Locale.US));
    assertTrue(cache.isMissing("k4", Locale.US));
  }

  @Test
  void concurrentReadWrite_noExceptions() throws Exception {
    int threadCount = 8;
    int iterations = 200;
    I18nNegativeCache cache = new I18nNegativeCache(50);

    ExecutorService pool = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);
    AtomicInteger errors = new AtomicInteger(0);

    for (int t = 0; t < threadCount; t++) {
      final int threadId = t;
      pool.submit(
          () -> {
            try {
              startLatch.await();
              for (int i = 0; i < iterations; i++) {
                String key = "cache-" + threadId + "-" + i;
                cache.markMissing(key, Locale.US);
                cache.isMissing(key, Locale.US);
              }
            } catch (Exception e) {
              errors.incrementAndGet();
            } finally {
              doneLatch.countDown();
            }
          });
    }

    startLatch.countDown();
    boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
    pool.shutdown();

    assertTrue(completed, "Concurrent execution timed out");
    assertEquals(0, errors.get(), "Errors during concurrent cache access");
  }
}
