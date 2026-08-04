package com.remisoft.cronjob.server.core.dispatch;

import java.io.Serial;
import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MapReduce 子任务远程派发请求 DTO（P0-1 分布式并行执行）。
 *
 * <p>Leader 节点将此对象序列化为 JSON，通过 HTTP POST 发送到执行器节点的
 * {@code /cronjob/internal/execute-sub-task} 接口。执行器节点反序列化后
 * 在本地执行子任务并返回结果。
 *
 * <h3>字段说明</h3>
 * <ul>
 *   <li>{@code jobId}: 任务 ID（关联 remi_job.id）</li>
 *   <li>{@code logId}: 执行日志 ID（关联 remi_job_log.id）</li>
 *   <li>{@code jobKey}: 任务 KEY</li>
 *   <li>{@code handler}: MapProcessor Bean 名称</li>
 *   <li>{@code taskName}: 子任务名称</li>
 *   <li>{@code taskParams}: 子任务参数 JSON</li>
 *   <li>{@code traceId}: 链路追踪 ID</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RemoteSubTaskRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务 ID */
    private String jobId;

    /** 执行日志 ID */
    private String logId;

    /** 任务 KEY */
    private String jobKey;

    /** MapProcessor Bean 名称 */
    private String handler;

    /** 子任务名称 */
    private String taskName;

    /** 子任务参数 JSON */
    private String taskParams;

    /** 链路追踪 ID */
    private String traceId;
}
