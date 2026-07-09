package com.njydsz.pmis.literule.expr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link ExpressionValidationService} 单元测试。
 *
 * <p>覆盖条件表达式校验、严重度表达式校验、模板表达式校验、批量校验、
 * 变量列表查询、表达式预览求值等能力，含正常路径、边界条件与异常场景。
 *
 * @author ydsz-pmis-team
 */
@DisplayName("表达式校验服务测试")
@ExtendWith(MockitoExtension.class)
class ExpressionValidationServiceTest {

    @Mock
    private ExpressionEvaluator evaluator;

    @Mock
    private VariableRegistry variableRegistry;

    private ExpressionValidationService serviceWithEmptyRegistry;
    private ExpressionValidationService serviceWithRegistry;

    @BeforeEach
    void setUp() {
        serviceWithEmptyRegistry = new ExpressionValidationService(evaluator);
        serviceWithRegistry = new ExpressionValidationService(evaluator, variableRegistry);
    }

    private ExpressionValidationResult okResult(String expr, String... vars) {
        return ExpressionValidationResult.ok(expr, 5L, List.of(vars));
    }

    private ExpressionValidationResult failResult(String expr, ExpressionValidationResult.ErrorType type,
                                                    String message) {
        return ExpressionValidationResult.fail(expr, type, message, 3L);
    }

    // ==================== validateCondition ====================

    @Nested
    @DisplayName("条件表达式校验：validateCondition")
    class ValidateConditionTest {

        @Test
        @DisplayName("正常场景：语法通过且无变量未定义")
        void shouldReturnOkWhenSyntaxValidAndVariablesDefined() {
            String expr = "amount > 1000";
            when(evaluator.validateDetailed(expr)).thenReturn(okResult(expr, "amount"));

            ExpressionValidationResult result = serviceWithEmptyRegistry.validateCondition(expr);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrorType()).isEqualTo(ExpressionValidationResult.ErrorType.OK);
            assertThat(result.getReferencedVariables()).containsExactly("amount");
        }

