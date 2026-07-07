package com.njydsz.pmis.common.job;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分片上下文（P3 阶段引入）。
 *
 * <p>当任务配置了分片参数（{@code shard_total > 1}）时，Leader 派发任务前会通过
 * {@code ShardingStrategy} 计算每个节点应处理的分片范围，构造本上下文传入 {@link JobHandler}。
 *
 * <p>对标 XXL-Job 的 ShardingUtil.getShardingVo() / PowerJob 的 InstanceContext：
 * <ul>
 *   <li>{@link #shardTotal}：总分片数（通常=在线节点数）</li>
 *   <li>{@link #shardIndex}：当前分片索引（0-based，由节点 ID 在在线列表中的位置决定）</li>
 *   <li>{@link #shardItems}：当前分片应处理的业务标识列表（如 ID 范围、表名等，可选）</li>
 * </ul>
 *
 * <p>典型用法（业务侧）：
 * <pre>{@code
 * if (ctx != null && ctx.getShardTotal() > 1) {
 *     // 分片模式：仅处理本分片的数据
 *     List<Long> ids = loadDataForShard(ctx.getShardIndex(), ctx.getShardTotal());
 *     processIds(ids);
 * } else {
 *     // 非分片模式：处理全部数据
 *     processAll();
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class ShardingContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 总分片数（1 表示非分片任务） */
    private final int shardTotal;

    /** 当前分片索引（0-based，范围 [0, shardTotal)） */
    private final int shardIndex;

    /** 当前分片应处理的业务标识列表（如 ID 范围、表名等，可为空） */
    private final List<String> shardItems;

    /** 任务 KEY（便于业务侧日志关联） */
    private final String jobKey;

    /** 执行日志 ID（便于业务侧日志关联） */
    private final String logId;

    public ShardingContext(int shardTotal, int shardIndex, List<String> shardItems,
                           String jobKey, String logId) {
        if (shardTotal < 1) {
            throw new IllegalArgumentException("shardTotal 必须 >= 1, 实际: " + shardTotal);
        }
        if (shardIndex < 0 || shardIndex >= shardTotal) {
            throw new IllegalArgumentException(
                    "shardIndex 必须 ∈ [0, " + shardTotal + "), 实际: " + shardIndex);
        }
        this.shardTotal = shardTotal;
        this.shardIndex = shardIndex;
        this.shardItems = shardItems == null ? Collections.emptyList() : shardItems;
        this.jobKey = jobKey;
        this.logId = logId;
    }

    public int getShardTotal() {
        return shardTotal;
    }

    public int getShardIndex() {
        return shardIndex;
    }

    public List<String> getShardItems() {
        return shardItems;
    }

    public String getJobKey() {
        return jobKey;
    }

    public String getLogId() {
        return logId;
    }

    /**
     * 是否为分片任务。
     *
     * @return true 当 shardTotal > 1
     */
    public boolean isSharding() {
        return shardTotal > 1;
    }

    @Override
    public String toString() {
        return "ShardingContext{shardTotal=" + shardTotal
                + ", shardIndex=" + shardIndex
                + ", shardItems=" + shardItems.size() + " items"
                + ", jobKey='" + jobKey + '\''
                + ", logId='" + logId + '\'' + '}';
    }
}
