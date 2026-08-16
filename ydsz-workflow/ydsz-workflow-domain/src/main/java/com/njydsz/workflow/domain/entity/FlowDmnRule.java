package com.njydsz.workflow.domain.entity;

import java.io.Serial;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * P0-1: DMN 决策规则行实体
 *
 * <p>对应数据库表 {@code ydsz_flow_dmn_rule}，每条规则对应决策表中的一行，
 * 包含输入条件单元格和输出值单元格。输入条件为逗号分隔的比较表达式列表（与 {@link FlowDmnDecision#getInputDefinitions} 一一对应），
 * 输出值为逗号分隔的值列表（与 {@link FlowDmnDecision#getOutputDefinitions} 一一对应）。
 *
 * <p><b>输入条件格式示例：</b>
 * <pre>
 *   ["&gt;=10000", "&lt;50000"]   — 金额 &gt;= 10000 且 &lt; 50000
 *   ["-", "engineering"]        — 第一个输入任意，第二个等于 {@code engineering}
 *   ["&gt;=50000", "-"]          — 金额 &gt;= 50000，第二个输入任意
 * </pre>
 * 「{@code -}」表示该列不做限制（通配）。
 *
 * <p><b>输出值格式示例：</b>
 * <pre>
 *   ["LEVEL_3", "user:1001"]   — 审批层级 {@code LEVEL_3}，审批人 {@code user:1001}
 * </pre>
 *
 * <p><b>匹配顺序：</b>按 {@code ruleOrder} 升序依次匹配，结合 {@link FlowDmnDecision#getHitPolicy} 决定：
 * <ul>
 *   <li>{@code FIRST}：取第一条命中的规则</li>
 *   <li>{@code UNIQUE}：仅一条命中（命中数量校验）</li>
 *   <li>{@code ANY}：多条命中时输出必须一致</li>
 *   <li>{@code COLLECT}：收集所有命中的输出</li>
 * </ul>
 *
 * <p><b>索引设计：</b>
 * <ul>
 *   <li>普通索引 {@code idx_decision_order}（{@code decision_id}, {@code rule_order}）：按决策表 + 顺序查询</li>
 *   <li>普通索引 {@code idx_enabled}（{@code enabled}）：按启用状态筛选</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see FlowDmnDecision 决策表
 * @see com.njydsz.workflow.server.engine.DmnDecisionEngine DMN 决策引擎
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_flow_dmn_rule")
public class FlowDmnRule extends MpBaseEntity<String> {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 所属决策表 ID */
    private String decisionId;

    /** 规则序号（从 {@code 1} 开始，决定匹配顺序） */
    private Integer ruleOrder;

    /**
     * 输入条件 JSON，与决策表 {@code inputDefinitions} 一一对应。
     *
     * <p>格式：{@code [">=10000", "<50000"]}，「{@code -}」表示该列不做限制（通配）。
     */
    private String inputEntries;

    /**
     * 输出值 JSON，与决策表 {@code outputDefinitions} 一一对应。
     *
     * <p>格式：{@code ["LEVEL_3", "user:1001"]}
     */
    private String outputEntries;

    /** 规则备注（可空） */
    private String remark;

    /** 是否启用（{@code 0}=禁用 / {@code 1}=启用） */
    private Integer enabled;

    /** 链路追踪 ID */
    private String providerTraceId;
}
