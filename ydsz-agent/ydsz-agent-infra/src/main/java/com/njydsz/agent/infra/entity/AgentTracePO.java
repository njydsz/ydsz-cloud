package com.njydsz.agent.infra.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * Agent 执行链路持久化对象（映射 ydsz_agt_trace 表）。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_agt_trace")
public class AgentTracePO extends MpBaseEntity<String> {

  private static final long serialVersionUID = 1L;

  /** 链路唯一 ID（主键，业务生成非自增）。 */
  @TableId(type = IdType.INPUT)
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
