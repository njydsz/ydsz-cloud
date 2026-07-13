package com.njydsz.pmis.common.cache.multilevel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.pmis.common.cache.api.Cache;
import com.njydsz.pmis.common.cache.internal.concurrent.StripedConcurrentCache;
import com.njydsz.pmis.common.cache.internal.lru.LRUCache;
import com.njydsz.pmis.common.cache.listener.RemovalCause;
import com.njydsz.pmis.common.cache.listener.RemovalListener;

/**
 * MultiLevelCache 单元测试
 *
 * @author Marvin Lee
 */
@DisplayName("MultiLevelCache 多级缓存测试")
class MultiLevelCacheTest {

  private Cache<String, String> newL1() {
    return new LRUCache<>(100);
  }

  private Cache<String, String> newL2() {
    return new StripedConcurrentCache<>(100);
  }

  @Nested
  @DisplayName("基础读写")
  class BasicOperations {

    @Test
    @DisplayName("put 同时写入 L1 和 L2")
    void putWritesBothLevels() {
      Cache<String, String> l1 = newL1();
      Cache<String, String> l2 = newL2();
      MultiLevelCache<String, String> cache = new MultiLevelCache<>(l1, l2);

      cache.put("key1", "value1");

      assertThat(l1.getIfPresent("key1")).isEqualTo("value1");
      assertThat(l2.getIfPresent("key1")).isEqualTo("value1");
    }

