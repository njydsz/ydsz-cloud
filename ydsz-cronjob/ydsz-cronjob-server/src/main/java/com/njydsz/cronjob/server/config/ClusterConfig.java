package com.njydsz.cronjob.server.config;

import lombok.Data;

/**
 * 集群级配置（P0-1 新增）。
 *
 * <p>提供全局并发控制器估算集群节点数的配置项， 当 NodeDiscoveryStrategy 不可用时作为回退值。
 *
 * <pre>{@code
 * ydsz:
 *   cronjob:
 *     cluster:
 *       max-nodes: 3
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class ClusterConfig {

  /**
   * 集群最大节点数估算值（默认 3）。
   *
   * <p>当节点发现策略不可用时，用于计算全局并发上限： maxGlobal = maxConcurrent × maxNodes。
   * 节点发现策略可用时自动使用实际在线节点数。
   */
  private int maxNodes = 3;
}
