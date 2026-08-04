package com.remisoft.common.cache.metrics;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * HotKeyMetrics 的可配置属性。
 *
 * <p>绑定前缀 {@code remi.cache.hot-key-tracking}，通过 application.yml / application.properties
 * 控制行为。
 *
 * @param enabled                 是否启用热点 Key 指标采集（默认 false）
 * @param topK                    每次快照导出的 Top-K 大小（默认 10）
 * @param snapshotIntervalSeconds 快照间隔，秒（默认 30）
 * @param maxLocalKeys            本地计数器最大条目数（默认 10,000）
 * @param cacheNamePatterns       显式包含的缓存名称模式（逗号分隔），空则包含全部
 * @author remi-team
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "remi.cache.hot-key-tracking")
public record HotKeyMetricsProperties(
    boolean enabled,
    int topK,
    long snapshotIntervalSeconds,
    int maxLocalKeys,
    String cacheNamePatterns) {

  public HotKeyMetricsProperties() {
    this(false, 10, 30L, 10_000, "");
  }

  public boolean enabled() {
    return enabled;
  }

  public int topK() {
    return topK;
  }

  public long snapshotIntervalSeconds() {
    return snapshotIntervalSeconds;
  }

  public int maxLocalKeys() {
    return maxLocalKeys;
  }

  public String cacheNamePatterns() {
    return cacheNamePatterns != null ? cacheNamePatterns : "";
  }

  /**
   * 判断指定缓存名称是否匹配用户指定的 cacheNamePatterns 表达式。
   *
   * <p>patterns 为空时视为「全部匹配」。
   *
   * @param cacheName 缓存名称
   * @return true 表示该缓存应开启采集
   */
  public boolean matches(String cacheName) {
    if (cacheNamePatterns == null || cacheNamePatterns.isBlank()) {
      return true;
    }
    for (String pattern : cacheNamePatterns.split(",")) {
      String trimmed = pattern.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      if (trimmed.endsWith("*")) {
        if (cacheName.startsWith(trimmed.substring(0, trimmed.length() - 1))) {
          return true;
        }
      } else if (trimmed.equals(cacheName)) {
        return true;
      }
    }
    return false;
  }
}
