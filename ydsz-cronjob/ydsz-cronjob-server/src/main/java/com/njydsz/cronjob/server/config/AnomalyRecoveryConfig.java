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
 * <p>对标 XXL-Job 的失败重试 + 分片任务转移、PowerJob 的自愈能力、SchedulerX 的自动恢复机制。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class AnomalyRecoveryConfig {

  /** 是否启用故障转移扫描（检测下线节点任务） */
  private boolean failoverEnabled = true;

  /** 是否启用自愈系统（卡死修复 + AUTO_PAUSED 恢复） */
  private boolean selfHealingEnabled = false;

  /** 扫描间隔（秒，默认 30s） */
  private int scanIntervalSeconds = 30;

  /** 单批最多扫描节点数 */
  private int scanNodeLimit = 10;

  /** 单节点最多转移任务数 */
  private int failoverTaskLimit = 50;

  /** RUNNING 状态无更新超时阈值（秒，超过此值视为卡死） */
  private int stuckThresholdSeconds = 300;

  /** 单次扫描最大修复任务数（防止批量修复压垮系统） */
  private int maxHealPerScan = 20;

  /** 是否自动重新派发修复后的任务 */
  private boolean autoRedispatch = true;

  /** 重新派发最大重试次数（超过此数不再自动派发，标记为需人工介入） */
  private int maxRedispatchRetries = 3;
}
