package com.njydsz.workflow.domain.entity;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.domain.entity.BaseDO;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程审计日志 DO
 *
 * <p>记录流程全生命周期的操作轨迹：谁在何时对哪个实例/任务做了什么操作。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_flow_audit_log")
public class FlowAuditLogDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 流程实例 ID */
    private String instanceId;
    /** 任务 ID（可为空） */
    private String taskId;
    /** 流程编码 */
    private String flowCode;
    /** 业务类型 */
    private String businessType;
    /** 业务单据 ID */
    private String businessId;
    /** 节点编码 */
    private String nodeCode;
    /** 节点名称 */
    private String nodeName;
    /** 操作类型：START/PASS/REJECT/TRANSFER/DELEGATE/COUNTERSIGN/RECALL/URGE/TERMINATE/SUSPEND/ACTIVATE/CLAIM */
    private String action;
    /** 操作人 ID */
    private String operatorId;
    /** 操作人姓名 */
    private String operatorName;
    /** 目标人 ID（转办/委派/加签） */
    private String targetId;
    /** 目标人姓名 */
    private String targetName;
    /** 审批意见 */
    private String comment;
    /** P2-42: 审批意见分类：AGREE/DISAGREE/SUGGEST/INQUIRE */
    private String commentType;
    /** 操作时间 */
    private LocalDateTime operatedAt;
    /** 租户 ID */
    private String tenantId;
    /** 链路追踪 ID */
    private String providerTraceId;
}
