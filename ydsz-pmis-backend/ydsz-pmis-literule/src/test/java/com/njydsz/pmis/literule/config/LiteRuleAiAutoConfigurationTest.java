package com.njydsz.pmis.literule.config;

import com.njydsz.pmis.literule.ai.LLMClient;
import com.njydsz.pmis.literule.ai.MockLLMClient;
import com.njydsz.pmis.literule.ai.OpenAICompatibleLLMClient;
import com.njydsz.pmis.literule.ai.RuleHealthScoreService;
import com.njydsz.pmis.literule.ai.RuleLLMService;
import com.njydsz.pmis.literule.ai.RuleRecommendationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 增强 Bean 装配条件测试
 *
 * <p>使用 {@link ApplicationContextRunner} 轻量验证 {@link LiteRuleAutoConfiguration} 中
 * 4 个 AI Bean 的 {@code @ConditionalOnProperty(prefix="pmis.literule.ai", name="enabled",
 * havingValue="true")} 装配行为，无需启动完整 Spring Boot 应用。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>默认（ai.enabled 缺失）→ 不装配任何 AI Bean</li>
 *   <li>ai.enabled=false → 不装配任何 AI Bean</li>
 *   <li>ai.enabled=true + llm-client=MOCK → 装配 MockLLMClient + 全部 4 Bean</li>
 *   <li>ai.enabled=true + llm-client 缺失 → 默认 MockLLMClient</li>
 *   <li>ai.enabled=true + llm-client=OPENAI_COMPATIBLE → OpenAICompatibleLLMClient</li>
 *   <li>ai.enabled=true + 未知 llm-client → 回退 MockLLMClient</li>
 *   <li>ai.enabled=true + 自定义健康度权重 → RuleHealthScoreService 使用配置权重</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@DisplayName("AI 增强 Bean 装配条件测试")
class LiteRuleAiAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LiteRuleAutoConfiguration.class));

    // ============ 默认禁用 ============

    @Test
    @DisplayName("默认配置（ai.enabled 缺失）不应装配任何 AI Bean")
    void defaultConfigShouldNotCreateAiBeans() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(LLMClient.class);
            assertThat(context).doesNotHaveBean(RuleLLMService.class);
            assertThat(context).doesNotHaveBean(RuleHealthScoreService.class);
            assertThat(context).doesNotHaveBean(RuleRecommendationService.class);
        });
    }

    @Test
    @DisplayName("ai.enabled=false 不应装配任何 AI Bean")
    void aiDisabledShouldNotCreateAiBeans() {
        runner.withPropertyValues("pmis.literule.ai.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(LLMClient.class);
            assertThat(context).doesNotHaveBean(RuleLLMService.class);
            assertThat(context).doesNotHaveBean(RuleHealthScoreService.class);
            assertThat(context).doesNotHaveBean(RuleRecommendationService.class);
        });
    }

    @Test
    @DisplayName("默认配置下非 AI 的公共 Bean（ExpressionEvaluator/ExpressionValidationService）应正常装配")
    void defaultConfigShouldStillCreateCommonBeans() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(com.njydsz.pmis.literule.expr.ExpressionEvaluator.class);
            assertThat(context).hasSingleBean(com.njydsz.pmis.literule.expr.ExpressionValidationService.class);
        });
    }

    // ============ 启用 AI ============

    @Test
    @DisplayName("ai.enabled=true 应装配全部 4 个 AI Bean")
    void aiEnabledShouldCreateAllAiBeans() {
        runner.withPropertyValues(
                "pmis.literule.ai.enabled=true",
                "pmis.literule.ai.llm-client=MOCK"
        ).run(context -> {
            assertThat(context).hasSingleBean(LLMClient.class);
            assertThat(context).hasSingleBean(RuleLLMService.class);
            assertThat(context).hasSingleBean(RuleHealthScoreService.class);
            assertThat(context).hasSingleBean(RuleRecommendationService.class);
        });
    }

    @Test
    @DisplayName("ai.enabled=true 且 llm-client=MOCK 应使用 MockLLMClient")
    void aiEnabledWithMockClientShouldUseMockLLMClient() {
        runner.withPropertyValues(
                "pmis.literule.ai.enabled=true",
                "pmis.literule.ai.llm-client=MOCK"
        ).run(context -> {
            LLMClient client = context.getBean(LLMClient.class);
            assertThat(client).isInstanceOf(MockLLMClient.class);
            assertThat(client.provider()).isEqualTo("MOCK");
        });
    }

    @Test
    @DisplayName("ai.enabled=true 且 llm-client 缺失应默认使用 MockLLMClient")
    void aiEnabledWithoutLlmClientShouldDefaultToMock() {
        runner.withPropertyValues("pmis.literule.ai.enabled=true").run(context -> {
            LLMClient client = context.getBean(LLMClient.class);
            assertThat(client).isInstanceOf(MockLLMClient.class);
        });
    }

    @Test
    @DisplayName("ai.enabled=true 且 llm-client=OPENAI_COMPATIBLE 应使用 OpenAICompatibleLLMClient")
    void aiEnabledWithOpenAiClientShouldUseOpenAICompatibleLLMClient() {
        runner.withPropertyValues(
                "pmis.literule.ai.enabled=true",
                "pmis.literule.ai.llm-client=OPENAI_COMPATIBLE",
                "pmis.literule.ai.llm-api-key=sk-test",
                "pmis.literule.ai.llm-api-url=https://api.openai.com/v1/chat/completions"
        ).run(context -> {
            LLMClient client = context.getBean(LLMClient.class);
            assertThat(client).isInstanceOf(OpenAICompatibleLLMClient.class);
            assertThat(client.provider()).isEqualTo("OPENAI_COMPATIBLE");
        });
    }

    @Test
    @DisplayName("ai.enabled=true 且 llm-client 未知应回退到 MockLLMClient")
    void aiEnabledWithUnknownLlmClientShouldFallbackToMock() {
        runner.withPropertyValues(
                "pmis.literule.ai.enabled=true",
                "pmis.literule.ai.llm-client=UNKNOWN_PROVIDER"
        ).run(context -> {
            LLMClient client = context.getBean(LLMClient.class);
            assertThat(client).isInstanceOf(MockLLMClient.class);
        });
    }

    @Test
    @DisplayName("ai.enabled=true 应装配 RuleHealthScoreService 并使用配置权重")
    void aiEnabledShouldCreateHealthScoreServiceWithConfiguredWeights() {
        runner.withPropertyValues(
                "pmis.literule.ai.enabled=true",
                "pmis.literule.ai.health-hit-rate-weight=0.5",
                "pmis.literule.ai.health-error-rate-weight=0.3",
                "pmis.literule.ai.health-complexity-weight=0.1",
                "pmis.literule.ai.health-coverage-weight=0.1"
        ).run(context -> {
            assertThat(context).hasSingleBean(RuleHealthScoreService.class);
            LiteRuleProperties props = context.getBean(LiteRuleProperties.class);
            assertThat(props.getAi().getHealthHitRateWeight()).isEqualTo(0.5);
            assertThat(props.getAi().getHealthErrorRateWeight()).isEqualTo(0.3);
            assertThat(props.getAi().getHealthComplexityWeight()).isEqualTo(0.1);
            assertThat(props.getAi().getHealthCoverageWeight()).isEqualTo(0.1);
        });
    }

    @Test
    @DisplayName("ai.enabled=true 应装配 RuleRecommendationService")
    void aiEnabledShouldCreateRecommendationService() {
        runner.withPropertyValues(
                "pmis.literule.ai.enabled=true",
                "pmis.literule.ai.recommend-top-n=5"
        ).run(context -> {
            assertThat(context).hasSingleBean(RuleRecommendationService.class);
            LiteRuleProperties props = context.getBean(LiteRuleProperties.class);
            assertThat(props.getAi().getRecommendTopN()).isEqualTo(5);
        });
    }

    @Test
    @DisplayName("ai.enabled=true 应装配 RuleLLMService")
    void aiEnabledShouldCreateRuleLLMService() {
        runner.withPropertyValues(
                "pmis.literule.ai.enabled=true",
                "pmis.literule.ai.llm-client=MOCK"
        ).run(context -> {
            assertThat(context).hasSingleBean(RuleLLMService.class);
            RuleLLMService svc = context.getBean(RuleLLMService.class);
            assertThat(svc).isNotNull();
        });
    }

    @Test
    @DisplayName("LiteRuleProperties.Ai 默认值应正确绑定")
    void aiDefaultValuesShouldBeBound() {
        runner.withPropertyValues("pmis.literule.ai.enabled=true").run(context -> {
            LiteRuleProperties props = context.getBean(LiteRuleProperties.class);
            LiteRuleProperties.Ai ai = props.getAi();
            assertThat(ai.isEnabled()).isTrue();
            assertThat(ai.getLlmClient()).isEqualTo("MOCK");
            assertThat(ai.getLlmModel()).isEqualTo("gpt-4o-mini");
            assertThat(ai.getLlmTimeoutMs()).isEqualTo(15000L);
            assertThat(ai.getLlmTemperature()).isEqualTo(0.2);
            assertThat(ai.getHealthHitRateWeight()).isEqualTo(0.30);
            assertThat(ai.getHealthErrorRateWeight()).isEqualTo(0.30);
            assertThat(ai.getHealthComplexityWeight()).isEqualTo(0.20);
            assertThat(ai.getHealthCoverageWeight()).isEqualTo(0.20);
            assertThat(ai.getHealthComplexityThreshold()).isEqualTo(80);
            assertThat(ai.getRecommendTopN()).isEqualTo(10);
        });
    }
}
