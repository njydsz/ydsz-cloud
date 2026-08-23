package com.njydsz.cronjob.server.config;

import lombok.Data;

/**
 * P2-9: 多 Active Leader 分区调度配置。
 *
 * <p>启用分区调度后，多个节点可同时作为不同分区的 Leader，提升调度吞吐量。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class PartitionConfig {

  /** 默认totalPartitions值（可被配置文件覆盖） */
  private static final int DEFAULT_TOTAL_PARTITIONS = 4;

  /**
   * 是否启用分区调度（默认 false）。
   *
   * <p>启用后，JobScanner 仅扫描属于当前节点 Leader 分区的任务， 多个节点可同时作为不同分区的 Leader，提升调度吞吐量。
   */
  private boolean enabled = false;

  /**
   * 分区总数（默认 4）。
   *
   * <p>建议设置为节点数的 2-4 倍，确保节点扩缩容时分区可均匀再分配。
   */
  private int totalPartitions = DEFAULT_TOTAL_PARTITIONS;

  /**
   * 分片分配策略: job_key（默认）/ job_group。
   *
   * <p>{@code job_key}: 按 jobKey 哈希取模分配分区（细粒度） {@code job_group}: 按 jobGroup 哈希取模分配分区（粗粒度，同组任务在同一分区）
   */
  private String hashStrategy = "job_key";
}
