package com.njydsz.pmis.cronjob.core.handler;

import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.job.JobLogger;
import com.njydsz.pmis.common.job.JobLoggerHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ScriptJobHandler} 单元测试（P1-3 SHELL/Python 脚本任务）。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>执行 Shell 脚本（echo hello）</li>
 *   <li>执行 Python 脚本（print）</li>
 *   <li>非零退出码抛 RuntimeException</li>
 *   <li>超时中断抛 RuntimeException</li>
 *   <li>stdout 写入 JobLogger</li>
 *   <li>参数校验（paramsJson/language/script 为空）</li>
 *   <li>不支持的语言抛异常</li>
 * </ul>
 *
 * <p>说明：脚本执行依赖系统环境（bash/python3），在 Windows 环境下部分 Shell 测试
 * 通过 {@code cmd /c} 执行；Python 测试在环境无 python3 时会被忽略。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ScriptJobHandler SHELL/Python 脚本任务处理器测试")
class ScriptJobHandlerTest {

    private ScriptJobHandler handler;
    private CapturingJobLogger capturingLogger;

    @BeforeEach
    void setUp() {
        handler = new ScriptJobHandler();
        capturingLogger = new CapturingJobLogger();
        JobLoggerHolder.set(capturingLogger);
    }

    @AfterEach
    void tearDown() {
        JobLoggerHolder.clear();
    }

