package com.njydsz.agent.infra.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseAuditEntity;

/**
 * Agent 审批请求持久化对象（映射 ydsz_agt_approval 表）。
 *
 * <p>基础设施层 PO，包含 MyBatis-Plus 持久化注解。
 * 对应的领域模型 {@code domain.entity.AgentApproval} 为纯净 POJO（不含 MP 注解）。
 *
 * <p><b>DDD 分层</code>：PO 仅存在于 infra 层，domain 层通过 MapStruct Converter 实现 Domain ↔ PO 转换。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_agt_approval")
public class AgentApprovalPO extends MpBaseAuditEntity<String> {

  /** 审批请求 ID（主键，业务生成非自增）。 */
  @TableId(type = IdType.INPUT)
  private String id;

  /** 所属对话 ID */
  private String conversationId;

  /** 执行链路 ID */
  private String traceId;

  /** 待审批步骤的业务描述 */
  private String stepDescription;

  /** 审批上下文（JSON 字符串） */
  private String contextJson;

  /** 审批状态（PENDING/APPROVED/REJECTED/EXPIRED） */
  private String status;

  /** 审批人标识 */
  private String approver;

  /** 审批意见 */
  private String comment;

  /** 租户 ID */
  private String tenantId;

  /** 审批完成时间 */
  private LocalDateTime resolvedAt;
}
