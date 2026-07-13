package com.njydsz.pmis.common.cache.internal.lru;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LRUCacheTest {

  private LRUCache<String, Integer> cache;

  @BeforeEach
  void setUp() {
    cache = new LRUCache<>(3);
  }

  @Nested
  @DisplayName("基础操作")
  class BasicOperations {

    @Test
    @DisplayName("put 和 getIfPresent 正常工作")
    void putAndGet() {
      cache.put("a", 1);
      assertThat(cache.getIfPresent("a")).isEqualTo(1);
    }

    @Test
    @DisplayName("getIfPresent 不存在的 key 返回 null")
    void getIfPresentMiss() {
      assertThat(cache.getIfPresent("nonexistent")).isNull();
    }

    @Test
    @DisplayName("put 覆盖已有值")
    void putOverwrite() {
      cache.put("a", 1);
      cache.put("a", 2);
      assertThat(cache.getIfPresent("a")).isEqualTo(2);
    }

    @Test
    @DisplayName("null key 和 null value 可以存入（LinkedHashMap 允许）")
    void nullKeyAndValue() {
      cache.put(null, 1);
      assertThat(cache.getIfPresent(null)).isEqualTo(1);
      cache.put("a", null);
      // LinkedHashMap 允许 null value，但 getIfPresent 返回 null 无法区分"值为null"和"key不存在"
      assertThat(cache.containsKey("a")).isTrue();
    }

    @Test
    @DisplayName("remove 正常工作")
    void remove() {
      cache.put("a", 1);
      assertThat(cache.remove("a")).isEqualTo(1);
      assertThat(cache.getIfPresent("a")).isNull();
    }

    @Test
    @DisplayName("remove 不存在的 key 返回 null")
    void removeNonExistent() {
      assertThat(cache.remove("nonexistent")).isNull();
    }

    @Test
    @DisplayName("clear 清空缓存")
    void clear() {
      cache.put("a", 1);
      cache.put("b", 2);
      cache.clear();
      assertThat(cache.estimatedSize()).isZero();
    }

    @Test
    @DisplayName("containsKey 正常工作")
    void containsKey() {
      cache.put("a", 1);
      assertThat(cache.containsKey("a")).isTrue();
      assertThat(cache.containsKey("b")).isFalse();
    }

    @Test
    @DisplayName("estimatedSize 正确")
    void estimatedSize() {
      assertThat(cache.estimatedSize()).isZero();
      cache.put("a", 1);
      assertThat(cache.estimatedSize()).isEqualTo(1);
      cache.put("b", 2);
      assertThat(cache.estimatedSize()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("LRU 淘汰策略")
  class EvictionPolicy {

    @Test
    @DisplayName("容量满时淘汰最久未访问的条目")
    void evictLeastRecentlyUsed() {
      cache.put("a", 1);
      cache.put("b", 2);
      cache.put("c", 3);
      // a 是最久未访问的
      cache.put("d", 4); // 应该淘汰 a
      assertThat(cache.getIfPresent("a")).isNull();
      assertThat(cache.getIfPresent("b")).isEqualTo(2);
      assertThat(cache.getIfPresent("c")).isEqualTo(3);
      assertThat(cache.getIfPresent("d")).isEqualTo(4);
    }

    @Test
    @DisplayName("访问后更新最近使用顺序")
    void accessUpdatesRecency() {
      cache.put("a", 1);
      cache.put("b", 2);
      cache.put("c", 3);
      // 访问 a，使其变为最近使用
      cache.getIfPresent("a");
      // 现在 b 是最久未访问的
      cache.put("d", 4); // 应该淘汰 b
      assertThat(cache.getIfPresent("a")).isEqualTo(1);
      assertThat(cache.getIfPresent("b")).isNull();
      assertThat(cache.getIfPresent("c")).isEqualTo(3);
      assertThat(cache.getIfPresent("d")).isEqualTo(4);
    }
  }

  @Nested
  @DisplayName("统计信息")
  class Statistics {

    @Test
    @DisplayName("命中和未命中计数正确")
    void hitAndMissCount() {
      cache.put("a", 1);
      cache.getIfPresent("a"); // hit
      cache.getIfPresent("b"); // miss
      assertThat(cache.getStats().getHitCount()).isEqualTo(1);
      assertThat(cache.getStats().getMissCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("命中率计算正确")
    void hitRate() {
      cache.put("a", 1);
      cache.getIfPresent("a"); // hit
      cache.getIfPresent("a"); // hit
      cache.getIfPresent("b"); // miss
      assertThat(cache.getHitRate()).isCloseTo(2.0 / 3, Offset.offset(0.001));
    }
  }

  @Nested
  @DisplayName("删除监听器")
  class RemovalListener {

    @Test
    @DisplayName("淘汰时触发监听器")
    void evictionTriggersListener() {
      StringBuilder log = new StringBuilder();
      cache.addListener((key, value, cause) -> log.append(key).append(":").append(cause));
      cache.put("a", 1);
      cache.put("b", 2);
      cache.put("c", 3);
      cache.put("d", 4); // 淘汰 a
      assertThat(log.toString()).contains("a:SIZE");
    }

    @Test
    @DisplayName("显式删除时触发监听器")
    void explicitRemovalTriggersListener() {
      StringBuilder log = new StringBuilder();
      cache.addListener((key, value, cause) -> log.append(key).append(":").append(cause));
      cache.put("a", 1);
      cache.remove("a");
      assertThat(log.toString()).isEqualTo("a:EXPLICIT");
    }
  }
}
