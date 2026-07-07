package com.njydsz.pmis.literule.core;

import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.expr.AviatorExpressionEvaluator;
import com.njydsz.pmis.literule.impl.ExpressionRule;
import com.njydsz.pmis.literule.model.ModelInputProvider;
import com.njydsz.pmis.literule.model.ModelInputRegistry;
import com.njydsz.pmis.literule.model.ModelInvocationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 规则+模型融合测试（P3-1）
 *
 * <p>验证规则引擎与模型输出的融合能力，对标滴滴 Newton、字节风控。
 * 测试核心场景：
 * <ul>
 *   <li>规则引用 {@code model.riskScore > 0.8}，模型输出满足时触发</li>
 *   <li>规则引用 {@code model.riskScore > 0.8}，模型输出不满足时不触发</li>
 *   <li>模型未注册时规则不触发（变量不存在）</li>
 *   <li>模型异常时降级（fallbackOnError=true 继续评估）</li>
 *   <li>模型异常时中断（fallbackOnError=false 抛异常）</li>
 *   <li>多模型混合（riskScore from modelA, fraudProb from modelB）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 */
@DisplayName("规则+模型融合测试")
class RuleModelFusionTest {

    private DefaultRuleEngine engine;
    private AviatorExpressionEvaluator evaluator;
    private ModelInputRegistry registry;

    @BeforeEach
    void setUp() {
        engine = new DefaultRuleEngine();
        evaluator = new AviatorExpressionEvaluator(true);
        // 默认降级开启，超时 1 秒（测试用宽松超时，避免误判）
        registry = new ModelInputRegistry(1000L, true);
        engine.setModelInputRegistry(registry);
    }

