package com.njydsz.cronjob.server.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
 * <h3>P1-12: 配置校验</h3>
 *
 * <p>通过 JSR-380 注解声明约束，启动时自动校验，配置错误立即报错。
 *
 * @author ydsz-team
 * @since 26.09.01
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
  @NotBlank(message = "Leader 选举角色 role 不能为空")
  private String role = "ydsz-job-scheduler";

  /**
   * 租约时长（秒，到期后自动释放，需在到期前续期）。
   *
   * <p>P1-12: 必须在 5~300 秒之间，过短导致频繁重新选举，过长导致故障切换慢。
   */
  @Min(value = 5, message = "租约时长 leaseSeconds 不能小于 5 秒")
  @Max(value = 300, message = "租约时长 leaseSeconds 不能大于 300 秒")
  private long leaseSeconds = DEFAULT_LEASE_SECONDS;

  /**
   * 续期间隔（秒，默认 3s 续期一次）。
   *
   * <p>P1-12: 必须小于 leaseSeconds，否则续期不及导致锁释放。
   */
  @Min(value = 1, message = "续期间隔 renewIntervalSeconds 不能小于 1 秒")
  @Max(value = 60, message = "续期间隔 renewIntervalSeconds 不能大于 60 秒")
  private long renewIntervalSeconds = DEFAULT_RENEW_INTERVAL_SECONDS;

  /**
   * P2-9: 多 Active Leader 分区调度配置。
   *
   * <p>启用后，将调度集群分为 N 个分区，每个分区有一个独立的 Leader，
   * 各 Leader 负责扫描和派发属于自己分区的任务。 单节点可以同时持有多个分区的 Leader 角色。
   */
  private PartitionConfig partition = new PartitionConfig();

  /**
   * 跨字段校验：renewIntervalSeconds 必须小于 leaseSeconds。
   *
   * <p>通过 {@code @AssertTrue} 触发方法级校验。
   *
   * @return true 表示配置合法（renewIntervalSeconds < leaseSeconds）
   */
  @AssertTrue(message = "续期间隔 renewIntervalSeconds 必须小于租约时长 leaseSeconds")
  public boolean isRenewIntervalValid() {
    return renewIntervalSeconds < leaseSeconds;
  }
}
