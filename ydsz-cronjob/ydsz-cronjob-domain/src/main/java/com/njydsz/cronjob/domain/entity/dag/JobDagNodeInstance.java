package com.njydsz.cronjob.domain.entity.dag;

import java.io.Serial;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * DAG 节点实例实体（ydsz_job_dag_node_instance 表，P2 DAG 增强）。
 *
 * <p>记录 DAG 实例中每个任务节点的执行状态。一个 DAG 实例包含若干节点实例，
 * 每个节点实例关联一个任务执行日志（ydsz_job_log）。
 *
 * <p>节点状态流转：
 * <ul>
 *   <li>PENDING → RUNNING（开始执行）</li>
 *   <li>RUNNING → SUCCESS / FAILED</li>
 *   <li>RUNNING → RETRYING → RUNNING（节点级重试）</li>
 *   <li>PENDING → SKIPPED（前置失败且 FAIL_FAST 时跳过）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_job_dag_node_instance")
public class JobDagNodeInstance extends MpBaseEntity<String> {

    @Serial
    private static final long serialVersionUID = 1L;

    /** DAG 实例 ID */
    private String dagInstanceId;

    /** DAG 定义 ID */
    private String dagId;

    /** 任务 ID */
    private String jobId;

    /** 任务 KEY（冗余） */
    private String jobKey;

    /** 节点状态: PENDING/RUNNING/SUCCESS/FAILED/SKIPPED/RETRYING */
    private String nodeStatus;

    /** 关联的任务执行日志 ID（ydsz_job_log.id） */
    private String logId;

    /** 节点级重试次数 */
    private Integer retryCount;

    /** 节点级最大重试次数 */
    private Integer maxRetries;

    /** 节点开始时间 */
    private LocalDateTime startedAt;

    /** 节点结束时间 */
    private LocalDateTime finishedAt;

    /** 节点执行耗时（毫秒） */
    private Long durationMs;

    /** 节点执行结果 JSON */
    private String resultJson;

    /** 节点错误信息 */
    private String errorMessage;

}
