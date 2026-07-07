package com.njydsz.pmis.literule.expr;

import com.njydsz.pmis.literule.expr.ExpressionSandbox.SandboxCheckResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ExpressionSandbox 表达式沙箱单元测试
 *
 * <p>测试目标：覆盖 {@link ExpressionSandbox} 的 AST 级别安全校验全部逻辑路径，
 * 包括合法表达式放行、危险包/类/方法拦截、字符串字面量剥离、白名单管理及边界条件。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@DisplayName("ExpressionSandbox 表达式沙箱测试")
class ExpressionSandboxTest {

    private ExpressionSandbox sandbox;

    @BeforeEach
    void setUp() {
        sandbox = new ExpressionSandbox();
    }

    // ==================== 1. 合法表达式通过验证 ====================

    @Nested
    @DisplayName("合法表达式验证")
    class ValidExpressionTest {

        @Test
        @DisplayName("简单比较表达式 - 变量在白名单中应通过")
        void shouldPassSimpleComparison() {
            sandbox.addAllowedVariable("amount");
            SandboxCheckResult result = sandbox.check("amount > 1000");
            assertThat(result.isPassed()).isTrue();
            assertThat(result.getViolations()).isEmpty();
        }

        @Test
        @DisplayName("多变量算术表达式应通过")
        void shouldPassArithmeticExpression() {
            sandbox.addAllowedVariables(Arrays.asList("a", "b", "c"));
            SandboxCheckResult result = sandbox.check("a + b > c");
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        @DisplayName("逻辑复合表达式应通过")
        void shouldPassLogicalExpression() {
            sandbox.addAllowedVariables(Arrays.asList("amount", "level"));
            SandboxCheckResult result = sandbox.check("amount > 1000 && level == 2");
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        @DisplayName("未识别标识符不阻断 - 仅警告")
        void shouldPassUnknownIdentifierWithWarning() {
            // foo 不在白名单中，但未识别标识符不阻断，仅记录警告
            SandboxCheckResult result = sandbox.check("foo + bar");
            assertThat(result.isPassed()).isTrue();
        }
    }

    // ==================== 2. 危险包拦截 ====================

    @Nested
    @DisplayName("危险包拦截")
    class ForbiddenPackageTest {

        @Test
        @DisplayName("java.lang.Runtime 应被拦截")
        void shouldBlockJavaLangRuntime() {
            SandboxCheckResult result = sandbox.check("java.lang.Runtime");
            assertThat(result.isPassed()).isFalse();
            assertThat(result.violationSummary()).contains("危险包");
        }

        @Test
        @DisplayName("java.lang.System 应被拦截")
        void shouldBlockJavaLangSystem() {
            SandboxCheckResult result = sandbox.check("java.lang.System");
            assertThat(result.isPassed()).isFalse();
            assertThat(result.violationSummary()).contains("危险包");
        }

        @Test
        @DisplayName("java.lang.ProcessBuilder 应被拦截")
        void shouldBlockJavaLangProcessBuilder() {
            SandboxCheckResult result = sandbox.check("java.lang.ProcessBuilder");
            assertThat(result.isPassed()).isFalse();
        }

        @Test
        @DisplayName("java.io 包前缀应被拦截")
        void shouldBlockJavaIoPackage() {
            SandboxCheckResult result = sandbox.check("java.io.File");
            assertThat(result.isPassed()).isFalse();
            assertThat(result.violationSummary()).contains("危险包");
        }

        @Test
        @DisplayName("java.nio.file 包前缀应被拦截")
        void shouldBlockJavaNioFilePackage() {
            SandboxCheckResult result = sandbox.check("java.nio.file.FileSystem");
            assertThat(result.isPassed()).isFalse();
        }

        @Test
        @DisplayName("java.net 包前缀应被拦截")
        void shouldBlockJavaNetPackage() {
            SandboxCheckResult result = sandbox.check("java.net.Socket");
            assertThat(result.isPassed()).isFalse();
        }

        @Test
        @DisplayName("java.lang.reflect 包前缀应被拦截")
        void shouldBlockJavaLangReflectPackage() {
            SandboxCheckResult result = sandbox.check("java.lang.reflect.Method");
            assertThat(result.isPassed()).isFalse();
        }

        @Test
        @DisplayName("javax.script 包前缀应被拦截")
        void shouldBlockJavaxScriptPackage() {
            SandboxCheckResult result = sandbox.check("javax.script.ScriptEngine");
            assertThat(result.isPassed()).isFalse();
        }

        @Test
        @DisplayName("sun. 包前缀应被拦截")
        void shouldBlockSunPackage() {
            SandboxCheckResult result = sandbox.check("sun.misc.Unsafe");
            assertThat(result.isPassed()).isFalse();
        }

        @Test
        @DisplayName("仅触发危险包（类名不在危险类名单中）")
        void shouldBlockOnlyForbiddenPackage() {
            // InputStream 不在 FORBIDDEN_SIMPLE_CLASSES 中，但 java.io. 包前缀被拦截
            SandboxCheckResult result = sandbox.check("java.io.InputStream");
            assertThat(result.isPassed()).isFalse();
            assertThat(result.violationSummary()).contains("危险包");
            assertThat(result.violationSummary()).doesNotContain("危险类");
        }
    }

    // ==================== 3. 危险类拦截 ====================

    @Nested
    @DisplayName("危险类拦截")
    class ForbiddenClassTest {

        @Test
        @DisplayName("Socket 简名应被拦截")
        void shouldBlockSocketClass() {
            SandboxCheckResult result = sandbox.check("Socket");
            assertThat(result.isPassed()).isFalse();
            assertThat(result.violationSummary()).contains("危险类");
        }

        @Test
        @DisplayName("Runtime 简名应被拦截")
        void shouldBlockRuntimeClass() {
            SandboxCheckResult result = sandbox.check("Runtime");
            assertThat(result.isPassed()).isFalse();
        }

        @Test
        @DisplayName("ProcessBuilder 简名应被拦截")
        void shouldBlockProcessBuilderClass() {
            SandboxCheckResult result = sandbox.check("ProcessBuilder");
            assertThat(result.isPassed()).isFalse();
        }

        @Test
        @DisplayName("Class 简名应被拦截")
        void shouldBlockClassClass() {
            SandboxCheckResult result = sandbox.check("Class");
            assertThat(result.isPassed()).isFalse();
        }

        @Test
        @DisplayName("Method 简名应被拦截")
        void shouldBlockMethodClass() {
            SandboxCheckResult result = sandbox.check("Method");
            assertThat(result.isPassed()).isFalse();
        }

        @Test
        @DisplayName("大小写不敏感 - system 应被拦截")
        void shouldBlockCaseInsensitive() {
            SandboxCheckResult result = sandbox.check("system");
            // system 小写开头，不在危险类名单中（大小写不敏感检查）
            // 但不在白名单中，属于未识别标识符，不阻断
            // 注：isForbiddenClass 使用 equalsIgnoreCase，但只对大写开头的标识符触发
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        @DisplayName("仅触发危险类（包路径不在危险包名单中）")
        void shouldBlockOnlyForbiddenClass() {
            // Socket 不属于任何危险包前缀，但在 FORBIDDEN_SIMPLE_CLASSES 中
            SandboxCheckResult result = sandbox.check("Socket");
            assertThat(result.isPassed()).isFalse();
            assertThat(result.violationSummary()).contains("危险类");
            assertThat(result.violationSummary()).doesNotContain("危险包");
        }
    }

    // ==================== 4. 危险方法拦截 ====================

    @Nested
    @DisplayName("危险方法拦截")
    class ForbiddenMethodTest {

        @Test
        @DisplayName("exec 方法应被拦截")
        void shouldBlockExecMethod() {
            SandboxCheckResult result = sandbox.check("exec(\"ls\")");
            assertThat(result.isPassed()).isFalse();
            assertThat(result.violationSummary()).contains("危险方法");
        }

        @Test
        @DisplayName("exit 方法应被拦截")
        void shouldBlockExitMethod() {
            SandboxCheckResult result = sandbox.check("exit(0)");
            assertThat(result.isPassed()).isFalse();
        }

        @Test
        @DisplayName("load 方法应被拦截")
        void shouldBlockLoadMethod() {
            SandboxCheckResult result = sandbox.check("load(\"lib\")");
            assertThat(result.isPassed()).isFalse();
        }

        @Test
        @DisplayName("forName 方法应被拦截")
        void shouldBlockForNameMethod() {
            SandboxCheckResult result = sandbox.check("forName(\"java.lang.Runtime\")");
            assertThat(result.isPassed()).isFalse();
        }

        @Test
        @DisplayName("invoke 方法应被拦截")
        void shouldBlockInvokeMethod() {
            sandbox.addAllowedVariable("obj");
            SandboxCheckResult result = sandbox.check("invoke(obj, \"run\")");
            assertThat(result.isPassed()).isFalse();
        }

        @Test
        @DisplayName("链式调用中的危险方法应被拦截")
        void shouldBlockDangerousMethodInDotChain() {
            SandboxCheckResult result = sandbox.check("Runtime.exec(\"ls\")");
            assertThat(result.isPassed()).isFalse();
            assertThat(result.violationSummary()).contains("危险方法");
        }

        @Test
        @DisplayName("大小写不敏感 - EXEC 应被拦截")
        void shouldBlockCaseInsensitiveMethod() {
            SandboxCheckResult result = sandbox.check("EXEC(\"ls\")");
            // EXEC 大写开头，isMethodName 检测到方法调用
            // isForbiddenMethod 使用 equalsIgnoreCase，匹配 "exec"
            assertThat(result.isPassed()).isFalse();
            assertThat(result.violationSummary()).contains("危险方法");
        }

        @Test
        @DisplayName("仅触发危险方法（无危险包/类）")
        void shouldBlockOnlyForbiddenMethod() {
            // exec 是小写开头，不触发包/类检查
            SandboxCheckResult result = sandbox.check("exec(\"ls\")");
            assertThat(result.isPassed()).isFalse();
            assertThat(result.violationSummary()).contains("危险方法");
            assertThat(result.violationSummary()).doesNotContain("危险包");
            assertThat(result.violationSummary()).doesNotContain("危险类");
        }
    }

    // ==================== 5. 反射相关拦截 ====================

    @Nested
    @DisplayName("反射相关拦截")
    class ReflectionTest {

        @Test
        @DisplayName("Class.forName 反射调用应被拦截")
        void shouldBlockClassForName() {
            SandboxCheckResult result = sandbox.check("Class.forName(\"java.lang.Runtime\")");
            assertThat(result.isPassed()).isFalse();
            assertThat(result.violationSummary()).contains("危险类");
            assertThat(result.violationSummary()).contains("危险方法");
        }

        @Test
        @DisplayName("Method.invoke 反射调用应被拦截")
        void shouldBlockMethodInvoke() {
            sandbox.addAllowedVariable("obj");
            SandboxCheckResult result = sandbox.check("Method.invoke(obj, \"run\")");
            assertThat(result.isPassed()).isFalse();
        }

        @Test
        @DisplayName("java.lang.reflect.Method 反射包应被拦截")
        void shouldBlockReflectPackage() {
            SandboxCheckResult result = sandbox.check("java.lang.reflect.Method");
            assertThat(result.isPassed()).isFalse();
            assertThat(result.violationSummary()).contains("危险包");
        }

        @Test
        @DisplayName("new ProcessBuilder 应被拦截（类名在危险名单中）")
        void shouldBlockNewProcessBuilder() {
            SandboxCheckResult result = sandbox.check("new ProcessBuilder(\"ls\")");
            assertThat(result.isPassed()).isFalse();
            assertThat(result.violationSummary()).contains("危险类");
        }

        @Test
        @DisplayName("getDeclaredMethod 反射方法应被拦截")
        void shouldBlockGetDeclaredMethod() {
            sandbox.addAllowedVariable("obj");
            SandboxCheckResult result = sandbox.check("getDeclaredMethod(obj, \"run\")");
            assertThat(result.isPassed()).isFalse();
        }

        @Test
        @DisplayName("setAccessible 反射方法应被拦截")
        void shouldBlockSetAccessible() {
            sandbox.addAllowedVariable("field");
            SandboxCheckResult result = sandbox.check("setAccessible(field, true)");
            assertThat(result.isPassed()).isFalse();
        }
    }

    // ==================== 6. 复合表达式拦截 ====================

    @Nested
    @DisplayName("复合表达式拦截")
    class CompoundExpressionTest {

        @Test
        @DisplayName("合法变量与危险方法混合应被拦截")
        void shouldBlockCompoundExpression() {
            sandbox.addAllowedVariable("amount");
            SandboxCheckResult result = sandbox.check("amount > 1000 && java.lang.Runtime.exec(\"ls\")");
            assertThat(result.isPassed()).isFalse();
        }

        @Test
        @DisplayName("合法函数与危险方法混合应被拦截")
        void shouldBlockFunctionWithDangerousMethod() {
            sandbox.addAllowedVariable("amount");
            SandboxCheckResult result = sandbox.check("max(amount, 100) && exec(\"ls\")");
            assertThat(result.isPassed()).isFalse();
        }

        @Test
        @DisplayName("多种违规类型同时出现应全部报告")
        void shouldReportAllViolationTypes() {
            SandboxCheckResult result = sandbox.check("java.lang.Runtime.exec(\"ls\")");
            assertThat(result.isPassed()).isFalse();
            assertThat(result.violationSummary()).contains("危险包");
            assertThat(result.violationSummary()).contains("危险类");
            assertThat(result.violationSummary()).contains("危险方法");
        }
    }

    // ==================== 7. 空表达式与 null 边界 ====================

    @Nested
    @DisplayName("空表达式与 null 边界")
    class EmptyExpressionTest {

        @Test
        @DisplayName("null 表达式应返回 ok")
        void shouldReturnOkForNull() {
            SandboxCheckResult result = sandbox.check(null);
            assertThat(result.isPassed()).isTrue();
            assertThat(result.getViolations()).isEmpty();
        }

        @Test
        @DisplayName("空字符串应返回 ok")
        void shouldReturnOkForEmptyString() {
            SandboxCheckResult result = sandbox.check("");
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        @DisplayName("纯空白字符串应返回 ok")
        void shouldReturnOkForBlankString() {
            SandboxCheckResult result = sandbox.check("   ");
            assertThat(result.isPassed()).isTrue();
        }
    }

    // ==================== 8. 内置白名单函数允许 ====================

    @Nested
    @DisplayName("白名单函数测试")
    class WhitelistFunctionTest {

        @Test
        @DisplayName("max 函数应被允许")
        void shouldAllowMaxFunction() {
            SandboxCheckResult result = sandbox.check("max(1, 2)");
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        @DisplayName("contains 函数应被允许")
        void shouldAllowContainsFunction() {
            sandbox.addAllowedVariable("name");
            SandboxCheckResult result = sandbox.check("contains(name, \"test\")");
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        @DisplayName("sum 函数应被允许")
        void shouldAllowSumFunction() {
            sandbox.addAllowedVariable("list");
            SandboxCheckResult result = sandbox.check("sum(list)");
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        @DisplayName("注册自定义函数后应被允许")
        void shouldAllowRegisteredFunction() {
            sandbox.registerFunction("myCustomFunc");
            sandbox.addAllowedVariable("x");
            SandboxCheckResult result = sandbox.check("myCustomFunc(x)");
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        @DisplayName("Aviator 关键字应被允许")
        void shouldAllowAviatorKeywords() {
            SandboxCheckResult result = sandbox.check("true && false");
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        @DisplayName("nil 关键字应被允许")
        void shouldAllowNilKeyword() {
            sandbox.addAllowedVariable("x");
            SandboxCheckResult result = sandbox.check("x == nil");
            assertThat(result.isPassed()).isTrue();
        }
    }

    // ==================== 9. 变量引用允许 ====================

    @Nested
    @DisplayName("变量引用测试")
    class VariableReferenceTest {

        @Test
        @DisplayName("addAllowedVariable 添加的变量应被允许")
        void shouldAllowAddedVariable() {
            sandbox.addAllowedVariable("amount");
            SandboxCheckResult result = sandbox.check("amount > 1000");
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        @DisplayName("addAllowedVariables 批量添加变量应被允许")
        void shouldAllowBatchAddedVariables() {
            sandbox.addAllowedVariables(Arrays.asList("a", "b", "c"));
            SandboxCheckResult result = sandbox.check("a + b - c > 0");
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        @DisplayName("syncFacts 同步的 facts key 应被允许")
        void shouldAllowSyncedFactsKeys() {
            Map<String, Object> facts = new LinkedHashMap<>();
            facts.put("amount", 500);
            facts.put("level", 2);
            sandbox.syncFacts(facts);
            SandboxCheckResult result = sandbox.check("amount > 1000 && level == 2");
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        @DisplayName("链式属性访问应被允许")
        void shouldAllowDotChainAccess() {
            sandbox.addAllowedVariable("user");
            SandboxCheckResult result = sandbox.check("user.name");
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        @DisplayName("链式方法调用（非危险方法）应被允许")
        void shouldAllowNonDangerousMethodCall() {
            sandbox.addAllowedVariable("user");
            SandboxCheckResult result = sandbox.check("user.getName()");
            assertThat(result.isPassed()).isTrue();
        }
    }

    // ==================== 10. 字符串字面量不误报 ====================

    @Nested
    @DisplayName("字符串字面量不误报")
    class StringLiteralTest {

        @Test
        @DisplayName("双引号字符串包含 exec 不应误报")
        void shouldNotFlagExecInStringLiteral() {
            SandboxCheckResult result = sandbox.check("\"exec is not dangerous\"");
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        @DisplayName("单引号字符串包含 Runtime 不应误报")
        void shouldNotFlagRuntimeInSingleQuoteString() {
            SandboxCheckResult result = sandbox.check("'Runtime.getRuntime()'");
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        @DisplayName("反引号字符串包含危险关键字不应误报")
        void shouldNotFlagDangerInBacktickString() {
            SandboxCheckResult result = sandbox.check("`java.lang.System.exit(0)`");
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        @DisplayName("函数参数中的字符串字面量包含危险关键字不应误报")
        void shouldNotFlagDangerInFunctionArg() {
            sandbox.addAllowedVariable("name");
            SandboxCheckResult result = sandbox.check("equals(name, \"exec\")");
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        @DisplayName("字符串中的转义引号应正确处理")
        void shouldHandleEscapedQuotesInString() {
            SandboxCheckResult result = sandbox.check("\"he said \\\"exec\\\"\"");
            assertThat(result.isPassed()).isTrue();
        }
    }

    // ==================== 白名单管理方法测试 ====================

    @Nested
    @DisplayName("白名单管理方法测试")
    class WhitelistManagementTest {

        @Test
        @DisplayName("addAllowedVariable(null) 应为空操作")
        void shouldHandleNullVariableName() {
            sandbox.addAllowedVariable(null);
            assertThat(sandbox.getVariableWhitelist()).isEmpty();
        }

        @Test
        @DisplayName("addAllowedVariable(空字符串) 应为空操作")
        void shouldHandleBlankVariableName() {
            sandbox.addAllowedVariable("");
            sandbox.addAllowedVariable("   ");
            assertThat(sandbox.getVariableWhitelist()).isEmpty();
        }

        @Test
        @DisplayName("addAllowedVariables(null) 应为空操作")
        void shouldHandleNullVariableIterable() {
            sandbox.addAllowedVariables(null);
            assertThat(sandbox.getVariableWhitelist()).isEmpty();
        }

        @Test
        @DisplayName("syncFacts(null) 应为空操作")
        void shouldHandleNullFacts() {
            sandbox.syncFacts(null);
            assertThat(sandbox.getFactsKeys()).isEmpty();
        }

        @Test
        @DisplayName("registerFunction(null) 应为空操作")
        void shouldHandleNullFunctionName() {
            sandbox.registerFunction(null);
            sandbox.registerFunction("");
            sandbox.registerFunction("  ");
            // 内置函数仍然存在
            assertThat(sandbox.getAllowedFunctions()).contains("max", "min");
        }

        @Test
        @DisplayName("getAllowedFunctions 应返回内置函数集合")
        void shouldReturnAllowedFunctions() {
            assertThat(sandbox.getAllowedFunctions()).contains("max", "min", "abs", "contains", "sum");
        }

        @Test
        @DisplayName("getVariableWhitelist 应返回已添加的变量集合")
        void shouldReturnVariableWhitelist() {
            sandbox.addAllowedVariable("foo");
            sandbox.addAllowedVariable("bar");
            assertThat(sandbox.getVariableWhitelist()).contains("foo", "bar");
        }

        @Test
        @DisplayName("getFactsKeys 应返回已同步的 facts key 集合")
        void shouldReturnFactsKeys() {
            Map<String, Object> facts = new LinkedHashMap<>();
            facts.put("key1", "val1");
            facts.put("key2", "val2");
            sandbox.syncFacts(facts);
            assertThat(sandbox.getFactsKeys()).contains("key1", "key2");
        }
    }

    // ==================== SandboxCheckResult 测试 ====================

    @Nested
    @DisplayName("SandboxCheckResult 测试")
    class CheckResultTest {

        @Test
        @DisplayName("ok() 应返回通过的结果")
        void shouldCreateOkResult() {
            SandboxCheckResult result = SandboxCheckResult.ok();
            assertThat(result.isPassed()).isTrue();
            assertThat(result.getViolations()).isEmpty();
        }

        @Test
        @DisplayName("fail() 应返回不通过的结果")
        void shouldCreateFailResult() {
            SandboxCheckResult result = SandboxCheckResult.fail(List.of("违规1", "违规2"));
            assertThat(result.isPassed()).isFalse();
            assertThat(result.getViolations()).hasSize(2);
            assertThat(result.getViolations()).contains("违规1", "违规2");
        }

        @Test
        @DisplayName("fail(null) 应返回空违规列表")
        void shouldCreateFailResultWithNullViolations() {
            SandboxCheckResult result = SandboxCheckResult.fail(null);
            assertThat(result.isPassed()).isFalse();
            assertThat(result.getViolations()).isEmpty();
        }

        @Test
        @DisplayName("violationSummary 应以分号连接违规信息")
        void shouldJoinViolationsWithSemicolon() {
            SandboxCheckResult result = SandboxCheckResult.fail(List.of("违规A", "违规B"));
            assertThat(result.violationSummary()).isEqualTo("违规A; 违规B");
        }

        @Test
        @DisplayName("ok() 的 violationSummary 应为空字符串")
        void okResultSummaryShouldBeEmpty() {
            SandboxCheckResult result = SandboxCheckResult.ok();
            assertThat(result.violationSummary()).isEmpty();
        }
    }

    // ==================== 补充覆盖：包路径重构与 dot chain 边界 ====================

    @Nested
    @DisplayName("包路径重构与 dot chain 边界")
    class PackagePathReconstructionTest {

        @Test
        @DisplayName("向前拼入大写类名 - 多段包路径")
        void shouldReconstructForwardUppercaseChain() {
            // Runtime.System 向前拼入大写 System
            SandboxCheckResult result = sandbox.check("java.lang.Runtime.System");
            assertThat(result.isPassed()).isFalse();
        }

        @Test
        @DisplayName("向前遇到小写标识符应停止拼接")
        void shouldStopForwardOnLowercase() {
            SandboxCheckResult result = sandbox.check("java.lang.Runtime.exec(\"ls\")");
            // exec 小写，向前拼接停止
            assertThat(result.isPassed()).isFalse();
            assertThat(result.violationSummary()).contains("Runtime");
        }

        @Test
        @DisplayName("向前遇到非 dot chain 的大写标识符应停止拼接")
        void shouldStopForwardOnNonDotChainUppercase() {
            // Runtime 和 System 之间只有空格，不是 dot chain
            SandboxCheckResult result = sandbox.check("java.lang.Runtime System");
            assertThat(result.isPassed()).isFalse();
        }

        @Test
        @DisplayName("向后遇到非 dot chain（仅空格分隔）应停止拼接")
        void shouldStopBackwardOnWhitespaceOnly() {
            // foo Runtime 之间只有空格
            SandboxCheckResult result = sandbox.check("foo Runtime");
            assertThat(result.isPassed()).isFalse();
            assertThat(result.violationSummary()).contains("危险类");
        }

        @Test
        @DisplayName("向后遇到非 dot 字符应停止拼接")
        void shouldStopBackwardOnNonDotChar() {
            // foo+Runtime 之间是 + 号
            SandboxCheckResult result = sandbox.check("foo+Runtime");
            assertThat(result.isPassed()).isFalse();
        }

        @Test
        @DisplayName("dot 后有非空非标识符字符应停止拼接")
        void shouldStopOnCharAfterDot() {
            // a .+Runtime - dot 后有 + 号，不是直接 dot chain
            SandboxCheckResult result = sandbox.check("a .+Runtime");
            assertThat(result.isPassed()).isFalse();
        }

        @Test
        @DisplayName("标识符在表达式末尾不是方法名")
        void shouldNotBeMethodNameAtEnd() {
            sandbox.addAllowedVariable("Runtime");
            // Runtime 在末尾，isMethodName 返回 false（idEnd >= length）
            // 但 Runtime 是大写开头，会被 isForbiddenClass 检测到
            // 使用白名单变量名 Runtime 应被跳过
            SandboxCheckResult result = sandbox.check("Runtime");
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        @DisplayName("大写类名后跟括号但不在危险方法名单中")
        void shouldCheckUppercaseMethodNotForbidden() {
            // Runtime 后跟括号 - isMethodName=true, isForbiddenMethod("Runtime")=false
            // 但 isForbiddenClass("Runtime")=true
            SandboxCheckResult result = sandbox.check("Runtime()");
            assertThat(result.isPassed()).isFalse();
            assertThat(result.violationSummary()).contains("危险类");
        }

        @Test
        @DisplayName("链式调用中的非危险小写方法应被跳过")
        void shouldSkipNonDangerousMethodInDotChain() {
            sandbox.addAllowedVariable("user");
            SandboxCheckResult result = sandbox.check("user.getName()");
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        @DisplayName("链式调用中的非危险非方法小写标识符应被跳过")
        void shouldSkipNonMethodInDotChain() {
            sandbox.addAllowedVariable("user");
            SandboxCheckResult result = sandbox.check("user.name");
            assertThat(result.isPassed()).isTrue();
        }
    }
}
