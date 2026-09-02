package com.njydsz.literule.server.dsl;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DSL 规则链编排条目
 *
 * <p>支持 5 种链类型（type 字段），与 {@link com.njydsz.literule.server.orchestrator.RuleChainType} 对齐：
 *
 * <ul>
 *   <li>{@code THEN} - 顺序执行：{@code steps: [A, B, C]}
 *   <li>{@code WHEN} - 并行执行：{@code steps: [A, B, C]}
 *   <li>{@code IF} - 条件执行：{@code condition + step}
 *   <li>{@code ELIF} - 多分支条件：{@code branches: {cond1: A, cond2: B} + default}
 *   <li>{@code SWITCH} - 分支选择：{@code branch_key + branches: {key1: A, key2: B} + default}
 * </ul>
 *
 * <p>DSL 示例：
 *
 * <pre>
 * chains:
 *   - name: RISK_CHAIN
 *     type: THEN
 *     steps: [EVM_RED_ALERT, SCORECARD_DEMO]
 *
 *   - name: PARALLEL_CHECK
 *     type: WHEN
 *     steps: [RULE_A, RULE_B]
 *
 *   - name: CONDITIONAL_FLOW
 *     type: IF
 *     condition: "amount > 1000"
 *     step: HIGH_AMOUNT_RULE
 *
 *   - name: BRANCH_FLOW
 *     type: SWITCH
 *     branch_key: projectType
 *     branches:
 *       A: RULE_A
 *       B: RULE_B
 *     default: RULE_DEFAULT
 * </pre>
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChainDslEntry implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 链名称（唯一标识） */
  private String name;

  /**
   * 链类型
   *
   * <p>可选值：THEN / WHEN / IF / ELIF / SWITCH
   */
  private String type;

  // ============ THEN / WHEN 使用 ============

  /** 步骤列表（THEN/WHEN 使用，按顺序或并行执行） */
  private List<String> steps;

  // ============ IF 使用 ============

  /** 条件表达式（IF 使用） */
  private String condition;

  /** 单个步骤（IF 使用） */
  private String step;

  // ============ ELIF 使用 ============

  /** 多分支条件映射（ELIF 使用：条件表达式 -> 步骤） */
  private Map<String, String> branches;

  /** 默认步骤（ELIF/SWITCH 未命中时执行） */
  private String defaultRule;

  // ============ SWITCH 使用 ============

  /** 分支 key 字段名（SWITCH 使用，从上下文取值） */
  private String branchKey;

}
