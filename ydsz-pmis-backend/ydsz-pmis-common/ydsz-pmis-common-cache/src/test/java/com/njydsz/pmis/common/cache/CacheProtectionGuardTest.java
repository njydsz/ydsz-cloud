package com.njydsz.pmis.common.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.pmis.common.cache.api.Cache;
import com.njydsz.pmis.common.cache.api.CacheProtectionGuard;
import com.njydsz.pmis.common.cache.builder.CacheBuilder;
import com.njydsz.pmis.common.cache.builder.CacheType;

/**
 * CacheProtectionGuard 单元测试
 *
 * <p>覆盖场景：
 *
 * <ul>
 *   <li>防穿透：加载器返回 null 时缓存空标记
 *   <li>防击穿：并发请求只有一个执行加载
 *   <li>防雪崩：空值占位符随机过期
 *   <li>正常加载：缓存命中后不再调用加载器
 *   <li>参数校验
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@DisplayName("CacheProtectionGuard 单元测试")
class CacheProtectionGuardTest {

  @Test
  @DisplayName("正常加载：缓存未命中时调用加载器并写入缓存")
  void shouldLoadOnMiss() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .type(CacheType.LRU)
            .maximumSize(100)
            .build();

    AtomicInteger loadCount = new AtomicInteger(0);
    String result =
        CacheProtectionGuard.getWithProtection(
            cache,
            "key1",
            k -> {
              loadCount.incrementAndGet();
              return "value-" + k;
            },
            1000,
            5000);

    assertThat(result).isEqualTo("value-key1");
    assertThat(loadCount.get()).isEqualTo(1);
    assertThat(cache.getIfPresent("key1")).isEqualTo("value-key1");
  }

  @Test
  @DisplayName("缓存命中时不调用加载器")
  void shouldNotLoadOnHit() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .type(CacheType.LRU)
            .maximumSize(100)
            .build();

    cache.put("key1", "value1");

    AtomicInteger loadCount = new AtomicInteger(0);
    String result =
        CacheProtectionGuard.getWithProtection(
            cache,
            "key1",
            k -> {
              loadCount.incrementAndGet();
              return "should-not-be-called";
            },
            1000,
            5000);

    assertThat(result).isEqualTo("value1");
    assertThat(loadCount.get()).isZero();
  }

  @Test
  @DisplayName("防穿透：加载器返回 null 时注册空值占位符")
  void shouldRegisterNullPlaceholderOnNullLoad() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .type(CacheType.LRU)
            .maximumSize(100)
            .build();

    AtomicInteger loadCount = new AtomicInteger(0);
    String result1 =
        CacheProtectionGuard.getWithProtection(
            cache,
            "nullKey",
            k -> {
              loadCount.incrementAndGet();
              return null;
            },
            1000,
            5000);

    assertThat(result1).isNull();
    assertThat(loadCount.get()).isEqualTo(1);
    assertThat(CacheProtectionGuard.isNullPlaceholderKey(cache, "nullKey")).isTrue();

    // 第二次访问应命中空值占位符，不再调用加载器
    String result2 =
        CacheProtectionGuard.getWithProtection(
            cache,
            "nullKey",
            k -> {
              loadCount.incrementAndGet();
              return "should-not-be-called";
            },
            1000,
            5000);

    assertThat(result2).isNull();
    assertThat(loadCount.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("防击穿：并发请求只有一个执行加载")
  void shouldPreventCacheBreakdown() throws InterruptedException {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .type(CacheType.LRU)
            .maximumSize(100)
            .build();

    AtomicInteger loadCount = new AtomicInteger(0);
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
                  "concurrentKey",
                  k -> {
                    loadCount.incrementAndGet();
                    try {
                      Thread.sleep(100);
                    } catch (InterruptedException e) {
                      Thread.currentThread().interrupt();
                    }
                    return "loaded-value";
                  },
                  1000,
                  5000);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            } finally {
              doneLatch.countDown();
            }
          });
    }

    startLatch.countDown();
    assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();
    executor.shutdown();

    // 加载器应该只被调用一次（防击穿）
    assertThat(loadCount.get()).isEqualTo(1);
    assertThat(cache.getIfPresent("concurrentKey")).isEqualTo("loaded-value");
  }

  @Test
  @DisplayName("参数校验：minExpireMs > maxExpireMs 抛异常")
  void shouldRejectInvalidExpireRange() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .type(CacheType.LRU)
            .maximumSize(10)
            .build();

    assertThatThrownBy(
                () ->
                    CacheProtectionGuard.getWithProtection(
                        cache, "key", k -> "value", 5000, 1000))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("loader 为 null 时直接返回缓存值或 null")
  void shouldReturnNullWhenLoaderIsNullAndCacheMisses() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .type(CacheType.LRU)
            .maximumSize(10)
            .build();

    String result = CacheProtectionGuard.getWithProtection(cache, "missing", null, 1000, 5000);
    assertThat(result).isNull();
  }
}
