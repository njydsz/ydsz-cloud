package com.njydsz.cronjob.server.config;

import lombok.Data;

/**
 * 任务扫描器配置。
 *
 * <p>控制 JobScanner 的扫描间隔、批量大小、并行派发等行为。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class ScannerConfig {

  /** 默认intervalMs值（可被配置文件覆盖） */
  private static final long DEFAULT_INTERVAL_MS = 5000;

  /** 默认batchSize值（可被配置文件覆盖） */
  private static final int DEFAULT_BATCH_SIZE = 500;

  /** 默认lockTtlSeconds值（可被配置文件覆盖） */
  private static final int DEFAULT_LOCK_TTL_SECONDS = 30;

  /** 默认misfireGraceMinutes值（可被配置文件覆盖） */
  private static final int DEFAULT_MISFIRE_GRACE_MINUTES = 30;

  /** 默认parallelDispatchPoolSize值（可被配置文件覆盖） */
  private static final int DEFAULT_PARALLEL_DISPATCH_POOL_SIZE = 8;

  /** 扫描间隔（毫秒，默认 5s） */
  private long intervalMs = DEFAULT_INTERVAL_MS;

  /**
   * 单批最多触发任务数（P0-2 吞吐提升：默认从 100 提升至 500）。
   *
   * <p>5s 扫描间隔 × 500 batch = 100 tasks/s 基线吞吐量。 万级任务场景可通过增大此值或缩短扫描间隔进一步提升。
   */
  private int batchSize = DEFAULT_BATCH_SIZE;

  /** 扫描锁 TTL（秒，默认 30s） */
  private int lockTtlSeconds = DEFAULT_LOCK_TTL_SECONDS;

  /** Misfire 宽容窗口（分钟，超过此窗口的任务按 misfire_policy 处理） */
  private int misfireGraceMinutes = DEFAULT_MISFIRE_GRACE_MINUTES;

  /**
   * P0-2: 是否启用并行派发（默认 true）。
   *
   * <p>启用后，JobScanner 扫描到待触发任务后，使用独立线程池并行执行 CAS 推进 + dispatch，避免大批量任务时单线程串行派发延迟。 CAS
   * 推进本身是幂等的（WHERE next_fire_time = old），并行不会导致重复派发。
   */
  private boolean parallelDispatchEnabled = true;

  /**
   * P0-2: 并行派发线程池大小（默认 8）。
   *
   * <p>控制单次扫描中并行派发的并发度。过大可能压垮 DB 连接池（CAS 操作）， 过小则并行效果不明显。
   */
  private int parallelDispatchPoolSize = DEFAULT_PARALLEL_DISPATCH_POOL_SIZE;
}
