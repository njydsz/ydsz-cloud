package com.njydsz.agent.domain.entity;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Agent 执行链路步骤（domain 层持久化实体，YDIZ-DDD-007 单包模式）
 *
 * <p>记录 Agent 执行过程中的单个步骤，如 LLM 调用、工具执行、意图路由等。
 * 输入/输出以 JSON 字符串存储，支持回放与调试。
 *
 * <p><b>YDIZ-DDD-007</b>：domain Entity 直接携带 MyBatis-Plus ORM 注解，
 * infra 层通过依赖 domain 模块引用本类，禁止自建 PO/DO 副本。
 *
 * <p><b>表特征</b>：该表使用 (trace_id, step_index) 复合业务键，不使用 BaseMapper，
 * 通过 Repository 手写 SQL 精确控制查询列，故对象中继承的 id/revision/tenantId 等字段不参与持久化。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_agt_trace_step")
public class AgentTraceStep extends MpBaseEntity<String> {

  private static final long serialVersionUID = 1L;

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
