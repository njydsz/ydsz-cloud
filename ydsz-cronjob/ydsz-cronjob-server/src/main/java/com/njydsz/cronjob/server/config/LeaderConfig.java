package com.njydsz.cronjob.server.config;

import lombok.Data;

/**
 * Leader 选举配置。
 *
 * <p>控制定时任务集群的 Leader 选举行为，包括租约、续期、分区调度等。
 *
 * <h3>P0-2: HA 切换优化</h3>
 *
 * <p>默认 leaseSeconds 从 30s 下调至 10s，renewIntervalSeconds 从 10s 下调至 3s。
 * 配合 Redisson WatchDog（lockWatchdogTimeout 建议同步配置为 10s），Leader 宕机后
 * 10s 内锁自动释放，新 Leader 接管，故障切换时间从 30s 缩短至 10s 级。
 *
 * <p><b>注意</b>：{@link #leaseSeconds} 仅用于 holder key 的 TTL；RLock 本体由 Redisson WatchDog 续期，
 * 实际故障检测时间取决于 Redisson {@code lockWatchdogTimeout} 配置（建议 ≤ leaseSeconds）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class LeaderConfig {

  /**
   * 默认租约时长（秒）。
   *
   * <p>P0-2: 从 30s 下调至 10s，加快故障切换。需同步配置 Redisson {@code lockWatchdogTimeout ≤ 10000}。
   */
  private static final long DEFAULT_LEASE_SECONDS = 10;

  /**
   * 默认续期间隔（秒）。
   *
   * <p>P0-2: 从 10s 下调至 3s，确保 holder key 在 lease 到期前刷新。
   */
  private static final long DEFAULT_RENEW_INTERVAL_SECONDS = 3;

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

  /** 续期间隔（秒，默认 3s 续期一次） */
  private long renewIntervalSeconds = DEFAULT_RENEW_INTERVAL_SECONDS;

  /**
   * P2-9: 多 Active Leader 分区调度配置。
   *
   * <p>启用后，将调度集群分为 N 个分区，每个分区有一个独立的 Leader，
   * 各 Leader 负责扫描和派发属于自己分区的任务。 单节点可以同时持有多个分区的 Leader 角色。
   */
  private PartitionConfig partition = new PartitionConfig();
}
