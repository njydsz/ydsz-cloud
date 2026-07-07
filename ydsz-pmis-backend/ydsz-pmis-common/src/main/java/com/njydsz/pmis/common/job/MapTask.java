package com.njydsz.pmis.common.job;

import java.io.Serial;
import java.io.Serializable;

/**
 * Map 子任务定义（P0-4）。
 *
 * <p>由业务侧在 root task 中通过 {@link MapContext#map(java.util.List)} 产生，
 * 框架读取后为每个子任务创建 {@code pmis_job_task} 记录并执行。
 *
 * <p>对标 PowerJob 的 MapTaskDefinition：
 * <ul>
 *   <li>{@link #taskName}：子任务名称（便于日志展示与排查）</li>
 *   <li>{@link #taskParams}：子任务参数 JSON（业务侧自定义结构）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class MapTask implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 子任务名称 */
    private final String taskName;

    /** 子任务参数 JSON */
    private final String taskParams;

    public MapTask(String taskName, String taskParams) {
        this.taskName = taskName;
        this.taskParams = taskParams;
    }

    public String getTaskName() {
        return taskName;
    }

    public String getTaskParams() {
        return taskParams;
    }
}
