package com.njydsz.pmis.literule.core;

import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleEngineStats;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DefaultRuleEngine 单元测试
 *
 * <p>覆盖规则引擎核心能力：注册/注销、按优先级排序执行、场景过滤、
 * 严重度排序、统计、dry-run、topResult。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
class DefaultRuleEngineTest {

    private DefaultRuleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DefaultRuleEngine();
        engine.resetStats();
    }

    // ============ 注册 / 注销 ============

    @Test
    @DisplayName("注册规则 - 成功")
    void registerShouldAddRule() {
        Rule rule = createRule("R001", "测试规则", 100);

        engine.register(rule);

        assertThat(engine.getRules()).hasSize(1);
        assertThat(engine.getRules().get(0).getCode()).isEqualTo("R001");
    }

    @Test
    @DisplayName("注册多条规则 - 按优先级排序")
    void registerMultipleRulesShouldSortByPriority() {
        Rule highPriority = createRule("R_HIGH", "高优先级", 10);
        Rule lowPriority = createRule("R_LOW", "低优先级", 200);
        Rule midPriority = createRule("R_MID", "中优先级", 100);

        engine.register(lowPriority);
        engine.register(highPriority);
        engine.register(midPriority);

        List<Rule> rules = engine.getRules();
        assertThat(rules).hasSize(3);
        assertThat(rules.get(0).getCode()).isEqualTo("R_HIGH");
        assertThat(rules.get(1).getCode()).isEqualTo("R_MID");
        assertThat(rules.get(2).getCode()).isEqualTo("R_LOW");
    }

    @Test
    @DisplayName("注册同编码规则 - 覆盖旧规则")
    void registerSameCodeShouldReplace() {
        Rule oldRule = createRule("R001", "旧规则", 100);
        Rule newRule = createRule("R001", "新规则", 50);

        engine.register(oldRule);
        engine.register(newRule);

        assertThat(engine.getRules()).hasSize(1);
        assertThat(engine.getRules().get(0).getName()).isEqualTo("新规则");
    }

    @Test
    @DisplayName("注销规则 - 成功")
    void unregisterShouldRemoveRule() {
        Rule rule = createRule("R001", "测试规则", 100);
        engine.register(rule);

        engine.unregister("R001");

        assertThat(engine.getRules()).isEmpty();
    }

    @Test
    @DisplayName("注销不存在的规则 - 无影响")
    void unregisterNonExistentShouldNotFail() {
        engine.register(createRule("R001", "测试规则", 100));

        engine.unregister("R999");

        assertThat(engine.getRules()).hasSize(1);
    }

    // ============ 评估 ============

    @Test
    @DisplayName("评估 - 单条规则触发")
    void evaluateSingleRuleTriggered() {
        Rule rule = createTriggeredRule("R001", "超预算", "RED", "预算超支警告");
        engine.register(rule);

        RuleContext ctx = RuleContext.of(Map.of("amount", 1000));
        List<RuleResult> results = engine.evaluate(ctx);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getRuleCode()).isEqualTo("R001");
        assertThat(results.get(0).isTriggered()).isTrue();
        assertThat(results.get(0).getSeverity()).isEqualTo(RuleSeverity.RED);
    }

    @Test
    @DisplayName("评估 - 单条规则未触发")
    void evaluateSingleRuleNotTriggered() {
        Rule rule = createNotTriggeredRule("R001");
        engine.register(rule);

        RuleContext ctx = RuleContext.of(Map.of("amount", 100));
        List<RuleResult> results = engine.evaluate(ctx);

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("评估 - 多条规则混合触发")
    void evaluateMultipleRulesMixed() {
        Rule r1 = createTriggeredRule("R001", "规则1", "RED", "严重");
        Rule r2 = createNotTriggeredRule("R002");
        Rule r3 = createTriggeredRule("R003", "规则3", "YELLOW", "黄色预警");

        engine.register(r1);
        engine.register(r2);
        engine.register(r3);

        RuleContext ctx = RuleContext.of(Map.of("amount", 1000));
        List<RuleResult> results = engine.evaluate(ctx);

        assertThat(results).hasSize(2);
        // 按严重度排序：RED 在前
        assertThat(results.get(0).getSeverity()).isEqualTo(RuleSeverity.RED);
        assertThat(results.get(1).getSeverity()).isEqualTo(RuleSeverity.YELLOW);
    }

    @Test
    @DisplayName("评估 - 场景过滤跳过不匹配规则")
    void evaluateShouldFilterByScenario() {
        Rule allRule = createRuleWithScope("R_ALL", "全场景", 100, "ALL");
        Rule specificRule = createRuleWithScope("R_SPECIFIC", "特定场景", 100, "COCKPIT");

        engine.register(allRule);
        engine.register(specificRule);

        RuleContext ctx = RuleContext.of(Map.of("amount", 1000), "COCKPIT", "TEST");
        List<RuleResult> results = engine.evaluate(ctx);

        // 两条规则都应评估：ALL 匹配所有场景，COCKPIT 匹配 COCKPIT 场景
        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("评估 - 空规则列表返回空结果")
    void evaluateWithNoRulesShouldReturnEmpty() {
        RuleContext ctx = RuleContext.of(Map.of("amount", 1000));
        List<RuleResult> results = engine.evaluate(ctx);

        assertThat(results).isEmpty();
    }

    // ============ topResult ============

    @Test
    @DisplayName("topResult - 返回最严重的结果")
    void topResultShouldReturnMostSevere() {
        Rule r1 = createTriggeredRule("R001", "规则1", "YELLOW", "黄色预警");
        Rule r2 = createTriggeredRule("R002", "规则2", "RED", "红色严重");
        engine.register(r1);
        engine.register(r2);

        RuleResult result = engine.topResult(RuleContext.of(Map.of("amount", 1000)));

        assertThat(result).isNotNull();
        assertThat(result.getSeverity()).isEqualTo(RuleSeverity.RED);
    }

    @Test
    @DisplayName("topResult - 无触发放回 null")
    void topResultWithNoTriggeredShouldReturnNull() {
        Rule r1 = createNotTriggeredRule("R001");
        engine.register(r1);

        RuleResult result = engine.topResult(RuleContext.of(Map.of("amount", 100)));

        assertThat(result).isNull();
    }

    // ============ dryRun ============

    @Test
    @DisplayName("dryRun - 返回所有规则结果（含未触发）")
    void dryRunShouldReturnAllResults() {
        Rule r1 = createTriggeredRule("R001", "规则1", "RED", "严重");
        Rule r2 = createNotTriggeredRule("R002");
        engine.register(r1);
        engine.register(r2);

        List<RuleResult> results = engine.dryRun(RuleContext.of(Map.of("amount", 1000)));

        assertThat(results).hasSize(2);
        assertThat(results.stream().anyMatch(RuleResult::isTriggered)).isTrue();
        assertThat(results.stream().anyMatch(r -> !r.isTriggered())).isTrue();
    }

    @Test
    @DisplayName("dryRun - 规则异常不中断其他规则")
    void dryRunShouldNotInterruptOnException() {
        Rule normal = createTriggeredRule("R_NORMAL", "正常规则", "INFO", "提示");
        Rule throwing = createThrowingRule("R_THROW");
        engine.register(throwing);
        engine.register(normal);

        List<RuleResult> results = engine.dryRun(RuleContext.of(Map.of("amount", 1000)));

        assertThat(results).hasSize(2);
    }

    // ============ 统计 ============

    @Test
    @DisplayName("统计 - evaluate 后统计正确")
    void statsAfterEvaluateShouldBeCorrect() {
        Rule rule = createTriggeredRule("R001", "测试规则", "RED", "严重");
        engine.register(rule);

        engine.evaluate(RuleContext.of(Map.of("amount", 1000)));

        RuleEngineStats stats = engine.getStats();
        assertThat(stats.getTotalEvaluations()).isEqualTo(1);
        assertThat(stats.getTotalTriggered()).isEqualTo(1);
        assertThat(stats.getTotalErrors()).isEqualTo(0);
    }

    @Test
    @DisplayName("统计 - 重置统计后归零")
    void resetStatsShouldClearAll() {
        Rule rule = createTriggeredRule("R001", "测试规则", "RED", "严重");
        engine.register(rule);
        engine.evaluate(RuleContext.of(Map.of("amount", 1000)));

        engine.resetStats();

        RuleEngineStats stats = engine.getStats();
        assertThat(stats.getTotalEvaluations()).isEqualTo(0);
        assertThat(stats.getTotalTriggered()).isEqualTo(0);
    }

    @Test
    @DisplayName("统计 - 禁用统计后不记录")
    void statsDisabledShouldNotRecord() {
        Rule rule = createTriggeredRule("R001", "测试规则", "RED", "严重");
        engine.register(rule);
        engine.setStatsEnabled(false);

        engine.evaluate(RuleContext.of(Map.of("amount", 1000)));

        RuleEngineStats stats = engine.getStats();
        assertThat(stats.getTotalEvaluations()).isEqualTo(0);
    }

    // ============ 异常隔离 ============

    @Test
    @DisplayName("评估 - 单规则异常不中断其他规则")
    void evaluateShouldNotInterruptOnException() {
        Rule normal = createTriggeredRule("R_NORMAL", "正常规则", "RED", "严重");
        Rule throwing = createThrowingRule("R_THROW");
        engine.register(throwing);
        engine.register(normal);

        List<RuleResult> results = engine.evaluate(RuleContext.of(Map.of("amount", 1000)));

        // 正常规则应该触发，异常规则被隔离
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getRuleCode()).isEqualTo("R_NORMAL");
    }

    // ============ 辅助方法 ============

    private Rule createRule(String code, String name, int priority) {
        return new Rule() {
            @Override
            public String getCode() { return code; }
            @Override
            public String getName() { return name; }
            @Override
            public String getCategory() { return "TEST"; }
            @Override
            public int getPriority() { return priority; }
            @Override
            public RuleResult evaluate(RuleContext context) {
                return RuleResult.notTriggered(code);
            }
        };
    }

    private Rule createRuleWithScope(String code, String name, int priority, String scope) {
        return new Rule() {
            @Override
            public String getCode() { return code; }
            @Override
            public String getName() { return name; }
            @Override
            public String getCategory() { return "TEST"; }
            @Override
            public int getPriority() { return priority; }
            @Override
            public String getScope() { return scope; }
            @Override
            public RuleResult evaluate(RuleContext context) {
                return RuleResult.triggered(code, name, "TEST", RuleSeverity.INFO, name, "触发");
            }
        };
    }

    private Rule createTriggeredRule(String code, String name, String severity, String description) {
        return new Rule() {
            @Override
            public String getCode() { return code; }
            @Override
            public String getName() { return name; }
            @Override
            public String getCategory() { return "TEST"; }
            @Override
            public int getPriority() { return 100; }
            @Override
            public RuleResult evaluate(RuleContext context) {
                return RuleResult.triggered(code, name, "TEST",
                        RuleSeverity.valueOf(severity), name, description);
            }
        };
    }

    private Rule createNotTriggeredRule(String code) {
        return new Rule() {
            @Override
            public String getCode() { return code; }
            @Override
            public String getName() { return code; }
            @Override
            public String getCategory() { return "TEST"; }
            @Override
            public int getPriority() { return 100; }
            @Override
            public RuleResult evaluate(RuleContext context) {
                return RuleResult.notTriggered(code);
            }
        };
    }

    private Rule createThrowingRule(String code) {
        return new Rule() {
            @Override
            public String getCode() { return code; }
            @Override
            public String getName() { return code; }
            @Override
            public String getCategory() { return "TEST"; }
            @Override
            public int getPriority() { return 100; }
            @Override
            public RuleResult evaluate(RuleContext context) {
                throw new RuntimeException("模拟规则异常");
            }
        };
    }
}