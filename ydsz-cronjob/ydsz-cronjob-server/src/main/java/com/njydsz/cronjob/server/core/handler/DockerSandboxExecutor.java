package com.njydsz.cronjob.server.core.handler;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.njydsz.cronjob.server.config.CronjobProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * P2-15/P2-11: Docker 容器沙箱脚本执行器（增强版）。
 *
 * <p>将 SHELL/Python 脚本在 Docker 容器中隔离执行，提供更强的安全隔离：
 * <ul>
 *   <li><b>文件隔离</b>：容器内独立文件系统，无法访问宿主机</li>
 *   <li><b>网络隔离</b>：可配置 --network=none 禁止网络访问</li>
 *   <li><b>资源限制</b>：CPU / 内存 / PID 限制，防止资源耗尽（P2-11: 全部可配置）</li>
 *   <li><b>权限降级</b>：以非 root 用户运行（P2-11: 可配置用户）</li>
 *   <li><b>环境变量</b>：支持向容器传递白名单环境变量（P2-11 新增）</li>
 *   <li><b>输出安全</b>：异步读取 stdout，避免大输出导致管道阻塞死锁（P2-11 修复）</li>
 * </ul>
 *
 * <h3>执行命令</h3>
 * <pre>
 * docker run --rm \
 *   --name ydsz-sandbox-{jobKey}-{timestamp} \
 *   --network={dockerNetwork} \
 *   --memory={dockerMemory} \
 *   --cpus={dockerCpus} \
 *   --pids-limit={dockerPidsLimit} \
 *   --user={dockerUser} \
 *   --workdir={dockerWorkDir} \
 *   --read-only \
 *   --tmpfs /tmp:rw,size={dockerTmpfsSize} \
 *   -e JOB_PARAMS=... \
 *   -i \
 *   {image} {interpreter} -
 * </pre>
 *
 * <h3>启用方式</h3>
 * <pre>
 * ydsz.cronjob.sandbox.docker-enabled=true
 * ydsz.cronjob.sandbox.docker-image=python:3.11-slim
 * ydsz.cronjob.sandbox.docker-memory=512m
 * ydsz.cronjob.sandbox.docker-cpus=2
 * </pre>
 *
 * <p>对标 DolphinScheduler 的 Docker 沙箱和 Airflow 的 KubernetesPodOperator。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DockerSandboxExecutor {

    private final CronjobProperties cronjobProperties;

    /** Docker 可用性检查缓存 */
    private volatile Boolean dockerAvailable = null;

    /**
     * 在 Docker 容器中执行脚本。
     *
     * @param scriptContent 脚本内容
     * @param language      脚本语言：shell / python / python3
     * @param timeoutSeconds 超时时间（秒）
     * @return 执行结果（stdout + exitCode）
     */
    public SandboxResult execute(String scriptContent, String language, int timeoutSeconds) {
        return execute(scriptContent, language, timeoutSeconds, null);
    }

    /**
     * P2-11: 在 Docker 容器中执行脚本（支持环境变量）。
     *
     * @param scriptContent  脚本内容
     * @param language       脚本语言：shell / python / python3
     * @param timeoutSeconds 超时时间（秒）
     * @param envVars        环境变量（传递到容器内，如 JOB_PARAMS）
     * @return 执行结果（stdout + exitCode）
     */
    public SandboxResult execute(String scriptContent, String language, int timeoutSeconds,
                                  Map<String, String> envVars) {
        CronjobProperties.Sandbox sandboxConfig = cronjobProperties.getSandbox();
        if (!sandboxConfig.isEnabled() || !sandboxConfig.isDockerEnabled()) {
            return new SandboxResult(false, "Docker 沙箱未启用", -1, "");
        }

        // P2-11: Docker 可用性检查（带缓存）
        if (!isDockerAvailable()) {
            return new SandboxResult(false, "Docker 不可用，请检查 Docker 安装和权限", -1, "");
        }

        String image = resolveImage(language, sandboxConfig);
        String interpreter = resolveInterpreter(language);
        String containerName = "ydsz-sandbox-" + System.currentTimeMillis();

        // P2-11: 构造可配置的 Docker 命令
        List<String> command = buildDockerCommand(sandboxConfig, image, interpreter, containerName, envVars);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();

            // P2-11: 异步读取 stdout（避免大输出导致管道阻塞死锁）
            StringBuilder outputBuilder = new StringBuilder();
            Thread outputReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    int maxOutput = sandboxConfig.getMaxOutputSize();
                    while ((line = reader.readLine()) != null) {
                        if (outputBuilder.length() + line.length() > maxOutput) {
                            outputBuilder.append("\n[OUTPUT TRUNCATED]");
                            break;
                        }
                        outputBuilder.append(line).append("\n");
                    }
                } catch (Exception e) {
                    log.debug("[DockerSandbox] 输出读取异常: {}", e.getMessage());
                }
            }, "docker-sandbox-output");
            outputReader.setDaemon(true);
            outputReader.start();

            // 通过 stdin 传入脚本内容
            process.getOutputStream().write(scriptContent.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();

            // 等待完成或超时
            int effectiveTimeout = timeoutSeconds > 0 ? timeoutSeconds : sandboxConfig.getTimeoutSeconds();
            boolean finished = process.waitFor(effectiveTimeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                killContainer(containerName);
                outputReader.join(2000);
                return new SandboxResult(false, "执行超时 (" + effectiveTimeout + "s)", -1, outputBuilder.toString());
            }

            // 等待输出读取完成
            outputReader.join(3000);

            int exitCode = process.exitValue();
            boolean success = exitCode == 0;
            String output = outputBuilder.toString();

            return new SandboxResult(success, success ? "success" : "exit code: " + exitCode, exitCode, output);
        } catch (Exception e) {
            log.error("[DockerSandbox] 执行异常: reason={}", e.getMessage(), e);
            return new SandboxResult(false, "执行异常: " + e.getMessage(), -1, "");
        }
    }

    /**
     * P2-11: 构造可配置的 Docker run 命令。
     *
     * @param config        沙箱配置
     * @param image         Docker 镜像
     * @param interpreter   脚本解释器
     * @param containerName 容器名称
     * @param envVars       环境变量
     * @return Docker 命令参数列表
     */
    private List<String> buildDockerCommand(CronjobProperties.Sandbox config, String image,
                                             String interpreter, String containerName,
                                             Map<String, String> envVars) {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("--rm");
        cmd.add("--name");
        cmd.add(containerName);

        // 网络隔离
        cmd.add("--network=" + config.getDockerNetwork());

        // 资源限制
        cmd.add("--memory=" + config.getDockerMemory());
        cmd.add("--cpus=" + config.getDockerCpus());
        cmd.add("--pids-limit=" + String.valueOf(config.getDockerPidsLimit()));

        // 权限降级
        if (config.getDockerUser() != null && !config.getDockerUser().isBlank()) {
            cmd.add("--user=" + config.getDockerUser());
        }

        // 工作目录
        if (config.getDockerWorkDir() != null && !config.getDockerWorkDir().isBlank()) {
            cmd.add("--workdir=" + config.getDockerWorkDir());
        }

        // 只读文件系统
        if (config.isDockerReadOnly()) {
            cmd.add("--read-only");
        }

        // tmpfs 挂载
        if (config.getDockerTmpfsSize() != null && !config.getDockerTmpfsSize().isBlank()) {
            cmd.add("--tmpfs");
            cmd.add("/tmp:rw,size=" + config.getDockerTmpfsSize());
        }

        // P2-11: 环境变量传递
        if (envVars != null) {
            for (Map.Entry<String, String> entry : envVars.entrySet()) {
                cmd.add("-e");
                cmd.add(entry.getKey() + "=" + entry.getValue());
            }
        }

        // stdin 输入
        cmd.add("-i");

        // 镜像和解释器
        cmd.add(image);
        cmd.add(interpreter);
        cmd.add("-");

        return cmd;
    }

    /**
     * P2-11: 检查 Docker 是否可用（带缓存）。
     *
     * @return true Docker 可用
     */
    private boolean isDockerAvailable() {
        if (dockerAvailable != null) {
            return dockerAvailable;
        }
        try {
            Process check = new ProcessBuilder("docker", "info").start();
            boolean finished = check.waitFor(5, TimeUnit.SECONDS);
            dockerAvailable = finished && check.exitValue() == 0;
            if (!dockerAvailable) {
                log.warn("[DockerSandbox] Docker 不可用，请检查安装和权限");
            }
            return dockerAvailable;
        } catch (Exception e) {
            dockerAvailable = false;
            log.warn("[DockerSandbox] Docker 检查失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 根据脚本语言解析 Docker 镜像。
     */
    private String resolveImage(String language, CronjobProperties.Sandbox config) {
        if (language == null) {
            return config.getDockerImage();
        }
        return switch (language.toLowerCase()) {
            case "shell", "sh", "bash" -> config.getDockerShellImage();
            case "python", "python3" -> config.getDockerImage();
            default -> config.getDockerImage();
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
