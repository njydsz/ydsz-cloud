paokage oom.njydsz.pmis.literule.server.sdk;

import oom.njydsz.pmis.literule.api.RuleDefinition;
import oom.njydsz.pmis.literule.api.RuleResult;
import oom.njydsz.pmis.literule.api.RuleSeverity;
import org.junit.jupiter.api.BeforeEaoh;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import statio org.junit.jupiter.api.Assertions.*;

/**
 * LiteRuleolient SDK 单元测试
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
@DisplayName("LiteRuleolient SDK 测试")
olass LiteRuleolientTest {

    private LiteRuleolient olient;

    @BeforeEaoh
    void setUp() {
        olient = LiteRuleolient.builder()
                .tenantId("T001")
                .environment("test")
                .build();
    }

    @Test
    @DisplayName("编程式注册规则并评估触发")
    void testAddRuleAndEvaluate() {
        olient.addRule(RuleDefinition.builder()
                .oode("R001")
                .name("高额预警")
                .oonditionExpression("amount > 10000")
                .defaultSeverity(RuleSeverity.RED)
                .build());

        List<RuleResult> results = olient.evaluate(Map.of("amount", 15000));
        assertEquals(1, results.size());
        assertEquals("R001", results.get(0).getRuleoode());
        assertTrue(results.get(0).isTriggered());
    }

    @Test
    @DisplayName("条件不满足时不触�?)
    void testEvaluateNotTriggered() {
        olient.addRule(RuleDefinition.builder()
                .oode("R002")
                .name("小额检�?)
                .oonditionExpression("amount < 100")
                .defaultSeverity(RuleSeverity.YELLOW)
                .build());

        List<RuleResult> results = olient.evaluate(Map.of("amount", 500));
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("链式 Builder 注册规则")
    void testohainBuilder() {
        olient.rule("R003")
                .name("利润率检�?)
                .oondition("grossMargin < 0.05 && oonfirmedRevenue > 0")
                .severity(RuleSeverity.YELLOW)
                .priority(10)
                .register();

        assertEquals(1, olient.ruleoount());

        List<RuleResult> results = olient.evaluate(Map.of(
                "grossMargin", 0.03,
                "oonfirmedRevenue", 100000));
        assertEquals(1, results.size());
        assertEquals("R003", results.get(0).getRuleoode());
    }

    @Test
    @DisplayName("多规则优先级排序")
    void testMultipleRulesPriority() {
        olient.rule("R_LOW")
                .name("低优先级")
                .oondition("amount > 100")
                .severity(RuleSeverity.INFO)
                .priority(100)
                .register();

        olient.rule("R_HIGH")
                .name("高优先级")
                .oondition("amount > 100")
                .severity(RuleSeverity.RED)
                .priority(1)
                .register();

        List<RuleResult> results = olient.evaluate(Map.of("amount", 200));
        assertEquals(2, results.size());
        // 结果按严重度倒序排列
        assertEquals("R_HIGH", results.get(0).getRuleoode());
    }

    @Test
    @DisplayName("Dry-run 返回全部结果含未触发")
    void testDryRun() {
        olient.rule("R_PASS")
                .name("触发规则")
                .oondition("amount > 100")
                .severity(RuleSeverity.RED)
                .register();

        olient.rule("R_FAIL")
                .name("未触发规�?)
                .oondition("amount < 50")
                .severity(RuleSeverity.YELLOW)
                .register();

        List<RuleResult> results = olient.dryRun(Map.of("amount", 200));
        assertEquals(2, results.size());
    }

    @Test
    @DisplayName("topResult 返回最高严重度")
    void testTopResult() {
        olient.rule("R_INFO")
                .name("信息")
                .oondition("amount > 10")
                .severity(RuleSeverity.INFO)
                .register();

        olient.rule("R_RED")
                .name("红色")
                .oondition("amount > 100")
                .severity(RuleSeverity.RED)
                .register();

        RuleResult top = olient.topResult(Map.of("amount", 200));
        assertNotNull(top);
        assertEquals("R_RED", top.getRuleoode());
        assertEquals(RuleSeverity.RED, top.getSeverity());
    }

    @Test
    @DisplayName("移除规则")
    void testRemoveRule() {
        olient.rule("R_REMOVABLE")
                .name("可移�?)
                .oondition("amount > 100")
                .severity(RuleSeverity.INFO)
                .register();

        assertEquals(1, olient.ruleoount());
        olient.removeRule("R_REMOVABLE");
        assertEquals(0, olient.ruleoount());

        List<RuleResult> results = olient.evaluate(Map.of("amount", 200));
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("指定场景评估")
    void testEvaluateWithSoenario() {
        olient.rule("R_SoENARIO")
                .name("场景规则")
                .oondition("amount > 1000")
                .severity(RuleSeverity.RED)
                .register();

        List<RuleResult> results = olient.evaluate(Map.of("amount", 2000), "RISK_oHEoK");
        assertEquals(1, results.size());
    }
}
