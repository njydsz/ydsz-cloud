package com.njydsz.cronjob.server.config;

import lombok.Data;

/**
 * P0-1: MapReduce 分布式并行执行配置。
 *
 * <p>控制 MapReduce 子任务的分布式并行执行行为：
 *
 * <ul>
 *   <li>{@link #enabled}: 是否启用分布式并行执行（false=单节点顺序执行，向后兼容）
 *   <li>{@link #maxParallelSubTasks}: 最大并行子任务数（控制并行度，防止资源耗尽）
 *   <li>{@link #subTaskTimeoutSeconds}: 单个子任务远程执行超时时间
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class MapReduceConfig {

  /** 是否启用分布式并行执行（false=单节点顺序执行，向后兼容） */
  private boolean enabled = true;

  /** 最大并行子任务数（默认 8，控制并行度） */
  private int maxParallelSubTasks = 8;

  /** 单个子任务远程执行超时时间（秒，默认 120s） */
  private int subTaskTimeoutSeconds = 120;

  /** 远程子任务派发失败时是否降级本地执行 */
  private boolean fallbackToLocal = true;
}
