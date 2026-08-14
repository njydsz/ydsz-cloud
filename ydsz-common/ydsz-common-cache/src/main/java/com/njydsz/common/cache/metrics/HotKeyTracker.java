package com.njydsz.common.cache.metrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 热点 Key 频率跟踪器 — 基于本地计数器的轻量级近似 Top-K 统计。
 *
 * <p>为每个缓存维护一张 key → 估计频率的映射，由 {@link HotKeyMetrics} 周期性快照并
 * 导出为 Micrometer 指标。当底层缓存已内置 {@link com.njydsz.common.cache.internal.lfu.FrequencySketch}
 * （如 TINYLFU）时，可直接获得高精度估计；否则退化为 {@link LongAdder} 本地计数器，
 * 适用于 LRU / Concurrent / Striped 等不内置频率草图的缓存类型。
 *
 * <p><b>使用约定：</b>
 *
 * <ul>
 *   <li>GET 命中时调用 {@link #increment(Object)} 将对应 key 的估计次数 +1</li>
 *   <li>定期（默认 30s）调用 {@link #snapshotAndGetTopK(int)} 拉取 Top-K 快照，同时清空本地计数器</li>
 *   <li>缓存删除条目时可选择调用 {@link #remove(Object)}，以避免无效 key 累积</li>
 *   <li>自动钉选：通过 {@link #setAutoPinThreshold(int)} 设置阈值后，
 *       快照时频率超阈值的 key 自动触发 {@link AutoPinCallback}</li>
 * </ul>
 *
 * <p><b>并发与一致性：</b>内部 {@link ConcurrentHashMap} + {@link LongAdder} 保证线程安全。
 * 快照操作采用一次性读+{@code clear}，非原子 — 极端并发下可能遗漏或重复计数 1~2 次，
 * 但 Top-K 排序结果不受影响。
 *
 * @param <K> 键类型
 * @author ydsz-team
 * @since 1.0.0
 */
public class HotKeyTracker<K> {

  private static final Logger log = LoggerFactory.getLogger(HotKeyTracker.class);

  /** 默认 Top-K 大小 */
  public static final int DEFAULT_TOP_K = 10;

  /** 默认自动钉选阈值（频率达到此值的 key 触发自动钉选） */
  public static final int DEFAULT_AUTO_PIN_THRESHOLD = 100;

  /**
   * 表示一条热点 key 的频率快照。
   *
   * @param key 缓存 key
   * @param estimatedFrequency 频率估计值
   * @param rank 在 Top-K 中的排名
   */
  public record HotKeyEntry<K>(K key, int estimatedFrequency, int rank) {}

  /**
   * 自动钉选回调接口 — 当 key 频率超过阈值时触发。
   *
   * <p>实现类通常将 key 自动钉选到 {@code HotKeyPinningCacheDecorator}，
   * 防止热点 key 被容量淘汰。
   */
  @FunctionalInterface
  public interface AutoPinCallback<K> {
    /**
     * 自动钉选通知
     *
     * @param key 频率超阈值的 key
     * @param frequency 当前频率
     */
    void onAutoPin(K key, int frequency);
  }

  /**
   * key → 本地近似频率计数器。
   *
   * <p>快照后 {@code clear}，使后续排名反映最近窗口的热度变化。
   */
  private final ConcurrentMap<K, LongAdder> localCounters = new ConcurrentHashMap<>();

  /** 快照周期内本地计数器的最大条目数 */
  private volatile int maxLocalKeys = 10_000;

  /** 缓存名称，用于日志与标签 */
  private final String cacheName;

  /** 自动钉选阈值（0 表示关闭自动钉选） */
  private volatile int autoPinThreshold = 0;

  /** 自动钉选回调 */
  private volatile AutoPinCallback<K> autoPinCallback;

  public HotKeyTracker(String cacheName) {
    this.cacheName = cacheName;
  }

  /**
   * 记录一次 key 访问（命中或 miss 均调用，以追踪访问热度）。
   *
   * <p>本地计数器不存在时惰性创建；若条目数已超过 {@link #maxLocalKeys} 上限，
   * 稀疏 key 场景下新 key 会被静默丢弃（日志告警但不抛异常），
   * 避免热点 key 出现时因内存膨胀而引发 OOM。
   *
   * @param key 被访问的缓存 key；为 {@code null} 时忽略
   */
  public void increment(K key) {
    if (key == null) {
      return;
    }
    LongAdder counter = localCounters.get(key);
    if (counter == null) {
      if (localCounters.size() >= maxLocalKeys) {
        log.warn("HotKeyTracker[{}] 本地计数器条目数达上限 {}，忽略新 key {}",
            cacheName, maxLocalKeys, key);
        return;
      }
      LongAdder newCounter = new LongAdder();
      LongAdder existing = localCounters.putIfAbsent(key, newCounter);
      counter = (existing != null) ? existing : newCounter;
    }
    counter.increment();
  }

  /**
   * 移除指定 key 的本地计数器。
   *
   * <p>建议在缓存删除监听器中调用，以回收无效 key 占用的内存。
   *
   * @param key 被删除的缓存 key；为 {@code null} 时忽略
   */
  public void remove(K key) {
    if (key != null) {
      localCounters.remove(key);
    }
  }

  /**
   * 设置本地计数器的最大条目上限。
   *
   * <p>超过上限时新 key 将被静默丢弃。默认 10,000 条目。
   *
   * @param max 最大条目数，必须为正数
   * @throws IllegalArgumentException 当 {@code max <= 0} 时抛出
   */
  public void setMaxLocalKeys(int max) {
    if (max <= 0) {
      throw new IllegalArgumentException("maxLocalKeys 必须为正数，当前值=" + max);
    }
    this.maxLocalKeys = max;
  }

  /**
   * 启用自动钉选功能。
   *
   * <p>当快照发现某 key 的频率达到或超过 {@code threshold} 时，
   * 自动触发 {@code callback} 通知上层钉选该 key。
   *
   * @param threshold 自动钉选阈值（必须为正数）
   * @param callback 自动钉选回调（不可为 null）   */
  public void setAutoPinThreshold(int threshold, AutoPinCallback<K> callback) {
    if (threshold <= 0) {
      throw new IllegalArgumentException("自动钉选阈值必须为正数，当前值=" + threshold);
    }
    if (callback == null) {
      throw new IllegalArgumentException("自动钉选回调不可为 null");
    }
    this.autoPinThreshold = threshold;
    this.autoPinCallback = callback;
  }

  /**
   * 关闭自动钉选功能。
   */
  public void disableAutoPin() {
    this.autoPinThreshold = 0;
    this.autoPinCallback = null;
  }

  /**
   * 返回当前快照周期内本地计数器的条目数。
   *
   * @return 本地计数器大小（估计值）
   */
  public int localKeyCount() {
    return localCounters.size();
  }

  /**
   * 获取 Top-K 热点 key 快照，并清空本地计数器为下一窗口做准备。
   *
   * <p>快照算法：扫描全部条目 → 按 count 降序排列 → 截取前 K 个。
   * 复杂度 {@code O(n log n)}，其中 n 为热 key 去重数量。
   *
   * <p>如果开启了自动钉选（{@link #setAutoPinThreshold}），快照过程中频率超阈值的 key
   * 会触发 {@link AutoPinCallback}。
   *
   * @param k 期望返回的最大条目数，必须为正数
   * @return Top-K 热点列表（按频率降序排列；无任何访问时返回空列表）
   */
  public List<HotKeyEntry<K>> snapshotAndGetTopK(int k) {
    if (k <= 0) {
      k = DEFAULT_TOP_K;
    }
    Set<K> keySet = localCounters.keySet();
    if (keySet.isEmpty()) {
      return List.of();
    }
    // 构建 (key, count) 列表
    List<HotKeyEntry<K>> entries = new ArrayList<>();
    int rank = 1;
    for (K key : keySet) {
      LongAdder adder = localCounters.get(key);
      if (adder != null) {
        long count = adder.sum();
        if (count > 0) {
          int freq = (int) Math.min(count, Integer.MAX_VALUE);
          entries.add(new HotKeyEntry<>(key, freq, rank++));
        }
      }
    }
    entries.sort(Comparator.comparingInt(HotKeyEntry<K>::estimatedFrequency).reversed());

    // 自动钉选触发
    triggerAutoPin(entries);

    List<HotKeyEntry<K>> topK = entries.subList(0, Math.min(k, entries.size()));
    // 清除已快照的计数器，下一个窗口重新开始
    localCounters.clear();
    return List.copyOf(topK);
  }

  /**
   * 自动钉选触发（内部方法）
   *
   * <p>遍历排序后的条目，对频率超过阈值的 key 调用回调。
   *
   * @param entries 按频率降序排列的条目列表
   */
  private void triggerAutoPin(List<HotKeyEntry<K>> entries) {
    if (autoPinThreshold <= 0 || autoPinCallback == null) {
      return;
    }
    for (HotKeyEntry<K> entry : entries) {
      if (entry.estimatedFrequency >= autoPinThreshold) {
        try {
          autoPinCallback.onAutoPin(entry.key, entry.estimatedFrequency);
        } catch (Exception e) {
          log.warn("HotKeyTracker[{}] 自动钉选回调异常: key={}, freq={}",
              cacheName, entry.key, entry.estimatedFrequency, e);
        }
      } else {
        // 已排序，后续条目频率更低，提前结束
        break;
      }
    }
  }
}
