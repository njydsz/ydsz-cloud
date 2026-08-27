package com.njydsz.cronjob.cli.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.njydsz.cronjob.cli.CronjobCliApplication;

/**
 * DAG 工作流管理命令（P1-3：CLI 工具增强）。
 *
 * <p>提供命令行方式管理 DAG 工作流，支持以下子命令：
 *
 * <ul>
 *   <li>{@code dag list} - 列出所有 DAG 定义
 *   <li>{@code dag trigger <dagKey>} - 触发指定 DAG
 *   <li>{@code dag instances <dagKey>} - 查询 DAG 实例列表
 *   <li>{@code dag show <instanceId>} - 查询 DAG 实例详情
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class DagCommand extends AbstractHttpCommand {

  public DagCommand(String serverUrl) {
    super(serverUrl);
  }

  @Override
  public String name() {
    return "dag";
  }

  @Override
  public String description() {
    return "DAG 工作流管理（list/trigger/instances/show）";
  }

  @Override
  public String usage() {
    return "dag <subcommand> [args]"
        + "\n  dag list                           - 列出所有 DAG 定义"
        + "\n  dag trigger <dagKey>               - 触发指定 DAG"
        + "\n  dag instances <dagKey> [limit]     - 查询 DAG 实例列表"
        + "\n  dag show <instanceId>              - 查询 DAG 实例详情";
  }

  @Override
  public void execute(String[] args) throws Exception {
    if (args.length < 1) {
      System.out.println("用法: " + usage());
      return;
    }

    String subCmd = args[0].toLowerCase();
    switch (subCmd) {
      case "list" -> executeList();
      case "trigger" -> {
        if (args.length < 2) {
          System.out.println("用法: dag trigger <dagKey>");
          return;
        }
        executeTrigger(args[1]);
      }
      case "instances" -> {
        if (args.length < 2) {
          System.out.println("用法: dag instances <dagKey> [limit]");
          return;
        }
        int limit = args.length > 2 ? Integer.parseInt(args[2]) : 20;
        executeInstances(args[1], limit);
      }
      case "show" -> {
        if (args.length < 2) {
          System.out.println("用法: dag show <instanceId>");
          return;
        }
        executeShow(args[1]);
      }
      default -> System.out.println("未知子命令: " + subCmd + "\n" + usage());
    }
  }

  /**
   * 列出所有 DAG 定义。
   */
  private void executeList() throws Exception {
    String body = get("/api/v1/cronjob/dag/definition/list");
    JsonNode data = CronjobCliApplication.parseData(body);

    if (!data.isArray() || data.isEmpty()) {
      System.out.println("暂无 DAG 定义");
      return;
    }

    System.out.println("┌──────────────────────────────────────────────────────────────┐");
    System.out.println("│                      DAG 工作流列表                         │");
    System.out.println("├──────────────────────────────────────────────────────────────┤");

    for (JsonNode dag : data) {
      String id = dag.path("id").asText();
      String dagKey = dag.path("dagKey").asText();
      String name = dag.path("name").asText();
      String status = dag.path("status").asText();

      System.out.printf("│ ID: %s%n", id);
      System.out.printf("│ Key: %s%n", dagKey);
      System.out.printf("│ Name: %s%n", name);
      System.out.printf("│ Status: %s%n", status);
      System.out.println("├──────────────────────────────────────────────────────────────┤");
    }
    System.out.printf("共 %d 个 DAG 定义%n", data.size());
  }

  /**
   * 触发指定 DAG。
   *
   * @param dagKey DAG KEY
   */
  private void executeTrigger(String dagKey) throws Exception {
    String body = post("/api/v1/cronjob/dag/definition/trigger/" + dagKey);
    JsonNode data = CronjobCliApplication.parseData(body);

    System.out.println("触发成功！");
    System.out.printf("实例 ID: %s%n", data.path("instanceId").asText());
    System.out.printf("状态: %s%n", data.path("status").asText());
  }

  /**
   * 查询 DAG 实例列表。
   *
   * @param dagKey DAG KEY
   * @param limit 返回条数
   */
  private void executeInstances(String dagKey, int limit) throws Exception {
    String body = get("/api/v1/cronjob/dag/instance/dag/" + dagKey + "?limit=" + limit);
    JsonNode data = CronjobCliApplication.parseData(body);

    if (!data.isArray() || data.isEmpty()) {
      System.out.printf("DAG [%s] 暂无实例%n", dagKey);
      return;
    }

    System.out.printf("┌──────────────────────────────────────────┐%n");
    System.out.printf("│     DAG [%s] 实例列表 (%d 条)            │%n", dagKey, data.size());
    System.out.printf("├──────────────────────────────────────────┤%n");

    for (JsonNode inst : data) {
      String id = inst.path("id").asText();
      String status = inst.path("status").asText();
      String triggerType = inst.path("triggerType").asText();
      String startedAt = inst.path("startedAt").asText();
      long durationMs = inst.path("durationMs").asLong();

      System.out.printf("│ ID: %s%n", id);
      System.out.printf("│ Status: %s%n", status);
      System.out.printf("│ Trigger: %s%n", triggerType);
      System.out.printf("│ Started: %s%n", startedAt);
      if (durationMs > 0) {
        System.out.printf("│ Duration: %dms%n", durationMs);
      }
      System.out.printf("├──────────────────────────────────────────┤%n");
    }
  }

  /**
   * 查询 DAG 实例详情。
   *
   * @param instanceId 实例 ID
   */
  private void executeShow(String instanceId) throws Exception {
    String body = get("/api/v1/cronjob/dag/instance/" + instanceId);
    JsonNode data = CronjobCliApplication.parseData(body);

    System.out.println("┌──────────────────────────────────────────┐");
    System.out.println("│           DAG 实例详情                   │");
    System.out.println("├──────────────────────────────────────────┤");
    System.out.printf("│ ID: %s%n", data.path("id").asText());
    System.out.printf("│ DAG Key: %s%n", data.path("dagKey").asText());
    System.out.printf("│ Status: %s%n", data.path("status").asText());
    System.out.printf("│ Trigger Type: %s%n", data.path("triggerType").asText());
    System.out.printf("│ Trigger By: %s%n", data.path("triggerBy").asText());
    System.out.printf("│ Started At: %s%n", data.path("startedAt").asText());
    System.out.printf("│ Finished At: %s%n", data.path("finishedAt").asText());
    System.out.printf("│ Duration: %dms%n", data.path("durationMs").asLong());

    // 节点统计
    int totalNodes = data.path("totalNodes").asInt();
    int successNodes = data.path("successNodes").asInt();
    int failedNodes = data.path("failedNodes").asInt();
    int skippedNodes = data.path("skippedNodes").asInt();

    System.out.println("├──────────────────────────────────────────┤");
    System.out.println("│ 节点统计:                                │");
    System.out.printf("│   总数: %d  成功: %d  失败: %d  跳过: %d%n",
        totalNodes, successNodes, failedNodes, skippedNodes);

    // 错误信息
    String errorMessage = data.path("errorMessage").asText();
    if (errorMessage != null && !errorMessage.isEmpty() && !"null".equals(errorMessage)) {
      System.out.println("├──────────────────────────────────────────┤");
      System.out.printf("│ 错误: %s%n", errorMessage);
    }

    System.out.println("└──────────────────────────────────────────┘");
  }
}
