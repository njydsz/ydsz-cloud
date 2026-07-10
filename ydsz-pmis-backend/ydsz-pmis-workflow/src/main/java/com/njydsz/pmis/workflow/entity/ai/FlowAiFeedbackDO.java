package com.njydsz.pmis.workflow.entity.ai;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * AI 推荐审批人反馈记录 DO
 *
 * <p>P3-3: 记录用户对 AI 推荐审批人的反馈行为，形成推荐-反馈闭环。
 * 用于统计 AI 推荐准确率（接受率/拒绝率），并为后续推荐提供历史反馈数据。
 *
 * <p>反馈动作类型：
 * <ul>
 *   <li>ACCEPTED — 用户接受了 AI 推荐的审批人</li>
 *   <li>REJECTED — 用户拒绝了 AI 推荐的审批人</li>
 *   <li>CHOSEN_OTHER — 用户选择了非推荐列表中的其他人</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_flow_ai_feedback")
public class FlowAiFeedbackDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;
    /** 推荐调用追踪 ID（关联一次 recommendApprovers 调用） */
    private String traceId;
    /** 任务 ID（可空，草稿态无任务） */
    private String taskId;
    /** 流程实例 ID */
    private String instanceId;
    /** 流程编码 */
    private String flowCode;
    /** 节点编码 */
    private String nodeCode;
    /** AI 推荐的审批人 ID */
    private String recommendedUserId;
    /** AI 推荐的审批人姓名 */
    private String recommendedUserName;
    /** 推荐得分 0.0000~1.0000 */
    private BigDecimal recommendedScore;
    /** 推荐排名（1=第一推荐） */
    private Integer recommendedRank;
    /** 反馈动作：ACCEPTED / REJECTED / CHOSEN_OTHER */
    private String action;
    /** 实际选择的审批人 ID（CHOSEN_OTHER 时有值） */
    private String actualUserId;
    /** 实际选择的审批人姓名 */
    private String actualUserName;
    /** 反馈来源：USER_EXPLICIT / SYSTEM_INFERRED */
    private String feedbackSource;
    /** 备注 */
    private String remark;
    /** 链路追踪 ID */
    private String providerTraceId;
}
