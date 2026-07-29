package com.njydsz.common.domain.job;

import lombok.Data;

/**
 * 分片上下文
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class ShardingContext {

    /** 分片总数 */
    private int shardTotal;
    /** 当前分片序号（从 0 开始） */
    private int shardIndex;
    /** 任务 ID */
    private Long jobId;
    /** 日志 ID */
    private Long logId;
}