    @AfterEach
    void tearDown() {
        engine.destroy();
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造表达式规则（条件引用 model.xxx 变量）
     */
    private ExpressionRule modelRule(String code, String conditionExpr) {
        RuleDefinition def = RuleDefinition.builder()
                .code(code)
                .name("规则-" + code)
                .category("MODEL_FUSION")
                .conditionExpression(conditionExpr)
                .defaultSeverity(RuleSeverity.RED)
                .titleTemplate("模型触发: " + code)
                .descriptionTemplate("条件: " + conditionExpr)
                .tenantId("1")
                .build();
        return new ExpressionRule(def, evaluator);
    }

    /**
     * 构造 mock 模型 provider，返回固定输出
     */
    private ModelInputProvider mockProvider(String modelId, Map<String, Object> output) {
        ModelInputProvider provider = Mockito.mock(ModelInputProvider.class);
        when(provider.getModelId()).thenReturn(modelId);
        when(provider.isEnabled()).thenReturn(true);
        when(provider.getModelOutput(any())).thenReturn(output);
        return provider;
    }

    /**
     * 构造默认上下文（租户 1、场景 DEFAULT）
     */
    private RuleContext defaultContext() {
        Map<String, Object> facts = new HashMap<>();
        facts.put("amount", 1000);
        return RuleContext.of(facts, "DEFAULT", "TEST", "trace-1", "1");
    }

    // ==================== 模型输出触发规则 ====================

    @Nested
    @DisplayName("模型输出触发规则")
    class ModelTriggersRuleTest {

        @Test
        @DisplayName("model.riskScore > 0.8，模型输出 riskScore=0.9 - 规则触发")
        void shouldTriggerWhenModelOutputMeetsCondition() {
            registry.register(mockProvider("risk-model", Map.of("riskScore", 0.9)));
            engine.register(modelRule("R_HIGH_RISK", "model.riskScore > 0.8"));

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("R_HIGH_RISK");
            assertThat(results.get(0).isTriggered()).isTrue();
            assertThat(results.get(0).getSeverity()).isEqualTo(RuleSeverity.RED);
        }

        @Test
        @DisplayName("model.riskScore > 0.8，模型输出 riskScore=0.5 - 规则不触发")
        void shouldNotTriggerWhenModelOutputDoesNotMeetCondition() {
            registry.register(mockProvider("risk-model", Map.of("riskScore", 0.5)));
            engine.register(modelRule("R_HIGH_RISK", "model.riskScore > 0.8"));

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("model.fraudProbability < 0.1，模型输出 fraudProbability=0.05 - 规则触发")
        void shouldTriggerWhenFraudProbBelowThreshold() {
            registry.register(mockProvider("fraud-model", Map.of("fraudProbability", 0.05)));
            engine.register(modelRule("R_LOW_FRAUD", "model.fraudProbability < 0.1"));

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("R_LOW_FRAUD");
            assertThat(results.get(0).isTriggered()).isTrue();
        }

        @Test
        @DisplayName("模型输出 + 事实数据组合条件 - 满足时触发")
        void shouldTriggerWhenCombinedModelAndFactsCondition() {
            registry.register(mockProvider("risk-model", Map.of("riskScore", 0.9)));
            // 同时引用 model.riskScore 和事实 amount
            engine.register(modelRule("R_COMBO", "model.riskScore > 0.8 && amount > 500"));

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("R_COMBO");
        }

        @Test
        @DisplayName("模型输出 + 事实数据组合条件 - 事实不满足时不触发")
        void shouldNotTriggerWhenFactsConditionNotMet() {
            registry.register(mockProvider("risk-model", Map.of("riskScore", 0.9)));
            // amount=1000，但条件要求 > 2000
            engine.register(modelRule("R_COMBO", "model.riskScore > 0.8 && amount > 2000"));

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).isEmpty();
        }
    }

    // ==================== 模型未注册降级 ====================

    @Nested
    @DisplayName("模型未注册降级")
    class ModelNotRegisteredTest {

        @Test
        @DisplayName("模型未注册 - 规则引用 model.xxx 不触发（变量不存在）")
        void shouldNotTriggerWhenModelNotRegistered() {
            // 不注册任何 provider，但 registry 已注入引擎
            engine.register(modelRule("R_NEED_MODEL", "model.riskScore > 0.8"));

            List<RuleResult> results = engine.evaluate(defaultContext());

            // 模型变量不存在，表达式求值返回 false（由 AviatorExpressionEvaluator 兜底）
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("registry=null - 不影响规则评估（向后兼容）")
        void shouldWorkWithoutRegistry() {
            engine.setModelInputRegistry(null);
            engine.register(modelRule("R_NEED_MODEL", "model.riskScore > 0.8"));

            List<RuleResult> results = engine.evaluate(defaultContext());

            // 无 registry，模型变量不存在，规则不触发
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("非模型规则 - 不受模型未注册影响")
        void shouldEvaluateNonModelRulesWithoutModel() {
            engine.register(modelRule("R_NO_MODEL", "amount > 500"));

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("R_NO_MODEL");
        }
    }

    // ==================== 模型异常降级 ====================

    @Nested
    @DisplayName("模型异常降级")
    class ModelExceptionFallbackTest {

        @Test
        @DisplayName("模型异常 + fallbackOnError=true - 继续评估，模型规则不触发")
        void shouldFallbackWhenModelThrowsAndFallbackEnabled() {
            ModelInputProvider badProvider = Mockito.mock(ModelInputProvider.class);
            when(badProvider.getModelId()).thenReturn("bad-model");
            when(badProvider.isEnabled()).thenReturn(true);
            when(badProvider.getModelOutput(any())).thenThrow(new RuntimeException("模型服务不可用"));
            registry.register(badProvider);
            engine.register(modelRule("R_NEED_MODEL", "model.riskScore > 0.8"));

            List<RuleResult> results = engine.evaluate(defaultContext());

            // 模型异常降级，模型变量未注入，规则不触发
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("模型异常 + fallbackOnError=true - 非模型规则正常评估")
        void shouldEvaluateNonModelRulesWhenModelFails() {
            ModelInputProvider badProvider = Mockito.mock(ModelInputProvider.class);
            when(badProvider.getModelId()).thenReturn("bad-model");
            when(badProvider.isEnabled()).thenReturn(true);
            when(badProvider.getModelOutput(any())).thenThrow(new RuntimeException("模型服务不可用"));
            registry.register(badProvider);
            engine.register(modelRule("R_NO_MODEL", "amount > 500"));

            List<RuleResult> results = engine.evaluate(defaultContext());

            // 非模型规则不受影响
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("R_NO_MODEL");
        }

        @Test
        @DisplayName("模型异常 + fallbackOnError=false - 抛 ModelInvocationException 中断评估")
        void shouldThrowWhenModelFailsAndFallbackDisabled() {
            ModelInputProvider badProvider = Mockito.mock(ModelInputProvider.class);
            when(badProvider.getModelId()).thenReturn("bad-model");
            when(badProvider.isEnabled()).thenReturn(true);
            when(badProvider.getModelOutput(any())).thenThrow(new RuntimeException("模型服务不可用"));

            // 使用严格模式的 registry
            ModelInputRegistry strictRegistry = new ModelInputRegistry(1000L, false);
            try {
                strictRegistry.register(badProvider);
                engine.setModelInputRegistry(strictRegistry);
                engine.register(modelRule("R_NEED_MODEL", "model.riskScore > 0.8"));

                assertThatThrownBy(() -> engine.evaluate(defaultContext()))
                        .isInstanceOf(ModelInvocationException.class)
                        .hasMessageContaining("bad-model");
            } finally {
                strictRegistry.destroy();
            }
        }

        @Test
        @DisplayName("部分 provider 异常 + fallbackOnError=true - 其他 provider 仍生效")
        void shouldStillUseHealthyProvidersWhenOneFails() {
            ModelInputProvider badProvider = Mockito.mock(ModelInputProvider.class);
            when(badProvider.getModelId()).thenReturn("bad-model");
            when(badProvider.isEnabled()).thenReturn(true);
            when(badProvider.getModelOutput(any())).thenThrow(new RuntimeException("模型不可用"));
            registry.register(badProvider);
            // healthy provider 提供 riskScore
            registry.register(mockProvider("good-model", Map.of("riskScore", 0.9)));
            engine.register(modelRule("R_NEED_RISK_SCORE", "model.riskScore > 0.8"));

            List<RuleResult> results = engine.evaluate(defaultContext());

            // bad provider 失败，但 good provider 的 riskScore 仍注入
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("R_NEED_RISK_SCORE");
            assertThat(results.get(0).isTriggered()).isTrue();
        }
    }

    // ==================== 多模型混合 ====================

    @Nested
    @DisplayName("多模型混合")
    class MultiModelTest {

        @Test
        @DisplayName("riskScore from modelA + fraudProb from modelB - 同时引用两个模型字段")
        void shouldSupportMultiModelFieldsInSameRule() {
            registry.register(mockProvider("model-a", Map.of("riskScore", 0.9)));
            registry.register(mockProvider("model-b", Map.of("fraudProbability", 0.05)));
            // 同时引用两个模型字段
            engine.register(modelRule("R_MULTI_MODEL",
                    "model.riskScore > 0.8 && model.fraudProbability < 0.1"));

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("R_MULTI_MODEL");
            assertThat(results.get(0).isTriggered()).isTrue();
        }

        @Test
        @DisplayName("多模型混合 - 一个模型字段不满足时不触发")
        void shouldNotTriggerWhenOneModelFieldDoesNotMeet() {
            registry.register(mockProvider("model-a", Map.of("riskScore", 0.9)));
            registry.register(mockProvider("model-b", Map.of("fraudProbability", 0.5)));
            engine.register(modelRule("R_MULTI_MODEL",
                    "model.riskScore > 0.8 && model.fraudProbability < 0.1"));

            List<RuleResult> results = engine.evaluate(defaultContext());

            // fraudProbability=0.5 不满足 < 0.1
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("多模型混合 OR 条件 - 任一满足即触发")
        void shouldTriggerWhenEitherModelFieldMeetsOrCondition() {
            registry.register(mockProvider("model-a", Map.of("riskScore", 0.5)));
            registry.register(mockProvider("model-b", Map.of("fraudProbability", 0.02)));
            engine.register(modelRule("R_OR",
                    "model.riskScore > 0.8 || model.fraudProbability < 0.1"));

            List<RuleResult> results = engine.evaluate(defaultContext());

            // riskScore 不满足，但 fraudProbability 满足
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("R_OR");
        }

        @Test
        @DisplayName("多 provider 同名字段 - 后注册者覆盖")
        void shouldOverrideSameFieldNameFromMultipleProviders() {
            registry.register(mockProvider("model-a", Map.of("score", 0.5)));
            registry.register(mockProvider("model-b", Map.of("score", 0.9)));
            engine.register(modelRule("R_OVERRIDE", "model.score > 0.8"));

            List<RuleResult> results = engine.evaluate(defaultContext());

            // model-b 的 score=0.9 覆盖 model-a 的 score=0.5
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("R_OVERRIDE");
        }

        @Test
        @DisplayName("多模型多规则 - 各自独立触发")
        void shouldEvaluateMultipleRulesWithDifferentModelFields() {
            registry.register(mockProvider("model-a", Map.of("riskScore", 0.9)));
            registry.register(mockProvider("model-b", Map.of("fraudProbability", 0.05)));
            engine.register(modelRule("R_RISK", "model.riskScore > 0.8"));
            engine.register(modelRule("R_FRAUD", "model.fraudProbability < 0.1"));

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).hasSize(2);
            assertThat(results).extracting(RuleResult::getRuleCode)
                    .containsExactlyInAnyOrder("R_RISK", "R_FRAUD");
        }
    }

    // ==================== 模型输出为空降级 ====================

    @Nested
    @DisplayName("模型输出为空降级")
    class EmptyOutputTest {

        @Test
        @DisplayName("provider 返回空 Map - 模型变量未注入，规则不触发")
        void shouldNotTriggerWhenProviderReturnsEmpty() {
            ModelInputProvider emptyProvider = mockProvider("empty-model", new LinkedHashMap<>());
            registry.register(emptyProvider);
            engine.register(modelRule("R_NEED_MODEL", "model.riskScore > 0.8"));

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("provider 返回 null - 模型变量未注入，规则不触发")
        void shouldNotTriggerWhenProviderReturnsNull() {
            ModelInputProvider nullProvider = Mockito.mock(ModelInputProvider.class);
            when(nullProvider.getModelId()).thenReturn("null-model");
            when(nullProvider.isEnabled()).thenReturn(true);
            when(nullProvider.getModelOutput(any())).thenReturn(null);
            registry.register(nullProvider);
            engine.register(modelRule("R_NEED_MODEL", "model.riskScore > 0.8"));

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("provider 已禁用 - 不调用，规则不触发")
        void shouldNotCallDisabledProvider() {
            ModelInputProvider disabled = Mockito.mock(ModelInputProvider.class);
            when(disabled.getModelId()).thenReturn("disabled-model");
            when(disabled.isEnabled()).thenReturn(false);
            registry.register(disabled);
            engine.register(modelRule("R_NEED_MODEL", "model.riskScore > 0.8"));

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).isEmpty();
            Mockito.verify(disabled, Mockito.never()).getModelOutput(any());
        }
    }

    // ==================== dryRun 模型融合 ====================

    @Nested
    @DisplayName("dryRun 模型融合")
    class DryRunTest {

        @Test
        @DisplayName("dryRun 同样注入模型输出 - 含模型规则可触发")
        void shouldInjectModelInDryRun() {
            registry.register(mockProvider("risk-model", Map.of("riskScore", 0.9)));
            engine.register(modelRule("R_MODEL", "model.riskScore > 0.8"));

            List<RuleResult> results = engine.dryRun(defaultContext());

            assertThat(results).hasSize(1);
            assertThat(results.get(0).isTriggered()).isTrue();
            assertThat(results.get(0).getRuleCode()).isEqualTo("R_MODEL");
        }
    }
}
