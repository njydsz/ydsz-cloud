package com.njydsz.pmis.literule.server.dsl;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * LiteRule 声明式 DSL 顶层模型
 *
 * <p>一个 DSL 文件由 {@code rules}（规则定义列表）和 {@code chains}（规则链编排列表）组成。
 * 解析自 YAML，可零代码注册到 {@link com.njydsz.pmis.literule.server.core.DefaultRuleEngine}。
 *
 * <p><b>DSL 示例（YAML）</b>：
 * <pre>
 * rules:
 *   - code: EVM_RED_ALERT
 *     name: EVM红灯告警
 *     type: expression
 *     category: EVM
 *     priority: 10
 *     severity: RED
 *     condition: "evmRedCount &gt;= 3"
 *     title: "EVM 红灯 ${evmRedCount} 个"
 *     mutex_group: EVM_ALERTS
 *
 *   - code: CREDIT_SCORE
 *     name: 客户信用评分
 *     type: scorecard
 *     base_score: 100
 *     direction: DESCENDING
 *     factors:
 *       - when: "overdueCount &gt; 3"
 *         score: -30
 *         desc: "逾期过多"
 *     grades:
 *       - label: A
 *         range: [90, 200]
 *         severity: INFO
 *
 * chains:
 *   - name: RISK_CHAIN
 *     type: THEN
 *     steps: [EVM_RED_ALERT, CREDIT_SCORE]
 *
 *   - name: CONDITIONAL_FLOW
 *     type: IF
 *     condition: "amount &gt; 1000"
 *     step: HIGH_AMOUNT_RULE
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleDsl implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 规则定义列表 */
    private List<RuleDslEntry> rules;

    /** 规则链编排列表 */
    private List<ChainDslEntry> chains;

    /** DSL 元信息（version / description / tenant） */
    private Map<String, Object> meta;
}
