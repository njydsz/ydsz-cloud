package com.njydsz.pmis.common.core.job;

import lombok.Data;

import java.util.List;

/**
 * 分片上下文。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class ShardingContext {

    /** 当前分片索引（从 0 开始） */
    private int shardIndex;
    /** 总分片数 */
    private int shardTotal;
    /** 任务参数 JSON */
    private String paramsJson;
    /** 任务 ID */
    private String jobId;
    /** 任务 KEY */
    private String jobKey;
    /** 日志 ID */
    private String logId;
    /** 分片列表（可用于广播任务） */
    private List<Object> shards;

    public ShardingContext() {
    }

    public ShardingContext(int shardIndex, int shardTotal, String paramsJson) {
        this.shardIndex = shardIndex;
        this.shardTotal = shardTotal;
        this.paramsJson = paramsJson;
    }

    /**
     * 构造分片上下文。
     *
     * @param shardTotal 总分片数
     * @param shardIndex 当前分片索引
     * @param shards     分片列表
     * @param jobKey     任务 KEY
     * @param logId      日志 ID
     */
    public ShardingContext(int shardTotal, int shardIndex, List<Object> shards, String jobKey, String logId) {
        this.shardIndex = shardIndex;
        this.shardTotal = shardTotal;
        this.shards = shards;
        this.jobKey = jobKey;
        this.logId = logId;
    }
}
