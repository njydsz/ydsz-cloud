package com.njydsz.workflow.domain.entity;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;
import com.njydsz.common.domain.entity.BaseDO;

/**
 * 待办任务运行态 DO
 *
 * <p>对应表 {@code ydsz_flow_run_task}（原 {@code ydsz_flow_task}，2026-07-06 重命名），
 * 存储实例推进过程中产生的待办切片，办理人待办箱核心表。
 *
 * <p>命名说明：表名采用 {@code run_task} 而非 {@code task}，与 {@code ydsz_flow_his_task}（已完成归档）
 * 区分 —— 本表只承载「正在运行中」的待办实例。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_flow_run_task")
public class FlowRunTaskDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 流程实例 ID */
    private String instanceId;

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

    /** 委托人 ID */
    private String assignorId;

    /** 委托人姓名 */
    private String assignorName;

    /** 办理人类型（FlowAssigneeType.name） */
    private String assigneeType;

    /** 办理人 ID（按 type 解析） */
    private String assigneeId;

    /** 办理人姓名 */
    private String assigneeName;

    /** 办理人权限标识 */
    private String permissionFlag;

    /** 会签类型（FlowPerformType.name） */
    private String performType;

    /** 会签所需通过人数 */
    private Integer approveCount;

    /** 会签当前已通过人数 */
    private Integer approveFinished;

    /** P1-5: VOTE 模式通过率阈值（0~1，默认 0.5 表示过半数） */
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

    /** 截止时间 */
    private LocalDateTime dueAt;

    /** P1-1: 任务优先级（1-100，默认 50）；待办默认按 priority DESC, created_at ASC 排序 */
    private Integer priority;

    /** P1-6: 已发送的 SLA 催办次数 */
    private Integer reminderCount;

    /** P1-6: 最近一次催办时间 */
    private LocalDateTime lastRemindedAt;

    /** P1-6: 最终触发的 SLA 动作（REMIND/ESCALATE/AUTO_PASS/AUTO_REJECT） */
    private String slaAction;

    /** P1-6: 是否已升级（0 否 / 1 是，避免重复升级） */
    private Integer slaEscalated;

    /** 乐观锁版本号由 BaseDO 继承，无需在此声明 */

    /**
     * GAP-P2-10: FOREACH 当前迭代元素值
     *
     * <p>循环节点为集合中每个元素创建独立 task，该字段存储当前 task 对应的元素值
     * （如 userId、deptId 等），用于区分不同迭代实例。
     * 非 FOREACH 节点的 task 该字段为 null。
     */
    private String iterVar;

    /** 租户 ID */
    private String tenantId;

    /** 链路追踪 ID */
    private String providerTraceId;
}
