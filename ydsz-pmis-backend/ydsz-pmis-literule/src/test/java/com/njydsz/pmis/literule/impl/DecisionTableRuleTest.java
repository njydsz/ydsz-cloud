package com.njydsz.pmis.literule.impl;

import com.njydsz.pmis.literule.api.DecisionTableDefinition;
import com.njydsz.pmis.literule.api.HitPolicy;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 决策表规则单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
class DecisionTableRuleTest {

    private DecisionTableDefinition buildRiskTable(HitPolicy policy) {
        DecisionTableDefinition.Column evmCol = DecisionTableDefinition.Column.builder()
                .name("evmRedCount").label("EVM 红灯数").type("number").build();
        DecisionTableDefinition.Column marginCol = DecisionTableDefinition.Column.builder()
                .name("grossMargin").label("毛利率").type("number").build();
        DecisionTableDefinition.Column sevCol = DecisionTableDefinition.Column.builder()
                .name("severity").label("严重度").type("string").build();
        DecisionTableDefinition.Column titleCol = DecisionTableDefinition.Column.builder()
                .name("title").label("标题").type("string").build();

        DecisionTableDefinition.Row row1 = DecisionTableDefinition.Row.builder()
                .conditions(Map.of("evmRedCount", ">=3"))
                .actions(Map.of("severity", "RED", "title", "EVM 严重偏离"))
                .priority(10)
                .build();
        DecisionTableDefinition.Row row2 = DecisionTableDefinition.Row.builder()
                .conditions(Map.of("grossMargin", "<0.05"))
                .actions(Map.of("severity", "YELLOW", "title", "毛利率过低"))
                .priority(20)
                .build();
        DecisionTableDefinition.Row row3 = DecisionTableDefinition.Row.builder()
                .conditions(Map.of("grossMargin", "[0.05,0.15)"))
                .actions(Map.of("severity", "YELLOW", "title", "毛利率预警"))
                .priority(30)
                .build();

        return DecisionTableDefinition.builder()
                .tableCode("DT_TEST")
                .tableName("测试决策表")
                .category("TEST")
                .hitPolicy(policy == null ? HitPolicy.FIRST : policy)
                .conditionColumns(List.of(evmCol, marginCol))
                .actionColumns(List.of(sevCol, titleCol))
                .rows(List.of(row1, row2, row3))
                .defaultActions(Map.of("severity", "INFO", "title", "正常"))
                .build();
    }

    @Test
    void shouldMatchFirstHitAndReturnRed() {
        DecisionTableRule rule = new DecisionTableRule(buildRiskTable(HitPolicy.FIRST), null);
        Map<String, Object> facts = new HashMap<>();
        facts.put("evmRedCount", 5);
        facts.put("grossMargin", 0.10);
        RuleResult result = rule.evaluate(RuleContext.of(facts));

        assertTrue(result.isTriggered());
        assertEquals(RuleSeverity.RED, result.getSeverity());
        assertEquals("EVM 严重偏离", result.getTitle());
    }

    @Test
    void shouldMatchComparisonExpression() {
        DecisionTableRule rule = new DecisionTableRule(buildRiskTable(HitPolicy.FIRST), null);
        Map<String, Object> facts = new HashMap<>();
        facts.put("evmRedCount", 1);
        facts.put("grossMargin", 0.03);
        RuleResult result = rule.evaluate(RuleContext.of(facts));

        assertTrue(result.isTriggered());
        assertEquals(RuleSeverity.YELLOW, result.getSeverity());
        assertEquals("毛利率过低", result.getTitle());
    }

    @Test
    void shouldMatchInterval() {
        DecisionTableRule rule = new DecisionTableRule(buildRiskTable(HitPolicy.FIRST), null);
        Map<String, Object> facts = new HashMap<>();
        facts.put("evmRedCount", 0);
        facts.put("grossMargin", 0.08);
        RuleResult result = rule.evaluate(RuleContext.of(facts));

        assertTrue(result.isTriggered());
        assertEquals("毛利率预警", result.getTitle());
    }

