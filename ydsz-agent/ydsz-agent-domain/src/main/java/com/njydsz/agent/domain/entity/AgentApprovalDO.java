package com.njydsz.agent.domain.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 人工审批请求 DO（映射 ydsz_agent_approval 表）
 *
 * <p>持久化 Human-in-the-Loop 审批请求，支持多实例共享、重启不丢与长期审计。
 *
 * <p><b>DDL：</b>
 *
 * <pre>
 * CREATE TABLE ydsz_agent_approval (
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
 * CREATE INDEX idx_approval_status ON ydsz_agent_approval(status);
 * CREATE INDEX idx_approval_tenant ON ydsz_agent_approval(tenant_id);
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ydsz_agent_approval")
public class AgentApprovalDO {

  /** 审批请求 ID（主键，业务生成非自增） */
  private String id;

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

  /** 租户 ID */
  private String tenantId;

  /** 请求创建时间 */
  private LocalDateTime createdAt;

  /** 审批完成时间 */
  private LocalDateTime resolvedAt;
}
