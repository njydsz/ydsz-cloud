package com.njydsz.cronjob.server.config;

import lombok.Data;

/**
 * 告警扫描配置（P3-2 周期性告警扫描）。
 *
 * <p>控制 AlertScanner 的扫描间隔。 仅 Leader 节点启用周期性扫描，统计 FAIL_RATE / DURATION_P95 等需要聚合计算的告警类型。
 *
 * <p>对应配置前缀 {@code ydsz.cronjob.alert.scan-interval-ms}（与 {@link AlertProperties} 共享前缀， Spring
 * Boot 会自动合并字段，无冲突）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class AlertScanConfig {

  /** 默认scanIntervalMs值（可被配置文件覆盖） */
  private static final long DEFAULT_SCAN_INTERVAL_MS = 300000L;

  /** 默认ruleCacheTtlSeconds值（可被配置文件覆盖） */
  private static final int DEFAULT_RULE_CACHE_TTL_SECONDS = 60;

  /** 告警扫描间隔（毫秒，默认 5 分钟） */
  private long scanIntervalMs = DEFAULT_SCAN_INTERVAL_MS;

  /**
   * P1-P5: 告警规则本地缓存 TTL（秒，默认 60s）。
   *
   * <p>规则变更频率极低，本地缓存可大幅减少每次告警触发的 DB 查询。 缓存失效策略：TTL 自动过期 + 规则增删改操作手动失效。
   */
  private int ruleCacheTtlSeconds = DEFAULT_RULE_CACHE_TTL_SECONDS;
}