    @Test
    void shouldUseDefaultActionsWhenNoMatch() {
        DecisionTableRule rule = new DecisionTableRule(buildRiskTable(HitPolicy.FIRST), null);
        Map<String, Object> facts = new HashMap<>();
        facts.put("evmRedCount", 0);
        facts.put("grossMargin", 0.20);
        RuleResult result = rule.evaluate(RuleContext.of(facts));

        assertTrue(result.isTriggered());
        assertEquals(RuleSeverity.INFO, result.getSeverity());
        assertEquals("正常", result.getTitle());
    }

    @Test
    void shouldReturnNotTriggeredWhenNoMatchAndNoDefault() {
        DecisionTableDefinition def = buildRiskTable(HitPolicy.FIRST);
        def.setDefaultActions(null);
        DecisionTableRule rule = new DecisionTableRule(def, null);

        Map<String, Object> facts = new HashMap<>();
        facts.put("evmRedCount", 0);
        facts.put("grossMargin", 0.20);
        RuleResult result = rule.evaluate(RuleContext.of(facts));

        assertFalse(result.isTriggered());
    }

    @Test
    void shouldFailOnUniqueMultipleMatches() {
        DecisionTableRule rule = new DecisionTableRule(buildRiskTable(HitPolicy.UNIQUE), null);
        Map<String, Object> facts = new HashMap<>();
        facts.put("evmRedCount", 5);
        facts.put("grossMargin", 0.03);
        RuleResult result = rule.evaluate(RuleContext.of(facts));

        assertFalse(result.isTriggered());
        assertNotNull(result.getDescription());
        assertTrue(result.getDescription().contains("UNIQUE"));
    }

    @Test
    void shouldReturnHighestPriorityOnPriorityPolicy() {
        DecisionTableRule rule = new DecisionTableRule(buildRiskTable(HitPolicy.PRIORITY), null);
        Map<String, Object> facts = new HashMap<>();
        facts.put("evmRedCount", 5);
        facts.put("grossMargin", 0.03);
        RuleResult result = rule.evaluate(RuleContext.of(facts));

        assertTrue(result.isTriggered());
        assertEquals(RuleSeverity.RED, result.getSeverity());
    }

    @Test
    void shouldReturnFirstMatchedOnCollectPolicy() {
        DecisionTableRule rule = new DecisionTableRule(buildRiskTable(HitPolicy.COLLECT), null);
        Map<String, Object> facts = new HashMap<>();
        facts.put("evmRedCount", 5);
        facts.put("grossMargin", 0.03);
        RuleResult result = rule.evaluate(RuleContext.of(facts));

        assertTrue(result.isTriggered());
        assertEquals(RuleSeverity.RED, result.getSeverity());
    }

    @Test
    void shouldMatchEnumCondition() {
        DecisionTableDefinition.Column col = DecisionTableDefinition.Column.builder()
                .name("level").label("等级").type("string").build();
        DecisionTableDefinition.Row row = DecisionTableDefinition.Row.builder()
                .conditions(Map.of("level", "RED|YELLOW"))
                .actions(Map.of("severity", "RED", "title", "命中风险等级"))
                .build();
        DecisionTableDefinition def = DecisionTableDefinition.builder()
                .tableCode("DT_ENUM")
                .tableName("枚举决策表")
                .category("TEST")
                .hitPolicy(HitPolicy.FIRST)
                .conditionColumns(List.of(col))
                .actionColumns(List.of())
                .rows(List.of(row))
                .build();

        DecisionTableRule rule = new DecisionTableRule(def, null);

        // 命中 RED
        Map<String, Object> facts1 = new HashMap<>();
        facts1.put("level", "RED");
        assertTrue(rule.evaluate(RuleContext.of(facts1)).isTriggered());

        // 命中 YELLOW
        Map<String, Object> facts2 = new HashMap<>();
        facts2.put("level", "YELLOW");
        assertTrue(rule.evaluate(RuleContext.of(facts2)).isTriggered());

        // 未命中
        Map<String, Object> facts3 = new HashMap<>();
        facts3.put("level", "GREEN");
        assertFalse(rule.evaluate(RuleContext.of(facts3)).isTriggered());
    }
}
