package com.njydsz.pmis.literule.server.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.server.core.InferenceEngine.InferenceResult;
import com.njydsz.pmis.literule.server.core.InferenceEngine.InferenceRound;
import com.njydsz.pmis.literule.server.expr.ExpressionEvaluator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link InferenceEngine} 单元测试：覆盖前向链推理、推理链执行、事实变更检测、
 * 最大轮次限制、规则异常隔离等核心逻辑。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
@DisplayName("前向链推理引擎 InferenceEngine 测试")
class InferenceEngineTest {

    private ExpressionEvaluator evaluator;
    private InferenceEngine engine;

    @BeforeEach
    void setUp() {
        evaluator = mock(ExpressionEvaluator.class);
        engine = new InferenceEngine(evaluator);
    }

    /**
     * 构造一个 Mockito mock 的 Rule，预设基本元数据，evaluate 默认返回未触发。
     */
    private Rule mockRule(String code, String name) {
        Rule rule = mock(Rule.class);
        when(rule.getCode()).thenReturn(code);
        when(rule.getName()).thenReturn(name);
        when(rule.getCategory()).thenReturn("TEST");
        when(rule.getPriority()).thenReturn(100);
        when(rule.evaluate(any(RuleContext.class)))
                .thenReturn(RuleResult.notTriggered(code));
        return rule;
    }

    private RuleResult triggeredResult(String code) {
        return RuleResult.triggered(code, code, "TEST", RuleSeverity.INFO, "触发", "触发");
    }

    private RuleResult notTriggeredResult(String code) {
        return RuleResult.notTriggered(code);
    }

    @Nested
    @DisplayName("构造器与参数校验")
    class ConstructorCases {

        @Test
        @DisplayName("evaluator 为 null 时抛 NullPointerException")
        void shouldThrowWhenEvaluatorNull() {
            assertThatThrownBy(() -> new InferenceEngine(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("evaluator");
        }

        @Test
        @DisplayName("infer 传入 null facts 时抛 NullPointerException")
        void shouldThrowWhenFactsNull() {
            assertThatThrownBy(() -> engine.infer(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("facts");
        }
    }

    @Nested
    @DisplayName("事实注入和推理")
    class BasicInferenceCases {

        @Test
        @DisplayName("注入事实后推理：触发的规则出现在结果中")
        void shouldIncludeTriggeredRuleInResult() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("amount", 1500);

            Rule rule = mockRule("R001", "规则1");
            when(rule.evaluate(any(RuleContext.class)))
                    .thenReturn(triggeredResult("R001"));
            engine.register(rule);

            // 限制 1 轮，避免无限循环
            engine.setMaxRounds(1);

            InferenceResult result = engine.infer(facts);

            assertThat(result.getAllTriggeredRules()).containsExactly("R001");
            assertThat(result.getTotalRounds()).isEqualTo(1);
        }

        @Test
        @DisplayName("推理结果保留初始事实")
        void shouldRetainInitialFactsInResult() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("amount", 1500);
            facts.put("name", "test");

            Rule rule = mockRule("R001", "规则1");
            when(rule.evaluate(any(RuleContext.class)))
                    .thenReturn(triggeredResult("R001"));
            engine.register(rule);
            engine.setMaxRounds(1);

            InferenceResult result = engine.infer(facts);

            Map<String, Object> resultFacts = result.getFacts();
            assertThat(resultFacts.get("amount")).isEqualTo(1500);
            assertThat(resultFacts.get("name")).isEqualTo("test");
        }

        @Test
        @DisplayName("无规则触发时立即收敛，totalRounds=1")
        void shouldConvergeImmediatelyWhenNoRuleTriggered() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("v", 1);

            Rule rule = mockRule("R001", "未触发规则");
            // evaluate 默认返回 notTriggered
            engine.register(rule);

            InferenceResult result = engine.infer(facts);

            assertThat(result.getTotalRounds()).isEqualTo(1);
            assertThat(result.getAllTriggeredRules()).isEmpty();
        }

        @Test
        @DisplayName("无规则注册时推理返回空轨迹")
        void shouldReturnEmptyTraceWhenNoRules() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("v", 1);

            InferenceResult result = engine.infer(facts);

            assertThat(result.getTotalRounds()).isEqualTo(1);
            assertThat(result.getAllTriggeredRules()).isEmpty();
            assertThat(result.getTrace()).hasSize(1);
            assertThat(result.getTrace().get(0).getTriggeredRules()).isEmpty();
        }
    }

    @Nested
    @DisplayName("推理链执行")
    class InferenceChainCases {

        @Test
        @DisplayName("规则 A 首轮触发后不再触发，规则 B 第二轮触发后不再触发：推理链收敛于第 3 轮")
        void shouldExecuteInferenceChainAcrossRounds() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("amount", 1500);

            Rule ruleA = mockRule("R_A", "规则A");
            // A 首次触发，后续不触发
            when(ruleA.evaluate(any(RuleContext.class)))
                    .thenReturn(triggeredResult("R_A"))   // 第 1 轮
                    .thenReturn(notTriggeredResult("R_A")); // 第 2 轮起

