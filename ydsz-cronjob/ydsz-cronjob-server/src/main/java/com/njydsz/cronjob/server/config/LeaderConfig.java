package com.njydsz.cronjob.server.config;

import lombok.Data;

/**
 * Leader 选举配置。
 *
 * <p>控制定时任务集群的 Leader 选举行为，包括租约、续期、分区调度等。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class LeaderConfig {

  /** 默认leaseSeconds值（可被配置文件覆盖） */
  private static final long DEFAULT_LEASE_SECONDS = 30;

  /** 默认renewIntervalSeconds值（可被配置文件覆盖） */
  private static final long DEFAULT_RENEW_INTERVAL_SECONDS = 10;

  /**
   * 是否启用 Leader 选举模式（false=回退旧的 Leaderless 模式）。
   *
   * <p>P0-4: 默认改为 true，确保多实例环境下任务不会重复执行。 单节点环境也会正常工作（自己成为 Leader）。
   */
  private boolean enabled = true;

  /** 角色（多套调度集群隔离时使用） */
  private String role = "ydsz-job-scheduler";

  /** 租约时长（秒，到期后自动释放，需在到期前续期） */
  private long leaseSeconds = DEFAULT_LEASE_SECONDS;

  /** 续期间隔（秒，默认 10s 续期一次） */
  private long renewIntervalSeconds = DEFAULT_RENEW_INTERVAL_SECONDS;

  /**
   * P2-9: 多 Active Leader 分区调度配置。
   *
   * <p>启用后，将调度集群分为 N 个分区，每个分区有一个独立的 Leader，
   * 各 Leader 负责扫描和派发属于自己分区的任务。 单节点可以同时持有多个分区的 Leader 角色。
   */
  private PartitionConfig partition = new PartitionConfig();
}
