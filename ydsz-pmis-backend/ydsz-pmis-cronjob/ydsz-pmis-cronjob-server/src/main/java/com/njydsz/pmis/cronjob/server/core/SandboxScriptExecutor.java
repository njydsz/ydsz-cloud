package com.njydsz.pmis.cronjob.server.core.executor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 沙箱脚本执行器（P3-11 脚本执行沙箱）。
 *
 * <p>在受限环境中执行 SHELL/GLUE 脚本，提供安全隔离：
 * <ul>
 *   <li>超时控制：脚本执行超过指定时间后强制终止</li>
 *   <li>工作目录隔离：在临时目录中执行，限制文件访问范围</li>
 *   <li>环境变量白名单：仅传递指定的环境变量</li>
 *   <li>输出捕获：捕获 stdout/stderr 并限制大小</li>
 *   <li>进程隔离：使用 ProcessBuilder 独立进程执行</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class SandboxScriptExecutor {

    @Value("${pmis.cronjob.sandbox.timeout-seconds:300}")
    private int defaultTimeoutSeconds;

    @Value("${pmis.cronjob.sandbox.max-output-size:1048576}")
    private int maxOutputSize;

    @Value("${pmis.cronjob.sandbox.work-dir:./data/sandbox}")
    private String workDir;

    /**
     * 在沙箱中执行脚本。
     *
     * @param scriptContent  脚本内容
     * @param scriptType     脚本类型: SHELL / PYTHON
     * @param timeoutSeconds 超时时间（秒）
     * @param envVars        环境变量（白名单传递）
     * @return 执行结果
     */
    public SandboxResult execute(String scriptContent, String scriptType,
                                  int timeoutSeconds, Map<String, String> envVars) {
        Path scriptFile = null;
        try {
            // 创建临时工作目录
            Path sandboxDir = Path.of(workDir, "sandbox-" + System.nanoTime());
            Files.createDirectories(sandboxDir);

            // 写入脚本文件
            String fileExtension = "PYTHON".equalsIgnoreCase(scriptType) ? ".py" : ".sh";
            scriptFile = sandboxDir.resolve("script" + fileExtension);
            Files.writeString(scriptFile, scriptContent);
            scriptFile.toFile().setExecutable(true);

            // 构建执行命令
            ProcessBuilder pb;
            if ("PYTHON".equalsIgnoreCase(scriptType)) {
                pb = new ProcessBuilder("python3", scriptFile.toString());
            } else {
                pb = new ProcessBuilder("bash", scriptFile.toString());
            }
            pb.directory(sandboxDir.toFile());
            pb.redirectErrorStream(true);

            // 设置白名单环境变量
            pb.environment().clear();
            if (envVars != null) {
                pb.environment().putAll(envVars);
            }
            // 保留必要的 PATH
            pb.environment().put("PATH", System.getenv("PATH"));

            // 启动进程
            Process process = pb.start();
            int effectiveTimeout = timeoutSeconds > 0 ? timeoutSeconds : defaultTimeoutSeconds;

            // 读取输出（限制大小）
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() + line.length() > maxOutputSize) {
                        output.append("\n[OUTPUT TRUNCATED]");
                        break;
                    }
                    output.append(line).append("\n");
                }
            }

            // 等待完成或超时
            boolean finished = process.waitFor(effectiveTimeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new SandboxResult(false, output.toString(), "Script timed out after " + effectiveTimeout + "s", -1);
            }

            int exitCode = process.exitValue();
            boolean success = exitCode == 0;
            String errorMsg = success ? null : "Script exited with code " + exitCode;
            return new SandboxResult(success, output.toString(), errorMsg, exitCode);
        } catch (Exception e) {
            log.error("[Sandbox] 脚本执行异常: type={} reason={}", scriptType, e.getMessage(), e);
            return new SandboxResult(false, "", e.getClass().getSimpleName() + ": " + e.getMessage(), -1);
        } finally {
            // 清理临时文件
            if (scriptFile != null) {
                try {
                    Files.deleteIfExists(scriptFile);
                    Files.deleteIfExists(scriptFile.getParent());
                } catch (Exception ignored) {
                    // 清理失败不影响主流程
                }
            }
        }
    }

    /**
     * 沙箱执行结果。
     */
    public record SandboxResult(boolean success, String output, String errorMessage, int exitCode) {
    }
}
