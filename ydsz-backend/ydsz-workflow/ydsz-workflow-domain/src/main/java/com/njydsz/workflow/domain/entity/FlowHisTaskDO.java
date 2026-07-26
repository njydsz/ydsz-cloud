package com.njydsz.workflow.domain.entity;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 历史任务 DO
 *
 * <p>对标 Warm-Flow flow_his_task，已完成任务归档，避免主表膨胀。<br>
 * 设计要点：created_at 复用 MpBaseEntity 字段，但关闭自动填充，归档时由业务代码显式从源 task.createdAt 复制（保留业务创建时间，非归档时间）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_flow_his_task")
public class FlowHisTaskDO extends MpBaseEntity<String> {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 流程实例 ID */
    private String instanceId;
    /** 原始任务 ID */
    private String taskId;
    /** 流程编码 */
    private String flowCode;
    /** 流程定义 ID */
    private String definitionId;
    /** 节点编码 */
    private String nodeCode;
    /** 节点名称 */
    private String nodeName;
    /** 节点类型（FlowNodeType.code） */
    private Integer nodeType;
    /** 业务类型 */
    private String businessType;
    /** 业务单据 ID */
    private String businessId;
    /** 业务单据编号 */
    private String businessNo;
    /** 流程名称 */
    private String flowName;
    /** 任务标题 */
    private String title;
    /** 办理人类型（FlowAssigneeType.name） */
    private String assigneeType;
    /** 办理人 ID */
    private String assigneeId;
    /** 办理人姓名 */
    private String assigneeName;
    /** 会签类型（FlowPerformType.name） */
    private String performType;
    /** 会签所需通过人数 */
    private Integer approveCount;
    /** 会签当前已通过人数 */
    private Integer approveFinished;
    /** P1-5: VOTE 模式通过率阈值（0~1，从源 task 复制） */
    private BigDecimal votePassRate;
    /** 任务状态（FlowTaskStatus.name） */
    private String taskStatus;
    /** 审批意见 */
    private String comment;

    /** 签收时间 */
    private LocalDateTime claimAt;
    /** 完成时间 */
    private LocalDateTime finishAt;
    /** 耗时（毫秒） */
    private Long durationMs;
    /** 租户 ID */
    private String tenantId;
    /** 链路追踪 ID */
    private String providerTraceId;

    /**
     * GAP-P2-10: FOREACH 迭代元素值（从源 task 复制）
     *
     * <p>循环节点每条独立 task 对应的集合元素，归档后保留以支持审批历史追溯
     * （如「这个审批是谁做的？属于哪一轮迭代？」）。
     * 非 FOREACH 节点的 task 该字段为 null。
     */
    private String iterVar;
}
