package com.njydsz.cronjob.domain.job;

import lombok.Data;

/**
 * 分片上下文
 *
 * @author ydsz-team
 * @since 1.0.0
 * @since 1.0.0 由 common-domain 迁入 cronjob-domain
 */
@Data
public class ShardingContext {

  /** 分片总数 */
  private int shardTotal;

  /** 当前分片序号（从 0 开始） */
  private int shardIndex;

  /** 任务 ID */
  private String jobId;

  /** 任务 KEY */
  private String jobKey;

  /** 日志 ID */
  private String logId;
}
