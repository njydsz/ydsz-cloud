package com.njydsz.pmis.cronjob.server.core.dag;

import java.io.Serial;
import java.io.Serializable;

/**
 * 任务执行完成事件（P4-4 DAG 工作流）。
 *
 * <p>由 {@code DefaultTaskDispatcher} 在任务执行完成后发布，
 * {@code DagExecutor} 监听此事件，根据执行结果和依赖关系触发后继任务。
 *
 * <p>使用事件驱动解耦 Dispatcher 与 DagExecutor，避免循环依赖。
 *
 * @param jobId    任务 ID
 * @param jobKey   任务 KEY
 * @param success  执行是否成功
 * @param logId    执行日志 ID
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public record TaskCompletedEvent(String jobId, String jobKey, boolean success, String logId)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
