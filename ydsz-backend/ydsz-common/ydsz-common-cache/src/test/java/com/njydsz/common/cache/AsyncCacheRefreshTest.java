package com.njydsz.common.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.common.cache.api.AsyncCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheBuilder;
import com.njydsz.common.cache.internal.AsyncCacheAdapter;
import com.njydsz.common.cache.support.AsyncFunction;

import java.util.Collections;
/**
 * {@link AsyncCache#refresh} / {@link AsyncCache#refreshAll} 主动刷新能力测试
 *
 * <p>覆盖场景：
 *
 * <ul>
 *   <li>refresh：强制重新加载，绕过缓存命中
 *   <li>refresh：加载成功后更新缓存
 *   <li>refresh：加载返回 null 时从缓存移除键
 *   <li>refresh：加载失败时保留缓存旧值
 *   <li>refresh：并发刷新同一 key 共享 Future（防击穿）
 *   <li>refreshAll：批量刷新多个 key
 *   <li>refreshAll：批量加载部分失败保留旧值
 *   <li>refreshAll(loader)：默认方法刷新所有 key
 *   <li>参数校验：null key / null loader
 * </ul>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@DisplayName("AsyncCache 主动刷新能力测试")
class AsyncCacheRefreshTest {

  private Cache<String, String> delegate;
  private AsyncCache<String, String> asyncCache;

  @BeforeEach
  void setUp() {
    delegate = CacheBuilder.<String, String>newBuilder().maximumSize(100).build();
    asyncCache = new AsyncCacheAdapter<>(delegate);
  }

  @Test
  @DisplayName("refresh：强制重新加载，绕过缓存命中")
  void refreshShouldBypassCacheHit() throws Exception {
    // 初始缓存值
    delegate.put("k1", "old-value");

    AtomicInteger loadCount = new AtomicInteger(0);
    AsyncFunction<String, String> loader =
        k -> {
          loadCount.incrementAndGet();
          return CompletableFuture.completedFuture("new-value");
        };

    // get 在缓存命中时不调用 loader
    CompletableFuture<String> getResult = asyncCache.get("k1", loader);
    assertThat(getResult.get()).isEqualTo("old-value");
    assertThat(loadCount.get()).isZero();

    // refresh 总是调用 loader，即使缓存命中
    CompletableFuture<String> refreshResult = asyncCache.refresh("k1", loader);
    assertThat(refreshResult.get()).isEqualTo("new-value");
    assertThat(loadCount.get()).isEqualTo(1);

    // 缓存已更新为新值
    assertThat(delegate.getIfPresent("k1")).isEqualTo("new-value");
  }

  @Test
  @DisplayName("refresh：加载返回 null 时从缓存移除键")
  void refreshShouldRemoveKeyWhenLoaderReturnsNull() throws Exception {
    delegate.put("k1", "old-value");
    assertThat(delegate.getIfPresent("k1")).isEqualTo("old-value");

    AsyncFunction<String, String> loader = k -> CompletableFuture.completedFuture(null);

    CompletableFuture<String> result = asyncCache.refresh("k1", loader);

    assertThat(result.get()).isNull();
    assertThat(delegate.getIfPresent("k1")).isNull();
  }

  @Test
  @DisplayName("refresh：加载失败时保留缓存旧值")
  void refreshShouldKeepOldValueOnLoaderFailure() throws Exception {
    delegate.put("k1", "old-value");
    AsyncFunction<String, String> loader =
        k -> {
          throw new RuntimeException("模拟加载失败");
        };

    CompletableFuture<String> result = asyncCache.refresh("k1", loader);

    // Future 异常完成
    assertThatThrownBy(result::join).hasMessageContaining("模拟加载失败");
    // 缓存中的旧值应保留
    assertThat(delegate.getIfPresent("k1")).isEqualTo("old-value");
  }

  @Test
  @DisplayName("refresh：并发刷新同一 key 共享 Future（防击穿）")
  void refreshShouldShareFutureForConcurrentRefresh() throws Exception {
    delegate.put("k1", "old-value");

    AtomicInteger loadCount = new AtomicInteger(0);
    AsyncFunction<String, String> loader =
        k -> {
          loadCount.incrementAndGet();
          // 模拟慢加载
          return CompletableFuture.supplyAsync(
              () -> {
                try {
                  Thread.sleep(100);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                return "new-value";
              });
        };

    // 并发触发 5 次刷新
    CompletableFuture<String>[] futures = new CompletableFuture[5];
    for (int i = 0; i < 5; i++) {
      futures[i] = asyncCache.refresh("k1", loader);
    }

    CompletableFuture.allOf(futures).get();
    // 由于刷新防击穿，loader 应只被调用 1 次
    assertThat(loadCount.get()).isEqualTo(1);
    for (CompletableFuture<String> future : futures) {
      assertThat(future.get()).isEqualTo("new-value");
    }
  }

  @Test
  @DisplayName("refresh：null key 抛 NPE")
  void refreshShouldRejectNullKey() {
    AsyncFunction<String, String> loader = k -> CompletableFuture.completedFuture("v");
    CompletableFuture<String> result = asyncCache.refresh(null, loader);
    // failedFuture(NPE).join() 抛 CompletionException 包装的 NPE
    assertThatThrownBy(result::join)
        .hasCauseInstanceOf(NullPointerException.class)
        .hasMessageContaining("缓存键不能为 null");
  }

  @Test
  @DisplayName("refresh：null loader 抛 NPE")
  void refreshShouldRejectNullLoader() {
    CompletableFuture<String> result = asyncCache.refresh("k1", null);
    // failedFuture(NPE).join() 抛 CompletionException 包装的 NPE
    assertThatThrownBy(result::join)
        .hasCauseInstanceOf(NullPointerException.class)
        .hasMessageContaining("加载器不能为 null");
  }

  @Test
  @DisplayName("refreshAll：批量刷新多个 key")
  void refreshAllShouldBatchRefresh() throws Exception {
    delegate.put("k1", "old1");
    delegate.put("k2", "old2");
    delegate.put("k3", "old3");

    AsyncFunction<Collection<String>, Map<String, String>> loader =
        keys -> {
          Map<String, String> result = new HashMap<>();
          for (String key : keys) {
            result.put(key, "new-" + key);
          }
          return CompletableFuture.completedFuture(result);
        };

    CompletableFuture<Map<String, String>> result =
        asyncCache.refreshAll(Arrays.asList("k1", "k2", "k3"), loader);

    Map<String, String> refreshed = result.get();
    assertThat(refreshed).hasSize(3);
    assertThat(refreshed).containsEntry("k1", "new-k1");
    assertThat(refreshed).containsEntry("k2", "new-k2");
    assertThat(refreshed).containsEntry("k3", "new-k3");

    // 缓存已更新
    assertThat(delegate.getIfPresent("k1")).isEqualTo("new-k1");
    assertThat(delegate.getIfPresent("k2")).isEqualTo("new-k2");
    assertThat(delegate.getIfPresent("k3")).isEqualTo("new-k3");
  }

  @Test
  @DisplayName("refreshAll：批量加载部分 key 返回 null 时移除对应键")
  void refreshAllShouldRemoveKeysWhenLoaderReturnsNull() throws Exception {
    delegate.put("k1", "old1");
    delegate.put("k2", "old2");

    AsyncFunction<Collection<String>, Map<String, String>> loader =
        keys -> {
          Map<String, String> result = new HashMap<>();
          result.put("k1", "new1");
          result.put("k2", null); // k2 加载返回 null
          return CompletableFuture.completedFuture(result);
        };

    CompletableFuture<Map<String, String>> result =
        asyncCache.refreshAll(Arrays.asList("k1", "k2"), loader);

    Map<String, String> refreshed = result.get();
    // 只有非 null 的键出现在结果中
    assertThat(refreshed).hasSize(1);
    assertThat(refreshed).containsEntry("k1", "new1");

    // k2 应从缓存中移除
    assertThat(delegate.getIfPresent("k1")).isEqualTo("new1");
    assertThat(delegate.getIfPresent("k2")).isNull();
  }

  @Test
  @DisplayName("refreshAll：批量加载失败时保留所有键的旧值")
  void refreshAllShouldKeepOldValuesOnLoaderFailure() throws Exception {
    delegate.put("k1", "old1");
    delegate.put("k2", "old2");

    AsyncFunction<Collection<String>, Map<String, String>> loader =
        keys -> {
          throw new RuntimeException("批量加载失败");
        };

    CompletableFuture<Map<String, String>> result =
        asyncCache.refreshAll(Arrays.asList("k1", "k2"), loader);

    // 批量加载失败时返回空 Map，不抛异常
    Map<String, String> refreshed = result.get();
    assertThat(refreshed).isEmpty();

    // 旧值应保留
    assertThat(delegate.getIfPresent("k1")).isEqualTo("old1");
    assertThat(delegate.getIfPresent("k2")).isEqualTo("old2");
  }

  @Test
  @DisplayName("refreshAll：空集合返回空 Map，不调用 loader")
  void refreshAllShouldReturnEmptyForEmptyKeys() throws Exception {
    AtomicInteger loadCount = new AtomicInteger(0);
    AsyncFunction<Collection<String>, Map<String, String>> loader =
        keys -> {
          loadCount.incrementAndGet();
          return CompletableFuture.completedFuture(new HashMap<>());
        };

    CompletableFuture<Map<String, String>> result =
        asyncCache.refreshAll(Collections.emptyList(), loader);

    assertThat(result.get()).isEmpty();
    assertThat(loadCount.get()).isZero();
  }

  @Test
  @DisplayName("refreshAll(loader)：默认方法刷新缓存中所有 key")
  void refreshAllWithoutKeysShouldRefreshAllCacheKeys() throws Exception {
    delegate.put("k1", "old1");
    delegate.put("k2", "old2");

    AsyncFunction<Collection<String>, Map<String, String>> loader =
        keys -> {
          Map<String, String> result = new HashMap<>();
          for (String key : keys) {
            result.put(key, "new-" + key);
          }
          return CompletableFuture.completedFuture(result);
        };

    CompletableFuture<Map<String, String>> result = asyncCache.refreshAll(loader);

    Map<String, String> refreshed = result.get();
    assertThat(refreshed).hasSize(2);
    assertThat(refreshed).containsEntry("k1", "new-k1");
    assertThat(refreshed).containsEntry("k2", "new-k2");
  }

  @Test
  @DisplayName("refreshAll(loader)：空缓存返回空 Map，不调用 loader")
  void refreshAllWithoutKeysShouldReturnEmptyForEmptyCache() throws Exception {
    AtomicInteger loadCount = new AtomicInteger(0);
    AsyncFunction<Collection<String>, Map<String, String>> loader =
        keys -> {
          loadCount.incrementAndGet();
          return CompletableFuture.completedFuture(new HashMap<>());
        };

    CompletableFuture<Map<String, String>> result = asyncCache.refreshAll(loader);

    assertThat(result.get()).isEmpty();
    assertThat(loadCount.get()).isZero();
  }

  @Test
  @DisplayName("refresh 后并发 get 应能读到新值")
  void getAfterRefreshShouldReadNewValue() throws Exception {
    delegate.put("k1", "old");
    AsyncFunction<String, String> refreshLoader =
        k -> CompletableFuture.completedFuture("new");
    AsyncFunction<String, String> getLoader =
        k -> CompletableFuture.completedFuture("fallback");

    // 先刷新
    asyncCache.refresh("k1", refreshLoader).get();

    // 等待刷新完成写入缓存
    await()
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(delegate.getIfPresent("k1")).isEqualTo("new"));

    // get 应直接命中缓存新值，不调用 getLoader
    CompletableFuture<String> getResult = asyncCache.get("k1", getLoader);
    assertThat(getResult.get()).isEqualTo("new");
  }
}
