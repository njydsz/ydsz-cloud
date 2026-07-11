package com.njydsz.pmis.literule.expr.liteexpr;

import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.expr.ExpressionEvaluator;
import com.njydsz.pmis.literule.expr.ExpressionValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LiteExpr 自研表达式引擎单元测试
 *
 * <p>覆盖 Lexer、Parser、Interpreter、Evaluator、沙箱、函数库全链路。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
@DisplayName("LiteExpr 表达式引擎")
class LiteExprEvaluatorTest {

    private ExpressionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new LiteExprEvaluator(true);
    }

    // ===== 基础求值 =====

    @Nested
    @DisplayName("基础求值")
    class BasicEvaluation {

        @Test
        @DisplayName("整数算术")
        void testIntegerArithmetic() {
            assertEquals(3, ((Number) evaluator.eval("1 + 2", ctx(Map.of()))).intValue());
            assertEquals(6, ((Number) evaluator.eval("2 * 3", ctx(Map.of()))).intValue());
            assertEquals(7, ((Number) evaluator.eval("1 + 2 * 3", ctx(Map.of()))).intValue());
            assertEquals(2, ((Number) evaluator.eval("10 % 4", ctx(Map.of()))).intValue());
        }

        @Test
        @DisplayName("浮点算术")
        void testDecimalArithmetic() {
            Object result = evaluator.eval("0.1 + 0.2", ctx(Map.of()));
            assertInstanceOf(BigDecimal.class, result);
            assertEquals(0, new BigDecimal("0.3").compareTo((BigDecimal) result));
        }

        @Test
        @DisplayName("字符串拼接")
        void testStringConcat() {
            assertEquals("Hello World", evaluator.eval("\"Hello\" + \" \" + \"World\"", ctx(Map.of())));
        }

        @Test
        @DisplayName("布尔运算")
        void testBoolean() {
            assertTrue(evaluator.evalBoolean("true", ctx(Map.of())));
            assertFalse(evaluator.evalBoolean("false", ctx(Map.of())));
            assertTrue(evaluator.evalBoolean("true && true", ctx(Map.of())));
            assertFalse(evaluator.evalBoolean("true && false", ctx(Map.of())));
            assertTrue(evaluator.evalBoolean("false || true", ctx(Map.of())));
            assertTrue(evaluator.evalBoolean("!false", ctx(Map.of())));
        }

        @Test
        @DisplayName("比较运算")
        void testComparison() {
            assertTrue(evaluator.evalBoolean("1 < 2", ctx(Map.of())));
            assertTrue(evaluator.evalBoolean("2 >= 2", ctx(Map.of())));
            assertTrue(evaluator.evalBoolean("1 != 2", ctx(Map.of())));
            assertFalse(evaluator.evalBoolean("3 == 4", ctx(Map.of())));
        }

        @Test
        @DisplayName("三元表达式")
        void testTernary() {
            assertEquals("yes", evaluator.eval("1 > 0 ? \"yes\" : \"no\"", ctx(Map.of())));
            assertEquals("no", evaluator.eval("1 < 0 ? \"yes\" : \"no\"", ctx(Map.of())));
        }
    }

    // ===== 变量引用 =====

    @Nested
    @DisplayName("变量引用")
    class VariableAccess {

        @Test
        @DisplayName("基本变量引用")
        void testVariable() {
            Map<String, Object> facts = Map.of("amount", 1500, "score", 800);
            assertTrue(evaluator.evalBoolean("amount > 1000", ctx(facts)));
            assertFalse(evaluator.evalBoolean("score < 500", ctx(facts)));
        }

        @Test
        @DisplayName("组合条件")
        void testCombinedCondition() {
            Map<String, Object> facts = Map.of("amount", 1500, "score", 800, "type", "CAPEX");
            assertTrue(evaluator.evalBoolean("amount > 1000 && score > 700", ctx(facts)));
            assertTrue(evaluator.evalBoolean("amount > 1000 && type == \"CAPEX\"", ctx(facts)));
            assertFalse(evaluator.evalBoolean("amount < 1000 || score < 700", ctx(facts)));
        }

        @Test
        @DisplayName("属性访问")
        void testMemberAccess() {
            Map<String, Object> facts = Map.of("user", Map.of("name", "Alice", "age", 30));
            assertEquals("Alice", evaluator.eval("user.name", ctx(facts)));
            assertEquals(30, ((Number) evaluator.eval("user.age", ctx(facts))).intValue());
        }

        @Test
        @DisplayName("空值安全")
        void testNullSafety() {
            Map<String, Object> facts = new java.util.HashMap<>();
            facts.put("missing", null);
            assertNull(evaluator.eval("missing.field", ctx(facts)));
        }
    }

    // ===== 短路求值 =====

    @Nested
    @DisplayName("短路求值")
    class ShortCircuit {

        @Test
        @DisplayName("AND 短路")
        void testAndShortCircuit() {
            // 左侧 false 时右侧不应求值
            assertFalse(evaluator.evalBoolean("false && true", ctx(Map.of())));
            assertFalse(evaluator.evalBoolean("1 > 2 && 3 > 0", ctx(Map.of())));
        }

        @Test
        @DisplayName("OR 短路")
        void testOrShortCircuit() {
            // 左侧 true 时右侧不应求值
            assertTrue(evaluator.evalBoolean("true || false", ctx(Map.of())));
            assertTrue(evaluator.evalBoolean("1 < 2 || 3 < 0", ctx(Map.of())));
        }
    }

    // ===== 函数调用 =====

    @Nested
    @DisplayName("函数调用")
    class FunctionCalls {

        @Test
        @DisplayName("数学函数")
        void testMathFunctions() {
            assertEquals(5, ((Number) evaluator.eval("abs(-5)", ctx(Map.of()))).intValue());
            assertEquals(3, ((Number) evaluator.eval("max(1, 2, 3)", ctx(Map.of()))).intValue());
            assertEquals(1, ((Number) evaluator.eval("min(1, 2, 3)", ctx(Map.of()))).intValue());
            assertEquals(3.14, evaluator.<BigDecimal>eval("round(3.14159, 2)", ctx(Map.of())).doubleValue());
        }

        @Test
        @DisplayName("字符串函数")
        void testStringFunctions() {
            assertEquals("HELLO", evaluator.eval("upper(\"hello\")", ctx(Map.of())));
            assertEquals("world", evaluator.eval("lower(\"WORLD\")", ctx(Map.of())));
            assertEquals(5, evaluator.<Integer>eval("length(\"hello\")", ctx(Map.of())));
            assertTrue(evaluator.evalBoolean("contains(\"hello world\", \"world\")", ctx(Map.of())));
            assertTrue(evaluator.evalBoolean("startsWith(\"hello\", \"he\")", ctx(Map.of())));
        }

        @Test
        @DisplayName("类型转换函数")
        void testTypeFunctions() {
            assertTrue(evaluator.evalBoolean("isNull(null)", ctx(Map.of())));
            assertFalse(evaluator.evalBoolean("isNotNull(null)", ctx(Map.of())));
            assertEquals("42", evaluator.<String>eval("toString(42)", ctx(Map.of())));
        }

        @Test
        @DisplayName("if 函数")
        void testIfFunction() {
            assertEquals("positive", evaluator.eval("if(1 > 0, \"positive\", \"negative\")", ctx(Map.of())));
            assertEquals("negative", evaluator.eval("if(1 < 0, \"positive\", \"negative\")", ctx(Map.of())));
        }

        @Test
        @DisplayName("自定义函数注册")
        void testCustomFunction() {
            LiteExprEvaluator liteEval = (LiteExprEvaluator) evaluator;
            liteEval.getFunctionRegistry().register("double", args -> BuiltinFunctions.toLong(args[0]) * 2);
            assertEquals(10L, evaluator.<Long>eval("double(5)", ctx(Map.of())));
        }
    }

    // ===== 校验 =====

    @Nested
    @DisplayName("表达式校验")
    class Validation {

        @Test
        @DisplayName("合法表达式")
        void testValidExpression() {
            ExpressionValidationResult result = evaluator.validateDetailed("amount > 1000 && score > 800");
            assertTrue(result.isValid());
            assertTrue(result.getReferencedVariables().contains("amount"));
            assertTrue(result.getReferencedVariables().contains("score"));
        }

        @Test
        @DisplayName("空表达式")
        void testEmptyExpression() {
            ExpressionValidationResult result = evaluator.validateDetailed("");
            assertFalse(result.isValid());
            assertEquals(ExpressionValidationResult.ErrorType.EMPTY, result.getErrorType());
        }

        @Test
        @DisplayName("语法错误")
        void testSyntaxError() {
            ExpressionValidationResult result = evaluator.validateDetailed("1 + + 2");
            assertFalse(result.isValid());
            assertEquals(ExpressionValidationResult.ErrorType.SYNTAX_ERROR, result.getErrorType());
            assertTrue(result.getErrorLine() > 0);
        }

        @Test
        @DisplayName("未闭合括号")
        void testUnclosedParen() {
            ExpressionValidationResult result = evaluator.validateDetailed("(1 + 2");
            assertFalse(result.isValid());
            assertEquals(ExpressionValidationResult.ErrorType.SYNTAX_ERROR, result.getErrorType());
        }
    }

    // ===== 常量折叠 =====

    @Nested
    @DisplayName("常量折叠")
    class ConstantFolding {

        @Test
        @DisplayName("算术常量折叠")
        void testArithmeticFolding() {
            LiteExprCompiler compiler = new LiteExprCompiler();
            ExprNode ast = compiler.compile("1 + 2 * 3");
            // 折叠后应为 LiteralNode(7)
            assertInstanceOf(LiteralNode.class, ast, "1+2*3 应被折叠为字面值");
            assertEquals(7, ((Number) ((LiteralNode) ast).value()).intValue());
        }

        @Test
        @DisplayName("逻辑常量折叠")
        void testLogicalFolding() {
            LiteExprCompiler compiler = new LiteExprCompiler();
            ExprNode ast = compiler.compile("true && false");
            assertInstanceOf(LiteralNode.class, ast);
            assertEquals(false, ((LiteralNode) ast).value());
        }

        @Test
        @DisplayName("字符串常量折叠")
        void testStringFolding() {
            LiteExprCompiler compiler = new LiteExprCompiler();
            ExprNode ast = compiler.compile("\"a\" + \"b\" + \"c\"");
            assertInstanceOf(LiteralNode.class, ast);
            assertEquals("abc", ((LiteralNode) ast).value());
        }
    }

    // ===== 沙箱 =====

    @Nested
    @DisplayName("沙箱安全")
    class SandboxSecurity {

        @Test
        @DisplayName("阻断 System 类访问")
        void testSystemAccessBlocked() {
            ExpressionValidationResult result = evaluator.validateDetailed("System.exit(0)");
            assertFalse(result.isValid());
            assertEquals(ExpressionValidationResult.ErrorType.SANDBOX_VIOLATION, result.getErrorType());
        }

        @Test
        @DisplayName("阻断 Runtime 类访问")
        void testRuntimeAccessBlocked() {
            ExpressionValidationResult result = evaluator.validateDetailed("Runtime.getRuntime()");
            assertFalse(result.isValid());
            assertEquals(ExpressionValidationResult.ErrorType.SANDBOX_VIOLATION, result.getErrorType());
        }

        @Test
        @DisplayName("阻断 Class.forName")
        void testForNameBlocked() {
            ExpressionValidationResult result = evaluator.validateDetailed("Class.forName(\"java.lang.Runtime\")");
            assertFalse(result.isValid());
        }
    }

    // ===== 追踪树 =====

    @Nested
    @DisplayName("追踪树")
    class TraceTree {

        @Test
        @DisplayName("AND 短路追踪")
        void testAndShortCircuitTrace() {
            Map<String, Object> facts = Map.of("amount", 500);
            var result = evaluator.evalBooleanWithTrace("amount > 1000 && amount > 2000", ctx(facts));
            assertFalse(result.result());
            assertNotNull(result.traceTree());
        }

        @Test
        @DisplayName("OR 短路追踪")
        void testOrShortCircuitTrace() {
            Map<String, Object> facts = Map.of("amount", 1500);
            var result = evaluator.evalBooleanWithTrace("amount > 1000 || amount > 2000", ctx(facts));
            assertTrue(result.result());
            assertNotNull(result.traceTree());
        }
    }

    // ===== 辅助方法 =====

    private RuleContext ctx(Map<String, Object> facts) {
        return RuleContext.of(facts);
    }
}
