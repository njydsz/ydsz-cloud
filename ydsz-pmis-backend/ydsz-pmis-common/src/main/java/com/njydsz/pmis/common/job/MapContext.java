package com.njydsz.pmis.common.job;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Map 执行上下文（P0-4 MapReduce）。
 *
 * <p>由 {@code MapTaskExecutor} 在调用 {@link MapProcessor#process(MapContext)} 前构造，
 * 携带任务元信息（jobId/logId/jobKey/taskName/taskParams）并提供 {@link #map(List)} 方法
 * 供 root task 动态产生子任务。
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>root task（{@link #isRootTask()}=true）内调用 {@link #map(List)} 产生子任务，
 *       框架读取 {@link #getSubTasks()} 后为每个子任务创建 TaskDO 记录并执行</li>
 *   <li>子任务内调用 {@link #map(List)} 会被忽略（仅记 warn 日志），避免无限递归</li>
 *   <li>{@link #getSubTasks()} 返回不可变视图，避免框架读取后被业务侧篡改</li>
 * </ul>
 *
 * <p>对标 PowerJob 的 TaskContext，本上下文为单线程使用（每个任务执行线程独立构造），
 * 无需考虑线程安全。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class MapContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务 ID（关联 pmis_job.id） */
    private final String jobId;

    /** 执行日志 ID（关联 pmis_job_log.id） */
    private final String logId;

    /** 任务 KEY（冗余，便于业务侧日志关联） */
    private final String jobKey;

    /** 当前任务名称（root task 为 "root"，子任务为业务侧定义的 taskName） */
    private final String taskName;

    /** 当前任务参数 JSON（root task 为 pmis_job.params_json，子任务为 MapTask.taskParams） */
    private final String taskParams;

    /** 是否为 root task（true 时可调用 map() 产生子任务） */
    private final boolean rootTask;

    /** 业务侧产生的子任务列表（仅 root task 调用 map() 时追加） */
    private final List<MapTask> subTasks = new ArrayList<>();

    public MapContext(String jobId, String logId, String jobKey, String taskName,
                      String taskParams, boolean isRootTask) {
        this.jobId = jobId;
        this.logId = logId;
        this.jobKey = jobKey;
        this.taskName = taskName;
        this.taskParams = taskParams;
        this.rootTask = isRootTask;
    }

    /**
     * 产生子任务（仅 root task 有效）。
     *
     * <p>将参数列表追加到内部 {@link #subTasks}，供 {@code MapTaskExecutor} 读取后创建 TaskDO 记录。
     * 子任务调用本方法会被忽略（仅记 warn 日志），避免无限递归。
     *
     * @param tasks 子任务列表；null 或空列表将被忽略
     */
    public void map(List<MapTask> tasks) {
        if (!rootTask) {
            // 子任务调用 map() 被忽略，避免无限递归
            return;
        }
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        subTasks.addAll(tasks);
    }

    /**
     * 获取子任务列表的不可变视图。
     *
     * <p>供 {@code MapTaskExecutor} 在 root task 执行完成后读取，
     * 返回不可变视图避免框架读取后被业务侧篡改。
     *
     * @return 子任务列表的不可变视图；无子任务时返回空列表
     */
    public List<MapTask> getSubTasks() {
        return Collections.unmodifiableList(subTasks);
    }

    public String getJobId() {
        return jobId;
    }

    public String getLogId() {
        return logId;
    }

    public String getJobKey() {
        return jobKey;
    }

    public String getTaskName() {
        return taskName;
    }

    public String getTaskParams() {
        return taskParams;
    }

    public boolean isRootTask() {
        return rootTask;
    }
}
