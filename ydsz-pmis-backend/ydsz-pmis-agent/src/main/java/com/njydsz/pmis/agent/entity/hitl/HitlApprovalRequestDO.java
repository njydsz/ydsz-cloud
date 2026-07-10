package com.njydsz.pmis.agent.entity.hitl;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * HITL 人工审批请求实体（P3-4 落地）
 *
 * <p>当 ReAct 推理循环遇到标记 {@code requiresApproval()=true} 的工具时，
 * 创建一条审批请求并暂停循环。人工审批后通过 {@link #snapshotJson} 恢复执行。
 *
 * <p>状态流转由 {@link com.njydsz.pmis.agent.enums.HitlApprovalStatus} 管理：
 * PENDING → APPROVED / REJECTED / TIMEOUT / CANCELLED
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-4)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_agent_hitl_approval")
public class HitlApprovalRequestDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** 链路追踪 ID（与 AgentContext.traceId 对齐） */
    private String traceId;

    /** Agent 类型（如 RISK_WARNING / FLOW_GENERATOR） */
    private String agentType;

    /** 关联业务类型 */
    private String bizType;

    /** 关联业务 ID */
    private String bizId;

    /** 关联业务名称/编码（冗余，便于审批人识别） */
    private String bizRef;

    /** 需要审批的工具名称 */
    private String toolName;

    /** 工具参数 JSON（审批人查看将要执行的操作） */
    private String parametersJson;

    /** 审批描述（人工可读的工具执行意图说明） */
    private String description;

    /** 审批状态：PENDING/APPROVED/REJECTED/TIMEOUT/CANCELLED */
    private String status;

    /** ReAct 循环快照 JSON（用于恢复执行） */
    private String snapshotJson;

    /** 请求人 ID（触发 Agent 的用户） */
    private String requesterId;

    /** 请求人姓名 */
    private String requesterName;

    /** 审批人 ID */
    private String approverId;

    /** 审批人姓名 */
    private String approverName;

    /** 审批意见（批准/拒绝理由） */
    private String approverComment;

    /** 审批超时时间（超过此时间自动标记为 TIMEOUT） */
    private LocalDateTime timeoutAt;

    /** 审批结果时间（批准/拒绝/超时/取消的时间） */
    private LocalDateTime resolvedAt;
}
