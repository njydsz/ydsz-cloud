package com.njydsz.pmis.cronjob.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * DAG 工作流实例实体（pmis_job_dag_instance 表，P2 DAG 增强）。
 *
 * <p>记录每次 DAG 执行的整体状态。一个 DAG 定义可对应多次实例。
 * {@link #contextJson} 存储 DAG 实例级上下文，支持跨节点传参。
 *
 * <p>状态流转：
 * <ul>
 *   <li>PENDING → RUNNING（开始执行）</li>
 *   <li>RUNNING → SUCCESS（全部节点成功）/ FAILED（FAIL_FAST 中止）/ PARTIAL_SUCCESS（部分失败）</li>
 *   <li>RUNNING → PAUSED（手动暂停）/ CANCELED（手动取消）</li>
 *   <li>PAUSED → RUNNING（恢复）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_job_dag_instance")
public class JobDagInstanceDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** DAG 定义 ID */
    private String dagId;

    /** DAG KEY（冗余，便于查询） */
    private String dagKey;

    /** 实例状态: PENDING/RUNNING/SUCCESS/FAILED/PARTIAL_SUCCESS/PAUSED/CANCELED */
    private String status;

    /** 触发类型: MANUAL/CRON/DEPENDENT */
    private String triggerType;

    /** 触发人（MANUAL 时为用户 ID） */
    private String triggerBy;

    /** 触发 traceId（用于链路追踪） */
    private String triggerTraceId;

    /** DAG 实例级上下文 JSON（跨节点传参） */
    private String contextJson;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 结束时间 */
    private LocalDateTime finishedAt;

    /** 执行耗时（毫秒） */
    private Long durationMs;

    /** 错误信息（FAILED 时填充） */
    private String errorMessage;

    /** 总节点数 */
    private Integer totalNodes;

    /** 成功节点数 */
    private Integer successNodes;

    /** 失败节点数 */
    private Integer failedNodes;

    /** 跳过节点数 */
    private Integer skippedNodes;

    /** 租户 ID */
    private String tenantId;
}
