package com.njydsz.cronjob.server.config;

import lombok.Data;

/**
 * P3-11: 脚本执行沙箱配置。
 *
 * <p>控制 SandboxScriptExecutor 的安全隔离行为。 启用后，SHELL/GLUE 类型任务的脚本将在受限环境中执行。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class SandboxConfig {

  /** 默认timeoutSeconds值（可被配置文件覆盖） */
  private static final int DEFAULT_TIMEOUT_SECONDS = 300;

  /** 默认maxOutputSize值（可被配置文件覆盖） */
  private static final int DEFAULT_MAX_OUTPUT_SIZE = 1048576;

  /** 默认dockerPidsLimit值（可被配置文件覆盖） */
  private static final int DEFAULT_DOCKER_PIDS_LIMIT = 100;

  /**
   * 是否启用沙箱模式（false=使用 ScriptJobHandler 原始执行逻辑）。
   *
   * <p>P0-3: 默认改为 true，防止脚本任务代码注入风险。 可通过 {@code ydsz.cronjob.sandbox.enabled=false} 关闭。
   */
  private boolean enabled = true;

  /** 默认超时时间（秒） */
  private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;

  /** 最大输出大小（字节，默认 1MB） */
  private int maxOutputSize = DEFAULT_MAX_OUTPUT_SIZE;

  /** 沙箱工作目录 */
  private String workDir = "./data/sandbox";

  // ==================== P2-11: Docker 沙箱增强 ====================

  /**
   * P2-11: 是否启用 Docker 容器沙箱（比进程沙箱更强的隔离）。
   *
   * <p>启用后，SHELL/Python 脚本在 Docker 容器中执行，提供文件系统隔离、 网络隔离、资源限制和权限降级。 需要宿主机安装 Docker 且应用有 docker
   * 命令执行权限。
   */
  private boolean dockerEnabled = false;

  /** P2-11: 默认 Docker 镜像（Python 脚本） */
  private String dockerImage = "python:3.11-slim";

  /** P2-11: Shell 脚本 Docker 镜像 */
  private String dockerShellImage = "bash:5.2";

  /** P2-11: 容器内存限制（如 256m / 512m / 1g） */
  private String dockerMemory = "256m";

  /** P2-11: 容器 CPU 限制（核数，如 0.5 / 1 / 2） */
  private String dockerCpus = "1";

  /** P2-11: 容器最大进程数限制（防止 fork 炸弹） */
  private int dockerPidsLimit = DEFAULT_DOCKER_PIDS_LIMIT;

  /** P2-11: 网络模式: none（禁网）/ bridge（默认桥接）/ host */
  private String dockerNetwork = "none";

  /** P2-11: 容器内运行用户（如 nobody / 1000:1000），空则使用镜像默认用户 */
  private String dockerUser = "nobody";

  /** P2-11: 容器内工作目录 */
  private String dockerWorkDir = "/tmp/sandbox";

  /** P2-11: tmpfs 挂载大小（如 10m / 50m），空则不挂载 tmpfs */
  private String dockerTmpfsSize = "10m";

  /** P2-11: 是否只读文件系统（--read-only） */
  private boolean dockerReadOnly = true;
}
