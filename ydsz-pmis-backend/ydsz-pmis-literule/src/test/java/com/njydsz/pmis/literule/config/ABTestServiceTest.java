package com.njydsz.pmis.literule.config;

import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.expr.AviatorExpressionEvaluator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A/B 测试服务测试
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@DisplayName("A/B 测试服务测试")
class ABTestServiceTest {

    private ABTestService abTestService;

    @BeforeEach
    void setUp() {
        abTestService = new ABTestService(new AviatorExpressionEvaluator(false));
    }

    @Test
    @DisplayName("条件表达式变更导致触发差异")
    void testTriggeredDiff() {
        RuleDefinition current = RuleDefinition.builder()
                .code("AB_TEST_001")
                .name("测试规则")
                .category("TEST")
                .conditionExpression("amount > 1000")
                .defaultSeverity(RuleSeverity.YELLOW)
                .version(1)
                .build();

        RuleDefinition candidate = RuleDefinition.builder()
                .code("AB_TEST_001")
                .name("测试规则")
                .category("TEST")
                .conditionExpression("amount > 500")
                .defaultSeverity(RuleSeverity.YELLOW)
                .version(2)
                .build();

        // amount=800: 当前不触发，候选触发
        ABTestService.ABTestReport report = abTestService.test(current, candidate,
                Map.of("amount", 800));

        assertFalse(report.currentResult().isTriggered());
        assertTrue(report.candidateResult().isTriggered());
        assertTrue((Boolean) report.diff().get("triggeredChanged"));
        assertTrue((Boolean) report.diff().get("hasDiff"));
    }

    @Test
    @DisplayName("严重度表达式变更导致严重度差异")
    void testSeverityDiff() {
        RuleDefinition current = RuleDefinition.builder()
                .code("AB_TEST_002")
                .name("严重度测试")
                .category("TEST")
                .conditionExpression("amount > 100")
                .severityExpression("amount > 500 ? 'RED' : 'YELLOW'")
                .defaultSeverity(RuleSeverity.YELLOW)
                .version(1)
                .build();

        RuleDefinition candidate = RuleDefinition.builder()
                .code("AB_TEST_002")
                .name("严重度测试")
                .category("TEST")
                .conditionExpression("amount > 100")
                .severityExpression("amount > 300 ? 'RED' : 'YELLOW'")
                .defaultSeverity(RuleSeverity.YELLOW)
                .version(2)
                .build();

        // amount=400: 当前 YELLOW, 候选 RED
        ABTestService.ABTestReport report = abTestService.test(current, candidate,
                Map.of("amount", 400));

        assertTrue(report.currentResult().isTriggered());
        assertTrue(report.candidateResult().isTriggered());
        assertEquals(RuleSeverity.YELLOW, report.currentResult().getSeverity());
        assertEquals(RuleSeverity.RED, report.candidateResult().getSeverity());
        assertTrue((Boolean) report.diff().get("severityChanged"));
        assertFalse((Boolean) report.diff().get("triggeredChanged"));
    }

    @Test
    @DisplayName("模板变更导致标题差异")
    void testTitleDiff() {
        RuleDefinition current = RuleDefinition.builder()
                .code("AB_TEST_003")
                .name("模板测试")
                .category("TEST")
                .conditionExpression("amount > 100")
                .titleTemplate("旧标题: ${amount}")
                .defaultSeverity(RuleSeverity.INFO)
                .version(1)
                .build();

        RuleDefinition candidate = RuleDefinition.builder()
                .code("AB_TEST_003")
                .name("模板测试")
                .category("TEST")
                .conditionExpression("amount > 100")
                .titleTemplate("新标题: ${amount}")
                .defaultSeverity(RuleSeverity.INFO)
                .version(2)
                .build();

        ABTestService.ABTestReport report = abTestService.test(current, candidate,
                Map.of("amount", 200));

        assertTrue(report.currentResult().isTriggered());
        assertTrue(report.candidateResult().isTriggered());
        assertNotEquals(report.currentResult().getTitle(), report.candidateResult().getTitle());
        assertTrue((Boolean) report.diff().get("titleChanged"));
        assertFalse((Boolean) report.diff().get("triggeredChanged"));
        assertFalse((Boolean) report.diff().get("severityChanged"));
    }

    @Test
    @DisplayName("无变更时报告无差异")
    void testNoDiff() {
        RuleDefinition current = RuleDefinition.builder()
                .code("AB_TEST_004")
                .name("无差异测试")
                .category("TEST")
                .conditionExpression("amount > 100")
                .defaultSeverity(RuleSeverity.YELLOW)
                .version(1)
                .build();

        RuleDefinition candidate = RuleDefinition.builder()
                .code("AB_TEST_004")
                .name("无差异测试")
                .category("TEST")
                .conditionExpression("amount > 100")
                .defaultSeverity(RuleSeverity.YELLOW)
                .version(2)
                .build();

        ABTestService.ABTestReport report = abTestService.test(current, candidate,
                Map.of("amount", 200));

        assertEquals(report.currentResult().isTriggered(), report.candidateResult().isTriggered());
        assertEquals(report.currentResult().getSeverity(), report.candidateResult().getSeverity());
        assertFalse((Boolean) report.diff().get("hasDiff"));
    }

    @Test
    @DisplayName("报告包含正确的版本号和规则编码")
    void testReportMetadata() {
        RuleDefinition current = RuleDefinition.builder()
                .code("META_TEST")
                .name("元数据测试")
                .category("TEST")
                .conditionExpression("true")
                .defaultSeverity(RuleSeverity.INFO)
                .version(3)
                .build();

        RuleDefinition candidate = RuleDefinition.builder()
                .code("META_TEST")
                .name("元数据测试")
                .category("TEST")
                .conditionExpression("true")
                .defaultSeverity(RuleSeverity.INFO)
                .version(4)
                .build();

        ABTestService.ABTestReport report = abTestService.test(current, candidate, Map.of());

        assertEquals("META_TEST", report.ruleCode());
        assertEquals(3, report.currentVersion());
        assertEquals(4, report.candidateVersion());
        assertNotNull(report.summary());
    }
}
