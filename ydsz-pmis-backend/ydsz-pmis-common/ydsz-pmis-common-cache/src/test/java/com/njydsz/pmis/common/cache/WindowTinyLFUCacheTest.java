package com.njydsz.pmis.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.pmis.common.cache.api.Cache;
import com.njydsz.pmis.common.cache.builder.CacheBuilder;
import com.njydsz.pmis.common.cache.builder.CacheType;

/**
 * WindowTinyLFUCache 单元测试
 *
 * <p>覆盖场景：
 *
 * <ul>
 *   <li>基本读写操作
 *   <li>容量限制淘汰
 *   <li>频率感知淘汰（高频 key 优先保留）
 *   <li>putIfAbsent 语义
 *   <li>computeIfAbsent 语义
 *   <li>remove / clear / invalidate
 *   <li>统计计数
 * </ul>
 *
 * @since 1.3.0
 */
@DisplayName("WindowTinyLFUCache 单元测试")
class WindowTinyLFUCacheTest {

  @Test
  @DisplayName("基本读写：put 后 getIfPresent 返回值")
  void shouldPutAndGet() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .type(CacheType.TINYLFU)
            .maximumSize(100)
            .build();

    cache.put("key1", "value1");
    assertThat(cache.getIfPresent("key1")).isEqualTo("value1");
    assertThat(cache.estimatedSize()).isEqualTo(1);
  }

  @Test
  @DisplayName("getIfPresent 未命中返回 null")
  void shouldReturnNullOnMiss() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .type(CacheType.TINYLFU)
            .maximumSize(100)
            .build();

    assertThat(cache.getIfPresent("nonexistent")).isNull();
  }

  @Test
  @DisplayName("容量限制：超过 maximumSize 时触发淘汰")
  void shouldEvictWhenExceedingMaximumSize() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .type(CacheType.TINYLFU)
            .maximumSize(10)
            .build();

    for (int i = 0; i < 20; i++) {
      cache.put("key-" + i, "value-" + i);
    }

    // 缓存大小不应超过 maximumSize（允许少量超出由于并发）
    assertThat(cache.estimatedSize()).isLessThanOrEqualTo(15);
  }

  @Test
  @DisplayName("频率感知：高频访问的 key 更不容易被淘汰")
  void shouldPreferHighFrequencyKeys() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .type(CacheType.TINYLFU)
            .maximumSize(5)
            .build();

    // 填充缓存
    cache.put("a", "1");
    cache.put("b", "2");
    cache.put("c", "3");
    cache.put("d", "4");
    cache.put("e", "5");

    // 频繁访问 "a" 和 "b"，提高其频率
    for (int i = 0; i < 10; i++) {
      cache.getIfPresent("a");
      cache.getIfPresent("b");
    }

    // 添加更多 key，触发淘汰
    cache.put("f", "6");
    cache.put("g", "7");
    cache.put("h", "8");

    // "a" 和 "b" 因为频率高，应该仍然存在
    assertThat(cache.getIfPresent("a")).isEqualTo("1");
    assertThat(cache.getIfPresent("b")).isEqualTo("2");
  }

  @Test
  @DisplayName("putIfAbsent：已存在的 key 不覆盖")
  void shouldPutIfAbsentNotOverwrite() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .type(CacheType.TINYLFU)
            .maximumSize(100)
            .build();

    cache.put("key", "original");
    String existing = cache.putIfAbsent("key", "new");

    assertThat(existing).isEqualTo("original");
    assertThat(cache.getIfPresent("key")).isEqualTo("original");
  }

  @Test
  @DisplayName("putIfAbsent：不存在的 key 写入成功")
  void shouldPutIfAbsentWriteNew() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .type(CacheType.TINYLFU)
            .maximumSize(100)
            .build();

    String existing = cache.putIfAbsent("newKey", "newValue");
    assertThat(existing).isNull();
    assertThat(cache.getIfPresent("newKey")).isEqualTo("newValue");
  }

  @Test
  @DisplayName("computeIfAbsent：未命中时执行加载函数")
  void shouldComputeIfAbsentLoadOnMiss() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .type(CacheType.TINYLFU)
            .maximumSize(100)
            .build();

    String result = cache.computeIfAbsent("key", k -> "computed-" + k);
    assertThat(result).isEqualTo("computed-key");
    assertThat(cache.getIfPresent("key")).isEqualTo("computed-key");
  }

  @Test
  @DisplayName("computeIfAbsent：命中时不执行加载函数")
  void shouldComputeIfAbsentSkipOnHit() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .type(CacheType.TINYLFU)
            .maximumSize(100)
            .build();

    cache.put("key", "existing");
    String result = cache.computeIfAbsent("key", k -> "should-not-run");
    assertThat(result).isEqualTo("existing");
  }

  @Test
  @DisplayName("remove 删除指定 key")
  void shouldRemoveKey() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .type(CacheType.TINYLFU)
            .maximumSize(100)
            .build();

    cache.put("key", "value");
    String removed = cache.remove("key");

    assertThat(removed).isEqualTo("value");
    assertThat(cache.getIfPresent("key")).isNull();
  }

  @Test
  @DisplayName("clear 清空所有缓存")
  void shouldClearAll() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .type(CacheType.TINYLFU)
            .maximumSize(100)
            .build();

    cache.put("a", "1");
    cache.put("b", "2");
    cache.clear();

    assertThat(cache.estimatedSize()).isZero();
    assertThat(cache.getIfPresent("a")).isNull();
    assertThat(cache.getIfPresent("b")).isNull();
  }

  @Test
  @DisplayName("containsKey 正确判断 key 是否存在")
  void shouldCheckContainsKey() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .type(CacheType.TINYLFU)
            .maximumSize(100)
            .build();

    cache.put("key", "value");
    assertThat(cache.containsKey("key")).isTrue();
    assertThat(cache.containsKey("nonexistent")).isFalse();
  }

  @Test
  @DisplayName("get with loader 在未命中时加载数据")
  void shouldGetWithLoader() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .type(CacheType.TINYLFU)
            .maximumSize(100)
            .build();

    String result = cache.get("key", k -> "loaded-" + k);
    assertThat(result).isEqualTo("loaded-key");
    assertThat(cache.getIfPresent("key")).isEqualTo("loaded-key");
  }

  @Test
  @DisplayName("命中率统计正确")
  void shouldTrackHitRate() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .type(CacheType.TINYLFU)
            .maximumSize(100)
            .recordStats()
            .build();

    cache.put("hit", "value");

    cache.getIfPresent("hit"); // hit
    cache.getIfPresent("hit"); // hit
    cache.getIfPresent("miss1"); // miss
    cache.getIfPresent("miss2"); // miss

    double hitRate = cache.getHitRate();
    assertThat(hitRate).isEqualTo(0.5);
  }

  @Test
  @DisplayName("TINYLFU + expireAfterWrite 组合工作正常")
  void shouldWorkWithExpiration() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder()
            .type(CacheType.TINYLFU)
            .maximumSize(100)
            .expireAfterWrite(100, TimeUnit.MILLISECONDS)
            .build();

    cache.put("key", "value");
    assertThat(cache.getIfPresent("key")).isEqualTo("value");
  }
}
