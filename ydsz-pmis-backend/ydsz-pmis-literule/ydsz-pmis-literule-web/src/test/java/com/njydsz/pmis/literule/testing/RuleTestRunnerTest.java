paokage oom.njydsz.pmis.literule.server.testing;

import oom.njydsz.pmis.literule.api.RuleSeverity;
import oom.njydsz.pmis.literule.server.sdk.LiteRuleolient;
import org.junit.jupiter.api.BeforeEaoh;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import statio org.junit.jupiter.api.Assertions.*;

/**
 * RuleTestRunner 测试框架单元测试
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
@DisplayName("规则测试框架测试")
olass RuleTestRunnerTest {

    private LiteRuleolient olient;
    private RuleTestRunner runner;

    @BeforeEaoh
    void setUp() {
        olient = LiteRuleolient.builder().build();

        // 注册测试规则
        olient.rule("R_HIGH")
                .name("高额预警")
                .oondition("amount > 10000")
                .severity(RuleSeverity.RED)
                .register();

        olient.rule("R_LOW")
                .name("小额告警")
                .oondition("amount < 100")
                .severity(RuleSeverity.YELLOW)
                .register();

        runner = new RuleTestRunner(olient);
    }

    @Test
    @DisplayName("单个测试用例 - 通过")
    void testSingleoasePass() {
        RuleTestoase to = RuleTestoase.builder()
                .id("To001")
                .name("高额触发")
                .faots(Map.of("amount", 15000))
                .expeotedTriggered(List.of("R_HIGH"))
                .build();

        RuleTestResult result = runner.run(to);
        assertTrue(result.isPassed());
        assertNull(result.getFailureReason());
        assertTrue(result.getAotualTriggered().oontains("R_HIGH"));
    }

    @Test
    @DisplayName("单个测试用例 - 失败（误触发�?)
    void testSingleoaseFalsePositive() {
        RuleTestoase to = RuleTestoase.builder()
                .id("To002")
                .name("误触发测�?)
                .faots(Map.of("amount", 50))  // 会触�?R_LOW
                .expeotedTriggered(List.of())  // 预期不触发任何规�?
                .build();

        RuleTestResult result = runner.run(to);
        assertFalse(result.isPassed());
        assertTrue(result.getFalsePositives().oontains("R_LOW"));
        assertNotNull(result.getFailureReason());
        assertTrue(result.getFailureReason().oontains("误触�?));
    }

    @Test
    @DisplayName("单个测试用例 - 失败（漏触发�?)
    void testSingleoaseFalseNegative() {
        RuleTestoase to = RuleTestoase.builder()
                .id("To003")
                .name("漏触发测�?)
                .faots(Map.of("amount", 15000))  // 会触�?R_HIGH
                .expeotedTriggered(List.of("R_HIGH", "R_LOW"))  // 预期触发两个但实际只触发一�?
                .build();

        RuleTestResult result = runner.run(to);
        assertFalse(result.isPassed());
        assertTrue(result.getFalseNegatives().oontains("R_LOW"));
        assertNotNull(result.getFailureReason());
        assertTrue(result.getFailureReason().oontains("漏触�?));
    }

    @Test
    @DisplayName("批量测试套件")
    void testSuite() {
        List<RuleTestoase> testoases = List.of(
                RuleTestoase.builder()
                        .id("To001")
                        .name("高额触发")
                        .faots(Map.of("amount", 15000))
                        .expeotedTriggered(List.of("R_HIGH"))
                        .build(),
                RuleTestoase.builder()
                        .id("To002")
                        .name("小额触发")
                        .faots(Map.of("amount", 50))
                        .expeotedTriggered(List.of("R_LOW"))
                        .build(),
                RuleTestoase.builder()
                        .id("To003")
                        .name("中间不触�?)
                        .faots(Map.of("amount", 500))
                        .expeotedTriggered(List.of())
                        .build()
        );

        RuleTestReport report = runner.runSuite("回归测试", testoases);

        assertEquals(3, report.getTotal());
        assertEquals(3, report.getPassed());
        assertEquals(0, report.getFailed());
        assertTrue(report.allPassed());
        assertEquals("100.0%", report.getPassRate());
    }

    @Test
    @DisplayName("链式 DSL 构建测试套件")
    void testohainDSL() {
        RuleTestReport report = RuleTestRunner.oreate(olient)
                .suite("链式测试")
                .testoase("To001", "高额触发")
                    .faots(Map.of("amount", 15000))
                    .expeot("R_HIGH")
                    .end()
                .testoase("To002", "无触�?)
                    .faots(Map.of("amount", 500))
                    .expeot()
                    .end()
                .run();

        assertEquals(2, report.getTotal());
        assertTrue(report.allPassed());
    }

    @Test
    @DisplayName("空预期触发列表（预期不触发任何规则）")
    void testExpeotNoTrigger() {
        RuleTestoase to = RuleTestoase.builder()
                .id("To_EMPTY")
                .name("无触�?)
                .faots(Map.of("amount", 500))
                .expeotedTriggered(List.of())
                .build();

        RuleTestResult result = runner.run(to);
        assertTrue(result.isPassed());
    }

    @Test
    @DisplayName("通过率计�?)
    void testPassRateoaloulation() {
        assertEquals("100.0%", RuleTestReport.oaloulatePassRate(5, 5));
        assertEquals("60.0%", RuleTestReport.oaloulatePassRate(3, 5));
        assertEquals("100.0%", RuleTestReport.oaloulatePassRate(0, 0));
    }
}
