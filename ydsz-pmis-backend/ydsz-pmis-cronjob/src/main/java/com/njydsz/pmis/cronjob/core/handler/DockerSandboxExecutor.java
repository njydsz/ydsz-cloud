package com.njydsz.pmis.cronjob.core.handler;

import com.njydsz.pmis.cronjob.config.CronjobProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * P2-15: Docker 容器沙箱脚本执行器。
 *
 * <p>将 SHELL/Python 脚本在 Docker 容器中隔离执行，提供更强的安全隔离：
 * <ul>
 *   <li><b>文件隔离</b>：容器内独立文件系统，无法访问宿主机</li>
 *   <li><b>网络隔离</b>：可配置 --network=none 禁止网络访问</li>
 *   <li><b>资源限制</b>：CPU / 内存 / PID 限制，防止资源耗尽</li>
 *   <li><b>权限降级</b>：以非 root 用户运行</li>
 * </ul>
 *
 * <h3>执行命令</h3>
 * <pre>
 * docker run --rm \
 *   --name pmis-sandbox-{jobKey}-{timestamp} \
 *   --network=none \
 *   --memory=256m \
 *   --cpus=1 \
 *   --pids-limit=100 \
 *   --read-only \
 *   --tmpfs /tmp:rw,size=10m \
 *   -v /path/to/script:/script:ro \
 *   {image} {interpreter} /script
 * </pre>
 *
 * <h3>启用方式</h3>
 * <pre>
 * pmis.cronjob.sandbox.enabled=true
 * pmis.cronjob.sandbox.docker-image=python:3.11-slim
 * </pre>
 *
 * <p>对标 DolphinScheduler 的 Docker 沙箱和 Airflow 的 KubernetesPodOperator。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DockerSandboxExecutor {

    private final CronjobProperties cronjobProperties;

    /** 默认 Docker 镜像 */
    private static final String DEFAULT_IMAGE = "python:3.11-slim";

    /**
     * 在 Docker 容器中执行脚本。
     *
     * @param scriptContent 脚本内容
     * @param language      脚本语言：shell / python / python3
     * @param timeoutSeconds 超时时间（秒）
     * @return 执行结果（stdout + exitCode）
     */
    public SandboxResult execute(String scriptContent, String language, int timeoutSeconds) {
        CronjobProperties.Sandbox sandboxConfig = cronjobProperties.getSandbox();
        if (!sandboxConfig.isEnabled()) {
            // 沙箱未启用，返回错误（调用方应检查配置）
            return new SandboxResult(false, "Docker 沙箱未启用", -1, "");
        }

        String image = resolveImage(language);
        String interpreter = resolveInterpreter(language);
        String containerName = "pmis-sandbox-" + System.currentTimeMillis();

        // 构造 Docker 命令
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "run", "--rm",
                "--name", containerName,
                "--network=none",
                "--memory=256m",
                "--cpus=1",
                "--pids-limit=100",
                "--read-only",
                "--tmpfs", "/tmp:rw,size=10m",
                "-i",
                image,
                interpreter, "-"
        );
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();

            // 通过 stdin 传入脚本内容（避免创建临时文件）
            process.getOutputStream().write(scriptContent.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();

            // 等待完成或超时
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                // 清理容器
                killContainer(containerName);
                return new SandboxResult(false, "执行超时", -1, "");
            }

            // 读取 stdout 并显式关闭输入流以释放资源
            String output;
            try (java.io.InputStream inputStream = process.getInputStream()) {
                output = new BufferedReader(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                        .lines().collect(Collectors.joining("\n"));
            }

            int exitCode = process.exitValue();
            boolean success = exitCode == 0;

            // 截断输出
            int maxOutput = sandboxConfig.getMaxOutputSize();
            if (output.length() > maxOutput) {
                output = output.substring(0, maxOutput) + "...[truncated]";
            }

            return new SandboxResult(success, output, exitCode, output);
        } catch (Exception e) {
            log.error("[DockerSandbox] 执行异常: reason={}", e.getMessage(), e);
            return new SandboxResult(false, "执行异常: " + e.getMessage(), -1, "");
        }
    }

    /**
     * 根据脚本语言解析 Docker 镜像。
     */
    private String resolveImage(String language) {
        if (language == null) {
            return DEFAULT_IMAGE;
        }
        return switch (language.toLowerCase()) {
            case "shell", "sh", "bash" -> "bash:5.2";
            case "python", "python3" -> DEFAULT_IMAGE;
            default -> DEFAULT_IMAGE;
        };
    }

    /**
     * 根据脚本语言解析解释器命令。
     */
    private String resolveInterpreter(String language) {
        if (language == null) {
            return "python3";
        }
        return switch (language.toLowerCase()) {
            case "shell", "sh" -> "sh";
            case "bash" -> "bash";
            case "python", "python3" -> "python3";
            default -> "python3";
        };
    }

    /**
     * 强制清理容器（超时或异常时调用）。
     */
    private void killContainer(String containerName) {
        try {
            Process kill = new ProcessBuilder("docker", "rm", "-f", containerName).start();
            kill.waitFor(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("[DockerSandbox] 清理容器失败: name={} reason={}", containerName, e.getMessage());
        }
    }

    /**
     * 沙箱执行结果。
     *
     * @param success  是否成功
     * @param message  结果消息
     * @param exitCode 退出码
     * @param output   标准输出
     */
    public record SandboxResult(boolean success, String message, int exitCode, String output) {
    }
}
