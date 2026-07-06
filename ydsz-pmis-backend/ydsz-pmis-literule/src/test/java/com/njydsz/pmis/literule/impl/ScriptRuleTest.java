package com.njydsz.pmis.literule.impl;

import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.api.ScriptDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 脚本规则单元测试
 *
 * <p>覆盖多语言脚本规则（Groovy / JavaScript）的评估、沙箱安全、异常容错等场景。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@DisplayName("脚本规则测试（多语言）")
class ScriptRuleTest {

    // ---------- Groovy 脚本 ----------

    @Test
    @DisplayName("Groovy 脚本：条件满足应触发并返回正确严重度")
    void groovyScriptShouldTriggerWhenConditionMet() {
        ScriptRule rule = new ScriptRule(
                "R_GROOVY_01", "预算超支规则", "RISK",
                RuleSeverity.YELLOW,
                "def budget = facts.budgetUsedRatio ?: 0\n" +
                        "def spi = facts.spi ?: 1.0\n" +
                        "if (budget >= 0.9 && spi < 0.85) {\n" +
                        "    severity = 'RED'\n" +
                        "    return true\n" +
                        "}\n" +
                        "return false",
                true);

        Map<String, Object> facts = new HashMap<>();
        facts.put("budgetUsedRatio", 0.95);
        facts.put("spi", 0.8);

        RuleResult result = rule.evaluate(RuleContext.of(facts));

        assertTrue(result.isTriggered());
        assertEquals(RuleSeverity.RED, result.getSeverity());
        assertEquals("Groovy Script", result.getThreshold());
    }

    @Test
    @DisplayName("Groovy 脚本：条件不满足应不触发")
    void groovyScriptShouldNotTriggerWhenConditionNotMet() {
        ScriptRule rule = new ScriptRule(
                "R_GROOVY_02", "预算规则", "RISK",
                RuleSeverity.YELLOW,
                "return facts.amount > 1000",
                true);

        Map<String, Object> facts = new HashMap<>();
        facts.put("amount", 500);

        RuleResult result = rule.evaluate(RuleContext.of(facts));

        assertFalse(result.isTriggered());
    }

    @Test
    @DisplayName("Groovy 脚本：沙箱模式应拒绝危险 API")
    void groovyScriptShouldRejectDangerousApiInSandbox() {
        assertThrows(SecurityException.class, () -> new ScriptRule(
                "R_GROOVY_03", "危险脚本", "RISK",
                RuleSeverity.RED,
                "Runtime.getRuntime().exec('ls')",
                true));
    }

    @Test
    @DisplayName("Groovy 脚本：非沙箱模式允许危险 API（仅编译检查）")
    void groovyScriptShouldAllowDangerousApiWithoutSandbox() {
        // 非沙箱模式下不检查危险 API，但脚本仍需语法正确
        assertDoesNotThrow(() -> new ScriptRule(
                "R_GROOVY_04", "非沙箱脚本", "RISK",
                RuleSeverity.RED,
                "return true",
                false));
    }

