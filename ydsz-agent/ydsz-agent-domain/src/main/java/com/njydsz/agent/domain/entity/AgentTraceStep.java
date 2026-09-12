package com.njydsz.agent.domain.entity;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * Agent 执行链路步骤（映射 ydsz_agt_trace_step 表）
 *
 * <p>记录 Agent 执行过程中的单个步骤，如 LLM 调用、工具执行、意图路由等。 输入/输出以 JSON 字符串存储，支持回放与调试。
 *
 * <p><b>线程安全</b>：持久化实体，可变；仅在单请求/单事务内使用，勿跨线程共享。
 *
 * <p><b>Schema 说明</b>：{@code ydsz_agt_trace_step} 表使用 {@code (traceId, stepIndex)} 作为复合业务键， 无独立 {@code id} 列。
 * 本类继承的 {@code id} 字段（来自 {@link MpBaseEntity}）标注为 {@code exist=false}，不映射到数据库列， 仅作为
 * MyBatis-Plus 运行时占位需要。业务主键通过 {@link #traceId} + {@link #stepIndex} 体现。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_agt_trace_step")
public class AgentTraceStep extends MpBaseEntity<Long> {

  /** 主键占位（基类继承，无 DB 列映射；schema 用 traceId+stepIndex 复合业务键）。 */
  @TableField(exist = false)
  private Long id;

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
