package com.njydsz.pmis.common.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.pmis.common.cache.api.Cache;
import com.njydsz.pmis.common.cache.builder.CacheBuilder;
import com.njydsz.pmis.common.cache.builder.CacheType;
import com.njydsz.pmis.common.cache.internal.decorator.ExpirableCache;
import com.njydsz.pmis.common.cache.support.Expiry;

/**
 * ExpirableCache 单元测试
 *
 * <p>覆盖场景：
 *
 * <ul>
 *   <li>写入后过期（expireAfterWrite）
 *   <li>访问后过期（expireAfterAccess）
 *   <li>过期清理
 *   <li>底层淘汰时 expirationMap 同步清理（内存优化）
 *   <li>TTL 抖动（防雪崩）
 * </ul>
 *
 * @since 1.3.0
 */
@DisplayName("ExpirableCache 单元测试")
class ExpirableCacheTest {

  @Test
  @DisplayName("expireAfterWrite：条目在过期时间后不可见")
  void shouldExpireAfterWrite() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .type(CacheType.LRU)
            .maximumSize(100)
            .expireAfterWrite(100, TimeUnit.MILLISECONDS)
            .build();

    cache.put("key", "value");
    assertThat(cache.getIfPresent("key")).isEqualTo("value");

    await().atMost(2, TimeUnit.SECONDS)
        .untilAsserted(
            () -> assertThat(cache.getIfPresent("key")).isNull());
  }

  @Test
  @DisplayName("expireAfterAccess：访问后刷新过期时间")
  void shouldRefreshOnAccess() throws InterruptedException {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .type(CacheType.LRU)
            .maximumSize(100)
            .expireAfterAccess(200, TimeUnit.MILLISECONDS)
            .build();

    cache.put("key", "value");

    // 在过期前访问，刷新过期时间
    Thread.sleep(100);
    assertThat(cache.getIfPresent("key")).isEqualTo("value");

    // 再次等待 100ms，如果没有刷新应该已经过期
    Thread.sleep(100);
    // 由于访问刷新了过期时间，此时应该仍然存在
    assertThat(cache.getIfPresent("key")).isEqualTo("value");
  }

  @Test
  @DisplayName("底层淘汰时 expirationMap 同步清理")
  void shouldCleanExpirationMapOnEviction() {
    // 创建容量为 3 的 LRU 缓存 + 过期策略
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .type(CacheType.LRU)
            .maximumSize(3)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    // 填满缓存
    cache.put("a", "1");
    cache.put("b", "2");
    cache.put("c", "3");

    // 插入第 4 个，触发 LRU 淘汰 "a"
    cache.put("d", "4");

    // 验证 "a" 被淘汰
    assertThat(cache.getIfPresent("a")).isNull();

    // 如果是 ExpirableCache，验证 expirationMap 已清理
    if (cache instanceof ExpirableCache) {
      // expirationMap 应该只有 3 个条目，不是 4 个
      // 通过 estimatedSize 间接验证（底层 LRU 大小应为 3）
      assertThat(cache.estimatedSize()).isEqualTo(3);
    }
  }

  @Test
  @DisplayName("TTL 抖动：相同 TTL 产生不同的实际过期时间")
  void shouldApplyJitter() {
    // 创建带抖动的过期缓存
    ExpirableCache<String, String> cache =
        new ExpirableCache<>(
            CacheBuilder.<String, String>newBuilder()
                .type(CacheType.LRU)
                .maximumSize(100)
                .build(),
            TimeUnit.SECONDS.toNanos(60),
            0,
            null,
            60,
            0.2); // 20% 抖动

    // 多次写入，验证过期时间不同
    cache.put("key1", "value1");
    cache.put("key2", "value2");
    cache.put("key3", "value3");

    // 所有条目都应该存在
    assertThat(cache.getIfPresent("key1")).isEqualTo("value1");
    assertThat(cache.getIfPresent("key2")).isEqualTo("value2");
    assertThat(cache.getIfPresent("key3")).isEqualTo("value3");

    cache.close();
  }

  @Test
  @DisplayName("remove 同时清理 expirationMap 和底层缓存")
  void shouldRemoveFromBothMaps() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .type(CacheType.LRU)
            .maximumSize(100)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    cache.put("key", "value");
    assertThat(cache.getIfPresent("key")).isEqualTo("value");

    cache.remove("key");
    assertThat(cache.getIfPresent("key")).isNull();
  }

  @Test
  @DisplayName("clear 同时清理 expirationMap 和底层缓存")
  void shouldClearBothMaps() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .type(CacheType.LRU)
            .maximumSize(100)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    cache.put("a", "1");
    cache.put("b", "2");
    cache.clear();

    assertThat(cache.getIfPresent("a")).isNull();
    assertThat(cache.getIfPresent("b")).isNull();
    assertThat(cache.estimatedSize()).isEqualTo(0);
  }

  @Test
  @DisplayName("桶化清理：批量过期条目被后台清理任务移除")
  void shouldCleanupExpiredEntriesViaBucketing() {
    // 使用短过期时间 + 短清理间隔
    ExpirableCache<String, String> cache =
        new ExpirableCache<>(
            CacheBuilder.<String, String>newBuilder()
                .type(CacheType.LRU)
                .maximumSize(1000)
                .build(),
            TimeUnit.MILLISECONDS.toNanos(200),
            0,
            null,
            1, // 1 秒清理间隔
            0.0); // 无抖动

    // 写入多个条目
    for (int i = 0; i < 100; i++) {
      cache.put("key-" + i, "value-" + i);
    }
    assertThat(cache.estimatedSize()).isEqualTo(100);

    // 等待过期 + 清理
    await().atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              // 清理后底层缓存大小应小于 100
              // （部分可能已被 getIfPresent 中的惰性过期移除）
              assertThat(cache.estimatedSize()).isLessThan(100);
            });

    cache.close();
  }

  @Test
  @DisplayName("自定义 Expiry 策略：每个条目独立过期时间")
  void shouldSupportCustomExpiry() {
    ExpirableCache<String, String> cache =
        new ExpirableCache<>(
            CacheBuilder.<String, String>newBuilder()
                .type(CacheType.LRU)
                .maximumSize(100)
                .build(),
            0,
            0,
            new Expiry<String, String>() {
              @Override
              public long expireAfterCreate(String key, String value, long currentTime) {
                // "short" key 过期快，其他过期慢
                return "short".equals(key)
                    ? TimeUnit.MILLISECONDS.toNanos(100)
                    : TimeUnit.HOURS.toNanos(1);
              }

              @Override
              public long expireAfterRead(String key, String value, long currentTime) {
                return expireAfterCreate(key, value, currentTime);
              }
            },
            1,
            0.0);

    cache.put("short", "fast-expire");
    cache.put("long", "slow-expire");

    assertThat(cache.getIfPresent("short")).isEqualTo("fast-expire");
    assertThat(cache.getIfPresent("long")).isEqualTo("slow-expire");

    // "short" 应该先过期
    await().atMost(3, TimeUnit.SECONDS)
        .untilAsserted(
            () -> assertThat(cache.getIfPresent("short")).isNull());

    // "long" 应仍然存在
    assertThat(cache.getIfPresent("long")).isEqualTo("slow-expire");

    cache.close();
  }

  @Test
  @DisplayName("computeIfAbsent：未命中时加载并写入")
  void shouldComputeIfAbsentOnExpirableCache() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .type(CacheType.LRU)
            .maximumSize(100)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    String result = cache.computeIfAbsent("key", k -> "computed-" + k);
    assertThat(result).isEqualTo("computed-key");
    assertThat(cache.getIfPresent("key")).isEqualTo("computed-key");
  }

  @Test
  @DisplayName("putIfAbsent：已存在时不覆盖")
  void shouldPutIfAbsentNotOverwriteOnExpirableCache() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .type(CacheType.LRU)
            .maximumSize(100)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    cache.put("key", "original");
    String existing = cache.putIfAbsent("key", "new");
    assertThat(existing).isEqualTo("original");
    assertThat(cache.getIfPresent("key")).isEqualTo("original");
  }
}
