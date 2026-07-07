package com.njydsz.pmis.literule.orchestrator;

import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.api.StatsRecorder;
import com.njydsz.pmis.literule.expr.ExpressionEvaluator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RuleChain 规则链单元测试
 *
 * <p>测试目标：覆盖 {@link RuleChain} 的全部编排语义（THEN/WHEN/IF/ELIF/SWITCH/FOR/WHILE/BREAK）、
 * 工厂方法、evaluate 各重载入口、节点异常隔离、统计记录、嵌套组合及防御性边界条件。
 * 通过反射访问私有构造方法，覆盖工厂方法不可达的防御性分支，确保行/分支覆盖率最大化。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@DisplayName("RuleChain 规则链测试")
class RuleChainTest {

    // ==================== 辅助方法 ====================

    /** 创建触发规则 */
    private Rule triggeredRule(String code) {
        return new TestRule(code, RuleResult.triggered(
                code, "测试规则-" + code, "TEST", RuleSeverity.YELLOW, "触发", "测试触发"));
    }

    /** 创建未触发规则 */
    private Rule notTriggeredRule(String code) {
        return new TestRule(code, RuleResult.notTriggered(code));
    }

    /** 创建抛出异常的规则 */
    private Rule errorRule(String code) {
        return new Rule() {
            @Override
            public String getCode() { return code; }
            @Override
            public String getName() { return "异常规则-" + code; }
            @Override
            public String getCategory() { return "TEST"; }
            @Override
            public RuleResult evaluate(RuleContext context) {
                throw new RuntimeException("测试规则异常: " + code);
            }
        };
    }

    /** 创建抛出 Error 的规则（用于 WHEN 异常路径测试） */
    private Rule errorThrowingRule(String code) {
        return new Rule() {
            @Override
            public String getCode() { return code; }
            @Override
            public String getName() { return "Error规则-" + code; }
            @Override
            public String getCategory() { return "TEST"; }
            @Override
            public RuleResult evaluate(RuleContext context) {
                throw new Error("测试Error: " + code);
            }
        };
    }

    /** 创建 BREAK 规则（返回 BREAK_CODE 结果） */
    private Rule breakRule() {
        return new Rule() {
            @Override
            public String getCode() { return RuleResult.BREAK_CODE; }
            @Override
            public String getName() { return "BREAK规则"; }
            @Override
            public String getCategory() { return "TEST"; }
            @Override
            public RuleResult evaluate(RuleContext context) {
                RuleResult result = new RuleResult();
                result.setRuleCode(RuleResult.BREAK_CODE);
                result.setTriggered(true);
                result.setSeverity(RuleSeverity.INFO);
                result.setTitle("BREAK 终止循环");
                return result;
            }
        };
    }

