package com.njydsz.agent.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * Agent 执行链路步骤 DTO。
 *
 * <p>用于创建链路步骤记录。
 *
 * <p><b>线程安全</b>：由 @Data 生成 setter，属可变入参载体；仅在单次请求绑定期间使用，框架按请求单线程处理，勿跨线程共享。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class AgentTraceStepDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

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

  /** Token 成本（USD，精确到 6 位小数；非 LLM 调用步骤为 0） */
  private Double cost;
}
