package com.njydsz.common.core.job;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * MapReduce 任务执行上下文。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class MapContext implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务 ID */
    private String jobId;

    /** 执行日志 ID */
    private String logId;

    /** 任务 KEY */
    private String jobKey;

    /** 任务名称 */
    private String taskName;

    /** 任务参数 JSON */
    private String taskParams;

    /** 是否为 Root 任务 */
    private boolean root;

    /** 子任务列表（仅 root 任务可写） */
    private List<MapTask> subTasks = new ArrayList<>();

    /** 任务结果存储（reduce 阶段使用） */
    private Map<String, Object> results = new HashMap<>();

    public MapContext() {
    }

    public MapContext(String jobId, String logId, String jobKey, String taskName,
                      String taskParams, boolean root) {
        this.jobId = jobId;
        this.logId = logId;
        this.jobKey = jobKey;
        this.taskName = taskName;
        this.taskParams = taskParams;
        this.root = root;
    }

    /**
     * 判断是否为 Root 任务。
     *
     * @return true 表示 Root 任务
     */
    public boolean isRoot() {
        return root;
    }

    /**
     * 添加子任务。
     *
     * @param taskName   任务名称
     * @param taskParams 任务参数 JSON
     */
    public void addSubTask(String taskName, String taskParams) {
        this.subTasks.add(new MapTask(taskName, taskParams));
    }

    /**
     * 添加子任务。
     *
     * @param subTask 子任务
     */
    public void addSubTask(MapTask subTask) {
        this.subTasks.add(subTask);
    }
}
