package com.njydsz.agent.domain.entity;

import java.time.LocalDateTime;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.agent.entity.base.DomainBaseEntity;

/**
 * Agent 人工审批请求（domain 纯净 POJO，无 MP 注解）
 *
 * <p>持久化 Human-in-the-Loop 审批请求，支持多实例共享、重启不丢与长期审计。
 *
 * <p><b>DDD 分层</b>：domain 层不携带 MyBatis-Plus 注解（@TableName/@TableId/@TableField）；
 * 持久化映射由 {@code infra.entity.AgentApprovalPO} 承担。
 *
 * <p><b>DDL：</b>
 *
 * <pre>
 * CREATE TABLE ydsz_agt_approval (
 *   id                VARCHAR(64) PRIMARY KEY,
 *   conversation_id   VARCHAR(64),
 *   trace_id          VARCHAR(64),
 *   step_description  VARCHAR(512),
 *   context_json      TEXT,
 *   status            VARCHAR(32) DEFAULT 'PENDING',
 *   approver          VARCHAR(64),
 *   comment           VARCHAR(512),
 *   tenant_id         VARCHAR(64),
 *   created_at        TIMESTAMPTZ DEFAULT NOW(),
 *   resolved_at       TIMESTAMPTZ
 * );
 * CREATE INDEX idx_approval_status ON ydsz_agt_approval(status);
 * CREATE INDEX idx_approval_tenant ON ydsz_agt_approval(tenant_id);
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AgentApproval extends DomainBaseEntity<String> {

  private static final long serialVersionUID = 1L;

  /** 所属对话 ID */
  private String conversationId;

  /** 执行链路 ID */
  private String traceId;

  /** 待审批步骤的业务描述 */
  private String stepDescription;

  /** 审批上下文（JSON 字符串，含用户输入、已有结果等） */
  private String contextJson;

  /** 审批状态（PENDING/APPROVED/REJECTED/EXPIRED） */
  private String status;

  /** 审批人标识 */
  private String approver;

  /** 审批意见 */
  private String comment;

  /** 审批完成时间 */
  private LocalDateTime resolvedAt;
}
