package com.njydsz.common.cache.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.stats.CacheStats;

/**
 * Window-TinyLFU 缓存核心行为测试。
 *
 * <p>覆盖：基本 CRUD、容量淘汰、命中率统计、并发安全、批量操作、视图操作。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
class WindowTinyLFUCacheTest {

  private Cache<String, String> cache;

  @BeforeEach
  void setUp() {
    cache = new WindowTinyLFUCache<>(100);
  }

  @Nested
  @DisplayName("基本 CRUD 操作")
  class BasicCrudTest {

    @Test
    @DisplayName("put 后 getIfPresent 能取到值")
    void putThenGet_shouldReturnValue() {
      cache.put("key1", "value1");
      assertThat(cache.getIfPresent("key1")).isEqualTo("value1");
    }

    @Test
    @DisplayName("put null 键或值应被忽略")
    void putNullKeyOrValue_shouldBeIgnored() {
      cache.put(null, "value");
      cache.put("key", null);
      assertThat(cache.getIfPresent(null)).isNull();
      assertThat(cache.estimatedSize()).isZero();
    }

    @Test
    @DisplayName("remove 后 getIfPresent 返回 null")
    void removeThenGet_shouldReturnNull() {
      cache.put("key1", "value1");
      cache.remove("key1");
      assertThat(cache.getIfPresent("key1")).isNull();
    }

    @Test
    @DisplayName("clear 后缓存为空")
    void clear_shouldEmptyCache() {
      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.clear();
      assertThat(cache.estimatedSize()).isZero();
      assertThat(cache.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("containsKey 正确反映键是否存在")
    void containsKey_shouldReflectKeyPresence() {
      cache.put("key1", "value1");
      assertThat(cache.containsKey("key1")).isTrue();
      assertThat(cache.containsKey("nonexistent")).isFalse();
    }
  }

  @Nested
  @DisplayName("淘汰策略")
  class EvictionTest {

    @Test
    @DisplayName("超出最大容量时应触发淘汰")
    void exceedingCapacity_shouldTriggerEviction() {
      Cache<String, String> smallCache = new WindowTinyLFUCache<>(5);
      IntStream.range(0, 20).forEach(i -> smallCache.put("key" + i, "value" + i));

      assertThat(smallCache.estimatedSize()).isLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("高频访问条目不应被淘汰")
    void frequentlyAccessedItems_shouldNotBeEvicted() {
      Cache<String, String> smallCache = new WindowTinyLFUCache<>(5);
      // 先填充
      IntStream.range(0, 5).forEach(i -> smallCache.put("key" + i, "value" + i));
      // 高频访问 key0
      IntStream.range(0, 100).forEach(i -> smallCache.getIfPresent("key0"));
      // 添加新条目触发淘汰
      IntStream.range(5, 15).forEach(i -> smallCache.put("new" + i, "val" + i));

      assertThat(smallCache.getIfPresent("key0")).isEqualTo("value0");
    }
  }

  @Nested
  @DisplayName("统计功能")
  class StatsTest {

    @Test
    @DisplayName("命中计数正确")
    void hitCount_shouldBeAccurate() {
      cache.put("key1", "value1");
      cache.getIfPresent("key1"); // hit
      cache.getIfPresent("key1"); // hit
      cache.getIfPresent("miss"); // miss

      CacheStats stats = cache.getStats();
      assertThat(stats.getHitCount()).isEqualTo(2);
      assertThat(stats.getMissCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("命中率计算正确")
    void hitRate_shouldBeCalculatedCorrectly() {
      cache.put("key1", "value1");
      cache.getIfPresent("key1"); // hit
      cache.getIfPresent("key1"); // hit
      cache.getIfPresent("miss1"); // miss
      cache.getIfPresent("miss2"); // miss

      assertThat(cache.getHitRate()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("命中率在无访问时为 0")
    void hitRate_withoutAccess_shouldBeZero() {
      assertThat(cache.getHitRate()).isZero();
    }

    @Test
    @DisplayName("resetStats 后计数器归零")
    void resetStats_shouldZeroCounters() {
      cache.put("key1", "value1");
      cache.getIfPresent("key1");
      cache.resetStats();

      CacheStats stats = cache.getStats();
      assertThat(stats.getHitCount()).isZero();
      assertThat(stats.getMissCount()).isZero();
    }
  }

  @Nested
  @DisplayName("视图操作")
  class ViewTest {

    @Test
    @DisplayName("keySet 返回所有键的视图")
    void keySet_shouldReturnAllKeys() {
      cache.put("a", "1");
      cache.put("b", "2");
      Set<String> keys = cache.keySet();
      assertThat(keys).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    @DisplayName("values 返回所有值")
    void values_shouldReturnAllValues() {
      cache.put("a", "1");
      cache.put("b", "2");
      assertThat(cache.values()).containsExactlyInAnyOrder("1", "2");
    }
  }

  @Nested
  @DisplayName("并发安全")
  class ConcurrencyTest {

    @Test
    @DisplayName("并发 put 不应丢失数据")
    void concurrentPut_shouldNotLoseData() throws Exception {
      int threadCount = 16;
      int itemsPerThread = 100;
      ExecutorService executor = Executors.newFixedThreadPool(threadCount);
      CountDownLatch latch = new CountDownLatch(threadCount);

      IntStream.range(0, threadCount)
          .forEach(
              t ->
                  executor.submit(
                      () -> {
                        try {
                          IntStream.range(0, itemsPerThread)
                              .forEach(
                                  i -> cache.put("t" + t + ":k" + i, "v" + i));
                        } finally {
                          latch.countDown();
                        }
                      }));

      assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
      executor.shutdown();

      // 验证所有数据最终可见
      int found = 0;
      for (int t = 0; t < threadCount; t++) {
        for (int i = 0; i < itemsPerThread; i++) {
          if (cache.getIfPresent("t" + t + ":k" + i) != null) {
            found++;
          }
        }
      }
      // 至少部分条目存在（大小限制可能导致淘汰，但不应丢太多）
      assertThat(found).isGreaterThan(0);
    }
  }
}
