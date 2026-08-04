package com.njydsz.workflow.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * 流程任务视图对象
 *
 * <p>用于 Controller 层返回待办/已办任务数据，对应实体 {@link com.njydsz.workflow.domain.entity.FlowRunTask}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowRunTaskVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
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

    /** 节点类型 */
    private Integer nodeType;

    /** 业务类型 */
    private String businessType;

    /** 业务单据 ID */
    private String businessId;

    /** 业务单据编号 */
    private String businessNo;

    /** 流程名称 */
    private String flowName;

    /** 流程标题 */
    private String title;

    /** 分配人 ID */
    private String assignorId;

    /** 分配人名称 */
    private String assignorName;

    /** 审批人类型 */
    private String assigneeType;

    /** 审批人 ID */
    private String assigneeId;

    /** 审批人名称 */
    private String assigneeName;

    /** 权限标识 */
    private String permissionFlag;

    /** 办理方式（会签/或签） */
    private String performType;

    /** 审批总数 */
    private Integer approveCount;

    /** 已完成审批数 */
    private Integer approveFinished;

    /** 投票通过率 */
    private BigDecimal votePassRate;

    /** 任务状态 */
    private String taskStatus;

    /** 审批意见 */
    private String comment;

    /** 签收时间 */
    private LocalDateTime claimAt;

    /** 完成时间 */
    private LocalDateTime finishAt;

    /** 耗时（毫秒） */
    private Long durationMs;

    /** 期望完成时间（SLA 超期时间） */
    private LocalDateTime dueAt;

    /** 优先级 */
    private Integer priority;

    /** 催办次数 */
    private Integer reminderCount;

    /** 最后催办时间 */
    private LocalDateTime lastRemindedAt;

    /** SLA 动作 */
    private String slaAction;

    /** SLA 是否已升级 */
    private Integer slaEscalated;

    /** 迭代变量 */
    private String iterVar;

    /** 外部追踪 ID */
    private String providerTraceId;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新人 */
    private String updatedBy;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}