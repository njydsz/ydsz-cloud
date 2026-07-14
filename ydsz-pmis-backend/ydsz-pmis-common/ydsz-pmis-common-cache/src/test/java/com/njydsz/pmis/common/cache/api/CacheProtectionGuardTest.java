package com.njydsz.pmis.common.cache.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.pmis.common.cache.internal.concurrent.StripedConcurrentCache;

/**
 * CacheProtectionGuard 防穿透/防击穿/防雪崩测试
 *
 * @author ydsz-pmis-team
 */
@DisplayName("CacheProtectionGuard 缓存防护测试")
class CacheProtectionGuardTest {

  private Cache<String, String> newCache() {
    return new StripedConcurrentCache<>(100);
  }

  @Nested
  @DisplayName("防穿透")
  class PenetrationProtection {

    @Test
    @DisplayName("loader 返回 null 时缓存空标记")
    void nullValueCached() {
      Cache<String, String> cache = newCache();
      AtomicInteger loaderCount = new AtomicInteger(0);

      // 第一次调用：loader 返回 null
      String result1 =
          CacheProtectionGuard.getWithProtection(
              cache,
              "key1",
              k -> {
                loaderCount.incrementAndGet();
                return null;
              },
              1000,
              5000);

      assertThat(result1).isNull();
      assertThat(loaderCount.get()).isEqualTo(1);

      // 第二次调用：应命中空标记，不调用 loader
      String result2 =
          CacheProtectionGuard.getWithProtection(
              cache,
              "key1",
              k -> {
                loaderCount.incrementAndGet();
                return null;
              },
              1000,
              5000);

      assertThat(result2).isNull();
      assertThat(loaderCount.get()).isEqualTo(1); // loader 未被再次调用
    }

    @Test
    @DisplayName("空标记过期后重新加载")
    void nullPlaceholderExpires() throws InterruptedException {
      Cache<String, String> cache = newCache();
      AtomicInteger loaderCount = new AtomicInteger(0);

      // 第一次调用：返回 null，设置短过期时间
      CacheProtectionGuard.getWithProtection(
          cache,
          "key1",
          k -> {
            loaderCount.incrementAndGet();
            return null;
          },
          50,
          100);

      // 等待过期
      Thread.sleep(150);

      // 第二次调用：空标记已过期，重新加载
      CacheProtectionGuard.getWithProtection(
          cache,
          "key1",
          k -> {
            loaderCount.incrementAndGet();
            return "value1";
          },
          50,
          100);

      assertThat(loaderCount.get()).isEqualTo(2);
      assertThat(cache.getIfPresent("key1")).isEqualTo("value1");
    }

    @Test
    @DisplayName("isNullPlaceholderKey 检查空标记")
    void isNullPlaceholderKey() {
      Cache<String, String> cache = newCache();

      assertThat(CacheProtectionGuard.isNullPlaceholderKey(cache, "key1")).isFalse();

      CacheProtectionGuard.getWithProtection(cache, "key1", k -> null, 1000, 5000);

      assertThat(CacheProtectionGuard.isNullPlaceholderKey(cache, "key1")).isTrue();
    }
  }

  @Nested
  @DisplayName("防击穿")
  class BreakdownProtection {

    @Test
    @DisplayName("并发请求同一 key 只加载一次")
    void concurrentSingleLoad() throws InterruptedException {
      Cache<String, String> cache = newCache();
      AtomicInteger loaderCount = new AtomicInteger(0);
      int threadCount = 10;
      CountDownLatch startLatch = new CountDownLatch(1);
      CountDownLatch doneLatch = new CountDownLatch(threadCount);
      ExecutorService executor = Executors.newFixedThreadPool(threadCount);

      for (int i = 0; i < threadCount; i++) {
        executor.submit(
            () -> {
              try {
                startLatch.await();
                CacheProtectionGuard.getWithProtection(
                    cache,
                    "hot-key",
                    k -> {
                      loaderCount.incrementAndGet();
                      try {
                        Thread.sleep(50); // 模拟慢加载
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                      }
                      return "loaded-value";
                    },
                    1000,
                    5000);
              } catch (Exception ignored) {
                // ignore
              } finally {
                doneLatch.countDown();
              }
            });
      }

      startLatch.countDown();
      doneLatch.await(5, TimeUnit.SECONDS);
      executor.shutdown();

      // loader 应该只被调用一次（防击穿）
      assertThat(loaderCount.get()).isEqualTo(1);
      assertThat(cache.getIfPresent("hot-key")).isEqualTo("loaded-value");
    }
  }

  @Nested
  @DisplayName("参数校验")
  class ParameterValidation {

    @Test
    @DisplayName("minExpireMs > maxExpireMs 抛出异常")
    void invalidExpireRange() {
      Cache<String, String> cache = newCache();

      assertThatThrownBy(
              () -> CacheProtectionGuard.getWithProtection(cache, "key1", k -> "value", 5000, 1000))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("minExpireMs must be <= maxExpireMs");
    }
  }

  @Nested
  @DisplayName("Per-cache 实例隔离")
  class PerCacheIsolation {

    @Test
    @DisplayName("不同缓存实例的空标记互不影响")
    void differentCacheIsolation() {
      Cache<String, String> cache1 = newCache();
      Cache<String, String> cache2 = newCache();

      // cache1 注册空标记
      CacheProtectionGuard.getWithProtection(cache1, "key1", k -> null, 10000, 50000);

      // cache2 的 key1 不应有空标记
      assertThat(CacheProtectionGuard.isNullPlaceholderKey(cache1, "key1")).isTrue();
      assertThat(CacheProtectionGuard.isNullPlaceholderKey(cache2, "key1")).isFalse();
    }
  }
}