    @Test
    @DisplayName("L1 命中直接返回")
    void l1Hit() {
      Cache<String, String> l1 = newL1();
      Cache<String, String> l2 = newL2();
      MultiLevelCache<String, String> cache = new MultiLevelCache<>(l1, l2);

      cache.put("key1", "value1");
      // 清空 L2 验证只从 L1 读取
      l2.clear();

      assertThat(cache.getIfPresent("key1")).isEqualTo("value1");
      assertThat(cache.getL1HitCount()).isEqualTo(1);
      assertThat(cache.getL2HitCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("L1 未命中 L2 命中，回填 L1")
    void l1MissL2Hit() {
      Cache<String, String> l1 = newL1();
      Cache<String, String> l2 = newL2();
      MultiLevelCache<String, String> cache = new MultiLevelCache<>(l1, l2);

      cache.put("key1", "value1");
      // 清空 L1，验证从 L2 读取并回填
      l1.clear();

      assertThat(cache.getIfPresent("key1")).isEqualTo("value1");
      assertThat(cache.getL1HitCount()).isEqualTo(0);
      assertThat(cache.getL2HitCount()).isEqualTo(1);
      // 验证回填
      assertThat(l1.getIfPresent("key1")).isEqualTo("value1");
    }

    @Test
    @DisplayName("L1 和 L2 都未命中返回 null")
    void bothMiss() {
      MultiLevelCache<String, String> cache = new MultiLevelCache<>(newL1(), newL2());

      assertThat(cache.getIfPresent("missing")).isNull();
    }
  }

  @Nested
  @DisplayName("加载器")
  class LoaderTest {

    @Test
    @DisplayName("get with loader 未命中时加载并写入")
    void getWithLoader() {
      MultiLevelCache<String, String> cache = new MultiLevelCache<>(newL1(), newL2());

      String result = cache.get("key1", k -> "loaded-value");

      assertThat(result).isEqualTo("loaded-value");
      assertThat(cache.getL1Cache().getIfPresent("key1")).isEqualTo("loaded-value");
      assertThat(cache.getL2Cache().getIfPresent("key1")).isEqualTo("loaded-value");
    }

    @Test
    @DisplayName("get with loader 命中时不调用 loader")
    void getWithLoaderHit() {
      MultiLevelCache<String, String> cache = new MultiLevelCache<>(newL1(), newL2());
      cache.put("key1", "existing");

      String result =
          cache.get(
              "key1",
              k -> {
                throw new RuntimeException("should not be called");
              });

      assertThat(result).isEqualTo("existing");
    }
  }

  @Nested
  @DisplayName("删除和清空")
  class EvictionTest {

    @Test
    @DisplayName("remove 同时从 L1 和 L2 删除")
    void remove() {
      MultiLevelCache<String, String> cache = new MultiLevelCache<>(newL1(), newL2());
      cache.put("key1", "value1");

      cache.remove("key1");

      assertThat(cache.getL1Cache().getIfPresent("key1")).isNull();
      assertThat(cache.getL2Cache().getIfPresent("key1")).isNull();
    }

    @Test
    @DisplayName("clear 同时清空 L1 和 L2")
    void clear() {
      MultiLevelCache<String, String> cache = new MultiLevelCache<>(newL1(), newL2());
      cache.put("key1", "value1");
      cache.put("key2", "value2");

      cache.clear();

      assertThat(cache.estimatedSize()).isZero();
    }
  }

  @Nested
  @DisplayName("批量操作")
  class BatchOperations {

    @Test
    @DisplayName("putAll 批量写入两级缓存")
    void putAll() {
      MultiLevelCache<String, String> cache = new MultiLevelCache<>(newL1(), newL2());
      Map<String, String> data = new HashMap<>();
      data.put("k1", "v1");
      data.put("k2", "v2");
      data.put("k3", "v3");

      cache.putAll(data);

      assertThat(cache.getWriteCount()).isEqualTo(3);
      assertThat(cache.getIfPresent("k1")).isEqualTo("v1");
      assertThat(cache.getIfPresent("k2")).isEqualTo("v2");
      assertThat(cache.getIfPresent("k3")).isEqualTo("v3");
    }

    @Test
    @DisplayName("getAll 批量读取")
    void getAll() {
      MultiLevelCache<String, String> cache = new MultiLevelCache<>(newL1(), newL2());
      cache.put("k1", "v1");
      cache.put("k2", "v2");

      Map<String, String> result = cache.getAll(List.of("k1", "k2", "k3"));

      assertThat(result).hasSize(2);
      assertThat(result.get("k1")).isEqualTo("v1");
      assertThat(result.get("k2")).isEqualTo("v2");
    }
  }

  @Nested
  @DisplayName("统计和监听")
  class StatsAndListener {

    @Test
    @DisplayName("命中率统计")
    void hitRate() {
      MultiLevelCache<String, String> cache = new MultiLevelCache<>(newL1(), newL2());
      cache.put("key1", "value1");

      cache.getIfPresent("key1"); // hit
      cache.getIfPresent("missing"); // miss

      assertThat(cache.getHitRate()).isBetween(0.49, 0.51);
    }

    @Test
    @DisplayName("删除监听器触发")
    void removalListener() {
      MultiLevelCache<String, String> cache = new MultiLevelCache<>(newL1(), newL2());
      cache.put("key1", "value1");

      StringBuilder sb = new StringBuilder();
      cache.addListener(
          new RemovalListener<String, String>() {
            @Override
            public void onRemoval(String key, String value, RemovalCause cause) {
              sb.append(key).append(":").append(value).append(":").append(cause);
            }
          });

      cache.remove("key1");

      assertThat(sb.toString()).contains("key1:value1:EXPLICIT");
    }
  }

  @Nested
  @DisplayName("computeIfAbsent")
  class ComputeTest {

    @Test
    @DisplayName("computeIfAbsent 未命中时计算并写入")
    void computeIfAbsent() {
      MultiLevelCache<String, String> cache = new MultiLevelCache<>(newL1(), newL2());

      String result = cache.computeIfAbsent("key1", k -> "computed");

      assertThat(result).isEqualTo("computed");
      assertThat(cache.getL1Cache().getIfPresent("key1")).isEqualTo("computed");
      assertThat(cache.getL2Cache().getIfPresent("key1")).isEqualTo("computed");
    }

    @Test
    @DisplayName("computeIfAbsent 命中时不计算")
    void computeIfAbsentHit() {
      MultiLevelCache<String, String> cache = new MultiLevelCache<>(newL1(), newL2());
      cache.put("key1", "existing");

      String result =
          cache.computeIfAbsent(
              "key1",
              k -> {
                throw new RuntimeException("should not be called");
              });

      assertThat(result).isEqualTo("existing");
    }
  }
}