    @Test
    @DisplayName("execute: paramsJson 为空抛 IllegalArgumentException")
    void execute_emptyParams_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> handler.execute(null));
        assertThrows(IllegalArgumentException.class, () -> handler.execute(""));
        assertThrows(IllegalArgumentException.class, () -> handler.execute("  "));
    }

    @Test
    @DisplayName("execute: 缺少 language 抛 IllegalArgumentException")
    void execute_missingLanguage_throwsException() {
        JSONObject params = new JSONObject();
        params.put("script", "echo hello");
        assertThrows(IllegalArgumentException.class, () -> handler.execute(params.toJSONString()));
    }

    @Test
    @DisplayName("execute: 缺少 script 抛 IllegalArgumentException")
    void execute_missingScript_throwsException() {
        JSONObject params = new JSONObject();
        params.put("language", "shell");
        assertThrows(IllegalArgumentException.class, () -> handler.execute(params.toJSONString()));
    }

    @Test
    @DisplayName("execute: 不支持的语言抛 IllegalArgumentException")
    void execute_unsupportedLanguage_throwsException() {
        JSONObject params = new JSONObject();
        params.put("language", "ruby");
        params.put("script", "puts 'hi'");
        // 不支持的语言会在执行阶段抛出 IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> handler.execute(params.toJSONString()));
    }

    @Test
    @DisplayName("execute: Shell 脚本输出捕获（echo hello）")
    void execute_shellScript_capturesStdout() throws Exception {
        // 跳过条件：Windows 之外的系统需要 bash；Windows 通过 cmd /c 执行
        JSONObject params = new JSONObject();
        if (isWindows()) {
            // Windows cmd: echo hello
            params.put("language", "shell");
            params.put("script", "@echo hello");
        } else {
            params.put("language", "shell");
            params.put("script", "echo hello");
        }

        Object result = handler.execute(params.toJSONString());

        assertNotNull(result);
        assertTrue(result instanceof ScriptJobHandler.ScriptResult, "结果应为 ScriptResult");
        ScriptJobHandler.ScriptResult sr = (ScriptJobHandler.ScriptResult) result;
        assertEquals(0, sr.getExitCode(), "退出码应为 0");
        assertTrue(sr.getStdout().contains("hello"), "stdout 应包含 hello");
        // stdout 应通过 JobLogger 写入
        assertTrue(capturingLogger.lines.stream().anyMatch(l -> l.contains("hello")),
                "JobLogger 应捕获 hello 输出");
    }

    @Test
    @DisplayName("execute: Shell 脚本非零退出码抛 RuntimeException")
    void execute_shellScript_nonZeroExit_throwsException() {
        JSONObject params = new JSONObject();
        params.put("language", "shell");
        if (isWindows()) {
            // cmd 退出码：exit 7
            params.put("script", "@exit 7");
        } else {
            params.put("script", "exit 7");
        }

        RuntimeException ex = assertThrows(RuntimeException.class, () -> handler.execute(params.toJSONString()));
        assertTrue(ex.getMessage().contains("exitCode=7"), "异常应包含 exitCode=7");
    }

    @Test
    @DisplayName("execute: 超时中断抛 RuntimeException")
    void execute_timeout_throwsRuntimeException() {
        JSONObject params = new JSONObject();
        params.put("language", "shell");
        params.put("timeoutMs", 100);
        if (isWindows()) {
            // cmd 等待 5 秒：ping 127.0.0.1 -n 5
            params.put("script", "@ping 127.0.0.1 -n 5 > nul");
        } else {
            params.put("script", "sleep 5");
        }

        RuntimeException ex = assertThrows(RuntimeException.class, () -> handler.execute(params.toJSONString()));
        assertTrue(ex.getMessage().contains("超时"), "异常信息应包含超时");
    }

    @Test
    @DisplayName("execute: 传入参数列表（args）")
    void execute_shellScriptWithArgs_returnsResult() throws Exception {
        JSONObject params = new JSONObject();
        params.put("language", "shell");
        if (isWindows()) {
            // cmd: echo %1
            params.put("script", "@echo %1");
        } else {
            params.put("script", "echo $1");
        }
        params.put("args", List.of("hello-arg"));

        Object result = handler.execute(params.toJSONString());

        assertNotNull(result);
        ScriptJobHandler.ScriptResult sr = (ScriptJobHandler.ScriptResult) result;
        assertTrue(sr.getStdout().contains("hello-arg"), "stdout 应包含传入的参数");
    }

    @Test
    @DisplayName("execute: file: 前缀脚本路径直接执行")
    void execute_filePrefix_executesDirectly() throws Exception {
        // 仅在 bash 环境验证（避免 Windows 路径兼容问题）
        org.junit.jupiter.api.Assumptions.assumeFalse(isWindows(), "跳过 Windows 环境");
        // 创建临时脚本文件
        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("pmis-test-script-", ".sh");
        java.nio.file.Files.writeString(tempFile, "echo from-file\n", java.nio.charset.StandardCharsets.UTF_8);
        tempFile.toFile().setExecutable(true);
        try {
            JSONObject params = new JSONObject();
            params.put("language", "shell");
            params.put("script", "file:" + tempFile.toString());

            Object result = handler.execute(params.toJSONString());

            assertNotNull(result);
            ScriptJobHandler.ScriptResult sr = (ScriptJobHandler.ScriptResult) result;
            assertTrue(sr.getStdout().contains("from-file"));
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @DisplayName("execute: Python 脚本执行（print hello）")
    @EnabledOnOs(value = {OS.LINUX, OS.MAC}, disabledReason = "Python 测试仅在 Linux/Mac 环境验证（Windows 可能无 python3）")
    void execute_pythonScript_capturesStdout() throws Exception {
        // 假设 Linux/Mac 环境下 python3 可用
        org.junit.jupiter.api.Assumptions.assumeTrue(isPython3Available(), "python3 不可用，跳过");

        JSONObject params = new JSONObject();
        params.put("language", "python");
        params.put("script", "print('hello-python')");

        Object result = handler.execute(params.toJSONString());

        assertNotNull(result);
        ScriptJobHandler.ScriptResult sr = (ScriptJobHandler.ScriptResult) result;
        assertEquals(0, sr.getExitCode());
        assertTrue(sr.getStdout().contains("hello-python"));
        assertTrue(capturingLogger.lines.stream().anyMatch(l -> l.contains("hello-python")));
    }

    @Test
    @DisplayName("execute: stdout 写入 JobLogger（多行）")
    void execute_multiLineStdout_allWrittenToLogger() throws Exception {
        JSONObject params = new JSONObject();
        params.put("language", "shell");
        if (isWindows()) {
            params.put("script", "@echo line1\r\n@echo line2\r\n@echo line3");
        } else {
            params.put("script", "echo line1\necho line2\necho line3");
        }

        handler.execute(params.toJSONString());

        // 至少应捕获 line1/line2/line3
        assertTrue(capturingLogger.lines.stream().anyMatch(l -> l.contains("line1")));
        assertTrue(capturingLogger.lines.stream().anyMatch(l -> l.contains("line2")));
        assertTrue(capturingLogger.lines.stream().anyMatch(l -> l.contains("line3")));
    }

    @Test
    @DisplayName("ScriptResult: toString 包含 exitCode 与长度信息")
    void scriptResult_toString_containsInfo() {
        ScriptJobHandler.ScriptResult result = new ScriptJobHandler.ScriptResult(0, "out", "err");
        String s = result.toString();
        assertTrue(s.contains("exitCode=0"));
        assertTrue(s.contains("stdoutLen=3"));
        assertTrue(s.contains("stderrLen=3"));
    }

    @Test
    @DisplayName("ScriptResult: getter 返回正确值")
    void scriptResult_getters_returnValues() {
        ScriptJobHandler.ScriptResult result = new ScriptJobHandler.ScriptResult(0, "out", "err");
        assertEquals(0, result.getExitCode());
        assertEquals("out", result.getStdout());
        assertEquals("err", result.getStderr());
    }

    // ==================== 辅助方法 ====================

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private boolean isPython3Available() {
        try {
            Process p = new ProcessBuilder("python3", "--version")
                    .redirectErrorStream(true).start();
            boolean finished = p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            return finished && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 测试用 JobLogger 实现，捕获写入的日志行便于断言。
     */
    private static class CapturingJobLogger implements JobLogger {
        final List<String> lines = new ArrayList<>();

        @Override
        public void info(String message) {
            lines.add(message);
        }

        @Override
        public void info(String format, Object... args) {
            lines.add(format);
        }

        @Override
        public void warn(String message) {
            lines.add(message);
        }

        @Override
        public void warn(String format, Object... args) {
            lines.add(format);
        }

        @Override
        public void error(String message) {
            lines.add(message);
        }

        @Override
        public void error(String format, Object... args) {
            lines.add(format);
        }

        @Override
        public void error(String message, Throwable t) {
            lines.add(message);
        }

        @Override
        public void debug(String message) {
            // 测试不记录 debug
        }

        @Override
        public void debug(String format, Object... args) {
            // 测试不记录 debug
        }

        @Override
        public void flush() {
            // 无操作
        }
    }
}
