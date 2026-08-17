package com.njydsz.cronjob.server.config;

import lombok.Data;

/**
 * P1-1: 自适应批量调度配置。
 *
 * <p>根据系统实时负载指标（CPU、内存、线程池活跃度）动态调整 JobScanner 的 batchSize， 避免高负载时大批量派发压垮系统，低负载时提升吞吐量。
 *
 * <h3>工作原理</h3>
 *
 * <ol>
 *   <li>定时采集 JVM 和操作系统指标（CPU 使用率、堆内存使用率、线程池活跃线程数）
 *   <li>根据负载评分计算最优 batchSize（低负载时放大，高负载时缩小）
 *   <li>通过 AtomicReference 安全发布新值，JobScanner 下次扫描时自动生效
 * </ol>
 *
 * <p>对标 PowerJob 的自适应调度和 SchedulerX 的流量控制能力。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class AdaptiveBatchConfig {

  /** 是否启用自适应批量调度（false=使用固定 batchSize，向后兼容） */
  private boolean enabled = false;

  /** 最小批量大小（高负载时不低于此值，防止饥饿） */
  private int minBatchSize = 50;

  /** 最大批量大小（低负载时不超过此值，防止 DB 连接耗尽） */
  private int maxBatchSize = 1000;

  /** CPU 使用率阈值（百分比），超过此值开始缩减批量 */
  private double cpuThreshold = 70.0;

  /** 内存使用率阈值（百分比），超过此值开始缩减批量 */
  private double memThreshold = 80.0;

  /** 线程池活跃度阈值（百分比，activeThreads/maxThreads），超过此值开始缩减批量 */
  private double poolActiveThreshold = 80.0;

  /** 负载评估间隔（秒，默认 10s） */
  private int evalIntervalSeconds = 10;
}
