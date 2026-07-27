package com.njydsz.common.core.job;

import java.util.Collections;
import java.util.List;

/**
 * 分片任务执行上下文。
 *
 * <p>封装分片任务执行时的分片信息，由调度器构造并传入
 * {@link JobHandler#execute(String, ShardingContext)} 方法。
 *
 * <p>对标 ElasticJob 的 ShardingContext / XXL-Job 的分片广播参数。
 *
 * @param shardTotal    分片总数
 * @param shardIndex    当前分片索引（从 0 开始）
 * @param shardParams   分片参数列表（可选，由分片策略传入）
 * @param jobKey        任务标识
 * @param logId         执行日志 ID
 * @author ydsz-team
 * @since 1.0.0
 */
public record ShardingContext(
        int shardTotal,
        int shardIndex,
        List<String> shardParams,
        String jobKey,
        String logId
) {

    /**
     * 便捷构造器：无分片参数列表。
     */
    public ShardingContext(int shardTotal, int shardIndex, String jobKey, String logId) {
        this(shardTotal, shardIndex, Collections.emptyList(), jobKey, logId);
    }

    /**
     * 是否为单分片（非分片场景）。
     *
     * @return shardTotal <= 1 时返回 true
     */
    public boolean isSingleShard() {
        return shardTotal <= 1;
    }
}
