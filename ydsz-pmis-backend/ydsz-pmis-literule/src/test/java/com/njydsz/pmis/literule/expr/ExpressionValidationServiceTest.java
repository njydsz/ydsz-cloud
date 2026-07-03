package com.njydsz.pmis.literule.expr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 表达式校验服务单元测试
 *
 * <p>覆盖：
 * <ul>
 *   <li>合法条件表达式 → valid=true + referencedVariables 提取</li>
 *   <li>空表达式 → EMPTY</li>
 *   <li>语法错误 → SYNTAX_ERROR</li>
 *   <li>沙箱拦截 → SANDBOX_VIOLATION</li>
 *   <li>严重度表达式为空 → valid=true（可选字段）</li>
 *   <li>模板表达式合法 → valid=true + referencedVariables 包含占位符变量</li>
 *   <li>模板表达式未闭合 → TEMPLATE_FORMAT_ERROR</li>
 *   <li>批量校验</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
class ExpressionValidationServiceTest {

    private ExpressionValidationService service;

    @BeforeEach
    void setUp() {
        // 启用沙箱测试，确保沙箱拦截场景可复现
        AviatorExpressionEvaluator evaluator = new AviatorExpressionEvaluator(true);
        service = new ExpressionValidationService(evaluator);
    }

    // ---------- 场景 1：合法条件表达式 ----------

    @Test
    void validConditionShouldReturnOkWithReferencedVariables() {
        ExpressionValidationResult result = service.validateCondition("evmRedCount >= 3 && grossMargin < 0.05");

        assertTrue(result.isValid());
        assertEquals(ExpressionValidationResult.ErrorType.OK, result.getErrorType());
        assertNotNull(result.getReferencedVariables());
        assertTrue(result.getReferencedVariables().contains("evmRedCount"));
        assertTrue(result.getReferencedVariables().contains("grossMargin"));
    }

    // ---------- 场景 2：空表达式 ----------

    @Test
    void emptyExpressionShouldReturnEmptyType() {
        ExpressionValidationResult result = service.validateCondition("");

        assertFalse(result.isValid());
        assertEquals(ExpressionValidationResult.ErrorType.EMPTY, result.getErrorType());
    }

    @Test
    void nullExpressionShouldReturnEmptyType() {
        ExpressionValidationResult result = service.validateCondition(null);

        assertFalse(result.isValid());
        assertEquals(ExpressionValidationResult.ErrorType.EMPTY, result.getErrorType());
    }

    @Test
    void blankExpressionShouldReturnEmptyType() {
        ExpressionValidationResult result = service.validateCondition("   ");

        assertFalse(result.isValid());
        assertEquals(ExpressionValidationResult.ErrorType.EMPTY, result.getErrorType());
    }

    // ---------- 场景 3：语法错误 ----------

