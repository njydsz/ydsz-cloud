package com.njydsz.pmis.literule.ai;

import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.expr.ExpressionValidationResult;
import com.njydsz.pmis.literule.expr.ExpressionValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * {@link RuleLLMService} 单元测试。
 *
 * <p>覆盖自然语言转规则、规则描述生成、表达式优化建议等能力，
 * 含 LLM 不可用降级、JSON 解析、表达式校验等场景。
 *
 * @author ydsz-pmis-team
 */
@DisplayName("规则 LLM 服务测试")
@ExtendWith(MockitoExtension.class)
class RuleLLMServiceTest {

    @Mock
    private LLMClient llmClient;

    @Mock
    private ExpressionValidationService expressionValidator;

    private RuleLLMService service;

    @BeforeEach
    void setUp() {
        service = new RuleLLMService(llmClient, expressionValidator);
    }

    private ExpressionValidationResult okResult(String expr) {
        return ExpressionValidationResult.ok(expr, 5L, List.of());
    }

    // ==================== naturalLanguageToRule ====================

    @Nested
    @DisplayName("自然语言转规则：naturalLanguageToRule")
    class NaturalLanguageToRuleTest {

        @Test
        @DisplayName("异常场景：naturalLanguage 为 null 抛异常")
        void shouldThrowWhenNaturalLanguageNull() {
            assertThatThrownBy(() -> service.naturalLanguageToRule(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不能为空");
        }

        @Test
        @DisplayName("异常场景：naturalLanguage 为空白抛异常")
        void shouldThrowWhenNaturalLanguageBlank() {
            assertThatThrownBy(() -> service.naturalLanguageToRule("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不能为空");
        }

        @Test
        @DisplayName("异常场景：LLM 不可用时降级返回空壳规则")
        void shouldReturnFallbackWhenLLMUnavailable() {
            when(llmClient.chat(anyString(), anyString(), any()))
                    .thenThrow(new LLMException("MOCK", "LLM 不可用"));

            RuleDefinition rule = service.naturalLanguageToRule("金额超过 1000 时告警");

            assertThat(rule.getName()).isEqualTo("金额超过 1000 时告警");
            assertThat(rule.getDescription()).contains("LLM 不可用");
        }

        @Test
        @DisplayName("正常场景：LLM 返回完整 JSON 时解析为规则")
        void shouldParseJsonWhenLLMReturnsValidJson() {
            String json = "{\"code\":\"amount-warn\",\"name\":\"金额告警\","
                    + "\"conditionExpression\":\"amount > 1000\","
                    + "\"defaultSeverity\":\"RED\","
                    + "\"description\":\"金额超过阈值告警\"}";
            when(llmClient.chat(anyString(), anyString(), any())).thenReturn(json);
            when(expressionValidator.validateCondition("amount > 1000"))
                    .thenReturn(okResult("amount > 1000"));

            RuleDefinition rule = service.naturalLanguageToRule("金额超过 1000 时告警");

            assertThat(rule.getCode()).isEqualTo("amount-warn");
            assertThat(rule.getName()).isEqualTo("金额告警");
            assertThat(rule.getConditionExpression()).isEqualTo("amount > 1000");
            assertThat(rule.getDefaultSeverity()).isEqualTo(RuleSeverity.RED);
            assertThat(rule.getDescription()).isEqualTo("金额超过阈值告警");
        }

        @Test
        @DisplayName("正常场景：LLM 返回 ```json 包裹的 JSON 时正确解析")
        void shouldParseJsonWhenWrappedInCodeFence() {
            String json = "```json\n{\"code\":\"test-rule\",\"name\":\"测试规则\","
                    + "\"conditionExpression\":\"x > 0\","
                    + "\"defaultSeverity\":\"YELLOW\"}\n```";
            when(llmClient.chat(anyString(), anyString(), any())).thenReturn(json);
            when(expressionValidator.validateCondition("x > 0"))
                    .thenReturn(okResult("x > 0"));

            RuleDefinition rule = service.naturalLanguageToRule("测试");

            assertThat(rule.getCode()).isEqualTo("test-rule");
            assertThat(rule.getName()).isEqualTo("测试规则");
        }

        @Test
        @DisplayName("正常场景：JSON 缺失 code 字段时使用 ai- 前缀")
        void shouldUseAiPrefixWhenCodeMissing() {
            String json = "{\"name\":\"无编码规则\",\"conditionExpression\":\"x > 0\"}";
            when(llmClient.chat(anyString(), anyString(), any())).thenReturn(json);
            when(expressionValidator.validateCondition("x > 0"))
                    .thenReturn(okResult("x > 0"));

            RuleDefinition rule = service.naturalLanguageToRule("测试");

            assertThat(rule.getCode()).startsWith("ai-");
            assertThat(rule.getName()).isEqualTo("无编码规则");
        }

        @Test
        @DisplayName("正常场景：JSON 缺失 name 字段时使用 fallbackDesc")
        void shouldUseFallbackWhenNameMissing() {
            String json = "{\"code\":\"test-rule\",\"conditionExpression\":\"x > 0\"}";
            when(llmClient.chat(anyString(), anyString(), any())).thenReturn(json);
            when(expressionValidator.validateCondition("x > 0"))
                    .thenReturn(okResult("x > 0"));

            RuleDefinition rule = service.naturalLanguageToRule("我的描述");

            assertThat(rule.getName()).isEqualTo("我的描述");
        }

        @Test
        @DisplayName("正常场景：defaultSeverity 非法时默认 YELLOW")
        void shouldDefaultYellowWhenSeverityInvalid() {
            String json = "{\"code\":\"test-rule\",\"name\":\"测试\","
                    + "\"conditionExpression\":\"x > 0\","
                    + "\"defaultSeverity\":\"INVALID\"}";
            when(llmClient.chat(anyString(), anyString(), any())).thenReturn(json);
            when(expressionValidator.validateCondition("x > 0"))
                    .thenReturn(okResult("x > 0"));

            RuleDefinition rule = service.naturalLanguageToRule("测试");

            assertThat(rule.getDefaultSeverity()).isEqualTo(RuleSeverity.YELLOW);
        }

        @Test
        @DisplayName("正常场景：表达式未通过校验时不设置 conditionExpression")
        void shouldNotSetConditionWhenValidationFails() {
            String json = "{\"code\":\"test-rule\",\"name\":\"测试\","
                    + "\"conditionExpression\":\"invalid expr\"}";
            when(llmClient.chat(anyString(), anyString(), any())).thenReturn(json);
            ExpressionValidationResult fail = ExpressionValidationResult.fail("invalid expr",
                    ExpressionValidationResult.ErrorType.SYNTAX_ERROR, "语法错误", 1L);
            when(expressionValidator.validateCondition("invalid expr")).thenReturn(fail);

            RuleDefinition rule = service.naturalLanguageToRule("测试");

            assertThat(rule.getConditionExpression()).isNull();
        }

        @Test
        @DisplayName("异常场景：LLM 返回非法 JSON 时使用 fallbackDesc")
        void shouldUseFallbackWhenJsonInvalid() {
            when(llmClient.chat(anyString(), anyString(), any())).thenReturn("not a json");

            RuleDefinition rule = service.naturalLanguageToRule("我的描述");

            assertThat(rule.getDescription()).isEqualTo("我的描述");
        }

        @Test
        @DisplayName("边界条件：LLM 返回空字符串时返回 fallbackDesc")
        void shouldReturnFallbackWhenLLMReturnsEmpty() {
            when(llmClient.chat(anyString(), anyString(), any())).thenReturn("");

            RuleDefinition rule = service.naturalLanguageToRule("我的描述");

            assertThat(rule.getDescription()).isEqualTo("我的描述");
        }
    }

    // ==================== describeRule ====================

    @Nested
    @DisplayName("规则描述生成：describeRule")
    class DescribeRuleTest {

        @Test
        @DisplayName("边界条件：rule 为 null 返回 null")
        void shouldReturnNullWhenRuleNull() {
            assertThat(service.describeRule(null)).isNull();
        }

        @Test
        @DisplayName("正常场景：LLM 返回描述文本")
        void shouldReturnDescription() {
            RuleDefinition rule = RuleDefinition.builder()
                    .code("R001").name("规则-R001")
                    .conditionExpression("amount > 1000").build();
            when(llmClient.chat(anyString(), anyString(), any())).thenReturn("当金额超过 1000 时触发告警");

            String description = service.describeRule(rule);

            assertThat(description).isEqualTo("当金额超过 1000 时触发告警");
        }

        @Test
        @DisplayName("异常场景：LLM 调用失败时返回 null")
        void shouldReturnNullWhenLLMThrowsException() {
            RuleDefinition rule = RuleDefinition.builder()
                    .code("R001").name("规则-R001").build();
            when(llmClient.chat(anyString(), anyString(), any()))
                    .thenThrow(new LLMException("MOCK", "LLM 不可用"));

            String description = service.describeRule(rule);

            assertThat(description).isNull();
        }
    }

    // ==================== optimizeExpression ====================

    @Nested
    @DisplayName("表达式优化：optimizeExpression")
    class OptimizeExpressionTest {

        @Test
        @DisplayName("边界条件：expression 为 null 返回 null")
        void shouldReturnNullWhenExpressionNull() {
            assertThat(service.optimizeExpression(null)).isNull();
        }

        @Test
        @DisplayName("边界条件：expression 为空白返回 null")
        void shouldReturnNullWhenExpressionBlank() {
            assertThat(service.optimizeExpression("   ")).isNull();
        }

        @Test
        @DisplayName("正常场景：LLM 返回优化建议")
        void shouldReturnOptimizationSuggestions() {
            when(llmClient.chat(anyString(), anyString(), any()))
                    .thenReturn("建议1：简化表达式\n建议2：提取公共变量");

            String result = service.optimizeExpression("amount > 1000 && amount < 5000");

            assertThat(result).contains("建议1");
        }

        @Test
        @DisplayName("异常场景：LLM 调用失败时返回 null")
        void shouldReturnNullWhenLLMThrowsException() {
            when(llmClient.chat(anyString(), anyString(), any()))
                    .thenThrow(new LLMException("MOCK", "LLM 不可用"));

            String result = service.optimizeExpression("amount > 1000");

            assertThat(result).isNull();
        }
    }
}
