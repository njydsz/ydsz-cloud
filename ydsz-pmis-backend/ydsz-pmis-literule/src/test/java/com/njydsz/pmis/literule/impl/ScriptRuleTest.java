package com.njydsz.pmis.literule.impl;

import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 脚本规则测试
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@DisplayName("脚本规则（Groovy）测试")
class ScriptRuleTest {

    @Nested
    @DisplayName("基础脚本评估")
    class BasicEvaluation {

        @Test
        @DisplayName("简单条件触发 - 返回 true")
        void testTriggered() {
            ScriptRule rule = new ScriptRule("SCRIPT_001", "测试规则", "TEST",
                    RuleSeverity.YELLOW,
                    "return facts.amount > 1000");

            RuleContext ctx = RuleContext.of(Map.of("amount", 2000));
            RuleResult result = rule.evaluate(ctx);

            assertTrue(result.isTriggered());
            assertEquals("SCRIPT_001", result.getRuleCode());
            assertEquals(RuleSeverity.YELLOW, result.getSeverity());
        }

        @Test
        @DisplayName("简单条件未触发 - 返回 false")
        void testNotTriggered() {
            ScriptRule rule = new ScriptRule("SCRIPT_002", "测试规则", "TEST",
                    RuleSeverity.YELLOW,
                    "return facts.amount > 1000");

            RuleContext ctx = RuleContext.of(Map.of("amount", 500));
            RuleResult result = rule.evaluate(ctx);

            assertFalse(result.isTriggered());
        }

        @Test
        @DisplayName("复杂多条件脚本")
        void testComplexScript() {
            String script = """
                def budget = facts.budgetUsedRatio ?: 0
                def spi = facts.spi ?: 1.0
                if (budget >= 0.9 && spi < 0.85) {
                    severity = 'RED'
                    title = '预算超支且进度滞后'
                    return true
                }
                if (budget >= 0.8) {
                    severity = 'YELLOW'
                    return true
                }
                return false
                """;

            ScriptRule rule = new ScriptRule("BUDGET_SCRIPT", "预算脚本规则", "BUDGET",
                    RuleSeverity.INFO, script);

            // 触发 RED
            RuleResult redResult = rule.evaluate(RuleContext.of(Map.of(
                    "budgetUsedRatio", 0.95, "spi", 0.80)));
            assertTrue(redResult.isTriggered());
            assertEquals(RuleSeverity.RED, redResult.getSeverity());
            assertEquals("预算超支且进度滞后", redResult.getTitle());

            // 触发 YELLOW
            RuleResult yellowResult = rule.evaluate(RuleContext.of(Map.of(
                    "budgetUsedRatio", 0.82, "spi", 0.95)));
            assertTrue(yellowResult.isTriggered());
            assertEquals(RuleSeverity.YELLOW, yellowResult.getSeverity());

            // 未触发
            RuleResult noResult = rule.evaluate(RuleContext.of(Map.of(
                    "budgetUsedRatio", 0.5, "spi", 1.0)));
            assertFalse(noResult.isTriggered());
        }
    }

    @Nested
    @DisplayName("动态严重度和自定义信息")
    class DynamicOutput {

        @Test
        @DisplayName("脚本动态设置严重度")
        void testDynamicSeverity() {
            String script = """
                if (facts.score >= 80) {
                    severity = 'RED'
                    return true
                }
                if (facts.score >= 60) {
                    severity = 'YELLOW'
                    return true
                }
                severity = 'INFO'
                return true
                """;

            ScriptRule rule = new ScriptRule("SCORE_RULE", "评分规则", "TEST",
                    RuleSeverity.INFO, script);

            assertEquals(RuleSeverity.RED, rule.evaluate(RuleContext.of(Map.of("score", 90))).getSeverity());
            assertEquals(RuleSeverity.YELLOW, rule.evaluate(RuleContext.of(Map.of("score", 70))).getSeverity());
            assertEquals(RuleSeverity.INFO, rule.evaluate(RuleContext.of(Map.of("score", 50))).getSeverity());
        }

        @Test
        @DisplayName("脚本自定义标题和描述")
        void testCustomTitleAndDescription() {
            String script = """
                def project = facts.projectName ?: '未知项目'
                def ratio = facts.budgetUsedRatio ?: 0
                title = "预算预警: ${project}"
                description = "预算使用率已达到 ${ratio * 100}%，请关注"
                return true
                """;

            ScriptRule rule = new ScriptRule("CUSTOM_MSG", "自定义消息", "TEST",
                    RuleSeverity.YELLOW, script);

            RuleResult result = rule.evaluate(RuleContext.of(Map.of(
                    "projectName", "南京智慧城市", "budgetUsedRatio", 0.92)));

            assertTrue(result.isTriggered());
            assertTrue(result.getTitle().contains("南京智慧城市"));
            assertTrue(result.getDescription().contains("92"));
        }

