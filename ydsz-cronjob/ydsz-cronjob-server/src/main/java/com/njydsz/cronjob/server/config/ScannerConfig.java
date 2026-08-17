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

  /** 扫描间隔（毫秒，默认 5s） */
  private long intervalMs = 5000;

  /**
   * 单批最多触发任务数（P0-2 吞吐提升：默认从 100 提升至 500）。
   *
   * <p>5s 扫描间隔 × 500 batch = 100 tasks/s 基线吞吐量。 万级任务场景可通过增大此值或缩短扫描间隔进一步提升。
   */
  private int batchSize = 500;

  /** 扫描锁 TTL（秒，默认 30s） */
  private int lockTtlSeconds = 30;

  /** Misfire 宽容窗口（分钟，超过此窗口的任务按 misfire_policy 处理） */
  private int misfireGraceMinutes = 30;

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
  private int parallelDispatchPoolSize = 8;
}