        @Test
        @DisplayName("异常场景：语法错误直接返回，不进入变量校验")
        void shouldReturnSyntaxErrorDirectly() {
            String expr = "amount > > 1000";
            ExpressionValidationResult fail = failResult(expr,
                    ExpressionValidationResult.ErrorType.SYNTAX_ERROR, "语法错误");
            when(evaluator.validateDetailed(expr)).thenReturn(fail);

            ExpressionValidationResult result = serviceWithRegistry.validateCondition(expr);

            assertThat(result).isSameAs(fail);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorType()).isEqualTo(ExpressionValidationResult.ErrorType.SYNTAX_ERROR);
        }

        @Test
        @DisplayName("异常场景：registry 为空时跳过未定义变量校验")
        void shouldSkipUndefinedCheckWhenRegistryEmpty() {
            String expr = "amount > 1000";
            when(evaluator.validateDetailed(expr)).thenReturn(okResult(expr, "amount"));

            ExpressionValidationResult result = serviceWithEmptyRegistry.validateCondition(expr);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("异常场景：registry 非空且变量未注册时返回 UNDEFINED_VARIABLE")
        void shouldReturnUndefinedWhenVariableNotRegistered() {
            String expr = "amount > 1000";
            when(evaluator.validateDetailed(expr)).thenReturn(okResult(expr, "amount", "score"));
            when(variableRegistry.isEmpty()).thenReturn(false);
            when(variableRegistry.contains("amount")).thenReturn(true);
            when(variableRegistry.contains("score")).thenReturn(false);

            ExpressionValidationResult result = serviceWithRegistry.validateCondition(expr);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorType()).isEqualTo(ExpressionValidationResult.ErrorType.UNDEFINED_VARIABLE);
            assertThat(result.getErrorMessage()).contains("score");
            assertThat(result.getReferencedVariables()).containsExactly("amount", "score");
        }

        @Test
        @DisplayName("正常场景：registry 非空且全部变量已注册时通过")
        void shouldPassWhenAllVariablesRegistered() {
            String expr = "amount > 1000 && score < 800";
            when(evaluator.validateDetailed(expr)).thenReturn(okResult(expr, "amount", "score"));
            when(variableRegistry.isEmpty()).thenReturn(false);
            when(variableRegistry.contains("amount")).thenReturn(true);
            when(variableRegistry.contains("score")).thenReturn(true);

            ExpressionValidationResult result = serviceWithRegistry.validateCondition(expr);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrorType()).isEqualTo(ExpressionValidationResult.ErrorType.OK);
        }
    }

    // ==================== validateSeverity ====================

    @Nested
    @DisplayName("严重度表达式校验：validateSeverity")
    class ValidateSeverityTest {

        @Test
        @DisplayName("边界条件：null 表达式返回 ok")
        void shouldReturnOkWhenExpressionNull() {
            ExpressionValidationResult result = serviceWithRegistry.validateSeverity(null);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrorType()).isEqualTo(ExpressionValidationResult.ErrorType.OK);
            assertThat(result.getReferencedVariables()).isEmpty();
        }

        @Test
        @DisplayName("边界条件：空白表达式返回 ok")
        void shouldReturnOkWhenExpressionBlank() {
            ExpressionValidationResult result = serviceWithRegistry.validateSeverity("   ");

            assertThat(result.isValid()).isTrue();
            assertThat(result.getReferencedVariables()).isEmpty();
        }

        @Test
        @DisplayName("正常场景：语法通过")
        void shouldReturnOkWhenSyntaxValid() {
            String expr = "\"RED\"";
            when(evaluator.validateDetailed(expr)).thenReturn(okResult(expr));

            ExpressionValidationResult result = serviceWithRegistry.validateSeverity(expr);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("异常场景：语法错误")
        void shouldReturnFailWhenSyntaxError() {
            String expr = "RED\"";
            ExpressionValidationResult fail = failResult(expr,
                    ExpressionValidationResult.ErrorType.SYNTAX_ERROR, "引号未闭合");
            when(evaluator.validateDetailed(expr)).thenReturn(fail);

            ExpressionValidationResult result = serviceWithRegistry.validateSeverity(expr);

            assertThat(result).isSameAs(fail);
            assertThat(result.isValid()).isFalse();
        }
    }

    // ==================== validateTemplate ====================

    @Nested
    @DisplayName("模板表达式校验：validateTemplate")
    class ValidateTemplateTest {

        @Test
        @DisplayName("边界条件：null 模板返回 ok")
        void shouldReturnOkWhenTemplateNull() {
            ExpressionValidationResult result = serviceWithRegistry.validateTemplate(null);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("边界条件：空白模板返回 ok")
        void shouldReturnOkWhenTemplateBlank() {
            ExpressionValidationResult result = serviceWithRegistry.validateTemplate("  ");

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("异常场景：未闭合占位符返回 TEMPLATE_FORMAT_ERROR")
        void shouldReturnTemplateFormatErrorWhenUnclosed() {
            String template = "金额 ${amount 超过阈值";

            ExpressionValidationResult result = serviceWithRegistry.validateTemplate(template);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorType()).isEqualTo(ExpressionValidationResult.ErrorType.TEMPLATE_FORMAT_ERROR);
            assertThat(result.getErrorMessage()).contains("未闭合");
        }

        @Test
        @DisplayName("正常场景：提取多个变量且全部已注册")
        void shouldExtractVariablesAndPassWhenRegistered() {
            String template = "${amount} 超过 ${threshold}";
            when(variableRegistry.isEmpty()).thenReturn(false);
            when(variableRegistry.contains("amount")).thenReturn(true);
            when(variableRegistry.contains("threshold")).thenReturn(true);

            ExpressionValidationResult result = serviceWithRegistry.validateTemplate(template);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getReferencedVariables()).containsExactly("amount", "threshold");
        }

        @Test
        @DisplayName("异常场景：模板变量未注册返回 UNDEFINED_VARIABLE")
        void shouldReturnUndefinedWhenTemplateVarNotRegistered() {
            String template = "${amount} 超过 ${threshold}";
            when(variableRegistry.isEmpty()).thenReturn(false);
            when(variableRegistry.contains("amount")).thenReturn(true);
            when(variableRegistry.contains("threshold")).thenReturn(false);

            ExpressionValidationResult result = serviceWithRegistry.validateTemplate(template);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorType()).isEqualTo(ExpressionValidationResult.ErrorType.UNDEFINED_VARIABLE);
            assertThat(result.getErrorMessage()).contains("threshold");
        }

        @Test
        @DisplayName("正常场景：空 registry 时仅提取变量不做未定义校验")
        void shouldExtractVariablesOnlyWhenRegistryEmpty() {
            String template = "${amount} 超过 ${threshold}";

            ExpressionValidationResult result = serviceWithEmptyRegistry.validateTemplate(template);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getReferencedVariables()).containsExactly("amount", "threshold");
        }

        @Test
        @DisplayName("边界条件：占位符内仅空白时不提取变量")
        void shouldNotExtractBlankVariable() {
            String template = "金额 ${   } 超过阈值";

            ExpressionValidationResult result = serviceWithEmptyRegistry.validateTemplate(template);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getReferencedVariables()).isEmpty();
        }
    }

    // ==================== validateBatch ====================

    @Nested
    @DisplayName("批量校验：validateBatch")
    class ValidateBatchTest {

        @Test
        @DisplayName("边界条件：null 入参返回空 Map")
        void shouldReturnEmptyMapWhenInputNull() {
            Map<String, ExpressionValidationResult> results = serviceWithRegistry.validateBatch(null);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("正常场景：批量校验全部通过")
        void shouldValidateAllExpressions() {
            Map<String, String> inputs = new LinkedHashMap<>();
            inputs.put("cond", "amount > 1000");
            inputs.put("sev", "\"RED\"");
            when(evaluator.validateDetailed("amount > 1000"))
                    .thenReturn(okResult("amount > 1000", "amount"));
            when(evaluator.validateDetailed("\"RED\""))
                    .thenReturn(okResult("\"RED\""));

            Map<String, ExpressionValidationResult> results = serviceWithEmptyRegistry.validateBatch(inputs);

            assertThat(results).hasSize(2);
            assertThat(results.get("cond").isValid()).isTrue();
            assertThat(results.get("sev").isValid()).isTrue();
        }

        @Test
        @DisplayName("异常场景：单条表达式校验抛异常时返回 UNKNOWN 错误")
        void shouldReturnUnknownWhenExceptionThrown() {
            Map<String, String> inputs = new LinkedHashMap<>();
            inputs.put("cond", "amount > 1000");
            when(evaluator.validateDetailed("amount > 1000"))
                    .thenThrow(new RuntimeException("内部异常"));

            Map<String, ExpressionValidationResult> results = serviceWithRegistry.validateBatch(inputs);

            assertThat(results).hasSize(1);
            ExpressionValidationResult result = results.get("cond");
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorType()).isEqualTo(ExpressionValidationResult.ErrorType.UNKNOWN);
            assertThat(result.getErrorMessage()).contains("校验异常");
        }

        @Test
        @DisplayName("正常场景：语法通过时叠加变量校验")
        void shouldRunUndefinedCheckAfterSyntaxPass() {
            Map<String, String> inputs = new LinkedHashMap<>();
            inputs.put("cond", "amount > 1000");
            when(evaluator.validateDetailed("amount > 1000"))
                    .thenReturn(okResult("amount > 1000", "amount"));
            when(variableRegistry.isEmpty()).thenReturn(false);
            when(variableRegistry.contains("amount")).thenReturn(false);

            Map<String, ExpressionValidationResult> results = serviceWithRegistry.validateBatch(inputs);

            assertThat(results.get("cond").isValid()).isFalse();
            assertThat(results.get("cond").getErrorType())
                    .isEqualTo(ExpressionValidationResult.ErrorType.UNDEFINED_VARIABLE);
        }
    }

    // ==================== 变量查询方法 ====================

    @Nested
    @DisplayName("变量查询方法")
    class VariableQueryTest {

        @Test
        @DisplayName("正常场景：listAvailableVariables 委托给 registry")
        void shouldDelegateListAllToRegistry() {
            VariableDefinition def = VariableDefinition.builder()
                    .name("amount").type("java.lang.Number").category("EVM").build();
            when(variableRegistry.listAll()).thenReturn(List.of(def));

            List<VariableDefinition> result = serviceWithRegistry.listAvailableVariables();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("amount");
        }

        @Test
        @DisplayName("正常场景：listVariablesByCategory 委托给 registry")
        void shouldDelegateListByCategoryToRegistry() {
            VariableDefinition def = VariableDefinition.builder()
                    .name("amount").category("EVM").build();
            when(variableRegistry.listByCategory("EVM")).thenReturn(List.of(def));

            List<VariableDefinition> result = serviceWithRegistry.listVariablesByCategory("EVM");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCategory()).isEqualTo("EVM");
        }

        @Test
        @DisplayName("正常场景：getVariableRegistry 返回注入的 registry")
        void shouldReturnInjectedRegistry() {
            assertThat(serviceWithRegistry.getVariableRegistry()).isSameAs(variableRegistry);
        }

        @Test
        @DisplayName("正常场景：EmptyVariableRegistry 实例可被获取")
        void shouldReturnEmptyRegistryWhenUsingSingleArgConstructor() {
            VariableRegistry registry = serviceWithEmptyRegistry.getVariableRegistry();

            assertThat(registry).isInstanceOf(EmptyVariableRegistry.class);
            assertThat(registry.isEmpty()).isTrue();
        }
    }

    // ==================== previewEvaluate ====================

    @Nested
    @DisplayName("表达式求值预览：previewEvaluate")
    class PreviewEvaluateTest {

        @Test
        @DisplayName("边界条件：null 表达式返回错误")
        void shouldReturnErrorWhenExpressionNull() {
            ExpressionPreviewResult result = serviceWithRegistry.previewEvaluate(null, Map.of());

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getError()).isEqualTo("表达式为空");
        }

        @Test
        @DisplayName("边界条件：空白表达式返回错误")
        void shouldReturnErrorWhenExpressionBlank() {
            ExpressionPreviewResult result = serviceWithRegistry.previewEvaluate("   ", Map.of());

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getError()).isEqualTo("表达式为空");
        }

        @Test
        @DisplayName("异常场景：语法错误返回错误")
        void shouldReturnErrorWhenSyntaxInvalid() {
            String expr = "amount > > 1000";
            ExpressionValidationResult fail = failResult(expr,
                    ExpressionValidationResult.ErrorType.SYNTAX_ERROR, "语法错误");
            when(evaluator.validateDetailed(expr)).thenReturn(fail);

            ExpressionPreviewResult result = serviceWithRegistry.previewEvaluate(expr, Map.of());

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getError()).contains("语法错误");
        }

        @Test
        @DisplayName("正常场景：求值布尔值返回 booleanValue")
        void shouldReturnBooleanValue() {
            String expr = "amount > 1000";
            when(evaluator.validateDetailed(expr)).thenReturn(okResult(expr));
            when(evaluator.eval(eq(expr), any())).thenReturn(true);

            ExpressionPreviewResult result = serviceWithRegistry.previewEvaluate(expr, Map.of("amount", 2000));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getValue()).isEqualTo("true");
            assertThat(result.getJavaType()).isEqualTo("Boolean");
            assertThat(result.getBooleanValue()).isTrue();
        }

        @Test
        @DisplayName("正常场景：求值 null 值")
        void shouldHandleNullValue() {
            String expr = "undefined_var";
            when(evaluator.validateDetailed(expr)).thenReturn(okResult(expr));
            when(evaluator.eval(eq(expr), any())).thenReturn(null);

            ExpressionPreviewResult result = serviceWithRegistry.previewEvaluate(expr, Map.of());

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getValue()).isEqualTo("null");
            assertThat(result.getJavaType()).isEqualTo("null");
            assertThat(result.getBooleanValue()).isNull();
        }

        @Test
        @DisplayName("异常场景：求值抛异常时返回错误")
        void shouldReturnErrorWhenEvalThrowsException() {
            String expr = "amount / 0";
            when(evaluator.validateDetailed(expr)).thenReturn(okResult(expr));
            when(evaluator.eval(eq(expr), any())).thenThrow(new RuntimeException("除零错误"));

            ExpressionPreviewResult result = serviceWithRegistry.previewEvaluate(expr, Map.of());

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getError()).contains("求值失败");
        }

        @Test
        @DisplayName("正常场景：null facts 视为空 Map")
        void shouldHandleNullFacts() {
            String expr = "true";
            when(evaluator.validateDetailed(expr)).thenReturn(okResult(expr));
            when(evaluator.eval(eq(expr), any())).thenReturn(true);

            ExpressionPreviewResult result = serviceWithRegistry.previewEvaluate(expr, null);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getBooleanValue()).isTrue();
        }

        @Test
        @DisplayName("正常场景：求值字符串值")
        void shouldReturnStringValue() {
            String expr = "\"hello\"";
            when(evaluator.validateDetailed(expr)).thenReturn(okResult(expr));
            when(evaluator.eval(eq(expr), any())).thenReturn("hello");

            ExpressionPreviewResult result = serviceWithRegistry.previewEvaluate(expr, Map.of());

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getValue()).isEqualTo("hello");
            assertThat(result.getJavaType()).isEqualTo("String");
            assertThat(result.getBooleanValue()).isNull();
        }
    }
}
