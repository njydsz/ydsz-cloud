package com.njydsz.common.core.job;

import java.util.Collections;
import java.util.List;

/**
 * 分片任务执行上下文
 *
 * <p>封装分片任务执行时的分片信息，由调度器构造并传入
 * {@link JobHandler#execute(String, ShardingContext)} 方法。
 *
 * <p>对标 ElasticJob 的 ShardingContext / XXL-Job 的分片广播参数。
 * 分片任务用于将大任务拆分为多个子任务并行处理，提升吞吐量。
 *
 * <p><b>典型分片策略：</b>
 * <ul>
 *   <li><b>按主键区间</b>：shardIndex=0 处理 ID 1-10000，shardIndex=1 处理 10001-20000</li>
 *   <li><b>按哈希取模</b>：shardParams={"hashMod:4"} 表示按 hash 模 4 拆分</li>
 *   <li><b>按时间窗口</b>：每个分片处理一个时间窗口的数据</li>
 * </ul>
 *
 * @param shardTotal  分片总数（如配置为 4 表示拆为 4 个子任务）
 * @param shardIndex  当前分片索引（从 0 开始，范围 [0, shardTotal)）
 * @param shardParams 分片参数列表（可选，由分片策略传入）
 * @param jobKey      任务标识
 * @param logId       执行日志 ID
 * @author ydsz-team
 * @since 1.0.0
 * @see JobHandler
 */
public record ShardingContext(
        int shardTotal,
        int shardIndex,
        List<String> shardParams,
        String jobKey,
        String logId
) {

    /**
     * 便捷构造器：无分片参数列表
     *
     * @param shardTotal 分片总数
     * @param shardIndex 当前分片索引
     * @param jobKey     任务标识
     * @param logId      执行日志 ID
     */
    public ShardingContext(int shardTotal, int shardIndex, String jobKey, String logId) {
        this(shardTotal, shardIndex, Collections.emptyList(), jobKey, logId);
    }

    /**
     * 是否为单分片（非分片场景）
     *
     * @return shardTotal &lt;= 1 时返回 true（即未启用分片）
     */
    public boolean isSingleShard() {
        return shardTotal <= 1;
    }
}
