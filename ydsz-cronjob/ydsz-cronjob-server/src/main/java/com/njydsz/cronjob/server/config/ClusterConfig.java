package com.njydsz.cronjob.server.config;

import lombok.Data;

/**
 * 集群级配置（P0-1 新增，P0-4 扩展：校准间隔配置化）。
 *
 * <p>提供全局并发控制器估算集群节点数的配置项，以及并发计数器校准间隔配置。
 *
 * <pre>{@code
 * ydsz:
 *   cronjob:
 *     cluster:
 *       max-nodes: 3
 *       calibration-interval-ms: 60000
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.2
 */
@Data
public class ClusterConfig {

  /** 默认maxNodes值（可被配置文件覆盖） */
  private static final int DEFAULT_MAX_NODES = 3;

  /** 默认校准间隔（毫秒）：60s */
  private static final long DEFAULT_CALIBRATION_INTERVAL_MS = 60_000L;

  /**
   * 集群最大节点数估算值（默认 3）。
   *
   * <p>当节点发现策略不可用时，用于计算全局并发上限： maxGlobal = maxConcurrent × maxNodes。
   * 节点发现策略可用时自动使用实际在线节点数。
   */
  private int maxNodes = DEFAULT_MAX_NODES;

  /**
   * P0-4: 全局并发计数器校准间隔（毫秒，默认 60s）。
   *
   * <p>定期统计 RUNNING 状态日志数校准 Redis 全局并发计数器，防止进程崩溃导致的计数器漂移。
   * 间隔不宜过短（每次需查询 DB），不宜过长（计数器长时间不准确）。
   */
  private long calibrationIntervalMs = DEFAULT_CALIBRATION_INTERVAL_MS;
}