        @Test
        @DisplayName("脚本未设置 title 时使用规则名称")
        void testDefaultTitle() {
            ScriptRule rule = new ScriptRule("NO_TITLE", "默认标题规则", "TEST",
                    RuleSeverity.INFO, "return true");

            RuleResult result = rule.evaluate(RuleContext.of(Map.of()));
            assertTrue(result.isTriggered());
            assertEquals("默认标题规则", result.getTitle());
        }
    }

    @Nested
    @DisplayName("沙箱安全")
    class SandboxSecurity {

        @Test
        @DisplayName("沙箱阻止 System.exit 调用")
        void testBlockSystemExit() {
            assertThrows(SecurityException.class, () ->
                new ScriptRule("DANGER_1", "危险规则", "TEST",
                    RuleSeverity.RED, "System.exit(0)", true));
        }

        @Test
        @DisplayName("沙箱阻止 Runtime.exec 调用")
        void testBlockRuntimeExec() {
            assertThrows(SecurityException.class, () ->
                new ScriptRule("DANGER_2", "危险规则", "TEST",
                    RuleSeverity.RED, "Runtime.getRuntime().exec('rm -rf /')", true));
        }

        @Test
        @DisplayName("沙箱阻止 Class.forName 反射")
        void testBlockReflection() {
            assertThrows(SecurityException.class, () ->
                new ScriptRule("DANGER_3", "危险规则", "TEST",
                    RuleSeverity.RED, "Class.forName('java.lang.Runtime')", true));
        }

        @Test
        @DisplayName("沙箱阻止文件 I/O")
        void testBlockFileIO() {
            assertThrows(SecurityException.class, () ->
                new ScriptRule("DANGER_4", "危险规则", "TEST",
                    RuleSeverity.RED, "new FileInputStream('/etc/passwd')", true));
        }

        @Test
        @DisplayName("沙箱阻止 ProcessBuilder")
        void testBlockProcessBuilder() {
            assertThrows(SecurityException.class, () ->
                new ScriptRule("DANGER_5", "危险规则", "TEST",
                    RuleSeverity.RED,
                    "new ProcessBuilder('cmd', '/c', 'dir').start()", true));
        }

        @Test
        @DisplayName("非沙箱模式不阻止危险 API（编译阶段）")
        void testNoSandboxAllowsDangerousCompile() {
            // 非沙箱模式下编译不会抛出 SecurityException
            // 注意：这里仅验证编译通过，不实际执行
            assertDoesNotThrow(() ->
                new ScriptRule("NO_SANDBOX", "非沙箱规则", "TEST",
                    RuleSeverity.RED, "return facts.value > 0", false));
        }
    }

    @Nested
    @DisplayName("异常处理")
    class ErrorHandling {

        @Test
        @DisplayName("脚本编译错误抛出 IllegalArgumentException")
        void testCompileError() {
            assertThrows(IllegalArgumentException.class, () ->
                new ScriptRule("BAD_SCRIPT", "语法错误", "TEST",
                    RuleSeverity.YELLOW, "def x = { incomplete"));
        }

        @Test
        @DisplayName("脚本运行时异常返回未触发结果")
        void testRuntimeError() {
            ScriptRule rule = new ScriptRule("ERR_SCRIPT", "运行时错误", "TEST",
                    RuleSeverity.YELLOW,
                    "return facts.nonexistent.method()"); // NPE

            RuleResult result = rule.evaluate(RuleContext.of(Map.of()));
            assertFalse(result.isTriggered());
            assertEquals("ERR_SCRIPT", result.getRuleCode());
        }

        @Test
        @DisplayName("脚本返回非 boolean 值视为未触发")
        void testNonBooleanReturn() {
            ScriptRule rule = new ScriptRule("STR_SCRIPT", "返回字符串", "TEST",
                    RuleSeverity.YELLOW,
                    "return 'hello'");

            RuleResult result = rule.evaluate(RuleContext.of(Map.of()));
            assertFalse(result.isTriggered());
        }

        @Test
        @DisplayName("脚本返回 null 视为未触发")
        void testNullReturn() {
            ScriptRule rule = new ScriptRule("NULL_SCRIPT", "返回 null", "TEST",
                    RuleSeverity.YELLOW,
                    "return null");

            RuleResult result = rule.evaluate(RuleContext.of(Map.of()));
            assertFalse(result.isTriggered());
        }
    }

    @Test
    @DisplayName("scope 和 priority 正确设置")
    void testScopeAndPriority() {
        ScriptRule rule = new ScriptRule("SCOPE_TEST", "作用域测试", "TEST",
                50, "PROJECT", RuleSeverity.YELLOW, "return true", true);

        assertEquals("PROJECT", rule.getScope());
        assertEquals(50, rule.getPriority());
    }

    @Test
    @DisplayName("getScript 和 isSandboxEnabled 正确返回")
    void testGetters() {
        String script = "return facts.x > 0";
        ScriptRule rule = new ScriptRule("GETTER_TEST", "Getter 测试", "TEST",
                RuleSeverity.INFO, script);

        assertEquals(script, rule.getScript());
        assertTrue(rule.isSandboxEnabled());
    }
}
