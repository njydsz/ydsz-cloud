package com.njydsz.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 执行链路步骤 DO（映射 ydsz_agent_trace_step 表）
 *
 * <p>记录 Agent 执行过程中的单个步骤，如 LLM 调用、工具执行、意图路由等。 输入/输出以 JSON 字符串存储，支持回放与调试。
 *
 * <p><b>线程安全</b>：持久化实体，可变；仅在单请求/单事务内使用，勿跨线程共享。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ydsz_agent_trace_step")
public class AgentTraceStepDO {

  /** 链路 ID（关联 ydsz_agent_trace.traceId） */
  private String traceId;

  /** 步骤序号（从 0 开始递增） */
  private Integer stepIndex;

  /** 步骤类型（LLM_CALL/TOOL_CALL/THOUGHT/OBSERVATION/ROUTE/LLM_CALL_ERROR） */
  private String stepType;

  /** 步骤内容描述 */
  private String content;

  /** 步骤输入（JSON 字符串） */
  private String inputJson;

  /** 步骤输出（JSON 字符串） */
  private String outputJson;

  /** 耗时（毫秒） */
  private Long durationMs;
}
