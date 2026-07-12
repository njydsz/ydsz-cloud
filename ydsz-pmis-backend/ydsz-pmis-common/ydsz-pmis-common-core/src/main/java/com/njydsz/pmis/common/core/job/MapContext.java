package com.njydsz.pmis.common.core.job;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * MapReduce Map 阶段上下文。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class MapContext {

    /** 任务 ID */
    private String jobId;
    /** 日志 ID */
    private String logId;
    /** 任务 KEY */
    private String jobKey;
    /** 任务名称 */
    private String taskName;
    /** 任务参数 JSON */
    private String paramsJson;
    /** 是否为根任务 */
    private boolean isRoot;
    /** 子任务列表 */
    private List<MapTask> subTasks = new ArrayList<>();

    public MapContext() {
    }

    /**
     * 构造 Map 上下文。
     *
     * @param jobId       任务 ID
     * @param logId       日志 ID
     * @param jobKey      任务 KEY
     * @param taskName    任务名称
     * @param paramsJson  任务参数 JSON
     * @param isRoot      是否为根任务
     */
    public MapContext(String jobId, String logId, String jobKey, String taskName,
                      String paramsJson, boolean isRoot) {
        this.jobId = jobId;
        this.logId = logId;
        this.jobKey = jobKey;
        this.taskName = taskName;
        this.paramsJson = paramsJson;
        this.isRoot = isRoot;
    }

    /**
     * 添加子任务。
     *
     * @param taskName    子任务名称
     * @param taskParams  子任务参数
     */
    public void addSubTask(String taskName, String taskParams) {
        subTasks.add(new MapTask(taskName, taskParams));
    }

    /**
     * 获取子任务列表。
     *
     * @return 子任务列表
     */
    public List<MapTask> getSubTasks() {
        return subTasks;
    }
}