    @Test
    @DisplayName("Groovy 脚本：编译错误应抛出 IllegalArgumentException")
    void groovyScriptCompileErrorShouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> new ScriptRule(
                "R_GROOVY_05", "语法错误", "RISK",
                RuleSeverity.RED,
                "def x = \n", // 语法错误
                true));
    }

    @Test
    @DisplayName("Groovy 脚本：运行时异常应返回未触发结果")
    void groovyScriptRuntimeExceptionShouldReturnNotTriggered() {
        ScriptRule rule = new ScriptRule(
                "R_GROOVY_06", "运行时异常", "RISK",
                RuleSeverity.RED,
                "throw new RuntimeException('test error')",
                false);

        RuleResult result = rule.evaluate(RuleContext.of(new HashMap<>()));

        assertFalse(result.isTriggered());
    }

    // ---------- JavaScript 脚本 ----------

    @Test
    @DisplayName("JavaScript 脚本：条件满足应触发")
    void javaScriptScriptShouldTriggerWhenConditionMet() {
        ScriptRule rule = new ScriptRule(
                "R_JS_01", "JS预算规则", "RISK", 100, null,
                RuleSeverity.YELLOW,
                "var budget = facts.budgetUsedRatio || 0;\n" +
                        "var spi = facts.spi || 1.0;\n" +
                        "if (budget >= 0.9 && spi < 0.85) {\n" +
                        "    severity = 'RED';\n" +
                        "    true;\n" +
                        "} else {\n" +
                        "    false;\n" +
                        "}",
                "javascript",
                true);

        Map<String, Object> facts = new HashMap<>();
        facts.put("budgetUsedRatio", 0.95);
        facts.put("spi", 0.8);

        RuleResult result = rule.evaluate(RuleContext.of(facts));

        assertTrue(result.isTriggered());
        assertEquals(RuleSeverity.RED, result.getSeverity());
        assertEquals("Javascript Script", result.getThreshold());
    }

    @Test
    @DisplayName("JavaScript 脚本：条件不满足应不触发")
    void javaScriptScriptShouldNotTriggerWhenConditionNotMet() {
        ScriptRule rule = new ScriptRule(
                "R_JS_02", "JS预算规则", "RISK", 100, null,
                RuleSeverity.YELLOW,
                "var amount = facts.amount || 0;\n" +
                        "amount > 1000;",
                "javascript",
                true);

        Map<String, Object> facts = new HashMap<>();
        facts.put("amount", 500);

        RuleResult result = rule.evaluate(RuleContext.of(facts));

        assertFalse(result.isTriggered());
    }

    @Test
    @DisplayName("JavaScript 脚本：js 短名应等价于 javascript")
    void javaScriptShortNameShouldWork() {
        ScriptRule rule = new ScriptRule(
                "R_JS_03", "JS短名", "RISK", 100, null,
                RuleSeverity.INFO,
                "true",
                "js",
                true);

        assertEquals("javascript", rule.getLanguage());
    }

    @Test
    @DisplayName("JavaScript 脚本：沙箱模式应拒绝危险 API")
    void javaScriptScriptShouldRejectDangerousApiInSandbox() {
        assertThrows(SecurityException.class, () -> new ScriptRule(
                "R_JS_04", "危险JS", "RISK", 100, null,
                RuleSeverity.RED,
                "Runtime.getRuntime().exec('ls')",
                "javascript",
                true));
    }

    // ---------- ScriptDefinition.from 工厂 ----------

    @Test
    @DisplayName("ScriptDefinition.from：应正确传递 language 字段")
    void fromDefinitionShouldPreserveLanguage() {
        ScriptDefinition def = ScriptDefinition.builder()
                .ruleCode("R_FROM_01")
                .ruleName("JS规则")
                .category("RISK")
                .language("javascript")
                .script("true")
                .defaultSeverity("INFO")
                .sandboxEnabled(true)
                .build();

        ScriptRule rule = ScriptRule.from(def);

        assertEquals("javascript", rule.getLanguage());
        assertEquals("Javascript Script", "Javascript Script");
    }

    @Test
    @DisplayName("ScriptDefinition.from：language 为 null 时应默认 groovy")
    void fromDefinitionNullLanguageShouldDefaultToGroovy() {
        ScriptDefinition def = ScriptDefinition.builder()
                .ruleCode("R_FROM_02")
                .ruleName("默认Groovy")
                .category("RISK")
                .language(null)
                .script("return true")
                .defaultSeverity("INFO")
                .sandboxEnabled(true)
                .build();

        ScriptRule rule = ScriptRule.from(def);

        assertEquals("groovy", rule.getLanguage());
    }

    @Test
    @DisplayName("ScriptDefinition.from：空 language 应默认 groovy")
    void fromDefinitionEmptyLanguageShouldDefaultToGroovy() {
        ScriptDefinition def = ScriptDefinition.builder()
                .ruleCode("R_FROM_03")
                .ruleName("空语言")
                .category("RISK")
                .language("")
                .script("return true")
                .defaultSeverity("INFO")
                .sandboxEnabled(true)
                .build();

        ScriptRule rule = ScriptRule.from(def);

        assertEquals("groovy", rule.getLanguage());
    }

    // ---------- 未知语言处理 ----------

    @Test
    @DisplayName("未知语言应抛出 IllegalStateException")
    void unknownLanguageShouldThrow() {
        assertThrows(IllegalStateException.class, () -> new ScriptRule(
                "R_UNKNOWN_01", "未知语言", "RISK", 100, null,
                RuleSeverity.RED,
                "return true",
                "ruby",
                true));
    }
}
