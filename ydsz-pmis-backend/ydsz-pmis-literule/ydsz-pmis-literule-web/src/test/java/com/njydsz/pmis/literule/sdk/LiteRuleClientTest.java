package com.njydsz.pmis.literule.server.sdk;

import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LiteRuleClient SDK 单元测试
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
@DisplayName("LiteRuleClient SDK 测试")
class LiteRuleClientTest {

    private LiteRuleClient client;

    @BeforeEach
    void setUp() {
        client = LiteRuleClient.builder()
                .tenantId("T001")
                .environment("test")
                .build();
    }

    @Test
    @DisplayName("编程式注册规则并评估触发")
    void testAddRuleAndEvaluate() {
        client.addRule(RuleDefinition.builder()
                .code("R001")
                .name("高额预警")
                .conditionExpression("amount > 10000")
                .defaultSeverity(RuleSeverity.RED)
                .build());

        List<RuleResult> results = client.evaluate(Map.of("amount", 15000));
        assertEquals(1, results.size());
        assertEquals("R001", results.get(0).getRuleCode());
        assertTrue(results.get(0).isTriggered());
    }

    @Test
    @DisplayName("条件不满足时不触发")
    void testEvaluateNotTriggered() {
        client.addRule(RuleDefinition.builder()
                .code("R002")
                .name("小额检查")
                .conditionExpression("amount < 100")
                .defaultSeverity(RuleSeverity.YELLOW)
                .build());

        List<RuleResult> results = client.evaluate(Map.of("amount", 500));
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("链式 Builder 注册规则")
    void testChainBuilder() {
        client.rule("R003")
                .name("利润率检查")
                .condition("grossMargin < 0.05 && confirmedRevenue > 0")
                .severity(RuleSeverity.YELLOW)
                .priority(10)
                .register();

        assertEquals(1, client.ruleCount());

        List<RuleResult> results = client.evaluate(Map.of(
                "grossMargin", 0.03,
                "confirmedRevenue", 100000));
        assertEquals(1, results.size());
        assertEquals("R003", results.get(0).getRuleCode());
    }

    @Test
    @DisplayName("多规则优先级排序")
    void testMultipleRulesPriority() {
        client.rule("R_LOW")
                .name("低优先级")
                .condition("amount > 100")
                .severity(RuleSeverity.INFO)
                .priority(100)
                .register();

        client.rule("R_HIGH")
                .name("高优先级")
                .condition("amount > 100")
                .severity(RuleSeverity.RED)
                .priority(1)
                .register();

        List<RuleResult> results = client.evaluate(Map.of("amount", 200));
        assertEquals(2, results.size());
        // 结果按严重度倒序排列
        assertEquals("R_HIGH", results.get(0).getRuleCode());
    }

    @Test
    @DisplayName("Dry-run 返回全部结果含未触发")
    void testDryRun() {
        client.rule("R_PASS")
                .name("触发规则")
                .condition("amount > 100")
                .severity(RuleSeverity.RED)
                .register();

        client.rule("R_FAIL")
                .name("未触发规则")
                .condition("amount < 50")
                .severity(RuleSeverity.YELLOW)
                .register();

        List<RuleResult> results = client.dryRun(Map.of("amount", 200));
        assertEquals(2, results.size());
    }

    @Test
    @DisplayName("topResult 返回最高严重度")
    void testTopResult() {
        client.rule("R_INFO")
                .name("信息")
                .condition("amount > 10")
                .severity(RuleSeverity.INFO)
                .register();

        client.rule("R_RED")
                .name("红色")
                .condition("amount > 100")
                .severity(RuleSeverity.RED)
                .register();

        RuleResult top = client.topResult(Map.of("amount", 200));
        assertNotNull(top);
        assertEquals("R_RED", top.getRuleCode());
        assertEquals(RuleSeverity.RED, top.getSeverity());
    }

    @Test
    @DisplayName("移除规则")
    void testRemoveRule() {
        client.rule("R_REMOVABLE")
                .name("可移除")
                .condition("amount > 100")
                .severity(RuleSeverity.INFO)
                .register();

        assertEquals(1, client.ruleCount());
        client.removeRule("R_REMOVABLE");
        assertEquals(0, client.ruleCount());

        List<RuleResult> results = client.evaluate(Map.of("amount", 200));
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("指定场景评估")
    void testEvaluateWithScenario() {
        client.rule("R_SCENARIO")
                .name("场景规则")
                .condition("amount > 1000")
                .severity(RuleSeverity.RED)
                .register();

        List<RuleResult> results = client.evaluate(Map.of("amount", 2000), "RISK_CHECK");
        assertEquals(1, results.size());
    }
}
