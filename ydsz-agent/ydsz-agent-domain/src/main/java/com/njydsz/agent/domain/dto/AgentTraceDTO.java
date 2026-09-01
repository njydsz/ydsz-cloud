package com.njydsz.agent.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * Agent 执行链路 DTO。
 *
 * <p>用于创建 Agent 执行链路记录。
 *
 * <p><b>线程安全</b>：由 @Data 生成 setter，属可变入参载体；仅在单次请求绑定期间使用，框架按请求单线程处理，勿跨线程共享。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class AgentTraceDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 链路唯一 ID */
  private String traceId;

  /** 所属对话 ID */
  private String conversationId;

  /** Agent 类型标识 */
  private String agentId;

  /** 执行状态（RUNNING/SUCCESS/FAILED/MAX_ITERATIONS/GUARDRAIL_REJECTED） */
  private String status;

  /** 总耗时（毫秒） */
  private Long totalDurationMs;
}
