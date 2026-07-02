package com.njydsz.pmis.literule.expr;

import com.njydsz.pmis.literule.api.RuleContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AviatorExpressionEvaluator 单元测试
 *
 * @author ydsz-pmis-team
 */
@DisplayName("AviatorExpressionEvaluator 表达式求值器测试")
class AviatorExpressionEvaluatorTest {

    private AviatorExpressionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new AviatorExpressionEvaluator();
    }

    @Test
    @DisplayName("布尔表达式求值 - 基本比较")
    void testEvalBooleanBasic() {
        RuleContext ctx = RuleContext.of(Map.of("count", 5, "threshold", 3));
        assertTrue(evaluator.evalBoolean("count >= threshold", ctx));
        assertFalse(evaluator.evalBoolean("count < threshold", ctx));
    }

    @Test
    @DisplayName("布尔表达式求值 - 复合条件")
    void testEvalBooleanComplex() {
        RuleContext ctx = RuleContext.of(Map.of(
                "grossMargin", 0.03,
                "confirmedRevenue", 100000
        ));
        // 毛利率 < 0.05 且 有收入
        assertTrue(evaluator.evalBoolean("grossMargin < 0.05 && confirmedRevenue > 0", ctx));
        // 毛利率 < 0.01
        assertFalse(evaluator.evalBoolean("grossMargin < 0.01", ctx));
    }

    @Test
    @DisplayName("布尔表达式 - 动态严重度")
    void testEvalSeverityExpression() {
        RuleContext ctx = RuleContext.of(Map.of("benchIdleCost", 1200000));
        String severity = evaluator.eval("benchIdleCost >= 1000000 ? 'RED' : 'YELLOW'", ctx);
        assertEquals("RED", severity);

        RuleContext ctx2 = RuleContext.of(Map.of("benchIdleCost", 600000));
        String severity2 = evaluator.eval("benchIdleCost >= 1000000 ? 'RED' : 'YELLOW'", ctx2);
        assertEquals("YELLOW", severity2);
    }

    @Test
    @DisplayName("空表达式返回 false")
    void testEmptyExpression() {
        RuleContext ctx = RuleContext.of(Map.of());
        assertFalse(evaluator.evalBoolean("", ctx));
        assertFalse(evaluator.evalBoolean(null, ctx));
    }

    @Test
    @DisplayName("非法表达式返回 false（异常隔离）")
    void testInvalidExpression() {
        RuleContext ctx = RuleContext.of(Map.of());
        assertFalse(evaluator.evalBoolean("this is not valid !!!", ctx));
    }

    @Test
    @DisplayName("表达式校验")
    void testValidate() {
        assertTrue(evaluator.validate("a > b && c < d"));
        assertTrue(evaluator.validate("x >= 100"));
        assertFalse(evaluator.validate("func("));
        assertFalse(evaluator.validate(""));
        assertFalse(evaluator.validate(null));
    }

    @Test
    @DisplayName("编译缓存工作正常")
    void testCache() {
        RuleContext ctx = RuleContext.of(Map.of("x", 10));
        evaluator.evalBoolean("x > 5", ctx);
        evaluator.evalBoolean("x > 5", ctx);
        assertEquals(1, evaluator.cacheSize());
    }
}
