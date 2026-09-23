package com.njydsz.agent.infra.entity;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Agent 执行链路步骤持久化对象（映射 ydsz_agt_trace_step 表）。
 *
 * <p>该表使用 (traceId, stepIndex) 作为复合业务键，无独立 id 列。
 * 不使用 BaseMapper（避免主键映射冲突），改为纯 MyBatis XML Mapper 方式。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Data
@SuperBuilder
@NoArgsConstructor
@TableName("ydsz_agt_trace_step")
public class AgentTraceStepPO {

  private static final long serialVersionUID = 1L;

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
  private BigDecimal cost;
}
