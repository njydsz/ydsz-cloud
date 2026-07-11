package com.njydsz.pmis.literule.testing;

import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.sdk.LiteRuleClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RuleTestRunner 测试框架单元测试
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
@DisplayName("规则测试框架测试")
class RuleTestRunnerTest {

    private LiteRuleClient client;
    private RuleTestRunner runner;

    @BeforeEach
    void setUp() {
        client = LiteRuleClient.builder().build();

        // 注册测试规则
        client.rule("R_HIGH")
                .name("高额预警")
                .condition("amount > 10000")
                .severity(RuleSeverity.RED)
                .register();

        client.rule("R_LOW")
                .name("小额告警")
                .condition("amount < 100")
                .severity(RuleSeverity.YELLOW)
                .register();

        runner = new RuleTestRunner(client);
    }

    @Test
    @DisplayName("单个测试用例 - 通过")
    void testSingleCasePass() {
        RuleTestCase tc = RuleTestCase.builder()
                .id("TC001")
                .name("高额触发")
                .facts(Map.of("amount", 15000))
                .expectedTriggered(List.of("R_HIGH"))
                .build();

        RuleTestResult result = runner.run(tc);
        assertTrue(result.isPassed());
        assertNull(result.getFailureReason());
        assertTrue(result.getActualTriggered().contains("R_HIGH"));
    }

    @Test
    @DisplayName("单个测试用例 - 失败（误触发）")
    void testSingleCaseFalsePositive() {
        RuleTestCase tc = RuleTestCase.builder()
                .id("TC002")
                .name("误触发测试")
                .facts(Map.of("amount", 50))  // 会触发 R_LOW
                .expectedTriggered(List.of())  // 预期不触发任何规则
                .build();

        RuleTestResult result = runner.run(tc);
        assertFalse(result.isPassed());
        assertTrue(result.getFalsePositives().contains("R_LOW"));
        assertNotNull(result.getFailureReason());
        assertTrue(result.getFailureReason().contains("误触发"));
    }

    @Test
    @DisplayName("单个测试用例 - 失败（漏触发）")
    void testSingleCaseFalseNegative() {
        RuleTestCase tc = RuleTestCase.builder()
                .id("TC003")
                .name("漏触发测试")
                .facts(Map.of("amount", 15000))  // 会触发 R_HIGH
                .expectedTriggered(List.of("R_HIGH", "R_LOW"))  // 预期触发两个但实际只触发一个
                .build();

        RuleTestResult result = runner.run(tc);
        assertFalse(result.isPassed());
        assertTrue(result.getFalseNegatives().contains("R_LOW"));
        assertNotNull(result.getFailureReason());
        assertTrue(result.getFailureReason().contains("漏触发"));
    }

    @Test
    @DisplayName("批量测试套件")
    void testSuite() {
        List<RuleTestCase> testCases = List.of(
                RuleTestCase.builder()
                        .id("TC001")
                        .name("高额触发")
                        .facts(Map.of("amount", 15000))
                        .expectedTriggered(List.of("R_HIGH"))
                        .build(),
                RuleTestCase.builder()
                        .id("TC002")
                        .name("小额触发")
                        .facts(Map.of("amount", 50))
                        .expectedTriggered(List.of("R_LOW"))
                        .build(),
                RuleTestCase.builder()
                        .id("TC003")
                        .name("中间不触发")
                        .facts(Map.of("amount", 500))
                        .expectedTriggered(List.of())
                        .build()
        );

        RuleTestReport report = runner.runSuite("回归测试", testCases);

        assertEquals(3, report.getTotal());
        assertEquals(3, report.getPassed());
        assertEquals(0, report.getFailed());
        assertTrue(report.allPassed());
        assertEquals("100.0%", report.getPassRate());
    }

    @Test
    @DisplayName("链式 DSL 构建测试套件")
    void testChainDSL() {
        RuleTestReport report = RuleTestRunner.create(client)
                .suite("链式测试")
                .testCase("TC001", "高额触发")
                    .facts(Map.of("amount", 15000))
                    .expect("R_HIGH")
                    .end()
                .testCase("TC002", "无触发")
                    .facts(Map.of("amount", 500))
                    .expect()
                    .end()
                .run();

        assertEquals(2, report.getTotal());
        assertTrue(report.allPassed());
    }

    @Test
    @DisplayName("空预期触发列表（预期不触发任何规则）")
    void testExpectNoTrigger() {
        RuleTestCase tc = RuleTestCase.builder()
                .id("TC_EMPTY")
                .name("无触发")
                .facts(Map.of("amount", 500))
                .expectedTriggered(List.of())
                .build();

        RuleTestResult result = runner.run(tc);
        assertTrue(result.isPassed());
    }

    @Test
    @DisplayName("通过率计算")
    void testPassRateCalculation() {
        assertEquals("100.0%", RuleTestReport.calculatePassRate(5, 5));
        assertEquals("60.0%", RuleTestReport.calculatePassRate(3, 5));
        assertEquals("100.0%", RuleTestReport.calculatePassRate(0, 0));
    }
}
