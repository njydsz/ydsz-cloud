package com.njydsz.agent.domain.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.agent.entity.base.DomainBaseEntity;

/**
 * Agent 执行链路（domain 纯净 POJO，无 MP 注解）
 *
 * <p>记录一次 Agent 执行的完整元数据，包括所属对话、Agent 类型、执行状态与总耗时。
 * 步骤明细存储在 {@code ydsz_agt_trace_step} 表中，通过 traceId 关联。
 *
 * <p><b>DDD 分层</b>：domain 层不携带 MyBatis-Plus 注解；
 * 持久化映射由 {@code infra.entity.AgentTracePO} 承担。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AgentTrace extends DomainBaseEntity<String> {

  private static final long serialVersionUID = 1L;

  /** 所属对话 ID */
  private String conversationId;

  /** Agent 类型标识（CHAT/REACT/RAG/PLAN_EXECUTE/SUPERVISOR） */
  private String agentId;

  /** 总耗时（毫秒） */
  private Long totalDurationMs;
}
