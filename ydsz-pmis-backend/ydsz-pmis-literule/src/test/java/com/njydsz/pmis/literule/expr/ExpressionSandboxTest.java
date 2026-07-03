package com.njydsz.pmis.literule.expr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 表达式沙箱单元测试（P1-11 AST 级别拦截）
 *
 * <p>覆盖：
 * <ul>
 *   <li>字符串字面量中包含危险词 → 放行（剥离后分析）</li>
 *   <li>java.io.File 等危险包前缀 → 拦截</li>
 *   <li>java.net.URL → 拦截</li>
 *   <li>java.lang.reflect.Method → 拦截</li>
 *   <li>Runtime.getRuntime / System.exit / Class.forName → 拦截</li>
 *   <li>exec / loadClass / newInstance / setAccessible → 拦截</li>
 *   <li>普通变量引用 → 放行</li>
 *   <li>白名单函数（max/min/contains/sqrt/now）→ 放行</li>
 *   <li>业务注册函数 → 放行</li>
 *   <li>大小写变体（system.exit / RUNTIME）→ 拦截（不区分大小写）</li>
 *   <li>链式属性 user.profile.name → 放行</li>
 *   <li>链式属性 Runtime.Runtime.exec() → 拦截（多个大写段拼成包路径）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
class ExpressionSandboxTest {

    private ExpressionSandbox sandbox;

    @BeforeEach
    void setUp() {
        sandbox = new ExpressionSandbox();
    }

    @Test
    void testNullOrBlank_shouldPass() {
        assertTrue(sandbox.check(null).isPassed());
        assertTrue(sandbox.check("").isPassed());
        assertTrue(sandbox.check("   ").isPassed());
    }

    @Test
    void testStringLiteralContainingDangerousWord_shouldPass() {
        // 字符串字面量 "exec" 出现但不是真正的方法调用
        assertTrue(sandbox.check("status == \"exec\"").isPassed());
        assertTrue(sandbox.check("msg = \"Runtime.getRuntime is forbidden\"").isPassed());
        assertTrue(sandbox.check("a == 'Class.forName'").isPassed());
    }

    @Test
    void testForbiddenPackages_shouldBlock() {
        assertFalse(sandbox.check("java.io.File.list()").isPassed());
        assertFalse(sandbox.check("java.net.URL").isPassed());
        assertFalse(sandbox.check("java.net.Socket()").isPassed());
        assertFalse(sandbox.check("java.nio.file.Files.read()").isPassed());
        assertFalse(sandbox.check("java.lang.reflect.Method.invoke()").isPassed());
        assertFalse(sandbox.check("javax.script.ScriptEngine.eval()").isPassed());
    }

    @Test
    void testForbiddenSimpleClasses_shouldBlock() {
        assertFalse(sandbox.check("Runtime.getRuntime()").isPassed());
        assertFalse(sandbox.check("Class.forName(\"foo\")").isPassed());
        assertFalse(sandbox.check("System.exit(0)").isPassed());
        assertFalse(sandbox.check("Thread.sleep(1000)").isPassed());
        assertFalse(sandbox.check("ProcessBuilder(\"ls\")").isPassed());
    }

    @Test
    void testForbiddenMethods_shouldBlock() {
        assertFalse(sandbox.check("foo.exec(\"ls\")").isPassed());
        assertFalse(sandbox.check("bar.loadClass(\"x\")").isPassed());
        assertFalse(sandbox.check("m.invoke(target)").isPassed());
        assertFalse(sandbox.check("field.setAccessible(true)").isPassed());
        assertFalse(sandbox.check("f.setAccessible(true)").isPassed());
        assertFalse(sandbox.check("lib.loadLibrary(\"x\")").isPassed());
    }

    @Test
    void testCaseInsensitive_shouldBlock() {
        assertFalse(sandbox.check("RUNTIME.GETRUNTIME()").isPassed());
        assertFalse(sandbox.check("system.exit(0)").isPassed());
        assertFalse(sandbox.check("Class.FORNAME(\"x\")").isPassed());
    }

    @Test
    void testNormalVariables_shouldPass() {
        assertTrue(sandbox.check("amount > 1000").isPassed());
        assertTrue(sandbox.check("user.name == \"alice\"").isPassed());
        assertTrue(sandbox.check("a + b * c").isPassed());
        assertTrue(sandbox.check("isActive && (amount > 100)").isPassed());
    }

    @Test
    void testAllowedBuiltinFunctions_shouldPass() {
        assertTrue(sandbox.check("max(a, b) > 100").isPassed());
        assertTrue(sandbox.check("min(amount, 1000)").isPassed());
        assertTrue(sandbox.check("contains(name, \"foo\")").isPassed());
        assertTrue(sandbox.check("sqrt(value)").isPassed());
        assertTrue(sandbox.check("now()").isPassed());
        assertTrue(sandbox.check("length(name)").isPassed());
    }

    @Test
    void testRegisteredFunction_shouldPass() {
        sandbox.registerFunction("myCustomFunc");
        assertTrue(sandbox.check("myCustomFunc(amount)").isPassed());
        sandbox.registerFunction("businessRule.check");
        assertTrue(sandbox.check("businessRule.check(amount)").isPassed());
    }

    @Test
    void testVariableWhitelist_shouldPass() {
        sandbox.addAllowedVariable("myCustomVar");
        sandbox.addAllowedVariable("ctx");
        assertTrue(sandbox.check("myCustomVar + 1").isPassed());
        assertTrue(sandbox.check("ctx.user.name").isPassed());
    }

    @Test
    void testFactsKeySync_shouldPass() {
        sandbox.syncFacts(java.util.Map.of(
                "amount", 1000,
                "user", "alice"
        ));
        assertTrue(sandbox.check("amount > 500").isPassed());
        assertTrue(sandbox.check("user == \"alice\"").isPassed());
    }

    @Test
    void testChainPropertyAccess_shouldPass() {
        assertTrue(sandbox.check("user.profile.name").isPassed());
        assertTrue(sandbox.check("order.items.size()").isPassed());
        assertTrue(sandbox.check("data.users[0].email").isPassed());
    }

    @Test
    void testEmptyExpression_shouldPass() {
        ExpressionSandbox.SandboxCheckResult result = sandbox.check(null);
        assertNotNull(result);
        assertTrue(result.isPassed());
        assertTrue(result.getViolations().isEmpty());
    }

    @Test
    void testViolationSummary_shouldContainDetails() {
        ExpressionSandbox.SandboxCheckResult result = sandbox.check("java.io.File.list()");
        assertFalse(result.isPassed());
        String summary = result.violationSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("java.io.") || summary.contains("File"));
    }

    @Test
    void testNewInstance_shouldBeBlocked() {
        // newInstance 是危险方法名
        assertFalse(sandbox.check("foo.newInstance()").isPassed());
        assertFalse(sandbox.check("cls.newInstance()").isPassed());
    }

    @Test
    void testChainedDangerousCall_shouldBlock() {
        // 链式：java.io.File.list() → 含 java.io. 前缀
        assertFalse(sandbox.check("java.io.File(\"foo\")").isPassed());
        // 链式：a.b.Class → 三个大写段拼起来
        assertFalse(sandbox.check("a.b.Class.forName(\"x\")").isPassed());
    }
}
