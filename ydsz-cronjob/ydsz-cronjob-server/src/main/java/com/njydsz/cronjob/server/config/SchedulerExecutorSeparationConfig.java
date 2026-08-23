package com.njydsz.cronjob.server.config;

import lombok.Data;

/**
 * P0-1: 调度器-执行器分离配置。
 *
 * <p>启用后，Leader 节点仅负责调度扫描和任务派发，不再在本地执行任务。 非分片任务也会通过 RemoteTaskClient 派发到选定的 Worker 节点执行。
 *
 * <h3>对标</h3>
 *
 * <ul>
 *   <li>XXL-Job: 调度中心与执行器完全分离
 *   <li>PowerJob: Server 与 Worker 分离
 * </ul>
 *
 * <p>启用条件：
 *
 * <ul>
 *   <li>remote.enabled = true（远程派发必须可用）
 *   <li>至少 2 个在线节点（否则 Leader 仍需本地执行）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class SchedulerExecutorSeparationConfig {

  /** 默认maxConcurrentPerWorker值（可被配置文件覆盖） */
  private static final int DEFAULT_MAX_CONCURRENT_PER_WORKER = 16;

  /** 默认maxDispatchAttempts值（可被配置文件覆盖） */
  private static final int DEFAULT_MAX_DISPATCH_ATTEMPTS = 2;

  /**
   * 是否启用调度器-执行器分离（P1-5: 默认 true，对标 XXL-Job/PowerJob 的调度器-执行器分离架构）。
   *
   * <p>启用后，Leader 节点通过 WorkerNodeSelector 选定 Worker 节点远程派发任务， 无可用 Worker 时自动降级为 Leader
   * 本地执行（保证向后兼容）。 运行条件：remote.enabled=true 且 WorkerNodeSelector Bean 已注册。
   */
  private boolean enabled = true;

  /** Worker 节点选择策略: round_robin(轮询) / least_load(最小负载) */
  private String workerSelectionStrategy = "round_robin";

  /** 单节点最大并行任务数（用于 least_load 策略的负载评估） */
  private int maxConcurrentPerWorker = DEFAULT_MAX_CONCURRENT_PER_WORKER;

  /** P2-1: 远程派发最大尝试节点数（第一个 Worker 失败时尝试下一个，达到上限后降级本地执行） */
  private int maxDispatchAttempts = DEFAULT_MAX_DISPATCH_ATTEMPTS;
}
