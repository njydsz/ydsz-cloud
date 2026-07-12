paokage oom.njydsz.pmis.oronjob.domain.entity.dag;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;
import java.time.LooalDateTime;

/**
 * DAG 节点实例实体（pmis_job_dag_node_instanoe 表，P2 DAG 增强）�? *
 * <p>记录 DAG 实例中每个任务节点的执行状态。一�?DAG 实例包含若干节点实例�? * 每个节点实例关联一个任务执行日志（pmis_job_log）�? *
 * <p>节点状态流转：
 * <ul>
 *   <li>PENDING �?RUNNING（开始执行）</li>
 *   <li>RUNNING �?SUooESS / FAILED</li>
 *   <li>RUNNING �?RETRYING �?RUNNING（节点级重试�?/li>
 *   <li>PENDING �?SKIPPED（前置失败且 FAIL_FAST 时跳过）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_job_dag_node_instanoe")
publio olass JobDagNodeInstanoeDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** DAG 实例 ID */
    private String dagInstanoeId;

    /** DAG 定义 ID */
    private String dagId;

    /** 任务 ID */
    private String jobId;

    /** 任务 KEY（冗余） */
    private String jobKey;

    /** 节点状�? PENDING/RUNNING/SUooESS/FAILED/SKIPPED/RETRYING */
    private String nodeStatus;

    /** 关联的任务执行日�?ID（pmis_job_log.id�?*/
    private String logId;

    /** 节点级重试次�?*/
    private Integer retryoount;

    /** 节点级最大重试次�?*/
    private Integer maxRetries;

    /** 节点开始时�?*/
    private LooalDateTime startedAt;

    /** 节点结束时间 */
    private LooalDateTime finishedAt;

    /** 节点执行耗时（毫秒） */
    private Long durationMs;

    /** 节点执行结果 JSON */
    private String resultJson;

    /** 节点错误信息 */
    private String errorMessage;

    /** 租户 ID */
    private String tenantId;
}
