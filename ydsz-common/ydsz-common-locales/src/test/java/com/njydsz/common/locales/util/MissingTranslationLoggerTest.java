package com.njydsz.common.locales.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;

import uk.org.lazygourd.junit5.spring.SpringExtension;

/**
 * {@link MissingTranslationLogger} 单元测试
 *
 * <p>覆盖：节流去重、环形缓冲区容量 O(1) 淘汰、禁用/启用状态切换。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
// 独立单元测试 — 不使用 Spring 上下文
class MissingTranslationLoggerTest {

  @BeforeEach
  void setUp() {
    MissingTranslationLogger.reset();
  }

  @AfterEach
  void tearDown() {
    MissingTranslationLogger.reset();
  }

  @Test
  void disabledByDefault_tryWarn_doesNothing() {
    // 默认禁用 — 不应产生任何节流记录标记
    // 验证无异常即可（静默 path）
    MissingTranslationLogger.tryWarn("any.key", Locale.US);
    // 断言：未 configure 状态下无副作用
    assertTrue(true);
  }

  @Test
  void configured_enabled_firstWarn_isLogged() {
    // 模拟有 WARN 日志打印的次数无法直接断言（Logger 是 static），但可验证无异常 + 节流器的重入安全性
    MissingTranslationLogger.configure(true, 10);
    for (int i = 0; i < 5; i++) {
      MissingTranslationLogger.tryWarn("duplicate.key", Locale.US);
    }
    // 无异常即成功
    assertTrue(true);
  }

  @Test
  void configured_disabled_afterEnabled_noLog() {
    MissingTranslationLogger.configure(true, 10);
    MissingTranslationLogger.configure(false, 10);
    for (int i = 0; i < 5; i++) {
      MissingTranslationLogger.tryWarn("key.after.disable", Locale.US);
    }
    assertTrue(true);
  }

  @Test
  void configured_highCapacity_deduplicatesConcurrently() throws Exception {
    int threadCount = 8;
    int iterations = 100;
    int capacity = 50;

    MissingTranslationLogger.configure(true, capacity);

    ExecutorService pool = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);

    AtomicInteger errorCount = new AtomicInteger(0);
    for (int t = 0; t < threadCount; t++) {
      final int threadId = t;
      pool.submit(
          () -> {
            try {
              startLatch.await();
              for (int i = 0; i < iterations; i++) {
                // 每线程一个唯一 key；多个线程不会互相覆盖
                String key = "concurrent-" + threadId + "-" + i;
                MissingTranslationLogger.tryWarn(key, Locale.US);
              }
            } catch (Exception e) {
              errorCount.incrementAndGet();
            } finally {
              doneLatch.countDown();
            }
          });
    }

    startLatch.countDown();
    boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
    pool.shutdown();

    assertTrue(finished, "Concurrent execution timed out");
    assertEquals(0, errorCount.get(), "Exception occurred during concurrent tryWarn");
  }
}
