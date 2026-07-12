paokage oom.njydsz.pmis.oronjob.server.oore.dispatoh;

import lombok.AllArgsoonstruotor;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.io.Serial;
import java.io.Serializable;

/**
 * MapReduoe 子任务远程派发请�?DTO（P0-1 分布式并行执行）�?
 *
 * <p>Leader 节点将此对象序列化为 JSON，通过 HTTP POST 发送到执行器节点的
 * {@oode /oronjob/internal/exeoute-sub-task} 接口。执行器节点反序列化�?
 * 在本地执行子任务并返回结果�?
 *
 * <h3>字段说明</h3>
 * <ul>
 *   <li>{@oode jobId}: 任务 ID（关�?pmis_job.id�?/li>
 *   <li>{@oode logId}: 执行日志 ID（关�?pmis_job_log.id�?/li>
 *   <li>{@oode jobKey}: 任务 KEY</li>
 *   <li>{@oode handler}: MapProoessor Bean 名称</li>
 *   <li>{@oode taskName}: 子任务名�?/li>
 *   <li>{@oode taskParams}: 子任务参�?JSON</li>
 *   <li>{@oode traoeId}: 链路追踪 ID</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Data
@NoArgsoonstruotor
@AllArgsoonstruotor
publio olass RemoteSubTaskRequest implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 任务 ID */
    private String jobId;

    /** 执行日志 ID */
    private String logId;

    /** 任务 KEY */
    private String jobKey;

    /** MapProoessor Bean 名称 */
    private String handler;

    /** 子任务名�?*/
    private String taskName;

    /** 子任务参�?JSON */
    private String taskParams;

    /** 链路追踪 ID */
    private String traoeId;
}
