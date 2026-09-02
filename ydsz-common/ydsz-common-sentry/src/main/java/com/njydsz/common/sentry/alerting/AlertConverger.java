package com.njydsz.common.sentry.alerting;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.sentry.domain.AlertEvent;
import com.njydsz.common.sentry.spi.AlertPublisher;

/**
 * 告警收敛器
 *
 * <p>基于时间窗口聚合 + 去重 + 静默期实现告警降噪。
 *
 * <p>策略：
 *
 * <ul>
 *   <li>时间窗口聚合：同一告警在窗口内仅通知一次
 *   <li>去重：基于 dedupKey 去重
 *   <li>静默期：告警触发后设置静默期，期内不重复通知
 * </ul>
 *
 * <p>静默记录使用有界 Map，超出上限时移除最早过期的条目，防止高基数告警导致内存泄漏。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class AlertConverger implements AlertPublisher {

  /** 静默记录最大条目数 */
  private static final int MAX_SILENCE_MAP_SIZE = 10000;

  /** 触发清理的阈值（达到容量的 80%） */
  private static final double CLEANUP_THRESHOLD_RATIO = 0.8;

  /** 下游告警发布器 */
  private final AlertPublisher delegate;

  /** 静默期（毫秒） */
  private final long silencePeriodMillis;

  /** 告警计数器（用于统计） */
  private final AtomicInteger totalAlerts = new AtomicInteger(0);

  private final AtomicInteger suppressedAlerts = new AtomicInteger(0);

  /** 静默记录（key=dedupKey, value=上次通知时间） */
  private final ConcurrentHashMap<String, Instant> silenceMap = new ConcurrentHashMap<>();

  /**
   * alert converger。
   * @param delegate 参数
   * @param silencePeriodMillis 参数
 */
  public AlertConverger(AlertPublisher delegate, long silencePeriodMillis) {
    this.delegate = delegate;
    this.silencePeriodMillis = silencePeriodMillis;
    log.info("[Sentry] AlertConverger 初始化: silencePeriod={}ms", silencePeriodMillis);
  }

  @Override
  /**
   * publish。
   * @param event 参数
   * @return 结果
   */
  public boolean publish(AlertEvent event) {
    totalAlerts.incrementAndGet();
    String dedupKey = event.dedupKey();

    // 检查静默期
    if (isSilenced(dedupKey)) {
      suppressedAlerts.incrementAndGet();
      log.debug("[Sentry] 告警被静默: key={}, suppressed={}", dedupKey, suppressedAlerts.get());
      return false;
    }

    // 发布告警
    boolean published = delegate != null && delegate.publish(event);

    // 设置静默
    if (published) {
      ensureSilenceMapCapacity();
      silenceMap.put(dedupKey, Instant.now());
    }

    return published;
  }

  /**
   * 检查是否在静默期内
   *
   * @param dedupKey 告警去重键
   * @return {@code true} 表示在静默期内
   */
  private boolean isSilenced(String dedupKey) {
    Instant lastFired = silenceMap.get(dedupKey);
    if (lastFired == null) {
      return false;
    }
    if (lastFired.plusMillis(silencePeriodMillis).isAfter(Instant.now())) {
      return true;
    }
    // 已过期，移除无效记录
    silenceMap.remove(dedupKey);
    return false;
  }

  /**
   * 确保静默记录 Map 不超出容量上限。
   *
   * <p>当容量达到阈值的 80% 时，触发批量清理过期条目； 清理后仍超限，则移除最早过期的条目直到容量降至安全水位。
   */
  private void ensureSilenceMapCapacity() {
    if (silenceMap.size() < MAX_SILENCE_MAP_SIZE * CLEANUP_THRESHOLD_RATIO) {
      return;
    }
    // 先清理过期条目
    cleanupExpiredSilence();
    // 仍超限则强制移除最老的条目
    while (silenceMap.size() >= MAX_SILENCE_MAP_SIZE) {
      String oldestKey = findOldestEntry();
      if (oldestKey == null) {
        break;
      }
      silenceMap.remove(oldestKey);
    }
  }

  /**
   * 查找最早过期的条目 key
   *
   * @return 最老的 key，Map 为空时返回 {@code null}
   */
  private String findOldestEntry() {
    String oldestKey = null;
    Instant oldestTime = Instant.MAX;
    for (Map.Entry<String, Instant> entry : silenceMap.entrySet()) {
      if (entry.getValue().isBefore(oldestTime)) {
        oldestTime = entry.getValue();
        oldestKey = entry.getKey();
      }
    }
    return oldestKey;
  }

  /**
   * 清理过期静默记录。
   *
   * <p>遍历移除超过静默期的条目，防止 Map 无限增长。
   */
  public void cleanupExpiredSilence() {
    Instant now = Instant.now();
    List<String> expiredKeys = new ArrayList<>(16);
    silenceMap.forEach(
        (key, value) -> {
          if (value.plusMillis(silencePeriodMillis).isBefore(now)) {
            expiredKeys.add(key);
          }
        });
    expiredKeys.forEach(silenceMap::remove);
  }

  /**
   * 获取静默中的告警数量
   *
   * @return 当前静默记录数
   */
  public int getActiveSilenceCount() {
    return silenceMap.size();
  }

  /**
   * 获取总告警数
   *
   * @return 累计告警总数
   */
  public int getTotalAlerts() {
    return totalAlerts.get();
  }

  /**
   * 获取被抑制的告警数
   *
   * @return 累计被抑制数
   */
  public int getSuppressedAlerts() {
    return suppressedAlerts.get();
  }

  /**
   * 获取告警抑制率
   *
   * @return 抑制率（0.0~1.0）
   */
  public double getSuppressionRate() {
    int total = totalAlerts.get();
    if (total == 0) {
      return 0;
    }
    return (double) suppressedAlerts.get() / total;
  }

  @Override
  /**
   * is available。
   * @return 结果
   */
  public boolean isAvailable() {
    return delegate != null && delegate.isAvailable();
  }

  @Override
  /**
   * get name。
   * @return 结果
   */
  public String getName() {
    return "alert-converger";
  }
}