            Rule ruleB = mockRule("R_B", "规则B");
            // B 首次不触发，第 2 轮触发，第 3 轮起不触发
            when(ruleB.evaluate(any(RuleContext.class)))
                    .thenReturn(notTriggeredResult("R_B"))  // 第 1 轮
                    .thenReturn(triggeredResult("R_B"))     // 第 2 轮
                    .thenReturn(notTriggeredResult("R_B"));  // 第 3 轮起

            engine.register(ruleA);
            engine.register(ruleB);
            engine.setMaxRounds(10);

            InferenceResult result = engine.infer(facts);

            // 第 1 轮 A 触发，第 2 轮 B 触发，第 3 轮无触发退出
            assertThat(result.getTotalRounds()).isEqualTo(3);
            // 触发规则按轮次顺序：A, B
            assertThat(result.getAllTriggeredRules()).containsExactly("R_A", "R_B");

            // 验证每轮轨迹
            List<InferenceRound> trace = result.getTrace();
            assertThat(trace).hasSize(3);
            assertThat(trace.get(0).getRound()).isEqualTo(1);
            assertThat(trace.get(0).getTriggeredRules()).containsExactly("R_A");
            assertThat(trace.get(1).getRound()).isEqualTo(2);
            assertThat(trace.get(1).getTriggeredRules()).containsExactly("R_B");
            assertThat(trace.get(2).getRound()).isEqualTo(3);
            assertThat(trace.get(2).getTriggeredRules()).isEmpty();
        }

        @Test
        @DisplayName("规则每轮都触发时达到最大轮次后停止")
        void shouldStopAtMaxRoundsWhenRuleAlwaysTriggers() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("v", 1);

            Rule rule = mockRule("R001", "规则");
            when(rule.evaluate(any(RuleContext.class)))
                    .thenReturn(triggeredResult("R001"));
            engine.register(rule);
            engine.setMaxRounds(3);

            InferenceResult result = engine.infer(facts);

