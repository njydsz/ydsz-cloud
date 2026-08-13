package com.njydsz.common.cache.internal.decorator;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.api.CachePolicy;
import com.njydsz.common.cache.listener.RemovalListener;
import com.njydsz.common.cache.metrics.HotKeyTracker;
import com.njydsz.common.cache.stats.CacheStats;
import com.njydsz.common.cache.support.AsyncFunction;

import io.micrometer.core.instrument.Timer;

/**
 * 透明热点 Key 追踪装饰器。
 *
 * <p>将底层缓存的所有 GET/PUT 访问转发到 {@link HotKeyTracker}，从而获得访问频率Top-K 观测能力，
 * 无需修改底层缓存实现。
 *
 * <p><b>使用方法：</b>
 * <pre>{@code
 * Cache<String, User> cache = new LRUCache<>(1000);
 * HotKeyTracker<String> tracker = new HotKeyTracker<>("user_cache");
 * Cache<String, User> tracked = new HotKeyTrackingCacheDecorator<>(cache, tracker);
 * // 可选：同时桥接到 Micrometer
 * HotKeyMetrics<String> metrics = new HotKeyMetrics<>(tracker, 10);
 * metrics.bindTo(meterRegistry);
 * }</pre>
 *
 * <p><b>使用建议：</b>
 * <ul>
 *   <li>仅对键空间有限（如 region_code、dict_type 等枚举式前缀）且容量有限的缓存开启，
 *       避免 HotKeyTracker 的本地计数器膨胀</li>
 *   <li>无需同时使用 {@link TimedCacheDecorator} — 本装饰器不与耗时统计耦合，可任意嵌套</li>
 * </ul>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author ydsz-team
 * @since 1.0.0
 */
public class HotKeyTrackingCacheDecorator<K, V> implements Cache<K, V> {

  private final Cache<K, V> delegate;
  private final HotKeyTracker<K> tracker;

  /**
   * 构造热点追踪装饰器。
   *
   * @param delegate 底层缓存
   * @param tracker  用于频率累积的 {@link HotKeyTracker} 实例；
   *                 为 {@code null} 时将跳过频率追踪（成为透传装饰器，不推荐）
   */
  public HotKeyTrackingCacheDecorator(Cache<K, V> delegate, HotKeyTracker<K> tracker) {
    this.delegate = delegate;
    this.tracker = tracker;
  }

  @Override
  public V getIfPresent(K key) {
    V value = delegate.getIfPresent(key);
    trackAccess(key);
    return value;
  }

  @Override
  public V get(K key, Function<K, V> loader) {
    V value = delegate.get(key, loader);
    trackAccess(key);
    return value;
  }

  @Override
  public CompletableFuture<V> getAsync(K key, AsyncFunction<K, V> loader) {
    return delegate.getAsync(key, loader).whenComplete((v, ex) -> trackAccess(key));
  }

  @Override
  public boolean containsKey(K key) {
    boolean result = delegate.containsKey(key);
    trackAccess(key);
    return result;
  }

  @Override
  public void put(K key, V value) {
    delegate.put(key, value);
    // PUT 不纳入热点统计；热点仅衡量 GET 频率差异
  }

  @Override
  public V remove(K key) {
    V value = delegate.remove(key);
    if (tracker != null) {
      tracker.remove(key);
    }
    return value;
  }

  @Override
  public void clear() {
    delegate.clear();
    // 不清空 tracker.localCounters，避免遍历整个本地映射 —
    // 下一次快照自然清零；如需即时同步，可考虑 tracker.reset()
  }

  @Override
  public long estimatedSize() {
    return delegate.estimatedSize();
  }

  @Override
  public double getHitRate() {
    return delegate.getHitRate();
  }

  @Override
  public CacheStats getStats() {
    return delegate.getStats();
  }

  @Override
  public CachePolicy policy() {
    return delegate.policy();
  }

  @Override
  public Set<K> keySet() {
    return delegate.keySet();
  }

  @Override
  public Collection<V> values() {
    return delegate.values();
  }

  @Override
  public void cleanUp() {
    delegate.cleanUp();
  }

  @Override
  public void addListener(RemovalListener<? super K, ? super V> listener) {
    delegate.addListener(listener);
  }

  @Override
  public void forEach(BiConsumer<? super K, ? super V> action) {
    delegate.forEach(action);
  }

  /**
   * 获取底层缓存实例（供装饰器链接使用）。
   */
  public Cache<K, V> getDelegate() {
    return delegate;
  }

  /**
   * 获取热 key 追踪器实例（供外部注册 Micrometer 使用）。
   */
  public HotKeyTracker<K> getTracker() {
    return tracker;
  }

  /**
   * 获取当前 Top-K 热点 key 快照
   *
   * <p>委托给底层 {@link HotKeyTracker#snapshotAndGetTopK(int)}，
   * 返回频率最高的 K 个 key，同时清空本地计数器为下一窗口做准备。
   *
   * @param k 期望返回的最大条目数
   * @return Top-K 热点列表（按频率降序排列）；无任何访问时返回空列表
   */
  public List<HotKeyTracker.HotKeyEntry<K>> getTopHotKeys(int k) {
    if (tracker != null) {
      return tracker.snapshotAndGetTopK(k);
    }
    return Collections.emptyList();
  }

  /**
   * 获取默认数量（10）的 Top-K 热点 key 快照
   *
   * @return Top-10 热点列表
   */
  public List<HotKeyTracker.HotKeyEntry<K>> getTopHotKeys() {
    return getTopHotKeys(HotKeyTracker.DEFAULT_TOP_K);
  }

  /**
   * 获取底层 HotKeyTracker（供外部高级用法如 Micrometer 桥接使用）
   *
   * @return HotKeyTracker 实例
   */
  public HotKeyTracker<K> hotKeyTracker() {
    return tracker;
  }

  private void trackAccess(K key) {
    if (tracker != null) {
      tracker.increment(key);
    }
  }
}
