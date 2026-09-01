package com.njydsz.common.cache.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.common.cache.builder.CacheBuilder;
import com.njydsz.common.cache.builder.CacheType;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.api.CachePolicy;
import com.njydsz.common.cache.stats.CacheStats;
import com.njydsz.common.cache.support.AsyncFunction;

/**
 * 缓存接口能力完备性测试（自研路线深度完善，2026-09-01）。
 *
 * <p>覆盖三块新增接口能力的契约：
 *
 * <ul>
 *   <li>getAll(keys, loader) 批量加载：缺失键一次性批量加载、结果写回、null 条目跳过、异常传播、批量统计口径（单批一次）
 *   <li>CacheStats 加载统计：get/getAsync 路径的加载成功/异常次数与耗时（修复前恒为 0）
 *   <li>policy() 策略查询：WindowTinyLFUCache 淘汰策略查询与运行时缩容（修复前无实现）；
 *       StripedConcurrentCache getStats 完整性（修复前覆写丢弃 evictionCount）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
class CacheInterfaceCapabilityTest {

  // ==========================================================================
  // getAll(keys, loader) 批量加载
  // ==========================================================================

  @Test
  @DisplayName("批量加载：缺失键一次性交给 loader，结果合并并写回缓存")
  void getAllShouldBatchLoadMissingKeys() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder().type(CacheType.TINYLFU).build();
    cache.put("cached", "v-cached");

    AtomicReference<Set<String>> loaderInput = new AtomicReference<>();
    AtomicInteger loaderCalls = new AtomicInteger();

    Map<String, String> result =
        cache.getAll(
            Arrays.asList("cached", "m1", "m2"),
            missing -> {
              loaderCalls.incrementAndGet();
              loaderInput.set(new HashSet<>(missing));
              Map<String, String> loaded = new HashMap<>();
              loaded.put("m1", "v-m1");
              loaded.put("m2", "v-m2");
              return loaded;
            });

    assertThat(loaderCalls).hasValue(1);
    // loader 仅收到缺失键，不含已命中键
    assertThat(loaderInput.get()).containsExactlyInAnyOrder("m1", "m2");
    // 命中与加载结果合并
    assertThat(result)
        .containsEntry("cached", "v-cached")
        .containsEntry("m1", "v-m1")
        .containsEntry("m2", "v-m2")
        .hasSize(3);
    // 加载结果已写回缓存：再次批量获取不再调用 loader
    Map<String, String> second =
        cache.getAll(
            Arrays.asList("cached", "m1", "m2"),
            missing -> {
              loaderCalls.incrementAndGet();
              throw new IllegalStateException("不应再触发加载");
            });
    assertThat(loaderCalls).hasValue(1);
    assertThat(second).hasSize(3);
  }

  @Test
  @DisplayName("批量加载：loader 返回的 null 值条目跳过，不写缓存")
  void getAllShouldSkipNullLoadedEntries() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder().type(CacheType.TINYLFU).build();

    Map<String, String> result =
        cache.getAll(
            Arrays.asList("a", "b"),
            missing -> {
              Map<String, String> loaded = new HashMap<>();
              loaded.put("a", "va");
              loaded.put("b", null); // 数据库查不到
              return loaded;
            });

    assertThat(result).containsOnlyKeys("a").containsEntry("a", "va");
    assertThat(cache.getIfPresent("b")).isNull();
  }

  @Test
  @DisplayName("批量加载：loader 异常传播，缓存既有状态不变")
  void getAllShouldPropagateLoaderException() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder().type(CacheType.TINYLFU).build();
    cache.put("kept", "v");

    assertThatThrownBy(
            () ->
                cache.getAll(
                    Arrays.asList("kept", "x"),
                    missing -> {
                      throw new RuntimeException("批量查询失败");
                    }))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("批量查询失败");

    // 既有条目不受影响
    assertThat(cache.getIfPresent("kept")).isEqualTo("v");
  }

  @Test
  @DisplayName("批量加载统计：单批一次加载成功，异常批一次加载异常")
  void getAllShouldRecordBatchLoadStats() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder().type(CacheType.TINYLFU).build();

    cache.getAll(Arrays.asList("a", "b"), missing -> Map.of("a", "1", "b", "2"));
    CacheStats stats = cache.getStats();
    assertThat(stats.getLoadCount()).isEqualTo(1);
    assertThat(stats.getLoadSuccessCount()).isEqualTo(1);
    assertThat(stats.getLoadExceptionCount()).isZero();
    assertThat(stats.getTotalLoadTimeNanos()).isPositive();

    assertThatThrownBy(
            () -> cache.getAll(Arrays.asList("c", "d"), missing -> {
              throw new RuntimeException("boom");
            }))
        .isInstanceOf(RuntimeException.class);
    stats = cache.getStats();
    assertThat(stats.getLoadCount()).isEqualTo(2);
    assertThat(stats.getLoadExceptionCount()).isEqualTo(1);
  }

  // ==========================================================================
  // CacheStats 加载统计（get / getAsync）
  // ==========================================================================

  @Test
  @DisplayName("get(key, loader)：命中不计数，未命中成功/异常各计一次并累计耗时")
  void getShouldRecordLoadStats() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder().type(CacheType.TINYLFU).build();
    cache.put("hit", "v");

    // 命中：不产生加载
    assertThat(cache.get("hit", k -> "never")).isEqualTo("v");
    assertThat(cache.getStats().getLoadCount()).isZero();

    // 未命中加载成功
    assertThat(cache.get("m1", k -> "v-" + k)).isEqualTo("v-m1");
    CacheStats stats = cache.getStats();
    assertThat(stats.getLoadCount()).isEqualTo(1);
    assertThat(stats.getLoadSuccessCount()).isEqualTo(1);
    assertThat(stats.getLoadExceptionCount()).isZero();
    assertThat(stats.getTotalLoadTimeNanos()).isPositive();

    // 未命中加载异常
    assertThatThrownBy(() -> cache.get("m2", k -> {
      throw new RuntimeException("加载失败");
    })).isInstanceOf(RuntimeException.class);
    stats = cache.getStats();
    assertThat(stats.getLoadCount()).isEqualTo(2);
    assertThat(stats.getLoadSuccessCount()).isEqualTo(1);
    assertThat(stats.getLoadExceptionCount()).isEqualTo(1);

    // resetStats 清零加载统计
    cache.resetStats();
    assertThat(cache.getStats().getLoadCount()).isZero();
    assertThat(cache.getStats().getTotalLoadTimeNanos()).isZero();
  }

  @Test
  @DisplayName("getAsync：加载成功/异常计入加载统计（仅加载权持有者计数）")
  void getAsyncShouldRecordLoadStats() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder().type(CacheType.TINYLFU).build();

    AsyncFunction<String, String> okLoader = k -> CompletableFuture.completedFuture("v-" + k);
    cache.getAsync("a", okLoader).join();
    assertThat(cache.getStats().getLoadSuccessCount()).isEqualTo(1);

    AsyncFunction<String, String> failLoader =
        k -> CompletableFuture.failedFuture(new RuntimeException("加载失败"));
    assertThatThrownBy(() -> cache.getAsync("b", failLoader).join())
        .isInstanceOf(java.util.concurrent.CompletionException.class);
    assertThat(cache.getStats().getLoadExceptionCount()).isEqualTo(1);
    assertThat(cache.getStats().getLoadCount()).isEqualTo(2);
  }

  // ==========================================================================
  // policy()：WindowTinyLFUCache 淘汰策略（修复前无实现）
  // ==========================================================================

  @Test
  @DisplayName("TinyLFU policy()：查询容量、运行时缩容立即生效")
  void tinylfuPolicyShouldSupportRuntimeShrink() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder().type(CacheType.TINYLFU).maximumSize(100).build();

    CachePolicy.EvictionPolicy eviction =
        cache.policy().eviction().orElseThrow(() -> new AssertionError("TinyLFU 必须支持淘汰策略查询"));
    assertThat(eviction.getMaximum()).hasValue(100L);
    assertThat(eviction.isWeighted()).isFalse();
    assertThat(eviction.weightedSize()).isEmpty();
    // 过期由 ExpirableCache 装饰器负责，内核不提供
    assertThat(cache.policy().expiration()).isEmpty();

    for (int i = 0; i < 80; i++) {
      cache.put("key-" + i, "v-" + i);
    }
    assertThat(cache.estimatedSize()).isEqualTo(80);

    // 运行时缩容：立即淘汰至新容量
    eviction.setMaximum(10);
    assertThat(eviction.getMaximum()).hasValue(10L);
    assertThat(cache.estimatedSize()).isLessThanOrEqualTo(10);

    // 扩容：放宽后可继续写入
    eviction.setMaximum(50);
    for (int i = 100; i < 140; i++) {
      cache.put("key-" + i, "v-" + i);
    }
    assertThat(cache.estimatedSize()).isLessThanOrEqualTo(50);
  }

  @Test
  @DisplayName("TinyLFU policy()：非法容量（<1、>Integer.MAX_VALUE）拒绝")
  void tinylfuPolicyShouldRejectInvalidMaximum() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder().type(CacheType.TINYLFU).build();
    CachePolicy.EvictionPolicy eviction = cache.policy().eviction().orElseThrow();

    assertThatThrownBy(() -> eviction.setMaximum(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> eviction.setMaximum(-5))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> eviction.setMaximum((long) Integer.MAX_VALUE + 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Striped getStats 完整性：淘汰计数不再被覆写丢弃（有损统计回归）")
  void stripedStatsShouldIncludeEvictionCount() {
    Cache<String, String> cache =
        CacheBuilder.<String, String>newBuilder().type(CacheType.STRIPED).maximumSize(50).build();
    for (int i = 0; i < 300; i++) {
      cache.put("key-" + i, "v-" + i);
    }
    // 修复前：getStats 覆写仅返回 hit/miss，evictionCount 恒为 0
    assertThat(cache.getStats().getEvictionCount()).isPositive();
  }

  @Test
  @DisplayName("批量加载的默认实现（接口层）：直接实现 Cache 的类可用")
  void interfaceDefaultGetAllShouldWorkForDirectImplementations() {
    Cache<String, String> cache = new ManualCacheStub();

    Map<String, String> result =
        cache.getAll(
            Arrays.asList("a", "b"),
            missing -> {
              assertThat(missing).containsExactlyInAnyOrder("a", "b");
              return Map.of("a", "va");
            });

    assertThat(result).containsEntry("a", "va").hasSize(1);
  }

  /** 直接实现 Cache 接口的最小桩（验证 default getAll(keys, loader) 兼容存根） */
  private static final class ManualCacheStub implements Cache<String, String> {
    private final Map<String, String> store = new HashMap<>();

    @Override
    public String getIfPresent(String key) {
      return store.get(key);
    }

    @Override
    public CompletableFuture<String> getAsync(
        String key, AsyncFunction<String, String> loader) {
      return CompletableFuture.completedFuture(getIfPresent(key));
    }

    @Override
    public boolean containsKey(String key) {
      return store.containsKey(key);
    }

    @Override
    public void put(String key, String value) {
      store.put(key, value);
    }

    @Override
    public String remove(String key) {
      return store.remove(key);
    }

    @Override
    public void clear() {
      store.clear();
    }

    @Override
    public long estimatedSize() {
      return store.size();
    }

    @Override
    public double getHitRate() {
      return 0;
    }

    @Override
    public CacheStats getStats() {
      return CacheStats.EMPTY;
    }

    @Override
    public java.util.Set<String> keySet() {
      return store.keySet();
    }

    @Override
    public java.util.Collection<String> values() {
      return store.values();
    }
  }
}
