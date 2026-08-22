package com.njydsz.cronjob.server.config;

import lombok.Data;

/**
 * P1-P3: 秒级预读调度配置。
 *
 * <p>控制 {@link com.njydsz.cronjob.server.core.scheduler.TaskPreloadScheduler} 的行为。
 * 对标 XXL-Job 的 6000ms 预读窗口与 PowerJob 的时间轮：主扫描器（默认 5s 周期）作为兜底，
 * 预读调度器将窗口内到期的 CRON 任务注册到内存 {@code ScheduledExecutorService}，到期精确派发
 * （毫秒级精度），消除"最差 5s 扫描延迟 + 派发延迟"的精度损失。
 *
 * <p>默认关闭（保守启用）：现有 5s 扫描对分钟级任务已足够，秒级精度场景（倒计时、准点报表等）开启。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class PrecisionConfig {

  /** 是否启用秒级预读调度（默认关闭，开启后 CRON 任务获得毫秒级触发精度） */
  private boolean enabled = false;

  /** 预读扫描周期（毫秒），默认 3s 一次将窗口内任务注册到内存调度器 */
  private int scanIntervalMs = 3000;

  /** 预读窗口（秒）：仅预读 next_fire_time 在 [now, now+window] 内的 CRON 任务 */
  private int windowSeconds = 30;

  /** 单批预读最大任务数 */
  private int batchSize = 200;
}
