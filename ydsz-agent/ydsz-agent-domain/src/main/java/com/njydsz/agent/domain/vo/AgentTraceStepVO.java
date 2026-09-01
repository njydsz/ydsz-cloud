package com.njydsz.agent.domain.vo;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * Agent 执行链路步骤视图对象。
 *
 * <p>用于返回 Agent 执行链路步骤的展示数据。
 *
 * <p><b>线程安全</b>：由 @Data 生成 setter，属可变视图载体；在单次响应序列化前于单线程内填充，勿跨线程共享。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class AgentTraceStepVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 链路 ID（关联 ydsz_agt_trace.traceId） */
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
