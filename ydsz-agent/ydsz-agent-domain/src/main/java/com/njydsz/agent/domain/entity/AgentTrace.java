package com.njydsz.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 执行链路（映射 ydsz_agt_trace 表）
 *
 * <p>记录一次 Agent 执行的完整元数据，包括所属对话、Agent 类型、执行状态与总耗时。 步骤明细存储在 {@code ydsz_agt_trace_step} 表中，通过
 * {@code traceId} 关联。
 *
 * <p><b>线程安全</b>：持久化实体，可变；仅在单请求/单事务内使用，勿跨线程共享。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ydsz_agt_trace")
public class AgentTrace {

  /** 链路唯一 ID（主键，业务生成非自增） */
  private String traceId;

  /** 所属对话 ID */
  private String conversationId;

  /** Agent 类型标识（CHAT/REACT/RAG/PLAN_EXECUTE/SUPERVISOR） */
  private String agentId;

  /** 执行状态（RUNNING/SUCCESS/FAILED/MAX_ITERATIONS/GUARDRAIL_REJECTED） */
  private String status;

  /** 总耗时（毫秒） */
  private Long totalDurationMs;
}