            // 每轮都触发，达到 maxRounds=3 后停止
            assertThat(result.getTotalRounds()).isEqualTo(3);
            assertThat(result.getAllTriggeredRules()).containsExactly("R001", "R001", "R001");
            assertThat(result.getTrace()).hasSize(3);
        }
    }

    @Nested
    @DisplayName("事实变更检测与收敛")
    class FactChangeCases {

        @Test
        @DisplayName("规则触发后下一轮重新评估（changed=true）")
        void shouldReEvaluateWhenRuleTriggers() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("v", 1);

            // 规则首次触发，第二次不触发
            Rule rule = mockRule("R001", "规则");
            when(rule.evaluate(any(RuleContext.class)))
                    .thenReturn(triggeredResult("R001"))
                    .thenReturn(notTriggeredResult("R001"));
            engine.register(rule);
            engine.setMaxRounds(10);

            InferenceResult result = engine.infer(facts);

            // 第 1 轮触发 → changed=true，第 2 轮不触发 → 退出
            assertThat(result.getTotalRounds()).isEqualTo(2);
        }

        @Test
        @DisplayName("maxRounds=1 时仅执行 1 轮推理")
        void shouldExecuteSingleRoundWhenMaxRoundsIsOne() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("v", 1);

            Rule rule = mockRule("R001", "规则");
            when(rule.evaluate(any(RuleContext.class)))
                    .thenReturn(triggeredResult("R001"));
            engine.register(rule);
            engine.setMaxRounds(1);

            InferenceResult result = engine.infer(facts);

            assertThat(result.getTotalRounds()).isEqualTo(1);
            assertThat(result.getAllTriggeredRules()).containsExactly("R001");
        }

        @Test
        @DisplayName("setMaxRounds(0) 被钳制为 1")
        void shouldClampMaxRoundsToAtLeastOne() {
            engine.setMaxRounds(0);

            Map<String, Object> facts = new HashMap<>();
            facts.put("v", 1);

            Rule rule = mockRule("R001", "规则");
            when(rule.evaluate(any(RuleContext.class)))
                    .thenReturn(triggeredResult("R001"));
            engine.register(rule);

            InferenceResult result = engine.infer(facts);

            // maxRounds 被钳制为 1，仅执行 1 轮
            assertThat(result.getTotalRounds()).isEqualTo(1);
        }

        @Test
        @DisplayName("setMaxRounds(-1) 被钳制为 1")
        void shouldClampNegativeMaxRoundsToOne() {
            engine.setMaxRounds(-1);

            Map<String, Object> facts = new HashMap<>();
            facts.put("v", 1);

            Rule rule = mockRule("R001", "规则");
            engine.register(rule);

            InferenceResult result = engine.infer(facts);

            assertThat(result.getTotalRounds()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("规则异常隔离")
    class ExceptionIsolationCases {

        @Test
        @DisplayName("规则 A 抛异常不影响规则 B 评估")
        void shouldIsolateRuleException() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("v", 1);

            Rule throwingRule = mockRule("R_THROW", "异常规则");
            when(throwingRule.evaluate(any(RuleContext.class)))
                    .thenThrow(new RuntimeException("规则内部异常"));

            Rule normalRule = mockRule("R_NORMAL", "正常规则");
            when(normalRule.evaluate(any(RuleContext.class)))
                    .thenReturn(notTriggeredResult("R_NORMAL"));
            engine.register(throwingRule);
            engine.register(normalRule);

            InferenceResult result = engine.infer(facts);

            // 异常规则被跳过，正常规则被评估，无触发收敛
            assertThat(result.getTotalRounds()).isEqualTo(1);
            assertThat(result.getAllTriggeredRules()).isEmpty();
        }

        @Test
        @DisplayName("规则抛异常时其他触发规则仍出现在结果中")
        void shouldStillIncludeTriggeredWhenOtherThrows() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("v", 1);

            Rule throwingRule = mockRule("R_THROW", "异常规则");
            when(throwingRule.evaluate(any(RuleContext.class)))
                    .thenThrow(new RuntimeException("boom"));

            Rule triggeredRule = mockRule("R_TRIGGER", "触发规则");
            when(triggeredRule.evaluate(any(RuleContext.class)))
                    .thenReturn(triggeredResult("R_TRIGGER"));
            engine.register(throwingRule);
            engine.register(triggeredRule);
            engine.setMaxRounds(1);

            InferenceResult result = engine.infer(facts);

            // 异常规则被跳过，触发规则仍在结果中
            assertThat(result.getAllTriggeredRules()).containsExactly("R_TRIGGER");
        }
    }

    @Nested
    @DisplayName("规则注册")
    class RegisterCases {

        @Test
        @DisplayName("register(Rule) 注册后 ruleCount 增加")
        void shouldIncreaseRuleCountAfterRegister() {
            Rule rule1 = mockRule("R001", "规则1");
            Rule rule2 = mockRule("R002", "规则2");
            Rule rule3 = mockRule("R003", "规则3");

            engine.register(rule1);
            engine.register(rule2);
            engine.register(rule3);

            assertThat(engine.ruleCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("register(null Rule) 不影响 ruleCount")
        void shouldNotIncreaseWhenRegisterNullRule() {
            engine.register((Rule) null);

            assertThat(engine.ruleCount()).isZero();
        }

        @Test
        @DisplayName("register(RuleDefinition) 启用规则后注册")
        void shouldRegisterEnabledDefinition() {
            RuleDefinition def = RuleDefinition.builder()
                    .code("R001")
                    .name("规则1")
                    .enabled(true)
                    .build();

            engine.register(def);

            assertThat(engine.ruleCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("register(RuleDefinition) 禁用规则不注册")
        void shouldNotRegisterDisabledDefinition() {
            RuleDefinition def = RuleDefinition.builder()
                    .code("R001")
                    .name("规则1")
                    .enabled(false)
                    .build();

            engine.register(def);

            assertThat(engine.ruleCount()).isZero();
        }

        @Test
        @DisplayName("register(null RuleDefinition) 不影响 ruleCount")
        void shouldNotRegisterNullDefinition() {
            engine.register((RuleDefinition) null);

            assertThat(engine.ruleCount()).isZero();
        }
    }

    @Nested
    @DisplayName("推理结果轨迹")
    class TraceCases {

        @Test
        @DisplayName("每轮的 roundResults 包含触发的 RuleResult")
        void shouldIncludeResultsInTrace() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("v", 1);

            RuleResult triggered = triggeredResult("R001");
            Rule rule = mockRule("R001", "规则");
            when(rule.evaluate(any(RuleContext.class)))
                    .thenReturn(triggered)
                    .thenReturn(notTriggeredResult("R001"));
            engine.register(rule);
            engine.setMaxRounds(5);

            InferenceResult result = engine.infer(facts);

            // 第 1 轮触发，结果列表应包含该 RuleResult
            InferenceRound firstRound = result.getTrace().get(0);
            assertThat(firstRound.getResults()).hasSize(1);
            assertThat(firstRound.getResults().get(0).getRuleCode()).isEqualTo("R001");
            assertThat(firstRound.getResults().get(0).isTriggered()).isTrue();

            // 第 2 轮无触发，结果列表为空
            InferenceRound secondRound = result.getTrace().get(1);
            assertThat(secondRound.getResults()).isEmpty();
        }

        @Test
        @DisplayName("推理事实是初始事实的可变副本，不修改原始 Map")
        void shouldNotMutateOriginalFactsMap() {
            Map<String, Object> originalFacts = new HashMap<>();
            originalFacts.put("amount", 1000);

            Rule rule = mockRule("R001", "规则");
            when(rule.evaluate(any(RuleContext.class)))
                    .thenReturn(triggeredResult("R001"));
            engine.register(rule);
            engine.setMaxRounds(1);

            InferenceResult result = engine.infer(originalFacts);

            // 原始 Map 不应被修改
            assertThat(originalFacts).hasSize(1);
            assertThat(originalFacts.get("amount")).isEqualTo(1000);
            // 结果事实应包含初始值
            assertThat(result.getFacts().get("amount")).isEqualTo(1000);
        }
    }
}
