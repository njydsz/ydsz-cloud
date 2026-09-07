package com.njydsz.agent.domain.text2sql;

/**
 * Text2SQL 状态图节点枚举。
 *
 * <p>定义 NL2SQL 增强链路的各阶段，编排顺序为：
 *
 * <ol>
 *   <li>{@link #SCHEMA_RECALL} — 智能召回相关表 Schema
 *   <li>{@link #FEASIBILITY_ASSESSMENT} — LLM 评估问题在给定 Schema 下是否可回答
 *   <li>{@link #SQL_GENERATION} — LLM 生成 SQL
 *   <li>{@link #SEMANTIC_CONSISTENCY} — LLM 校验 SQL 与用户意图的语义一致性
 *   <li>{@link #EXECUTION} — 安全校验 + 执行 SQL
 * </ol>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public enum Text2SqlStateNode {

  /** Schema 智能召回节点 */
  SCHEMA_RECALL,

  /** 可行性评估节点 */
  FEASIBILITY_ASSESSMENT,

  /** SQL 生成节点 */
  SQL_GENERATION,

  /** 语义一致性校验节点 */
  SEMANTIC_CONSISTENCY,

  /** SQL 执行节点 */
  EXECUTION
}
