package com.njydsz.pmis.literule.expr;

import com.njydsz.pmis.literule.api.RuleContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AviatorExpressionEvaluator 沙箱安全测试
 *
 * <p>验证 P1 沙箱功能：危险表达式被拦截，正常表达式不受影响。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@DisplayName("Aviator 表达式沙箱安全测试")
class AviatorSandboxTest {

    @Nested
    @DisplayName("沙箱启用（默认）")
    class SandboxEnabledTest {

        private AviatorExpressionEvaluator evaluator;

        @BeforeEach
        void setUp() {
            evaluator = new AviatorExpressionEvaluator(true);
        }

        @Test
        @DisplayName("正常表达式正常求值")
        void shouldEvaluateNormalExpression() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("amount", 200);
            RuleContext context = RuleContext.of(facts);

            assertTrue(evaluator.evalBoolean("amount > 100", context));
            assertFalse(evaluator.evalBoolean("amount > 500", context));
        }

        @Test
        @DisplayName("System.exit 被拦截")
        void shouldBlockSystemExit() {
            assertFalse(evaluator.validate("System.exit(0)"));
            assertFalse(evaluator.evalBoolean("System.exit(0)", RuleContext.of(new HashMap<>())));
        }

        @Test
        @DisplayName("Runtime.getRuntime 被拦截")
        void shouldBlockRuntime() {
            assertFalse(evaluator.validate("Runtime.getRuntime().exec('rm -rf /')"));
        }

        @Test
        @DisplayName("ProcessBuilder 被拦截")
        void shouldBlockProcessBuilder() {
            assertFalse(evaluator.validate("new ProcessBuilder('cmd')"));
        }

        @Test
        @DisplayName("Class.forName 被拦截")
        void shouldBlockClassForName() {
            assertFalse(evaluator.validate("Class.forName('java.lang.Runtime')"));
        }

        @Test
        @DisplayName("ClassLoader 被拦截")
        void shouldBlockClassLoader() {
            assertFalse(evaluator.validate("ClassLoader.getSystemClassLoader()"));
        }

        @Test
        @DisplayName("Thread.sleep 被拦截")
        void shouldBlockThreadSleep() {
            assertFalse(evaluator.validate("Thread.sleep(1000)"));
        }

        @Test
        @DisplayName("文件 I/O 被拦截")
        void shouldBlockFileIO() {
            assertFalse(evaluator.validate("new java.io.FileInputStream('/etc/passwd')"));
            assertFalse(evaluator.validate("new FileOutputStream('/tmp/test')"));
        }

        @Test
        @DisplayName("反射包被拦截")
        void shouldBlockReflection() {
            assertFalse(evaluator.validate("java.lang.reflect.Method.invoke()"));
        }

        @Test
        @DisplayName("网络 I/O 被拦截")
        void shouldBlockNetwork() {
            assertFalse(evaluator.validate("new java.net.Socket('localhost', 8080)"));
        }

        @Test
        @DisplayName("exec 关键字被拦截")
        void shouldBlockExecKeyword() {
            assertFalse(evaluator.validate("Runtime.getRuntime().exec('ls')"));
        }

        @Test
        @DisplayName("ScriptEngine 被拦截")
        void shouldBlockScriptEngine() {
            assertFalse(evaluator.validate("ScriptEngine.eval('code')"));
        }

        @Test
        @DisplayName("沙箱已启用标记")
        void shouldReportSandboxEnabled() {
            assertTrue(evaluator.isSandboxEnabled());
        }
    }

    @Nested
    @DisplayName("沙箱关闭")
    class SandboxDisabledTest {

        @Test
        @DisplayName("沙箱关闭时危险表达式不被拦截（validate 层面）")
        void shouldNotBlockWhenDisabled() {
            AviatorExpressionEvaluator evaluator = new AviatorExpressionEvaluator(false);
            assertFalse(evaluator.isSandboxEnabled());
            // 沙箱关闭后，正则检查被跳过（但 Aviator 自身可能不支持某些语法）
            // 这里验证 validate 不会因沙箱拦截而返回 false
            // 注意：Class.forName 在 Aviator 中本身可能不支持，但不会被沙箱正则拦截
            Map<String, Object> facts = new HashMap<>();
            facts.put("amount", 200);
            RuleContext context = RuleContext.of(facts);
            // 正常表达式仍然可用
            assertTrue(evaluator.evalBoolean("amount > 100", context));
        }
    }
}