    @Test
    void syntaxErrorShouldReturnSyntaxErrorType() {
        // 缺少右操作数
        ExpressionValidationResult result = service.validateCondition("a > ");

        assertFalse(result.isValid());
        assertEquals(ExpressionValidationResult.ErrorType.SYNTAX_ERROR, result.getErrorType());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void unbalancedParenthesesShouldReturnSyntaxError() {
        ExpressionValidationResult result = service.validateCondition("(a > 1 && b < 2");

        assertFalse(result.isValid());
        assertEquals(ExpressionValidationResult.ErrorType.SYNTAX_ERROR, result.getErrorType());
    }

    // ---------- 场景 4：沙箱拦截 ----------

    @Test
    void sandboxViolationShouldReturnSandboxViolationType() {
        ExpressionValidationResult result = service.validateCondition("Runtime.getRuntime().exec('rm -rf /')");

        assertFalse(result.isValid());
        assertEquals(ExpressionValidationResult.ErrorType.SANDBOX_VIOLATION, result.getErrorType());
    }

    @Test
    void classForNameShouldBeBlockedBySandbox() {
        ExpressionValidationResult result = service.validateCondition("Class.forName('java.lang.Runtime')");

        assertFalse(result.isValid());
        assertEquals(ExpressionValidationResult.ErrorType.SANDBOX_VIOLATION, result.getErrorType());
    }

    // ---------- 场景 5：严重度表达式（可选） ----------

    @Test
    void nullSeverityExpressionShouldBeValid() {
        // 严重度表达式为空时使用 defaultSeverity，应视为合法
        ExpressionValidationResult result = service.validateSeverity(null);

        assertTrue(result.isValid());
        assertEquals(ExpressionValidationResult.ErrorType.OK, result.getErrorType());
    }

    @Test
    void validSeverityExpressionShouldReturnOk() {
        ExpressionValidationResult result = service.validateSeverity("amount > 5000 ? 'RED' : 'YELLOW'");

        assertTrue(result.isValid());
        assertEquals(ExpressionValidationResult.ErrorType.OK, result.getErrorType());
        assertTrue(result.getReferencedVariables().contains("amount"));
    }

    // ---------- 场景 6：模板表达式合法 ----------

    @Test
    void validTemplateShouldReturnOkWithReferencedVars() {
        ExpressionValidationResult result = service.validateTemplate("项目 ${projectName} 的红色预警数量为 ${evmRedCount}");

        assertTrue(result.isValid());
        assertEquals(ExpressionValidationResult.ErrorType.OK, result.getErrorType());
        assertTrue(result.getReferencedVariables().contains("projectName"));
        assertTrue(result.getReferencedVariables().contains("evmRedCount"));
    }

    @Test
    void templateWithoutPlaceholderShouldBeValid() {
        ExpressionValidationResult result = service.validateTemplate("纯文本模板，无占位符");

        assertTrue(result.isValid());
        assertEquals(ExpressionValidationResult.ErrorType.OK, result.getErrorType());
        assertTrue(result.getReferencedVariables().isEmpty());
    }

    @Test
    void nullTemplateShouldBeValid() {
        ExpressionValidationResult result = service.validateTemplate(null);

        assertTrue(result.isValid());
    }

    // ---------- 场景 7：模板未闭合 ----------

    @Test
    void unclosedPlaceholderShouldReturnTemplateFormatError() {
        ExpressionValidationResult result = service.validateTemplate("项目 ${projectName 的状态");

        assertFalse(result.isValid());
        assertEquals(ExpressionValidationResult.ErrorType.TEMPLATE_FORMAT_ERROR, result.getErrorType());
        assertTrue(result.getErrorMessage().contains("未闭合"));
    }

    @Test
    void multipleUnclosedPlaceholdersShouldReturnTemplateFormatError() {
        ExpressionValidationResult result = service.validateTemplate("价格 ${price 数量 ${quantity");

        assertFalse(result.isValid());
        assertEquals(ExpressionValidationResult.ErrorType.TEMPLATE_FORMAT_ERROR, result.getErrorType());
    }

    // ---------- 场景 8：批量校验 ----------

    @Test
    void batchValidateShouldReturnResultsForEachExpression() {
        Map<String, String> expressions = new LinkedHashMap<>();
        expressions.put("condition1", "a > 1");
        expressions.put("condition2", "b < 0");
        expressions.put("invalid", "c > ");
        expressions.put("empty", "");

        Map<String, ExpressionValidationResult> results = service.validateBatch(expressions);

        assertEquals(4, results.size());
        assertTrue(results.get("condition1").isValid());
        assertTrue(results.get("condition2").isValid());
        assertFalse(results.get("invalid").isValid());
        assertEquals(ExpressionValidationResult.ErrorType.SYNTAX_ERROR, results.get("invalid").getErrorType());
        assertFalse(results.get("empty").isValid());
        assertEquals(ExpressionValidationResult.ErrorType.EMPTY, results.get("empty").getErrorType());
    }

    @Test
    void batchValidateNullMapShouldReturnEmpty() {
        Map<String, ExpressionValidationResult> results = service.validateBatch(null);

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    // ---------- 场景 9：变量提取过滤 Aviator 关键字 ----------

    @Test
    void shouldNotExtractAviatorKeywordsAsVariables() {
        ExpressionValidationResult result = service.validateCondition("a > 1 && true");

        assertTrue(result.isValid());
        assertTrue(result.getReferencedVariables().contains("a"));
        // 'true' 是 Aviator 关键字，不应被提取为变量
        assertFalse(result.getReferencedVariables().contains("true"));
    }

    @Test
    void shouldExtractCamelCaseVariables() {
        ExpressionValidationResult result = service.validateCondition("evmRedCount >= benchIdleCost");

        assertTrue(result.isValid());
        List<String> vars = result.getReferencedVariables();
        assertTrue(vars.contains("evmRedCount"));
        assertTrue(vars.contains("benchIdleCost"));
    }

    // ---------- 场景 10：parseTimeMs 记录耗时 ----------

    @Test
    void parseTimeMsShouldBeNonNegative() {
        ExpressionValidationResult result = service.validateCondition("a > 1");

        assertTrue(result.isValid());
        assertTrue(result.getParseTimeMs() >= 0, "parseTimeMs 应为非负数");
    }
}