    /** 创建慢速规则（用于 WHEN 超时测试） */
    private Rule slowRule(String code, long delayMs) {
        return new Rule() {
            @Override
            public String getCode() { return code; }
            @Override
            public String getName() { return "慢速规则-" + code; }
            @Override
            public String getCategory() { return "TEST"; }
            @Override
            public RuleResult evaluate(RuleContext context) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return RuleResult.notTriggered(code);
                }
                return RuleResult.notTriggered(code);
            }
        };
    }

    /** 创建计数规则（记录被调用的次数） */
    private Rule countingRule(String code, AtomicInteger counter) {
        return new Rule() {
            @Override
            public String getCode() { return code; }
            @Override
            public String getName() { return "计数规则-" + code; }
            @Override
            public String getCategory() { return "TEST"; }
            @Override
            public RuleResult evaluate(RuleContext context) {
                counter.incrementAndGet();
                return RuleResult.notTriggered(code);
            }
        };
    }

    /** 创建带 facts 的上下文 */
    private RuleContext context(Map<String, Object> facts) {
        return RuleContext.of(facts);
    }

    /** 创建 mock 表达式求值器 */
    private ExpressionEvaluator mockEvaluator() {
        return mock(ExpressionEvaluator.class);
    }

    /** 通过反射调用私有构造方法创建 RuleChain（用于覆盖防御性分支） */
    private RuleChain createChainViaReflection(RuleChainType type, List<RuleNode> nodes,
                                                String conditionExpression, String branchKey,
                                                Map<String, RuleNode> branchMap, RuleNode defaultBranch,
                                                List<Map.Entry<String, RuleNode>> elifBranches,
                                                RuleNode elseNode, String iterableExpression,
                                                String iterationVar, int maxIterations) throws Exception {
        var ctor = RuleChain.class.getDeclaredConstructor(
                RuleChainType.class, List.class, String.class, String.class, Map.class,
                RuleNode.class, List.class, RuleNode.class, String.class, String.class, int.class);
        ctor.setAccessible(true);
        return (RuleChain) ctor.newInstance(type, nodes, conditionExpression, branchKey,
                branchMap, defaultBranch, elifBranches, elseNode, iterableExpression,
                iterationVar, maxIterations);
    }

    /** 简单测试规则实现 */
    private static class TestRule implements Rule {
        private final String code;
        private final RuleResult result;

        TestRule(String code, RuleResult result) {
            this.code = code;
            this.result = result;
        }

        @Override
        public String getCode() { return code; }
        @Override
        public String getName() { return "测试规则-" + code; }
        @Override
        public String getCategory() { return "TEST"; }
        @Override
        public RuleResult evaluate(RuleContext context) { return result; }
    }

    // ==================== 工厂方法测试 ====================

    @Nested
    @DisplayName("工厂方法测试")
    class FactoryMethodTest {

        @Test
        @DisplayName("then - 创建 THEN 链并获取节点")
        void shouldCreateThenChain() {
            Rule r1 = triggeredRule("R001");
            Rule r2 = triggeredRule("R002");
            RuleChain chain = RuleChain.then(r1, r2);
            assertThat(chain.getChainType()).isEqualTo(RuleChainType.THEN);
            assertThat(chain.getNodes()).hasSize(2);
        }

        @Test
        @DisplayName("then - 跳过 null 规则")
        void shouldSkipNullRulesInThen() {
            Rule r1 = triggeredRule("R001");
            RuleChain chain = RuleChain.then(r1, null, null);
            assertThat(chain.getNodes()).hasSize(1);
        }

        @Test
        @DisplayName("then - 空数组创建空链")
        void shouldCreateEmptyThenChain() {
            RuleChain chain = RuleChain.then();
            assertThat(chain.getNodes()).isEmpty();
        }

        @Test
        @DisplayName("then - null 数组抛出 NPE")
        void shouldThrowNpeForNullThenArray() {
            assertThatThrownBy(() -> RuleChain.then((Rule[]) null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("rules");
        }

        @Test
        @DisplayName("when - 创建 WHEN 链")
        void shouldCreateWhenChain() {
            Rule r1 = triggeredRule("R001");
            Rule r2 = triggeredRule("R002");
            RuleChain chain = RuleChain.when(r1, r2);
            assertThat(chain.getChainType()).isEqualTo(RuleChainType.WHEN);
            assertThat(chain.getNodes()).hasSize(2);
        }

        @Test
        @DisplayName("when - 跳过 null 规则")
        void shouldSkipNullRulesInWhen() {
            Rule r1 = triggeredRule("R001");
            RuleChain chain = RuleChain.when(r1, null);
            assertThat(chain.getNodes()).hasSize(1);
        }

        @Test
        @DisplayName("when - null 数组抛出 NPE")
        void shouldThrowNpeForNullWhenArray() {
            assertThatThrownBy(() -> RuleChain.when((Rule[]) null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("ifThen - 创建 IF 链")
        void shouldCreateIfChain() {
            Rule action = triggeredRule("R001");
            RuleChain chain = RuleChain.ifThen("amount > 1000", action);
            assertThat(chain.getChainType()).isEqualTo(RuleChainType.IF);
            assertThat(chain.getConditionExpression()).isEqualTo("amount > 1000");
            assertThat(chain.getNodes()).hasSize(1);
        }

        @Test
        @DisplayName("ifThen - null 条件表达式抛出 NPE")
        void shouldThrowNpeForNullConditionInIfThen() {
            assertThatThrownBy(() -> RuleChain.ifThen(null, triggeredRule("R001")))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("ifThen - null 动作规则抛出 NPE")
        void shouldThrowNpeForNullActionInIfThen() {
            assertThatThrownBy(() -> RuleChain.ifThen("amount > 1000", null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("switchOn - 创建 SWITCH 链（无默认分支）")
        void shouldCreateSwitchChainWithoutDefault() {
            Map<String, Rule> branches = new LinkedHashMap<>();
            branches.put("A", triggeredRule("R001"));
            branches.put("B", triggeredRule("R002"));
            RuleChain chain = RuleChain.switchOn("type", branches);
            assertThat(chain.getChainType()).isEqualTo(RuleChainType.SWITCH);
            assertThat(chain.getBranchKey()).isEqualTo("type");
            assertThat(chain.getBranchMap()).hasSize(2);
            assertThat(chain.getDefaultBranch()).isNull();
        }

        @Test
        @DisplayName("switchOn - 创建 SWITCH 链（带默认分支）")
        void shouldCreateSwitchChainWithDefault() {
            Map<String, Rule> branches = new LinkedHashMap<>();
            branches.put("A", triggeredRule("R001"));
            RuleChain chain = RuleChain.switchOn("type", branches, triggeredRule("DEFAULT"));
            assertThat(chain.getBranchMap()).hasSize(1);
            assertThat(chain.getDefaultBranch()).isNotNull();
        }

        @Test
        @DisplayName("switchOn - null 默认分支")
        void shouldCreateSwitchChainWithNullDefault() {
            Map<String, Rule> branches = new LinkedHashMap<>();
            branches.put("A", triggeredRule("R001"));
            RuleChain chain = RuleChain.switchOn("type", branches, null);
            assertThat(chain.getDefaultBranch()).isNull();
        }

        @Test
        @DisplayName("switchOn - 跳过 null key/value")
        void shouldSkipNullEntriesInSwitch() {
            Map<String, Rule> branches = new LinkedHashMap<>();
            branches.put("A", triggeredRule("R001"));
            branches.put(null, triggeredRule("R002"));
            branches.put("B", null);
            RuleChain chain = RuleChain.switchOn("type", branches);
            assertThat(chain.getBranchMap()).hasSize(1);
        }

        @Test
        @DisplayName("switchOn - null branchKey 抛出 NPE")
        void shouldThrowNpeForNullBranchKey() {
            assertThatThrownBy(() -> RuleChain.switchOn(null, new LinkedHashMap<>()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("switchOn - null branches 抛出 NPE")
        void shouldThrowNpeForNullBranches() {
            assertThatThrownBy(() -> RuleChain.switchOn("type", null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("elif - 创建 ELIF 链（带 ELSE 分支）")
        void shouldCreateElifChainWithElse() {
            Map<String, Rule> branches = new LinkedHashMap<>();
            branches.put("a > 1", triggeredRule("R001"));
            branches.put("b > 2", triggeredRule("R002"));
            RuleChain chain = RuleChain.elif(branches, triggeredRule("ELSE"));
            assertThat(chain.getChainType()).isEqualTo(RuleChainType.ELIF);
            assertThat(chain.getElifBranches()).hasSize(2);
            assertThat(chain.getElseNode()).isNotNull();
        }

        @Test
        @DisplayName("elif - 创建 ELIF 链（无 ELSE 分支）")
        void shouldCreateElifChainWithoutElse() {
            Map<String, Rule> branches = new LinkedHashMap<>();
            branches.put("a > 1", triggeredRule("R001"));
            RuleChain chain = RuleChain.elif(branches, null);
            assertThat(chain.getElseNode()).isNull();
        }

        @Test
        @DisplayName("elif - 跳过 null key/value")
        void shouldSkipNullEntriesInElif() {
            Map<String, Rule> branches = new LinkedHashMap<>();
            branches.put("a > 1", triggeredRule("R001"));
            branches.put(null, triggeredRule("R002"));
            branches.put("b > 2", null);
            RuleChain chain = RuleChain.elif(branches, null);
            assertThat(chain.getElifBranches()).hasSize(1);
        }

        @Test
        @DisplayName("elif - null branches 抛出 NPE")
        void shouldThrowNpeForNullElifBranches() {
            assertThatThrownBy(() -> RuleChain.elif(null, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("forEach - 创建 FOR 链")
        void shouldCreateForChain() {
            Rule action = triggeredRule("R001");
            RuleChain chain = RuleChain.forEach("items", "item", action);
            assertThat(chain.getChainType()).isEqualTo(RuleChainType.FOR);
            assertThat(chain.getIterableExpression()).isEqualTo("items");
            assertThat(chain.getIterationVar()).isEqualTo("item");
            assertThat(chain.getNodes()).hasSize(1);
        }

        @Test
        @DisplayName("forEach - null iterableExpression 抛出 NPE")
        void shouldThrowNpeForNullIterableExpression() {
            assertThatThrownBy(() -> RuleChain.forEach(null, "item", triggeredRule("R001")))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("forEach - null iterationVar 抛出 NPE")
        void shouldThrowNpeForNullIterationVar() {
            assertThatThrownBy(() -> RuleChain.forEach("items", null, triggeredRule("R001")))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("forEach - null actionRule 抛出 NPE")
        void shouldThrowNpeForNullActionRuleInFor() {
            assertThatThrownBy(() -> RuleChain.forEach("items", "item", null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("whileDo - 默认最大迭代次数为 100")
        void shouldCreateWhileChainWithDefaultMaxIterations() {
            Rule action = triggeredRule("R001");
            RuleChain chain = RuleChain.whileDo("amount > 0", action);
            assertThat(chain.getChainType()).isEqualTo(RuleChainType.WHILE);
            assertThat(chain.getConditionExpression()).isEqualTo("amount > 0");
            assertThat(chain.getMaxIterations()).isEqualTo(100);
        }

        @Test
        @DisplayName("whileDo - 自定义最大迭代次数")
        void shouldCreateWhileChainWithCustomMaxIterations() {
            RuleChain chain = RuleChain.whileDo("amount > 0", triggeredRule("R001"), 5);
            assertThat(chain.getMaxIterations()).isEqualTo(5);
        }

        @Test
        @DisplayName("whileDo - null 条件表达式抛出 NPE")
        void shouldThrowNpeForNullConditionInWhileDo() {
            assertThatThrownBy(() -> RuleChain.whileDo(null, triggeredRule("R001")))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("whileDo - null 动作规则抛出 NPE")
        void shouldThrowNpeForNullActionInWhileDo() {
            assertThatThrownBy(() -> RuleChain.whileDo("amount > 0", null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("whileDo - maxIterations=0 抛出 IllegalArgumentException")
        void shouldThrowForZeroMaxIterations() {
            assertThatThrownBy(() -> RuleChain.whileDo("amount > 0", triggeredRule("R001"), 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("whileDo - maxIterations<0 抛出 IllegalArgumentException")
        void shouldThrowForNegativeMaxIterations() {
            assertThatThrownBy(() -> RuleChain.whileDo("amount > 0", triggeredRule("R001"), -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("breakChain - 创建 BREAK 链")
        void shouldCreateBreakChain() {
            RuleChain chain = RuleChain.breakChain();
            assertThat(chain.getChainType()).isEqualTo(RuleChainType.BREAK);
        }
    }

    // ==================== THEN 串行执行测试 ====================

    @Nested
    @DisplayName("THEN 串行执行测试")
    class ThenEvaluateTest {

        @Test
        @DisplayName("全部触发 - 收集所有触发结果")
        void shouldCollectAllTriggeredResults() {
            RuleChain chain = RuleChain.then(triggeredRule("R001"), triggeredRule("R002"));
            RuleContext ctx = context(new HashMap<>());
            List<RuleResult> results = chain.evaluate(ctx, mockEvaluator());
            assertThat(results).hasSize(2);
            assertThat(results).extracting(RuleResult::getRuleCode).contains("R001", "R002");
        }

        @Test
        @DisplayName("混合触发和未触发 - 仅收集触发结果")
        void shouldCollectOnlyTriggeredResults() {
            RuleChain chain = RuleChain.then(triggeredRule("R001"), notTriggeredRule("R002"), triggeredRule("R003"));
            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), mockEvaluator());
            assertThat(results).hasSize(2);
            assertThat(results).extracting(RuleResult::getRuleCode).contains("R001", "R003");
        }

        @Test
        @DisplayName("规则异常被隔离 - 不影响其他规则")
        void shouldIsolateRuleException() {
            RuleChain chain = RuleChain.then(triggeredRule("R001"), errorRule("R002"), triggeredRule("R003"));
            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), mockEvaluator());
            // R002 异常被跳过，R001 和 R003 正常执行
            assertThat(results).hasSize(2);
            assertThat(results).extracting(RuleResult::getRuleCode).contains("R001", "R003");
        }

        @Test
        @DisplayName("带统计记录器 - 记录每条规则评估")
        void shouldRecordStats() {
            StatsRecorder recorder = mock(StatsRecorder.class);
            RuleChain chain = RuleChain.then(triggeredRule("R001"), notTriggeredRule("R002"));
            chain.evaluate(context(new HashMap<>()), mockEvaluator(), recorder);
            verify(recorder, times(1)).record(eq("R001"), eq(true), eq(false), any(Long.class));
            verify(recorder, times(1)).record(eq("R002"), eq(false), eq(false), any(Long.class));
        }

        @Test
        @DisplayName("异常规则 - 统计记录 error=true")
        void shouldRecordErrorInStats() {
            StatsRecorder recorder = mock(StatsRecorder.class);
            RuleChain chain = RuleChain.then(errorRule("R001"));
            chain.evaluate(context(new HashMap<>()), mockEvaluator(), recorder);
            verify(recorder, times(1)).record(eq("R001"), eq(false), eq(true), any(Long.class));
        }

        @Test
        @DisplayName("空 THEN 链 - 返回空结果")
        void shouldReturnEmptyForEmptyThenChain() {
            RuleChain chain = RuleChain.then();
            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), mockEvaluator());
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("nodes=null 防御性分支 - 返回空结果")
        void shouldReturnEmptyForNullNodesInThen() throws Exception {
            RuleChain chain = createChainViaReflection(RuleChainType.THEN, null,
                    null, null, null, null, null, null, null, null, 0);
            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), mockEvaluator());
            assertThat(results).isEmpty();
        }
    }

    // ==================== WHEN 并行执行测试 ====================

    @Nested
    @DisplayName("WHEN 并行执行测试")
    class WhenEvaluateTest {

        @Test
        @DisplayName("并行执行收集全部触发结果")
        void shouldCollectResultsInParallel() {
            RuleChain chain = RuleChain.when(triggeredRule("R001"), triggeredRule("R002"));
            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), mockEvaluator());
            assertThat(results).hasSize(2);
            assertThat(results).extracting(RuleResult::getRuleCode).containsExactlyInAnyOrder("R001", "R002");
        }

        @Test
        @DisplayName("空 WHEN 链 - 返回空结果")
        void shouldReturnEmptyForEmptyWhenChain() {
            RuleChain chain = RuleChain.when();
            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), mockEvaluator());
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("自定义线程池执行")
        void shouldExecuteWithCustomExecutor() {
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                RuleChain chain = RuleChain.when(triggeredRule("R001"), triggeredRule("R002"));
                List<RuleResult> results = chain.evaluate(context(new HashMap<>()), mockEvaluator(),
                        null, executor, 0);
                assertThat(results).hasSize(2);
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        @DisplayName("超时未触发 - 正常完成")
        void shouldCompleteWithinTimeout() {
            RuleChain chain = RuleChain.when(triggeredRule("R001"));
            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), mockEvaluator(),
                    null, null, 5000);
            assertThat(results).hasSize(1);
        }

        @Test
        @DisplayName("超时触发 - TimeoutException 被捕获")
        void shouldHandleTimeoutException() {
            RuleChain chain = RuleChain.when(slowRule("SLOW", 2000));
            // 超时 100ms，规则需要 2000ms
            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), mockEvaluator(),
                    null, null, 100);
            // 超时后结果为空（慢规则未完成）
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("并行执行异常 - Error 被隔离")
        void shouldHandleParallelError() {
            RuleChain chain = RuleChain.when(errorThrowingRule("R001"));
            // timeoutMs=0 使用 join()，CompletionException 被 catch(Exception) 捕获
            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), mockEvaluator(),
                    null, null, 0);
            // 异常 future 被跳过，结果为空
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("带统计记录器的并行执行")
        void shouldRecordStatsInParallel() {
            StatsRecorder recorder = mock(StatsRecorder.class);
            RuleChain chain = RuleChain.when(triggeredRule("R001"), notTriggeredRule("R002"));
            chain.evaluate(context(new HashMap<>()), mockEvaluator(), recorder, null, 0);
            verify(recorder, times(1)).record(eq("R001"), eq(true), eq(false), any(Long.class));
            verify(recorder, times(1)).record(eq("R002"), eq(false), eq(false), any(Long.class));
        }

        @Test
        @DisplayName("nodes=null 防御性分支 - 返回空结果")
        void shouldReturnEmptyForNullNodesInWhen() throws Exception {
            RuleChain chain = createChainViaReflection(RuleChainType.WHEN, null,
                    null, null, null, null, null, null, null, null, 0);
            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), mockEvaluator());
            assertThat(results).isEmpty();
        }
    }

    // ==================== IF 条件执行测试 ====================

    @Nested
    @DisplayName("IF 条件执行测试")
    class IfEvaluateTest {

        @Test
        @DisplayName("条件为 true - 执行动作规则")
        void shouldExecuteWhenConditionTrue() {
            ExpressionEvaluator evaluator = mockEvaluator();
            when(evaluator.evalBoolean(eq("amount > 1000"), any())).thenReturn(true);
            RuleChain chain = RuleChain.ifThen("amount > 1000", triggeredRule("R001"));
            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), evaluator);
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("R001");
        }

        @Test
        @DisplayName("条件为 false - 跳过动作规则")
        void shouldSkipWhenConditionFalse() {
            ExpressionEvaluator evaluator = mockEvaluator();
            when(evaluator.evalBoolean(eq("amount > 1000"), any())).thenReturn(false);
            RuleChain chain = RuleChain.ifThen("amount > 1000", triggeredRule("R001"));
            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), evaluator);
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("evaluator=null - 跳过求值返回空")
        void shouldReturnEmptyWhenEvaluatorNull() {
            RuleChain chain = RuleChain.ifThen("amount > 1000", triggeredRule("R001"));
            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), null);
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("nodes=null 防御性分支 - 条件 true 但无节点")
        void shouldHandleNullNodesInIf() throws Exception {
            ExpressionEvaluator evaluator = mockEvaluator();
            when(evaluator.evalBoolean(anyString(), any())).thenReturn(true);
            RuleChain chain = createChainViaReflection(RuleChainType.IF, null,
                    "amount > 1000", null, null, null, null, null, null, null, 0);
            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), evaluator);
            assertThat(results).isEmpty();
        }
    }

    // ==================== ELIF 多分支条件测试 ====================

    @Nested
    @DisplayName("ELIF 多分支条件测试")
    class ElifEvaluateTest {

        @Test
        @DisplayName("第一个分支匹配 - 执行第一个")
        void shouldExecuteFirstMatchingBranch() {
            ExpressionEvaluator evaluator = mockEvaluator();
            when(evaluator.evalBoolean(eq("a > 1"), any())).thenReturn(true);
            Map<String, Rule> branches = new LinkedHashMap<>();
            branches.put("a > 1", triggeredRule("R001"));
            branches.put("b > 2", triggeredRule("R002"));
            RuleChain chain = RuleChain.elif(branches, triggeredRule("ELSE"));
            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), evaluator);
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("R001");
        }

        @Test
        @DisplayName("第二个分支匹配 - 执行第二个")
        void shouldExecuteSecondMatchingBranch() {
            ExpressionEvaluator evaluator = mockEvaluator();
            when(evaluator.evalBoolean(eq("a > 1"), any())).thenReturn(false);
            when(evaluator.evalBoolean(eq("b > 2"), any())).thenReturn(true);
            Map<String, Rule> branches = new LinkedHashMap<>();
            branches.put("a > 1", triggeredRule("R001"));
            branches.put("b > 2", triggeredRule("R002"));
            RuleChain chain = RuleChain.elif(branches, triggeredRule("ELSE"));
            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), evaluator);
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("R002");
        }

        @Test
        @DisplayName("无匹配 - 执行 ELSE 分支")
        void shouldExecuteElseWhenNoMatch() {
            ExpressionEvaluator evaluator = mockEvaluator();
            when(evaluator.evalBoolean(anyString(), any())).thenReturn(false);
            Map<String, Rule> branches = new LinkedHashMap<>();
            branches.put("a > 1", triggeredRule("R001"));
            RuleChain chain = RuleChain.elif(branches, triggeredRule("ELSE"));
            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), evaluator);
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("ELSE");
        }

        @Test
        @DisplayName("无匹配且无 ELSE - 返回空")
        void shouldReturnEmptyWhenNoMatchAndNoElse() {
            ExpressionEvaluator evaluator = mockEvaluator();
            when(evaluator.evalBoolean(anyString(), any())).thenReturn(false);
            Map<String, Rule> branches = new LinkedHashMap<>();
            branches.put("a > 1", triggeredRule("R001"));
            RuleChain chain = RuleChain.elif(branches, null);
            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), evaluator);
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("evaluator=null - 跳过求值返回空")
        void shouldReturnEmptyWhenEvaluatorNull() {
            Map<String, Rule> branches = new LinkedHashMap<>();
            branches.put("a > 1", triggeredRule("R001"));
            RuleChain chain = RuleChain.elif(branches, triggeredRule("ELSE"));
            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), null);
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("条件求值异常 - 跳过该分支")
        void shouldSkipBranchOnEvaluationException() {
            ExpressionEvaluator evaluator = mockEvaluator();
            when(evaluator.evalBoolean(eq("a > 1"), any())).thenThrow(new RuntimeException("eval error"));
            when(evaluator.evalBoolean(eq("b > 2"), any())).thenReturn(true);
            Map<String, Rule> branches = new LinkedHashMap<>();
            branches.put("a > 1", triggeredRule("R001"));
            branches.put("b > 2", triggeredRule("R002"));
            RuleChain chain = RuleChain.elif(branches, null);
            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), evaluator);
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("R002");
        }

        @Test
        @DisplayName("elifBranches=null 防御性分支 - 执行 ELSE")
        void shouldHandleNullElifBranches() throws Exception {
            RuleChain chain = createChainViaReflection(RuleChainType.ELIF, null,
                    null, null, null, null, null,
                    RuleNode.of(triggeredRule("ELSE")), null, null, 0);
            ExpressionEvaluator evaluator = mockEvaluator();
            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), evaluator);
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("ELSE");
        }
    }

    // ==================== SWITCH 分支选择测试 ====================

    @Nested
    @DisplayName("SWITCH 分支选择测试")
    class SwitchEvaluateTest {

        @Test
        @DisplayName("分支 key 匹配 - 执行对应分支")
        void shouldExecuteMatchingBranch() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("type", "A");
            Map<String, Rule> branches = new LinkedHashMap<>();
            branches.put("A", triggeredRule("R001"));
            branches.put("B", triggeredRule("R002"));
            RuleChain chain = RuleChain.switchOn("type", branches);
            List<RuleResult> results = chain.evaluate(context(facts), mockEvaluator());
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("R001");
        }

        @Test
        @DisplayName("分支 key 不匹配 - 执行默认分支")
        void shouldExecuteDefaultWhenNoMatch() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("type", "C");
            Map<String, Rule> branches = new LinkedHashMap<>();
            branches.put("A", triggeredRule("R001"));
            RuleChain chain = RuleChain.switchOn("type", branches, triggeredRule("DEFAULT"));
            List<RuleResult> results = chain.evaluate(context(facts), mockEvaluator());
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("DEFAULT");
        }

        @Test
        @DisplayName("分支 key 不匹配且无默认 - 返回空")
        void shouldReturnEmptyWhenNoMatchAndNoDefault() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("type", "C");
            Map<String, Rule> branches = new LinkedHashMap<>();
            branches.put("A", triggeredRule("R001"));
            RuleChain chain = RuleChain.switchOn("type", branches);
            List<RuleResult> results = chain.evaluate(context(facts), mockEvaluator());
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("key 为 null - 执行默认分支")
        void shouldExecuteDefaultWhenKeyNull() {
            Map<String, Object> facts = new HashMap<>();
            // type 不在 facts 中，getFacts().get("type") 返回 null
            Map<String, Rule> branches = new LinkedHashMap<>();
            branches.put("A", triggeredRule("R001"));
            RuleChain chain = RuleChain.switchOn("type", branches, triggeredRule("DEFAULT"));
            List<RuleResult> results = chain.evaluate(context(facts), mockEvaluator());
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("DEFAULT");
        }

        @Test
        @DisplayName("key 为 null 且无默认 - 返回空")
        void shouldReturnEmptyWhenKeyNullAndNoDefault() {
            Map<String, Object> facts = new HashMap<>();
            Map<String, Rule> branches = new LinkedHashMap<>();
            branches.put("A", triggeredRule("R001"));
            RuleChain chain = RuleChain.switchOn("type", branches);
            List<RuleResult> results = chain.evaluate(context(facts), mockEvaluator());
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("branchMap=null 防御性分支 - 返回空")
        void shouldHandleNullBranchMap() throws Exception {
            RuleChain chain = createChainViaReflection(RuleChainType.SWITCH, null,
                    null, "type", null, null, null, null, null, null, 0);
            Map<String, Object> facts = new HashMap<>();
            facts.put("type", "A");
            List<RuleResult> results = chain.evaluate(context(facts), mockEvaluator());
            assertThat(results).isEmpty();
        }
    }

    // ==================== FOR 循环测试 ====================

    @Nested
    @DisplayName("FOR 循环测试")
    class ForEvaluateTest {

        @Test
        @DisplayName("正常遍历 - 对每个元素执行规则")
        void shouldIterateOverCollection() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("items", Arrays.asList("a", "b", "c"));
            AtomicInteger counter = new AtomicInteger(0);
            RuleChain chain = RuleChain.forEach("items", "item", countingRule("R001", counter));
            List<RuleResult> results = chain.evaluate(context(facts), mockEvaluator());
            assertThat(counter.get()).isEqualTo(3);
            assertThat(results).isEmpty(); // countingRule 返回未触发
        }

        @Test
        @DisplayName("BREAK 终止迭代")
        void shouldBreakIteration() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("items", Arrays.asList("a", "b", "c"));
            RuleChain chain = RuleChain.forEach("items", "item", breakRule());
            List<RuleResult> results = chain.evaluate(context(facts), mockEvaluator());
            // BREAK 在第一次迭代就终止，BREAK 结果本身不加入 results
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("非可迭代对象 - 返回空")
        void shouldReturnEmptyForNonIterable() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("items", "not a list");
            RuleChain chain = RuleChain.forEach("items", "item", triggeredRule("R001"));
            List<RuleResult> results = chain.evaluate(context(facts), mockEvaluator());
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("null 可迭代对象 - 返回空")
        void shouldReturnEmptyForNullIterable() {
            Map<String, Object> facts = new HashMap<>();
            // items 不在 facts 中
            RuleChain chain = RuleChain.forEach("items", "item", triggeredRule("R001"));
            List<RuleResult> results = chain.evaluate(context(facts), mockEvaluator());
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("空集合 - 0 次迭代")
        void shouldIterateZeroTimesForEmptyCollection() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("items", Collections.emptyList());
            AtomicInteger counter = new AtomicInteger(0);
            RuleChain chain = RuleChain.forEach("items", "item", countingRule("R001", counter));
            List<RuleResult> results = chain.evaluate(context(facts), mockEvaluator());
            assertThat(counter.get()).isEqualTo(0);
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("iterableExpression=null 防御性分支 - 返回空")
        void shouldHandleNullIterableExpression() throws Exception {
            RuleChain chain = createChainViaReflection(RuleChainType.FOR,
                    Collections.singletonList(RuleNode.of(triggeredRule("R001"))),
                    null, null, null, null, null, null, null, "item", 0);
            Map<String, Object> facts = new HashMap<>();
            facts.put("items", Arrays.asList("a", "b"));
            List<RuleResult> results = chain.evaluate(context(facts), mockEvaluator());
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("nodes=null 防御性分支 - 迭代但不执行")
        void shouldHandleNullNodesInFor() throws Exception {
            RuleChain chain = createChainViaReflection(RuleChainType.FOR, null,
                    null, null, null, null, null, null, "items", "item", 0);
            Map<String, Object> facts = new HashMap<>();
            facts.put("items", Arrays.asList("a", "b"));
            List<RuleResult> results = chain.evaluate(context(facts), mockEvaluator());
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("FOR 中嵌套 BREAK 子链 - 终止迭代")
        void shouldBreakIterationViaNestedBreakChain() throws Exception {
            Map<String, Object> facts = new HashMap<>();
            facts.put("items", Arrays.asList("a", "b", "c"));
            // 创建包含 BREAK 子链节点的 FOR 链
            RuleNode breakChainNode = RuleNode.of(RuleChain.breakChain());
            RuleChain chain = createChainViaReflection(RuleChainType.FOR,
                    Collections.singletonList(breakChainNode),
                    null, null, null, null, null, null, "items", "item", 0);
            List<RuleResult> results = chain.evaluate(context(facts), mockEvaluator());
            assertThat(results).isEmpty();
        }
    }

    // ==================== WHILE 循环测试 ====================

    @Nested
    @DisplayName("WHILE 循环测试")
    class WhileEvaluateTest {

        @Test
        @DisplayName("条件变为 false - 正常终止")
        void shouldTerminateWhenConditionBecomesFalse() {
            ExpressionEvaluator evaluator = mockEvaluator();
            // 第一次 true，第二次 false
            when(evaluator.evalBoolean(eq("x > 0"), any()))
                    .thenReturn(true, false);
            AtomicInteger counter = new AtomicInteger(0);
            RuleChain chain = RuleChain.whileDo("x > 0", countingRule("R001", counter));
            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), evaluator);
            assertThat(counter.get()).isEqualTo(1);
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("BREAK 终止循环")
        void shouldBreakWhileLoop() {
            ExpressionEvaluator evaluator = mockEvaluator();
            when(evaluator.evalBoolean(anyString(), any())).thenReturn(true);
            RuleChain chain = RuleChain.whileDo("x > 0", breakRule());
            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), evaluator);
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("达到最大迭代次数 - 终止")
        void shouldTerminateAtMaxIterations() {
            ExpressionEvaluator evaluator = mockEvaluator();
            when(evaluator.evalBoolean(anyString(), any())).thenReturn(true);
            AtomicInteger counter = new AtomicInteger(0);
            RuleChain chain = RuleChain.whileDo("x > 0", countingRule("R001", counter), 3);
            chain.evaluate(context(new HashMap<>()), evaluator);
            assertThat(counter.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("条件始终为 false - 0 次迭代")
        void shouldNotIterateWhenConditionAlwaysFalse() {
            ExpressionEvaluator evaluator = mockEvaluator();
            when(evaluator.evalBoolean(anyString(), any())).thenReturn(false);
            AtomicInteger counter = new AtomicInteger(0);
            RuleChain chain = RuleChain.whileDo("x > 0", countingRule("R001", counter));
            chain.evaluate(context(new HashMap<>()), evaluator);
            assertThat(counter.get()).isEqualTo(0);
        }

        @Test
        @DisplayName("evaluator=null - 跳过求值返回空")
        void shouldReturnEmptyWhenEvaluatorNull() {
            RuleChain chain = RuleChain.whileDo("x > 0", triggeredRule("R001"));
            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), null);
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("条件求值异常 - 终止循环")
        void shouldBreakOnConditionException() {
            ExpressionEvaluator evaluator = mockEvaluator();
            when(evaluator.evalBoolean(anyString(), any())).thenThrow(new RuntimeException("eval error"));
            AtomicInteger counter = new AtomicInteger(0);
            RuleChain chain = RuleChain.whileDo("x > 0", countingRule("R001", counter));
            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), evaluator);
            assertThat(counter.get()).isEqualTo(0);
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("WHILE 中嵌套 BREAK 子链 - 终止循环")
        void shouldBreakWhileLoopViaNestedBreakChain() throws Exception {
            ExpressionEvaluator evaluator = mockEvaluator();
            when(evaluator.evalBoolean(anyString(), any())).thenReturn(true);
            RuleNode breakChainNode = RuleNode.of(RuleChain.breakChain());
            RuleChain chain = createChainViaReflection(RuleChainType.WHILE,
                    Collections.singletonList(breakChainNode),
                    "x > 0", null, null, null, null, null, null, null, 100);
            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), evaluator);
            assertThat(results).isEmpty();
        }
    }

    // ==================== BREAK 终止测试 ====================

    @Nested
    @DisplayName("BREAK 终止测试")
    class BreakEvaluateTest {

        @Test
        @DisplayName("BREAK 链返回 BREAK 标记结果")
        void shouldReturnBreakResult() {
            RuleChain chain = RuleChain.breakChain();
            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), mockEvaluator());
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo(RuleResult.BREAK_CODE);
            assertThat(results.get(0).isTriggered()).isTrue();
            assertThat(results.get(0).getSeverity()).isEqualTo(RuleSeverity.INFO);
            assertThat(results.get(0).getTitle()).isEqualTo("BREAK 终止循环");
        }
    }

    // ==================== 嵌套组合测试 ====================

    @Nested
    @DisplayName("嵌套组合测试")
    class NestedChainTest {

        @Test
        @DisplayName("THEN+WHEN+IF 嵌套组合")
        void shouldEvaluateNestedThenWhenIf() throws Exception {
            ExpressionEvaluator evaluator = mockEvaluator();
            when(evaluator.evalBoolean(eq("amount > 1000"), any())).thenReturn(true);

            // 子链：IF 条件执行
            RuleChain ifChain = RuleChain.ifThen("amount > 1000", triggeredRule("IF_R"));
            // 子链：WHEN 并行执行
            RuleChain whenChain = RuleChain.when(triggeredRule("W1"), triggeredRule("W2"));
            // 子链：THEN 串行执行
            RuleChain thenChain = RuleChain.then(triggeredRule("T1"), triggeredRule("T2"));

            // 用 CHAIN 节点包装子链，放入 THEN 链
            List<RuleNode> nodes = Arrays.asList(
                    RuleNode.of(ifChain),
                    RuleNode.of(whenChain),
                    RuleNode.of(thenChain));
            RuleChain nestedChain = createChainViaReflection(RuleChainType.THEN, nodes,
                    null, null, null, null, null, null, null, null, 0);

            List<RuleResult> results = nestedChain.evaluate(context(new HashMap<>()), evaluator);
            // IF_R(1) + W1(1) + W2(1) + T1(1) + T2(1) = 5
            assertThat(results).hasSize(5);
            assertThat(results).extracting(RuleResult::getRuleCode)
                    .contains("IF_R", "W1", "W2", "T1", "T2");
        }

        @Test
        @DisplayName("CHAIN 节点 - 子链为 null 时不执行")
        void shouldHandleNullSubChain() throws Exception {
            RuleNode nullChainNode = mock(RuleNode.class);
            when(nullChainNode.getNodeType()).thenReturn(RuleNode.NodeType.CHAIN);
            when(nullChainNode.getChain()).thenReturn(null);

            // 通过反射调用私有构造方法创建 THEN 链，nodes 包含 mock 的 CHAIN 节点
            // （避免修改 final 字段；Java 21+ 已禁止通过反射改 modifiers）
            RuleChain chain = createChainViaReflection(RuleChainType.THEN,
                    Collections.singletonList(nullChainNode),
                    null, null, null, null, null, null, null, null, 0);

            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), mockEvaluator());
            // 子链为 null，不抛异常，返回空
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("GROUP 节点 - 依次评估全部子节点")
        void shouldEvaluateGroupNode() throws Exception {
            RuleNode child1 = RuleNode.of(triggeredRule("G1"));
            RuleNode child2 = RuleNode.of(triggeredRule("G2"));
            RuleNode groupNode = RuleNode.group(Arrays.asList(child1, child2));

            RuleChain chain = createChainViaReflection(RuleChainType.THEN,
                    Collections.singletonList(groupNode),
                    null, null, null, null, null, null, null, null, 0);

            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), mockEvaluator());
            assertThat(results).hasSize(2);
            assertThat(results).extracting(RuleResult::getRuleCode).contains("G1", "G2");
        }

        @Test
        @DisplayName("GROUP 节点 - children 为 null 时不执行")
        void shouldHandleNullChildrenInGroup() throws Exception {
            RuleNode nullChildrenGroupNode = mock(RuleNode.class);
            when(nullChildrenGroupNode.getNodeType()).thenReturn(RuleNode.NodeType.GROUP);
            when(nullChildrenGroupNode.getChildren()).thenReturn(null);

            // 通过反射调用私有构造方法创建 THEN 链，nodes 包含 mock 的 GROUP 节点
            RuleChain chain = createChainViaReflection(RuleChainType.THEN,
                    Collections.singletonList(nullChildrenGroupNode),
                    null, null, null, null, null, null, null, null, 0);

            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), mockEvaluator());
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("evaluateNode - node 为 null 时返回空")
        void shouldHandleNullNode() throws Exception {
            List<RuleNode> nodesWithNull = new ArrayList<>();
            nodesWithNull.add(null);
            RuleChain chain = createChainViaReflection(RuleChainType.THEN,
                    Collections.unmodifiableList(nodesWithNull),
                    null, null, null, null, null, null, null, null, 0);
            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), mockEvaluator());
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("evaluateNode - 外层异常被捕获")
        void shouldCatchOuterExceptionInEvaluateNode() throws Exception {
            RuleNode throwingNode = mock(RuleNode.class);
            // evaluateNode 的 try 块首次调用 getNodeType() 抛异常；
            // catch 块日志记录会再次调用 getNodeType()，需返回有效值避免异常逃逸
            when(throwingNode.getNodeType())
                    .thenThrow(new RuntimeException("node error"))
                    .thenReturn(RuleNode.NodeType.SINGLE);

            // 通过反射调用私有构造方法创建 THEN 链，nodes 包含抛异常的 mock 节点
            RuleChain chain = createChainViaReflection(RuleChainType.THEN,
                    Collections.singletonList(throwingNode),
                    null, null, null, null, null, null, null, null, 0);

            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), mockEvaluator());
            // 异常被外层 catch 捕获，返回空
            assertThat(results).isEmpty();
        }
    }

    // ==================== evaluate 方法委托测试 ====================

    @Nested
    @DisplayName("evaluate 方法委托测试")
    class EvaluateDelegationTest {

        @Test
        @DisplayName("2-param evaluate 委托到 3-param")
        void twoParamShouldDelegateToThreeParam() {
            RuleChain chain = RuleChain.then(triggeredRule("R001"));
            // 2-param 内部传 statsRecorder=null
            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), mockEvaluator());
            assertThat(results).hasSize(1);
        }

        @Test
        @DisplayName("3-param evaluate 委托到 5-param")
        void threeParamShouldDelegateToFiveParam() {
            RuleChain chain = RuleChain.then(triggeredRule("R001"));
            StatsRecorder recorder = mock(StatsRecorder.class);
            // 3-param 内部传 executor=null, timeoutMs=0
            List<RuleResult> results = chain.evaluate(context(new HashMap<>()), mockEvaluator(), recorder);
            assertThat(results).hasSize(1);
            verify(recorder, times(1)).record(eq("R001"), eq(true), eq(false), any(Long.class));
        }

        @Test
        @DisplayName("null context 抛出 NPE")
        void shouldThrowNpeForNullContext() {
            RuleChain chain = RuleChain.then(triggeredRule("R001"));
            assertThatThrownBy(() -> chain.evaluate(null, mockEvaluator()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("context");
        }
    }

    // ==================== Getter 方法测试 ====================

    @Nested
    @DisplayName("Getter 方法测试")
    class GetterTest {

        @Test
        @DisplayName("THEN 链 getter")
        void shouldGetThenChainProperties() {
            RuleChain chain = RuleChain.then(triggeredRule("R001"));
            assertThat(chain.getChainType()).isEqualTo(RuleChainType.THEN);
            assertThat(chain.getNodes()).hasSize(1);
            assertThat(chain.getConditionExpression()).isNull();
            assertThat(chain.getBranchKey()).isNull();
            assertThat(chain.getBranchMap()).isNull();
            assertThat(chain.getDefaultBranch()).isNull();
            assertThat(chain.getElifBranches()).isNull();
            assertThat(chain.getElseNode()).isNull();
            assertThat(chain.getIterableExpression()).isNull();
            assertThat(chain.getIterationVar()).isNull();
            assertThat(chain.getMaxIterations()).isEqualTo(0);
        }

        @Test
        @DisplayName("IF 链 getter")
        void shouldGetIfChainProperties() {
            RuleChain chain = RuleChain.ifThen("amount > 1000", triggeredRule("R001"));
            assertThat(chain.getChainType()).isEqualTo(RuleChainType.IF);
            assertThat(chain.getConditionExpression()).isEqualTo("amount > 1000");
            assertThat(chain.getNodes()).hasSize(1);
        }

        @Test
        @DisplayName("SWITCH 链 getter")
        void shouldGetSwitchChainProperties() {
            Map<String, Rule> branches = new LinkedHashMap<>();
            branches.put("A", triggeredRule("R001"));
            RuleChain chain = RuleChain.switchOn("type", branches, triggeredRule("DEFAULT"));
            assertThat(chain.getBranchKey()).isEqualTo("type");
            assertThat(chain.getBranchMap()).hasSize(1);
            assertThat(chain.getDefaultBranch()).isNotNull();
        }

        @Test
        @DisplayName("ELIF 链 getter")
        void shouldGetElifChainProperties() {
            Map<String, Rule> branches = new LinkedHashMap<>();
            branches.put("a > 1", triggeredRule("R001"));
            RuleChain chain = RuleChain.elif(branches, triggeredRule("ELSE"));
            assertThat(chain.getElifBranches()).hasSize(1);
            assertThat(chain.getElseNode()).isNotNull();
        }

        @Test
        @DisplayName("FOR 链 getter")
        void shouldGetForChainProperties() {
            RuleChain chain = RuleChain.forEach("items", "item", triggeredRule("R001"));
            assertThat(chain.getIterableExpression()).isEqualTo("items");
            assertThat(chain.getIterationVar()).isEqualTo("item");
        }

        @Test
        @DisplayName("WHILE 链 getter")
        void shouldGetWhileChainProperties() {
            RuleChain chain = RuleChain.whileDo("x > 0", triggeredRule("R001"), 50);
            assertThat(chain.getMaxIterations()).isEqualTo(50);
            assertThat(chain.getConditionExpression()).isEqualTo("x > 0");
        }
    }
}
