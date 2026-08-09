package com.njydsz.common.cache.metrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * HotKeyMetrics 注册中心 — 统一管理各缓存的 {@link HotKeyTracker} 与 {@link HotKeyMetrics} 实例。
 *
 * <p>作为 Spring Bean 存在，由 {@code DefaultHotKeyCacheBinder} 注入，
 * 在容器启动后遍历全部{@code Cache} Bean并自动注册符合条件的采集器。
 *
 * <p>线程安全：内部使用 {@link ConcurrentHashMap}，
 * 并发注册/注销不会发生冲突。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class HotKeyMetricsRegistry {

  private static final Logger log = LoggerFactory.getLogger(HotKeyMetricsRegistry.class);

  private final MeterRegistry meterRegistry;
  private final HotKeyMetricsProperties properties;
  private final ConcurrentMap<String, HotKeyMetrics<?>> metrics = new ConcurrentHashMap<>();

  public HotKeyMetricsRegistry(MeterRegistry meterRegistry, HotKeyMetricsProperties properties) {
    this.meterRegistry = meterRegistry;
    this.properties = properties;
  }

  /**
   * 为指定缓存名称注册一个热点 Key 指标采集器。
   *
   * <p>已存在同名采集器时返回现有实例，不重复注册。
   *
   * @param <K>       键类型
   * @param cacheName 缓存名称（用于指标标签）
   * @return 新建的或已存在的 HotKeyMetrics 实例
   */
  @SuppressWarnings("unchecked")
  public <K> HotKeyMetrics<K> register(String cacheName) {
    HotKeyMetrics<?> existing = metrics.get(cacheName);
    if (existing != null) {
      return (HotKeyMetrics<K>) existing;
    }
    HotKeyTracker<K> tracker = new HotKeyTracker<>(cacheName);
    tracker.setMaxLocalKeys(properties.maxLocalKeys());
    HotKeyMetrics<K> m = new HotKeyMetrics<>(tracker, properties.topK(),
        properties.snapshotIntervalSeconds());
    m.setCacheName(cacheName);
    HotKeyMetrics<?> prev = metrics.putIfAbsent(cacheName, m);
    if (prev != null) {
      return (HotKeyMetrics<K>) prev;
    }
    m.bindTo(meterRegistry);
    log.info("HotKeyMetrics 注册成功: cache={}, topK={}, interval={}s",
        cacheName, properties.topK(), properties.snapshotIntervalSeconds());
    return m;
  }

  /**
   * 返回指定缓存名称对应的 HotKeyMetrics，不存在时返回 Optional.empty()。
   */
  @SuppressWarnings("unchecked")
  public <K> java.util.Optional<HotKeyMetrics<K>> find(String cacheName) {
    HotKeyMetrics<?> m = metrics.get(cacheName);
    return m == null ? java.util.Optional.empty() : java.util.Optional.of((HotKeyMetrics<K>) m);
  }

  /**
   * 返回指定缓存名称对应的 HotKeyTracker，不存在时返回 Optional.empty()。
   */
  @SuppressWarnings("unchecked")
  public <K> java.util.Optional<HotKeyTracker<K>> findTracker(String cacheName) {
    return find(cacheName).map(metrics -> (HotKeyTracker<K>) metrics.getTracker());
  }

  /**
   * 移除指定缓存的指标采集器并关闭调度器。
   *
   * @return 是否成功删除
   */
  public boolean unregister(String cacheName) {
    HotKeyMetrics<?> m = metrics.remove(cacheName);
    if (m != null) {
      try {
        m.close();
      } catch (Exception e) {
        log.warn("HotKeyMetrics[{}] 关闭异常", cacheName, e);
      }
      return true;
    }
    return false;
  }

  /**
   * 返回当前已注册的采集器数量（用于健康检查）。
   */
  public int size() {
    return metrics.size();
  }
}
