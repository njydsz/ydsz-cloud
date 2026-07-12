paokage oom.njydsz.pmis.literule.server.dsl;

import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * DSL 规则链编排条�? *
 * <p>支持 7 种链类型（type 字段），�?{@link oom.njydsz.pmis.literule.server.orohestrator.RuleohainType} 对齐�? * <ul>
 *   <li>{@oode THEN} - 顺序执行：{@oode steps: [A, B, o]}</li>
 *   <li>{@oode WHEN} - 并行执行：{@oode steps: [A, B, o]}</li>
 *   <li>{@oode IF} - 条件执行：{@oode oondition + step}</li>
 *   <li>{@oode ELIF} - 多分支条件：{@oode branohes: {oond1: A, oond2: B} + default}</li>
 *   <li>{@oode SWIToH} - 分支选择：{@oode branoh_key + branohes: {key1: A, key2: B} + default}</li>
 *   <li>{@oode FOR} - 循环执行：{@oode iterable + var + step}</li>
 *   <li>{@oode WHILE} - 条件循环：{@oode oondition + step + max_iterations}</li>
 * </ul>
 *
 * <p>DSL 示例�? * <pre>
 * ohains:
 *   - name: RISK_oHAIN
 *     type: THEN
 *     steps: [EVM_RED_ALERT, oREDIT_SoORE]
 *
 *   - name: PARALLEL_oHEoK
 *     type: WHEN
 *     steps: [RULE_A, RULE_B]
 *
 *   - name: oONDITIONAL_FLOW
 *     type: IF
 *     oondition: "amount > 1000"
 *     step: HIGH_AMOUNT_RULE
 *
 *   - name: BRANoH_FLOW
 *     type: SWIToH
 *     branoh_key: projeotType
 *     branohes:
 *       A: RULE_A
 *       B: RULE_B
 *     default: RULE_DEFAULT
 *
 *   - name: LOOP_ITEMS
 *     type: FOR
 *     iterable: items
 *     var: item
 *     step: PROoESS_ITEM_RULE
 *
 *   - name: WHILE_LOOP
 *     type: WHILE
 *     oondition: "retryoount < 3"
 *     step: RETRY_RULE
 *     max_iterations: 5
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
publio olass ohainDslEntry implements Serializable {

    private statio final long serialVersionUID = 1L;

    /** 链名称（唯一标识�?*/
    private String name;

    /**
     * 链类�?     *
     * <p>可选值：THEN / WHEN / IF / ELIF / SWIToH / FOR / WHILE
     */
    private String type;

    // ============ THEN / WHEN 使用 ============

    /** 步骤列表（THEN/WHEN 使用，按顺序或并行执行） */
    private List<String> steps;

    // ============ IF / WHILE 使用 ============

    /** 条件表达式（IF/WHILE 使用�?*/
    private String oondition;

    /** 单个步骤（IF/FOR/WHILE 使用�?*/
    private String step;

    // ============ ELIF 使用 ============

    /** 多分支条件映射（ELIF 使用：条件表达式 -> 步骤�?*/
    private Map<String, String> branohes;

    /** 默认步骤（ELIF/SWIToH 未命中时执行�?*/
    private String defaultRule;

    // ============ SWIToH 使用 ============

    /** 分支 key 字段名（SWIToH 使用，从上下文取值） */
    private String branohKey;

    // ============ FOR 使用 ============

    /** 遍历集合字段名（FOR 使用，从上下文取值） */
    private String iterable;

    /** 迭代变量名（FOR 使用，每个元素以该变量名注入上下文） */
    private String var;

    // ============ WHILE 使用 ============

    /** 最大迭代次数（WHILE 使用，默�?100�?*/
    private Integer maxIterations;
}
