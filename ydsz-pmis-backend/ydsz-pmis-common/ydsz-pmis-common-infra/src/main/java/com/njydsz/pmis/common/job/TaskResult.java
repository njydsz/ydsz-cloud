package com.njydsz.pmis.common.job;

import java.io.Serial;
import java.io.Serializable;

/**
 * 子任务执行结果（P0-4）。
 *
 * <p>包含 taskName + {@link ProcessResult}，供 reduce 方法使用。
 * 与 {@link ProcessResult} 的区别：本类额外携带 taskName，便于业务侧在 reduce 时
 * 区分不同子任务的结果（如按 taskName 聚合）。
 *
 * <p>由 {@code MapTaskExecutor} 在每个子任务执行完成后构造，
 * 传递给 {@link MapReduceProcessor#reduce(MapContext, java.util.List)}。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class TaskResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 子任务名称 */
    private final String taskName;

    /** 子任务处理结果 */
    private final ProcessResult processResult;

    public TaskResult(String taskName, ProcessResult processResult) {
        this.taskName = taskName;
        this.processResult = processResult;
    }

    public String getTaskName() {
        return taskName;
    }

    public ProcessResult getProcessResult() {
        return processResult;
    }
}
