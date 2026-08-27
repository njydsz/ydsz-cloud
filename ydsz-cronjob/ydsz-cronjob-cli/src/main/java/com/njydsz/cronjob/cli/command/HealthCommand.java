package com.njydsz.cronjob.cli.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.njydsz.cronjob.cli.CronjobCliApplication;

/**
 * 系统健康检查命令（P1-3：CLI 工具增强）。
 *
 * <p>快速查看集群健康状态，无需打开浏览器：
 *
 * <pre>{@code
 * health           # 查看系统综合健康状态
 * health --detail  # 查看详细指标
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class HealthCommand extends AbstractHttpCommand {

  public HealthCommand(String serverUrl) {
    super(serverUrl);
  }

  @Override
  public String name() {
    return "health";
  }

  @Override
  public String description() {
    return "系统健康状态检查";
  }

  @Override
  public String usage() {
    return "health [--detail]    - 查看系统健康状态（--detail 显示详细指标）";
  }

  @Override
  public void execute(String[] args) throws Exception {
    boolean detail = args.length > 0 && "--detail".equals(args[0]);
    String body = get("/api/v1/cronjob/dashboard/health");
    JsonNode data = CronjobCliApplication.parseData(body);

    System.out.println("┌──────────────────────────────────────────┐");
    System.out.println("│          系统健康状态                    │");
    System.out.println("├──────────────────────────────────────────┤");
    System.out.printf("│ 节点: %s%n", data.path("nodeId").asText());
    System.out.printf("│ 时间: %s%n", data.path("timestamp").asText());

    // 综合评分
    int overallScore = data.path("overallScore").asInt();
    String scoreLabel = overallScore >= 80 ? "优秀" : overallScore >= 60 ? "良好" : "异常";
    System.out.printf("│ 综合评分: %d/100 (%s)%n", overallScore, scoreLabel);

    // 系统资源
    JsonNode system = data.path("system");
    if (system.isObject()) {
      System.out.println("├──────────────────────────────────────────┤");
      System.out.println("│ 【系统资源】                             │");
      System.out.printf("│ CPU: %s (%d 核)%n",
          system.path("cpuUsage").asText(),
          system.path("cpuCores").asInt());
      System.out.printf("│ 内存: %s (%dMB / %dMB)%n",
          system.path("memoryUsage").asText(),
          system.path("memoryUsedMB").asLong(),
          system.path("memoryMaxMB").asLong());

      String sysHealth = system.path("healthLevel").asText();
      System.out.printf("│ 健康级别: %s%n", sysHealth);
    }

    // 任务概览
    JsonNode tasks = data.path("tasks");
    if (tasks.isObject()) {
      System.out.println("├──────────────────────────────────────────┤");
      System.out.println("│ 【任务概览】                             │");
      System.out.printf("│ 总数: %d (运行中: %d, 暂停: %d, 异常: %d)%n",
          tasks.path("total").asLong(),
          tasks.path("normal").asLong(),
          tasks.path("paused").asLong(),
          tasks.path("error").asLong());

      JsonNode todayExec = tasks.path("todayExecution");
      if (todayExec.isObject()) {
        System.out.printf("│ 今日执行: 总计 %d | 成功 %d | 失败 %d | 运行中 %d%n",
            todayExec.path("total").asLong(),
            todayExec.path("success").asLong(),
            todayExec.path("failed").asLong(),
            todayExec.path("running").asLong());
        System.out.printf("│ 今日成功率: %s%n", todayExec.path("successRate").asText());
      }
    }

    // 调度器状态
    JsonNode scheduler = data.path("scheduler");
    if (scheduler.isObject()) {
      System.out.println("├──────────────────────────────────────────┤");
      System.out.println("│ 【调度器】                               │");
      System.out.printf("│ Leader 启用: %s%n", scheduler.path("leaderEnabled").asBoolean());
      System.out.printf("│ 当前节点是 Leader: %s%n", scheduler.path("isLeader").asBoolean(false));
      if (scheduler.path("currentLeader").isValueNode()) {
        System.out.printf("│ 当前 Leader: %s%n", scheduler.path("currentLeader").asText());
      }
      System.out.printf("│ 集群运行中任务: %d%n", scheduler.path("clusterRunningTasks").asLong());
    }

    // 最近异常
    JsonNode issues = data.path("recentIssues");
    if (issues.isObject()) {
      int failureCount = issues.path("failureCount").asInt();
      if (failureCount > 0) {
        System.out.println("├──────────────────────────────────────────┤");
        System.out.printf("│ 【最近失败】 (%d 条)%n", failureCount);
        JsonNode failures = issues.path("recentFailures");
        if (failures.isArray()) {
          int showCount = Math.min(failureCount, 5);
          for (int i = 0; i < showCount; i++) {
            JsonNode fail = failures.get(i);
            System.out.printf("│   - %s: %s (%s)%n",
                fail.path("jobKey").asText(),
                fail.path("status").asText(),
                fail.path("errorMessage").asText("未知错误"));
          }
          if (failureCount > showCount) {
            System.out.printf("│   ... 还有 %d 条%n", failureCount - showCount);
          }
        }
      }
    }

    // 详细模式：显示 DAG 和线程池信息
    if (detail) {
      JsonNode dag = data.path("dag");
      if (dag.isObject()) {
        System.out.println("├──────────────────────────────────────────┤");
        System.out.println("│ 【DAG 工作流】                           │");
        System.out.printf("│ 运行中实例: %d%n", dag.path("runningInstances").asLong());
        System.out.printf("│ 今日执行: 总计 %d | 成功 %d | 失败 %d%n",
            dag.path("todayTotal").asLong(),
            dag.path("todaySuccess").asLong(),
            dag.path("todayFailed").asLong());
        System.out.printf("│ 今日成功率: %s%n", dag.path("todaySuccessRate").asText());
      }

      JsonNode threadPool = data.path("system").path("threadPool");
      if (threadPool.isObject() && threadPool.size() > 0) {
        System.out.println("├──────────────────────────────────────────┤");
        System.out.println("│ 【线程池】                               │");
        System.out.printf("│ 活跃: %d / 池大小: %d / 最大: %d%n",
            threadPool.path("activeCount").asInt(),
            threadPool.path("poolSize").asInt(),
            threadPool.path("maxPoolSize").asInt());
        System.out.printf("│ 队列: %d (使用率: %d%%)%n",
            threadPool.path("queueSize").asInt(),
            threadPool.path("usagePct").asInt());
      }
    }

    System.out.println("└──────────────────────────────────────────┘");
  }
}
