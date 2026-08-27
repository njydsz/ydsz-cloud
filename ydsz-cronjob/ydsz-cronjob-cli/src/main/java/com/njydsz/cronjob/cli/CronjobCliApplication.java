package com.njydsz.cronjob.cli;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.njydsz.cronjob.cli.command.CliCommand;
import com.njydsz.cronjob.cli.command.DagCommand;
import com.njydsz.cronjob.cli.command.DiagnoseCommand;
import com.njydsz.cronjob.cli.command.HealthCommand;
import com.njydsz.cronjob.cli.command.HelpCommand;
import com.njydsz.cronjob.cli.command.JobListCommand;
import com.njydsz.cronjob.cli.command.StatusCommand;
import com.njydsz.cronjob.cli.command.TriggerCommand;

/**
 * Cronjob CLI 运维工具入口（P2-7）。
 *
 * <p>提供命令行方式管理分布式任务调度引擎，支持以下命令：
 *
 * <ul>
 *   <li>{@code status} - 查看集群运行状态
 *   <li>{@code list} - 分页查询任务列表
 *   <li>{@code trigger <jobId>} - 立即触发指定任务
 *   <li>{@code diagnose <jobKey>} - 诊断任务运行状态
 *   <li>{@code health} - 系统健康检查（P1-3）
 *   <li>{@code dag} - DAG 工作流管理（P1-3）
 *   <li>{@code help} - 显示帮助信息
 *   <li>{@code exit} - 退出 CLI
 * </ul>
 *
 * <p>使用方式：
 *
 * <pre>{@code
 * java -jar ydsz-cronjob-cli.jar --server=http://localhost:8080
 * > status
 * > list --page 1 --size 10
 * > trigger job-123
 * > diagnose order-sync
 * > exit
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class CronjobCliApplication {

  /** 默认服务端地址 */
  private static final String DEFAULT_SERVER = "http://localhost:8080";

  /** JSON 序列化/反序列化器 */
  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** 命令注册表 */
  private final Map<String, CliCommand> commands = new LinkedHashMap<>();

  /** 服务端地址 */
  private final String serverUrl;

  /**
   * 构造 CLI 应用。
   *
   * @param serverUrl 服务端地址
   */
  public CronjobCliApplication(String serverUrl) {
    this.serverUrl = serverUrl;
    registerCommands();
  }

  /** 注册所有命令 */
  private void registerCommands() {
    commands.put("status", new StatusCommand(serverUrl));
    commands.put("list", new JobListCommand(serverUrl));
    commands.put("trigger", new TriggerCommand(serverUrl));
    commands.put("diagnose", new DiagnoseCommand(serverUrl));
    commands.put("dag", new DagCommand(serverUrl));
    commands.put("health", new HealthCommand(serverUrl));
    commands.put("help", new HelpCommand(commands));
  }

  /**
   * 启动交互式 CLI 循环。   *
   * <p>从标准输入读取命令，解析并执行对应的 {@link CliCommand}。   */
  public void run() {
    printBanner();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
      String line;
      while (true) {
        System.out.print("cronjob> ");
        line = reader.readLine();
        if (line == null) {
          break;
        }
        line = line.trim();
        if (line.isEmpty()) {
          continue;
        }
        if ("exit".equalsIgnoreCase(line) || "quit".equalsIgnoreCase(line)) {
          System.out.println("再见！");
          break;
        }
        executeCommand(line);
      }
    } catch (Exception e) {
      System.err.println("CLI 异常: " + e.getMessage());
    }
  }

  /**
   * 执行单条命令。   *
   * @param line 命令行
   */
  private void executeCommand(String line) {
    String[] parts = line.split("\\s+");
    String cmdName = parts[0].toLowerCase();
    String[] args = Arrays.copyOfRange(parts, 1, parts.length);

    CliCommand cmd = commands.get(cmdName);
    if (cmd == null) {
      System.out.println("未知命令: " + cmdName + "，输入 'help' 查看可用命令");
      return;
    }
    try {
      cmd.execute(args);
    } catch (Exception e) {
      System.err.println("执行失败: " + e.getMessage());
    }
  }

  /** 打印启动横幅 */
  private void printBanner() {
    System.out.println("╔══════════════════════════════════════════╗");
    System.out.println("║        ydsz-cronjob CLI 运维工具         ║");
    System.out.println("║        Server: " + serverUrl);
    System.out.println("╚══════════════════════════════════════════╝");
    System.out.println("输入 'help' 查看可用命令，'exit' 退出\n");
  }

  /**
   * 程序入口。   *
   * @param args 命令行参数（--server=http://host:port）
   */
  public static void main(String[] args) {
    String server = DEFAULT_SERVER;
    for (String arg : args) {
      if (arg.startsWith("--server=")) {
        server = arg.substring("--server=".length());
      }
    }
    new CronjobCliApplication(server).run();
  }

  /**
   * 获取 JSON 响应中的 data 节点。   *
   * @param body 响应体 JSON 字符串
   * @return data 节点
   * @throws Exception 解析异常
   */
  static JsonNode parseData(String body) throws Exception {
    JsonNode root = MAPPER.readTree(body);
    if (!"A00000".equals(root.path("code").asText())) {
      throw new RuntimeException(root.path("msg").asText("未知错误"));
    }
    return root.path("data");
  }
}
