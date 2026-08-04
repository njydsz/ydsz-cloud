package com.remisoft.common.cache.metrics;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.remisoft.common.cache.metrics.HotKeyTracker.HotKeyEntry;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * 热点 Key 频率指标 Micrometer 注册器 — 周期性采集 Top-K 并曝光为 Gauge 指标。
 *
 * <p>注册的指标：
 * <ul>
 *   <li>{@code cache.hotkey.frequency} — 单个热点 key 的估计频率，
 *       标签 {@code cache_name}、{@code key}（字符串化）、{@code rank}（排名）</li>
 *   <li>{@code cache.hotkey.topk.size} — 当 snapshot 后的 Top-K 条目数；
 *       标签 {@code cache_name}</li>
 *   <li>{@code cache.hotkey.tracker.local_key_count} — HotKeyTracker 当前本地条目数；
 *       标签 {@code cache_name}</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * Cache<String, User> cache = ...;
 * HotKeyTracker<String> tracker = new HotKeyTracker<>("user_cache");
 * HotKeyMetrics<String> metrics = new HotKeyMetrics<>(tracker, 10);
 * metrics.bindTo(meterRegistry); // 周期性注册 Gauge
 * }</pre>
 *
 * <p><b>高基数防护：</b>仅注册 Top-K（默认 10）个 key 的指标，而非全量 key。
 *
 * <p><b>线程模型：</b>单线程定时器运行在 daemon 线程，不阻止 JVM 退出。
 * 调用 {@link #close()} 可显式关闭（通常由 Spring 容器管理）。
 *
 * @param <K> 键类型
 * @author remi-team
 * @since 1.0.0
 */
public class HotKeyMetrics<K> implements MeterBinder, AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(HotKeyMetrics.class);

  /** 默认 Top-K 大小 */
  private static final int DEFAULT_TOP_K = 10;

  /** 默认快照周期（秒） */
  private static final long DEFAULT_SNAPSHOT_INTERVAL_SECONDS = 30;

  /** 高基数保护：key 字符串标签最大长度 */
  private static final int MAX_KEY_LABEL_LENGTH = 64;

  private final HotKeyTracker<K> tracker;
  private final int topK;
  private volatile long snapshotIntervalSeconds;

  /** 快照周期内的最新 Top-K 列表（volatile 立即可见） */
  private volatile List<HotKeyEntry> latestTopK = List.of();

  /** 注册中心 */
  private MeterRegistry registry;

  /** 快照调度器 */
  private ScheduledExecutorService scheduler;

  /** 启动一次标记，防止重复注册 scheduler */
  private final AtomicBoolean started = new AtomicBoolean(false);

  /** 缓存名称，用于标签 */
  private String cacheName;

  /** Micrometer 已注册的 Gauge 引用（便于销毁时反注册） */
  private final ConcurrentMap<String, Meter> registeredGauges = new ConcurrentHashMap<>();

  public HotKeyMetrics(HotKeyTracker<K> tracker) {
    this(tracker, DEFAULT_TOP_K, DEFAULT_SNAPSHOT_INTERVAL_SECONDS);
  }

  public HotKeyMetrics(HotKeyTracker<K> tracker, int topK) {
    this(tracker, topK, DEFAULT_SNAPSHOT_INTERVAL_SECONDS);
  }

  public HotKeyMetrics(HotKeyTracker<K> tracker, int topK, long snapshotIntervalSeconds) {
    if (tracker == null) {
      throw new IllegalArgumentException("HotKeyTracker 不能为 null");
    }
    this.tracker = tracker;
    this.topK = Math.max(1, topK);
    this.snapshotIntervalSeconds = Math.max(5, snapshotIntervalSeconds);
    this.cacheName = "unknown";
  }

  /**
   * （重新）设置缓存名称。须在 {@link #bindTo} 之前调用；bindTo 之后调用将不会立即生效
   * （下一次快照时生效）。
   *
   * @param cacheName 缓存名称
   */
  public void setCacheName(String cacheName) {
    if (cacheName != null && !cacheName.isBlank()) {
      this.cacheName = cacheName;
    }
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    this.registry = registry;

    // 注册 Top-K 大小 Gauge
    Gauge.builder("cache.hotkey.topk.size", latestTopK, List::size)
        .description("当前快照中的热点 key 数量（≤ topK）")
        .register(registry);

    // 注册本地计数器条目数 Gauge
    Gauge.builder(
            "cache.hotkey.tracker.local_key_count",
            tracker,
            t -> (double) t.localKeyCount())
        .description("HotKeyTracker 本地计数器条目数；内置 FrequencySketch 的缓存为 0")
        .register(registry);

    // 启动快照周期器
    startSnapshotScheduler();
  }

  private void startSnapshotScheduler() {
    if (!started.compareAndSet(false, true)) {
      return;
    }
    scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
      @Override
      public Thread newThread(Runnable r) {
        Thread t = new Thread(r, "hotkey-metrics-snapshot");
        t.setDaemon(true);
        return t;
      }
    });
    scheduler.scheduleAtFixedRate(this::snapshotAndUpdate, 0, snapshotIntervalSeconds,
        TimeUnit.SECONDS);
  }

  private void snapshotAndUpdate() {
    if (registry == null) {
      return;
    }
    try {
      List<HotKeyEntry> top = tracker.snapshotAndGetTopK(this.topK);
      this.latestTopK = top;
      ConcurrentHashMap<String, Boolean> activeKeys = new ConcurrentHashMap<>();
      for (HotKeyEntry entry : top) {
        String safeKey = sanitizeKeyLabel(entry.key());
        String meterKey = safeKey + "#" + entry.rank();
        activeKeys.put(meterKey, Boolean.TRUE);
        if (!registeredGauges.containsKey(meterKey)) {
          Meter gauge = Gauge.builder("cache.hotkey.frequency",
                  (HotKeyEntry) entry, e -> (double) e.estimatedFrequency())
              .tags(Tags.of(
                  "cache_name", cacheName,
                  "key", safeKey,
                  "rank", String.valueOf(entry.rank())))
              .description("热点 key 的估计访问频率（由 Count-Min Sketch 或本地计数器跟踪）")
              .register(registry);
          registeredGauges.put(meterKey, gauge);
        } else {
          // Gauge 绑定的是不可变 entry 快照；移除旧引用重新绑定以刷新数值
          registry.remove(registeredGauges.remove(meterKey));
          Meter gauge = Gauge.builder("cache.hotkey.frequency",
                  (HotKeyEntry) entry, e -> (double) e.estimatedFrequency())
              .tags(Tags.of(
                  "cache_name", cacheName,
                  "key", safeKey,
                  "rank", String.valueOf(entry.rank())))
              .description("热点 key 的估计访问频率")
              .register(registry);
          registeredGauges.put(meterKey, gauge);
        }
      }
      // 反注册已过期的 Top-K Gauge
      registeredGauges.forEach((meterKey, meter) -> {
        if (!activeKeys.containsKey(meterKey)) {
          registry.remove(meter);
          registeredGauges.remove(meterKey);
        }
      });
    } catch (Exception e) {
      log.warn("HotKeyMetrics[{}] 快照采集失败", cacheName, e);
    }
  }

  private String sanitizeKeyLabel(Object key) {
    if (key == null) {
      return "NULL_KEY";
    }
    String str = key.toString().replace("\n", "_").replace("\r", "_").replace("\"", "_");
    if (str.length() > MAX_KEY_LABEL_LENGTH) {
      return str.substring(0, MAX_KEY_LABEL_LENGTH - 1) + "~";
    }
    return str;
  }

  /**
   * 获取底层的 HotKeyTracker（供自定义桥接器使用）。
   */
  public HotKeyTracker<K> getTracker() {
    return tracker;
  }

  @Override
  public void close() {
    if (scheduler != null) {
      scheduler.shutdownNow();
      try {
        if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
          log.warn("HotKeyMetrics[{}] 关闭超时，强制退出", cacheName);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      scheduler = null;
    }
    started.set(false);
  }
}
