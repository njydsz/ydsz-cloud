package com.njydsz.pmis.literule.server.dsl;

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
 * <p>支持 7 种链类型（type 字段），与 {@link com.njydsz.pmis.literule.server.orchestrator.RuleChainType} 对齐：
 * <ul>
 *   <li>{@code THEN} - 顺序执行：{@code steps: [A, B, C]}</li>
 *   <li>{@code WHEN} - 并行执行：{@code steps: [A, B, C]}</li>
 *   <li>{@code IF} - 条件执行：{@code condition + step}</li>
 *   <li>{@code ELIF} - 多分支条件：{@code branches: {cond1: A, cond2: B} + default}</li>
 *   <li>{@code SWITCH} - 分支选择：{@code branch_key + branches: {key1: A, key2: B} + default}</li>
 *   <li>{@code FOR} - 循环执行：{@code iterable + var + step}</li>
 *   <li>{@code WHILE} - 条件循环：{@code condition + step + max_iterations}</li>
 * </ul>
 *
 * <p>DSL 示例：
 * <pre>
 * chains:
 *   - name: RISK_CHAIN
 *     type: THEN
 *     steps: [EVM_RED_ALERT, CREDIT_SCORE]
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
 *
 *   - name: LOOP_ITEMS
 *     type: FOR
 *     iterable: items
 *     var: item
 *     step: PROCESS_ITEM_RULE
 *
 *   - name: WHILE_LOOP
 *     type: WHILE
 *     condition: "retryCount < 3"
 *     step: RETRY_RULE
 *     max_iterations: 5
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
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
     * <p>可选值：THEN / WHEN / IF / ELIF / SWITCH / FOR / WHILE
     */
    private String type;

    // ============ THEN / WHEN 使用 ============

    /** 步骤列表（THEN/WHEN 使用，按顺序或并行执行） */
    private List<String> steps;

    // ============ IF / WHILE 使用 ============

    /** 条件表达式（IF/WHILE 使用） */
    private String condition;

    /** 单个步骤（IF/FOR/WHILE 使用） */
    private String step;

    // ============ ELIF 使用 ============

    /** 多分支条件映射（ELIF 使用：条件表达式 -> 步骤） */
    private Map<String, String> branches;

    /** 默认步骤（ELIF/SWITCH 未命中时执行） */
    private String defaultRule;

    // ============ SWITCH 使用 ============

    /** 分支 key 字段名（SWITCH 使用，从上下文取值） */
    private String branchKey;

    // ============ FOR 使用 ============

    /** 遍历集合字段名（FOR 使用，从上下文取值） */
    private String iterable;

    /** 迭代变量名（FOR 使用，每个元素以该变量名注入上下文） */
    private String var;

    // ============ WHILE 使用 ============

    /** 最大迭代次数（WHILE 使用，默认 100） */
    private Integer maxIterations;
}
