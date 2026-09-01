package com.njydsz.cronjob.server.config;

import lombok.Data;

/**
 * P1-P3: 秒级预读调度配置。
 *
 * <p>控制 {@link com.njydsz.cronjob.server.core.scheduler.TaskPreloadScheduler} 的行为。
 * 主扫描器（默认 5s 周期）作为兜底，
 * 预读调度器将窗口内到期的 CRON 任务注册到内存 {@code ScheduledExecutorService}，到期精确派发
 * （毫秒级精度），消除"最差 5s 扫描延迟 + 派发延迟"的精度损失。
 *
 * <p>默认开启：预读窗口内任务由内存时间轮毫秒级触发，主扫描器兜底；两者通过
 * CAS 推进 next_fire_time 互斥，不会重复派发（实现见 JobScanner/TaskPreloadScheduler）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class PrecisionConfig {

  /** 默认scanIntervalMs值（可被配置文件覆盖） */
  private static final int DEFAULT_SCAN_INTERVAL_MS = 3000;

  /** 默认windowSeconds值（可被配置文件覆盖） */
  private static final int DEFAULT_WINDOW_SECONDS = 30;

  /** 默认batchSize值（可被配置文件覆盖） */
  private static final int DEFAULT_BATCH_SIZE = 200;

  /** 是否启用秒级预读调度（默认开启，CRON 任务获得毫秒级触发精度；主扫描器兜底） */
  private boolean enabled = true;

  /** 预读扫描周期（毫秒），默认 3s 一次将窗口内任务注册到内存调度器 */
  private int scanIntervalMs = DEFAULT_SCAN_INTERVAL_MS;

  /** 预读窗口（秒）：仅预读 next_fire_time 在 [now, now+window] 内的 CRON 任务 */
  private int windowSeconds = DEFAULT_WINDOW_SECONDS;

  /** 单批预读最大任务数 */
  private int batchSize = DEFAULT_BATCH_SIZE;
}
