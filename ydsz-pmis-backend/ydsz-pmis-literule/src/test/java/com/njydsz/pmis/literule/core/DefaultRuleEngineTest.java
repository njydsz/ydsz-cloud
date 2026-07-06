package com.njydsz.pmis.literule.core;

import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleEngineStats;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

    @Test
    @DisplayName("规则规模监控 - 注册后 stats 反映注册规则数")
    void registeredRulesShouldBeReflectedInStats() {
        engine.register(createRule("R001", "规则1", 100));
        engine.register(createRule("R002", "规则2", 100));
        engine.register(createRule("R003", "规则3", 100));

        RuleEngineStats stats = engine.getStats();
        assertThat(stats.getRegisteredRules()).isEqualTo(3);
    }

    @Test
    @DisplayName("规则规模监控 - 注销后 stats 反映最新规则数")
    void unregisteredRulesShouldBeReflectedInStats() {
        engine.register(createRule("R001", "规则1", 100));
        engine.register(createRule("R002", "规则2", 100));

        engine.unregister("R001");

        RuleEngineStats stats = engine.getStats();
        assertThat(stats.getRegisteredRules()).isEqualTo(1);
    }

    @Test
    @DisplayName("规则规模监控 - metrics 记录注册规则数与遍历规则数")
    void metricsShouldRecordRuleScale() {
        RuleMetrics metrics = new RuleMetrics();
        engine.setMetrics(metrics);
        engine.register(createRule("R001", "规则1", 100));
        engine.register(createRule("R002", "规则2", 100));

        assertThat(metrics.getRegisteredRules()).isEqualTo(2);

        engine.evaluate(RuleContext.of(Map.of("amount", 1000)));

        assertThat(metrics.getLastEvaluatedRules()).isEqualTo(2);
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

    // ============ 互斥组 ============

    @Test
    @DisplayName("互斥组 - 高优先级命中后，同组低优先级规则跳过")
    void mutexGroupShouldSkipLowerPriorityWhenHigherTriggered() {
        Rule highPriority = createRuleWithMutexGroup("R_HIGH", "高优先级", 10, "G1", true, "RED");
        Rule lowPriority = createRuleWithMutexGroup("R_LOW", "低优先级", 200, "G1", true, "YELLOW");
        engine.register(lowPriority);
        engine.register(highPriority);

        List<RuleResult> results = engine.evaluate(RuleContext.of(Map.of("amount", 1000)));

        // 仅高优先级规则触发，低优先级被互斥组短路
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getRuleCode()).isEqualTo("R_HIGH");
    }

    @Test
    @DisplayName("互斥组 - 首条未命中时，同组后续规则正常评估")
    void mutexGroupShouldNotSkipWhenFirstNotTriggered() {
        Rule notTriggered = createRuleWithMutexGroup("R_NT", "未命中", 10, "G1", false, null);
        Rule triggered = createRuleWithMutexGroup("R_T", "命中", 200, "G1", true, "YELLOW");
        engine.register(notTriggered);
        engine.register(triggered);

        List<RuleResult> results = engine.evaluate(RuleContext.of(Map.of("amount", 1000)));

        // 首条未命中不占用互斥组，第二条正常触发
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getRuleCode()).isEqualTo("R_T");
    }

    @Test
    @DisplayName("互斥组 - 无互斥组的规则不受影响")
    void mutexGroupShouldNotAffectRulesWithoutGroup() {
        Rule grouped = createRuleWithMutexGroup("R_G", "组内规则", 10, "G1", true, "RED");
        Rule ungrouped = createTriggeredRule("R_U", "无组规则", "YELLOW", "独立");
        engine.register(grouped);
        engine.register(ungrouped);

        List<RuleResult> results = engine.evaluate(RuleContext.of(Map.of("amount", 1000)));

        // 两条规则都应触发
        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("互斥组 - 不同互斥组互不影响")
    void differentMutexGroupsShouldNotInterfere() {
        Rule g1Rule = createRuleWithMutexGroup("R_G1", "组1规则", 10, "G1", true, "RED");
        Rule g2Rule = createRuleWithMutexGroup("R_G2", "组2规则", 20, "G2", true, "YELLOW");
        engine.register(g1Rule);
        engine.register(g2Rule);

        List<RuleResult> results = engine.evaluate(RuleContext.of(Map.of("amount", 1000)));

        // 不同互斥组的规则都应触发
        assertThat(results).hasSize(2);
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

    /**
     * 创建带互斥组的测试规则
     *
     * @param code        规则编码
     * @param name        规则名称
     * @param priority    优先级
     * @param mutexGroup  互斥组名称
     * @param triggered   是否触发
     * @param severity    严重度（triggered=false 时可传 null）
     */
    private Rule createRuleWithMutexGroup(String code, String name, int priority,
                                           String mutexGroup, boolean triggered, String severity) {
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
            public String getMutexGroup() { return mutexGroup; }
            @Override
            public RuleResult evaluate(RuleContext context) {
                if (!triggered) {
                    return RuleResult.notTriggered(code);
                }
                return RuleResult.triggered(code, name, "TEST",
                        RuleSeverity.valueOf(severity), name, "触发");
            }
        };
    }
}