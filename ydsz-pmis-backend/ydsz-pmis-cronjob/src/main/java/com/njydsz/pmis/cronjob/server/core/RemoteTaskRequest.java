package com.njydsz.pmis.cronjob.server.core.dispatch;

import com.njydsz.pmis.cronjob.domain.entity.job.JobDO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 远程派发请求 DTO（P1-4）。
 *
 * <p>Leader 节点将此对象序列化为 JSON，通过 HTTP POST 发送到执行器节点的
 * {@code /cronjob/internal/execute} 接口。执行器节点反序列化后调用
 * {@link TaskDispatcher#executeLocally(JobDO, String, int, int)} 在本地执行。
 *
 * <h3>字段说明</h3>
 * <ul>
 *   <li>{@code job}: 任务定义（含 handler、paramsJson、cronExpression 等）</li>
 *   <li>{@code triggerType}: 触发类型（CRON/MANUAL/RETRY/MISFIRED/DEPENDENT）</li>
 *   <li>{@code shardIndex}: 分片索引（-1 表示非分片任务）</li>
 *   <li>{@code shardTotal}: 分片总数（1 表示非分片任务）</li>
 *   <li>{@code traceId}: 链路追踪 ID（从 Leader MDC 传递，保证全链路 traceId 串联）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RemoteTaskRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务定义 */
    private JobDO job;

    /** 触发类型: CRON / MANUAL / RETRY / MISFIRED / DEPENDENT */
    private String triggerType;

    /** 分片索引（-1 表示非分片任务） */
    private int shardIndex;

    /** 分片总数（1 表示非分片任务） */
    private int shardTotal;

    /** 链路追踪 ID（从 Leader MDC 传递） */
    private String traceId;
}
