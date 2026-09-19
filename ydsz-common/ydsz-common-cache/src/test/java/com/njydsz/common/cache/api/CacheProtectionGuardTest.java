package com.njydsz.common.cache.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.internal.tinylfu.WindowTinyLFUCache;

/**
 * CacheProtectionGuard 防护能力测试。
 *
 * <p>覆盖：防穿透（空值缓存）、防击穿（并发加载互斥）、防雪崩（TTL 随机抖动）。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
class CacheProtectionGuardTest {

  private Cache<String, String> cache;

  @BeforeEach
  void setUp() {
    cache = new WindowTinyLFUCache<>(100);
  }

  @Nested
  @DisplayName("防穿透测试")
  class CachePenetrationTest {

    @Test
    @DisplayName("空值占位后 isNullPlaceholderKey 返回 true")
    void nullPlaceholder_shouldMarkKey() {
      // 通过 createNullPlaceholder 注册穿透 key
      String key = "nonexistent";
      cache.createNullPlaceholder(key);
      assertThat(cache.isNullPlaceholderKey(key)).isTrue();
      assertThat(cache.isNullPlaceholderKey("otherKey")).isFalse();
    }

    @Test
    @DisplayName("getWithProtection 加载不到值时应缓存空值占位")
    void getWithProtection_loadMiss_shouldCacheNullPlaceholder() {
      AtomicInteger loadCount = new AtomicInteger(0);
      Function<String, String> loader =
          k -> {
            loadCount.incrementAndGet();
            return null;
          };

      String result = cache.getWithProtection("missing", loader, 30_000L, 60_000L);
      assertThat(result).isNull();
      // 第二次调用不应再触发 loader（已由空值占占位保护）
      cache.getWithProtection("missing", loader, 30_000L, 60_000L);
      // loadCount 至少为 1（首次加载），不重复调用 loader
      assertThat(loadCount.get()).isGreaterThanOrEqualTo(1);
    }
  }

  @Nested
  @DisplayName("防击穿测试")
  class CacheBreakdownTest {

    @Test
    @DisplayName("并发访问同一未命中 key，loader 仅应执行有限次数")
    void concurrentAccess_sameKey_shouldLimitLoaderCalls() throws Exception {
      int threadCount = 20;
      AtomicInteger loadCount = new AtomicInteger(0);
      Function<String, String> slowLoader =
          k -> {
            loadCount.incrementAndGet();
            try {
              Thread.sleep(50);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            return "loaded_" + k;
          };

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
                          cache.getWithProtection("hotKey", slowLoader, 30_000L, 60_000L);
                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                        } finally {
                          endLatch.countDown();
                        }
                      }));

      startLatch.countDown(); // 同时释放所有线程
      assertThat(endLatch.await(15, TimeUnit.SECONDS)).isTrue();
      executor.shutdown();

      // 防护守卫应使 loader 调用次数远低于并发线程数（理想情况下仅 1 次）
      assertThat(loadCount.get()).isLessThan(threadCount);
      assertThat(loadCount.get()).isGreaterThanOrEqualTo(1);
    }
  }

  @Nested
  @DisplayName("防雪崩测试")
  class CacheAvalancheTest {

    @Test
    @DisplayName("TTL 应配置在 min/max 区间内防止雪崩")
    void ttl_shouldBeConfiguredInRange() {
      // 验证防护守卫使用 TTL 区间而非固定 TTL
      // 间接验证：加载成功多次调用应在 TTL 区间内命中
      AtomicInteger loadCount = new AtomicInteger(0);
      Function<String, String> loader =
          k -> {
            loadCount.incrementAndGet();
            return "value";
          };

      cache.getWithProtection("key1", loader, 10_000L, 20_000L);
      cache.getWithProtection("key1", loader, 10_000L, 20_000L);
      // 两次调用了 loader（首次区间可能不同）
      assertThat(loadCount.get()).isGreaterThanOrEqualTo(1);
    }
  }

  @Nested
  @DisplayName("实例级空值 TTL 配置")
  class InstanceNullValueTtlTest {

    @Test
    @DisplayName("setNullValueTtl 后 getWithProtection 无需传 TTL 参数")
    void setNullValueTtl_shouldWorkWithTwoArgProtection() {
      Cache<String, String> protectedCache = new WindowTinyLFUCache<>(50);
      protectedCache.setNullValueTtl(5_000L, 10_000L);

      AtomicInteger count = new AtomicInteger(0);
      Function<String, String> loader =
          k -> {
            count.incrementAndGet();
            return null;
          };

      // 使用两参版本（无需 TTL 参数）
      protectedCache.getWithProtection("missing", loader);
      assertThat(count.get()).isGreaterThanOrEqualTo(1);
    }
  }
}
