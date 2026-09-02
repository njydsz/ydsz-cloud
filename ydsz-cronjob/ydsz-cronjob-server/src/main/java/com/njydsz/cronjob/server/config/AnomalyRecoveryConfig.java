package com.njydsz.cronjob.server.config;

import lombok.Data;

/**
 * P1-4: 异常修复统一配置（合并原 Failover + SelfHealing）。
 *
 * <p>控制 AnomalyRecoveryScanner 的扫描行为：
 *
 * <ul>
 *   <li>故障转移：检测下线节点上的 RUNNING 任务并重新派发
 *   <li>卡死修复：修复 RUNNING 状态超过阈值的任务
 *   <li>AUTO_PAUSED 恢复：到达恢复时间后自动恢复为 NORMAL
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class AnomalyRecoveryConfig {

  /** 默认scanIntervalSeconds值（可被配置文件覆盖） */
  private static final int DEFAULT_SCAN_INTERVAL_SECONDS = 30;

  /** 默认scanNodeLimit值（可被配置文件覆盖） */
  private static final int DEFAULT_SCAN_NODE_LIMIT = 10;

  /** 默认failoverTaskLimit值（可被配置文件覆盖） */
  private static final int DEFAULT_FAILOVER_TASK_LIMIT = 50;

  /** 默认stuckThresholdSeconds值（可被配置文件覆盖） */
  private static final int DEFAULT_STUCK_THRESHOLD_SECONDS = 300;

  /** 默认maxHealPerScan值（可被配置文件覆盖） */
  private static final int DEFAULT_MAX_HEAL_PER_SCAN = 20;

  /** 默认maxRedispatchRetries值（可被配置文件覆盖） */
  private static final int DEFAULT_MAX_REDISPATCH_RETRIES = 3;

  /** 是否启用故障转移扫描（检测下线节点任务） */
  private boolean failoverEnabled = true;

  /** 是否启用自愈系统（卡死修复 + AUTO_PAUSED 恢复） */
  private boolean selfHealingEnabled = false;

  /** 扫描间隔（秒，默认 30s） */
  private int scanIntervalSeconds = DEFAULT_SCAN_INTERVAL_SECONDS;

  /** 单批最多扫描节点数 */
  private int scanNodeLimit = DEFAULT_SCAN_NODE_LIMIT;

  /** 单节点最多转移任务数 */
  private int failoverTaskLimit = DEFAULT_FAILOVER_TASK_LIMIT;

  /** RUNNING 状态无更新超时阈值（秒，超过此值视为卡死） */
  private int stuckThresholdSeconds = DEFAULT_STUCK_THRESHOLD_SECONDS;

  /** 单次扫描最大修复任务数（防止批量修复压垮系统） */
  private int maxHealPerScan = DEFAULT_MAX_HEAL_PER_SCAN;

  /** 是否自动重新派发修复后的任务 */
  private boolean autoRedispatch = true;

  /** 重新派发最大重试次数（超过此数不再自动派发，标记为需人工介入） */
  private int maxRedispatchRetries = DEFAULT_MAX_REDISPATCH_RETRIES;
}
