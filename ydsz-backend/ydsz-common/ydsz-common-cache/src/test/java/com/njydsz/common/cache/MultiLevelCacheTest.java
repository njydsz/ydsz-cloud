package com.njydsz.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheBuilder;
import com.njydsz.common.cache.builder.CacheType;
import com.njydsz.common.cache.multilevel.MultiLevelCache;

/**
 * MultiLevelCache 单元测试
 *
 * <p>覆盖场景：
 *
 * <ul>
 *   <li>L1 命中
 *   <li>L1 未命中 L2 命中 + 回填 L1
 *   <li>批量查询 getAll（利用 multiGet）
 *   <li>写入同时写 L1 和 L2
 *   <li>删除同时删 L1 和 L2
 *   <li>统计计数（L1/L2 命中率）
 * </ul>
 *
 * @since 1.0.0
 */
@DisplayName("MultiLevelCache 单元测试")
class MultiLevelCacheTest {

  /** 创建简单的内存 L2 缓存（模拟 Redis） */
  private Cache<String, String> createInMemoryCache(int maxSize) {
    return CacheBuilder.<String, String>newBuilder()
        .type(CacheType.LRU)
        .maximumSize(maxSize)
        .build();
  }

  @Test
  @DisplayName("L1 命中直接返回")
  void shouldHitL1() {
    Cache<String, String> l1 = createInMemoryCache(100);
    Cache<String, String> l2 = createInMemoryCache(100);
    MultiLevelCache<String, String> cache = new MultiLevelCache<>(l1, l2);

    cache.put("key", "value");
    assertThat(cache.getIfPresent("key")).isEqualTo("value");
    assertThat(cache.getL1HitCount()).isEqualTo(1);
    assertThat(cache.getL2HitCount()).isEqualTo(0);
  }

  @Test
  @DisplayName("L1 未命中 L2 命中并回填 L1")
  void shouldHitL2AndBackfillL1() {
    Cache<String, String> l1 = createInMemoryCache(100);
    Cache<String, String> l2 = createInMemoryCache(100);
    MultiLevelCache<String, String> cache = new MultiLevelCache<>(l1, l2);

    // 直接写入 L2，模拟其他节点写入
    l2.put("key", "value");

    // 查询应从 L2 获取并回填 L1
    assertThat(cache.getIfPresent("key")).isEqualTo("value");
    assertThat(cache.getL1HitCount()).isEqualTo(0);
    assertThat(cache.getL2HitCount()).isEqualTo(1);

    // 再次查询应从 L1 获取
    assertThat(cache.getIfPresent("key")).isEqualTo("value");
    assertThat(cache.getL1HitCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("写入同时写入 L1 和 L2")
  void shouldWriteToBothLevels() {
    Cache<String, String> l1 = createInMemoryCache(100);
    Cache<String, String> l2 = createInMemoryCache(100);
    MultiLevelCache<String, String> cache = new MultiLevelCache<>(l1, l2);

    cache.put("key", "value");

    assertThat(l1.getIfPresent("key")).isEqualTo("value");
    assertThat(l2.getIfPresent("key")).isEqualTo("value");
    assertThat(cache.getWriteCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("删除同时从 L1 和 L2 删除")
  void shouldRemoveFromBothLevels() {
    Cache<String, String> l1 = createInMemoryCache(100);
    Cache<String, String> l2 = createInMemoryCache(100);
    MultiLevelCache<String, String> cache = new MultiLevelCache<>(l1, l2);

    cache.put("key", "value");
    cache.remove("key");

    assertThat(l1.getIfPresent("key")).isNull();
    assertThat(l2.getIfPresent("key")).isNull();
  }

  @Test
  @DisplayName("批量查询利用 multiGet 优化")
  void shouldBatchQueryWithMultiGet() {
    Cache<String, String> l1 = createInMemoryCache(100);
    Cache<String, String> l2 = createInMemoryCache(100);
    MultiLevelCache<String, String> cache = new MultiLevelCache<>(l1, l2);

    // 预写入 L2
    l2.put("a", "1");
    l2.put("b", "2");
    l2.put("c", "3");

    // 批量查询
    Map<String, String> result = cache.getAll(List.of("a", "b", "c", "d"));

    assertThat(result).hasSize(3);
    assertThat(result.get("a")).isEqualTo("1");
    assertThat(result.get("b")).isEqualTo("2");
    assertThat(result.get("c")).isEqualTo("3");
    assertThat(result.containsKey("d")).isFalse();

    // L1 应被回填
    assertThat(l1.getIfPresent("a")).isEqualTo("1");
    assertThat(l1.getIfPresent("b")).isEqualTo("2");
    assertThat(l1.getIfPresent("c")).isEqualTo("3");
  }

  @Test
  @DisplayName("get with loader 在缓存未命中时加载数据")
  void shouldLoadOnMiss() {
    Cache<String, String> l1 = createInMemoryCache(100);
    Cache<String, String> l2 = createInMemoryCache(100);
    MultiLevelCache<String, String> cache = new MultiLevelCache<>(l1, l2);

    String result = cache.get("key", k -> "loaded-" + k);

    assertThat(result).isEqualTo("loaded-key");
    assertThat(l1.getIfPresent("key")).isEqualTo("loaded-key");
    assertThat(l2.getIfPresent("key")).isEqualTo("loaded-key");
  }

  @Test
  @DisplayName("putAll 批量写入同时写入 L1 和 L2")
  void shouldPutAllToBothLevels() {
    Cache<String, String> l1 = createInMemoryCache(100);
    Cache<String, String> l2 = createInMemoryCache(100);
    MultiLevelCache<String, String> cache = new MultiLevelCache<>(l1, l2);

    Map<String, String> data = new HashMap<>();
    data.put("a", "1");
    data.put("b", "2");
    data.put("c", "3");
    cache.putAll(data);

    assertThat(l1.getIfPresent("a")).isEqualTo("1");
    assertThat(l2.getIfPresent("b")).isEqualTo("2");
    assertThat(cache.getWriteCount()).isEqualTo(3);
  }

  @Test
  @DisplayName("clear 同时清空 L1 和 L2")
  void shouldClearBothLevels() {
    Cache<String, String> l1 = createInMemoryCache(100);
    Cache<String, String> l2 = createInMemoryCache(100);
    MultiLevelCache<String, String> cache = new MultiLevelCache<>(l1, l2);

    cache.put("a", "1");
    cache.put("b", "2");
    cache.clear();

    assertThat(l1.estimatedSize()).isEqualTo(0);
    assertThat(l2.estimatedSize()).isEqualTo(0);
  }
}
