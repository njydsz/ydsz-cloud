package com.njydsz.pmis.common.core.job;

import lombok.Data;

/**
 * MapReduce 子任务定义。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class MapTask {

    /** 子任务 ID */
    private String taskId;
    /** 父任务 ID */
    private String parentJobId;
    /** 父日志 ID */
    private String parentLogId;
    /** 子任务名称 */
    private String taskName;
    /** 子任务参数 JSON */
    private String taskParams;
    /** 分片索引 */
    private int shardIndex;
    /** 分片总数 */
    private int shardTotal;
    /** 任务参数 JSON */
    private String paramsJson;
    /** 处理器 Bean 名称 */
    private String processorName;

    public MapTask() {
    }

    public MapTask(String taskId, String parentJobId, String parentLogId, int shardIndex, int shardTotal) {
        this.taskId = taskId;
        this.parentJobId = parentJobId;
        this.parentLogId = parentLogId;
        this.shardIndex = shardIndex;
        this.shardTotal = shardTotal;
    }

    /**
     * 构造子任务（带名称和参数）。
     *
     * @param taskName   子任务名称
     * @param taskParams 子任务参数
     */
    public MapTask(String taskName, String taskParams) {
        this.taskName = taskName;
        this.taskParams = taskParams;
    }
}
