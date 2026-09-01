package com.njydsz.agent.domain.vo;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * Agent 执行链路视图对象。
 *
 * <p>用于返回 Agent 执行链路的展示数据。
 *
 * <p><b>线程安全</b>：由 @Data 生成 setter，属可变视图载体；在单次响应序列化前于单线程内填充，勿跨线程共享。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class AgentTraceVO implements Serializable {

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
