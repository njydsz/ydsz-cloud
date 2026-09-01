package com.njydsz.common.cache.api;

import static org.assertj.core.api.Assertions.assertThat;

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
 * 三防（防穿透/防击穿/防雪崩）核心语义并发测试。
 *
 * <p>锁定的不变量：
 *
 * <ul>
 *   <li>防击穿：同一 key 并发请求只允许一个线程执行 loader
 *   <li>防穿透：loader 返回 null 后注册空值占位，占位期内不再回源
 *   <li>占位过期：超过 maxExpireMs 后恢复回源（空值占位不是永久屏蔽）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
class CacheProtectionGuardTest {

  /** 空值占位测试使用的过期区间（毫秒） */
  private static final long NULL_PLACEHOLDER_MIN_MS = 80;

  private static final long NULL_PLACEHOLDER_MAX_MS = 120;

  @Test
  @DisplayName("防击穿：16 线程并发加载同一 key，loader 仅执行一次")
  void concurrentLoadShouldExecuteLoaderOnce() throws Exception {
    Cache<String, String> cache = YdszCache.<String, String>newBuilder().build();
    AtomicInteger loadCount = new AtomicInteger();
    CountDownLatch ready = new CountDownLatch(16);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(16);

    ExecutorService pool = Executors.newFixedThreadPool(16);
    try {
      for (int i = 0; i < 16; i++) {
        pool.submit(
            () -> {
              ready.countDown();
              try {
                start.await();
                cache.getWithProtection(
                    "hot-key",
                    k -> {
                      loadCount.incrementAndGet();
                      sleepQuietly(50);
                      return "loaded";
                    },
                    10_000,
                    20_000);
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

    assertThat(loadCount.get()).as("并发下 loader 应仅执行一次").isEqualTo(1);
    assertThat(cache.getIfPresent("hot-key")).isEqualTo("loaded");
  }

  @Test
  @DisplayName("防穿透：空值占位期内不重复回源")
  void nullPlaceholderShouldBlockSubsequentLoads() {
    Cache<String, String> cache = YdszCache.<String, String>newBuilder().build();
    AtomicInteger loadCount = new AtomicInteger();

    String first =
        cache.getWithProtection("absent", k -> {
          loadCount.incrementAndGet();
          return null;
        }, NULL_PLACEHOLDER_MIN_MS, NULL_PLACEHOLDER_MAX_MS);
    String second =
        cache.getWithProtection("absent", k -> {
          loadCount.incrementAndGet();
          return null;
        }, NULL_PLACEHOLDER_MIN_MS, NULL_PLACEHOLDER_MAX_MS);

    assertThat(first).isNull();
    assertThat(second).isNull();
    assertThat(loadCount.get()).as("占位期内第二次调用不应回源").isEqualTo(1);
    assertThat(cache.isNullPlaceholderKey("absent")).isTrue();
  }

  @Test
  @DisplayName("防雪崩（占位过期）：超过随机过期区间后恢复回源")
  void nullPlaceholderShouldExpireAndReload() {
    Cache<String, String> cache = YdszCache.<String, String>newBuilder().build();
    AtomicInteger loadCount = new AtomicInteger();

    cache.getWithProtection(
        "absent-expire", k -> {
          loadCount.incrementAndGet();
          return null;
        }, NULL_PLACEHOLDER_MIN_MS, NULL_PLACEHOLDER_MAX_MS);
    sleepQuietly(NULL_PLACEHOLDER_MAX_MS + 150);
    String recovered =
        cache.getWithProtection(
            "absent-expire", k -> {
              loadCount.incrementAndGet();
              return "recovered";
            }, NULL_PLACEHOLDER_MIN_MS, NULL_PLACEHOLDER_MAX_MS);

    assertThat(recovered).isEqualTo("recovered");
    assertThat(loadCount.get()).as("占位过期后应再次回源").isEqualTo(2);
  }

  @Test
  @DisplayName("防护状态 per-cache 隔离：两个缓存实例的空值标记互不干扰")
  void protectionStateShouldBeIsolatedPerCache() {
    Cache<String, String> cacheA = YdszCache.<String, String>newBuilder().build();
    Cache<String, String> cacheB = YdszCache.<String, String>newBuilder().build();

    cacheA.getWithProtection("shared-key", k -> null, NULL_PLACEHOLDER_MIN_MS, NULL_PLACEHOLDER_MAX_MS);

    assertThat(cacheA.isNullPlaceholderKey("shared-key")).isTrue();
    assertThat(cacheB.isNullPlaceholderKey("shared-key")).isFalse();
  }

  @Test
  @DisplayName("minExpireMs 大于 maxExpireMs 时拒绝配置")
  void shouldRejectInvalidExpireRange() {
    Cache<String, String> cache =
        YdszCache.<String, String>newBuilder().type(CacheType.STRIPED).build();
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> cache.getWithProtection("k", k -> "v", 200, 100));
  }

  @Test
  @DisplayName("失败传播（P1 修复）：loader 异常直达所有等待者，无递归重试风暴")
  void loaderFailureShouldPropagateToAllWaitersWithoutRetryStorm() throws Exception {
    Cache<String, String> cache = YdszCache.<String, String>newBuilder().build();
    AtomicInteger loadCount = new AtomicInteger();
    AtomicInteger failureCount = new AtomicInteger();
    int threads = 16;
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);

    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      for (int i = 0; i < threads; i++) {
        pool.submit(
            () -> {
              ready.countDown();
              try {
                start.await();
                cache.getWithProtection(
                    "failing-key",
                    k -> {
                      loadCount.incrementAndGet();
                      sleepQuietly(50);
                      throw new IllegalStateException("backend down");
                    },
                    10_000,
                    20_000);
              } catch (Exception e) {
                if (e instanceof IllegalStateException
                    && "backend down".equals(e.getMessage())) {
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

    // 修复后的不变量：loader 仅执行 1 次；所有线程收到同一异常（含加载者与等待者）
    assertThat(loadCount.get()).as("失败不得引发重复回源（旧实现为无界重试风暴）").isEqualTo(1);
    assertThat(failureCount.get()).as("异常应传播给全部调用线程").isEqualTo(threads);
  }

  @Test
  @DisplayName("真实值优先（P1 修复）：空值占位期内其他路径写入的真实值不被屏蔽")
  void realValueWrittenAfterNullPlaceholderShouldNotBeMasked() {
    Cache<String, String> cache = YdszCache.<String, String>newBuilder().build();
    AtomicInteger loadCount = new AtomicInteger();

    // 第一步：loader 返回 null，注册空值占位
    String first =
        cache.getWithProtection(
            "masked-key",
            k -> {
              loadCount.incrementAndGet();
              return null;
            },
            NULL_PLACEHOLDER_MIN_MS,
            NULL_PLACEHOLDER_MAX_MS);
    assertThat(first).isNull();

    // 第二步：占位期内，其他路径（写穿透/上游推送）直接写入真实值
    cache.put("masked-key", "real-value");

    // 第三步：占位未过期，但读取必须返回真实值而非被空值占位符屏蔽
    String second =
        cache.getWithProtection(
            "masked-key",
            k -> {
              loadCount.incrementAndGet();
              return "should-not-load";
            },
            NULL_PLACEHOLDER_MIN_MS,
            NULL_PLACEHOLDER_MAX_MS);

    assertThat(second).as("真实值不得被空值占位符屏蔽").isEqualTo("real-value");
    assertThat(loadCount.get()).as("真实值命中后不应回源").isEqualTo(1);
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
