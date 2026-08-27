package com.njydsz.cronjob.cli.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.njydsz.cronjob.cli.CronjobCliApplication;

/**
 * 任务诊断命令（P2-7）。
 *
 * <p>诊断指定任务的运行状态，包括最近执行记录、锁状态、系统负载等。
 *
 * <p>用法：{@code diagnose <jobKey>}
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class DiagnoseCommand extends AbstractHttpCommand {

  /**
   * 构造诊断命令。
   *
   * @param serverUrl 服务端地址
   */
  public DiagnoseCommand(String serverUrl) {
    super(serverUrl);
  }

  @Override
  public String name() {
    return "diagnose";
  }

  @Override
  public String description() {
    return "诊断指定任务的运行状态";
  }

  @Override
  public String usage() {
    return "diagnose <jobKey>";
  }

  @Override
  public void execute(String[] args) throws Exception {
    if (args.length < 1) {
      System.out.println("用法: " + usage());
      return;
    }
    String jobKey = args[0];
    String body = get("/api/v1/cronjob/monitor/diagnosis/" + jobKey);
    JsonNode data = CronjobCliApplication.parseData(body);

    System.out.println("=== 任务诊断: " + jobKey + " ===");
    System.out.printf("  诊断时间:    %s%n", data.path("diagnosisTime").asText());
    System.out.printf("  节点:        %s%n", data.path("nodeId").asText());
    System.out.printf("  最近执行:    %d 次%n", data.path("recentExecutions").asInt());

    JsonNode lastExec = data.path("lastExecution");
    if (!lastExec.isMissingNode() && !lastExec.isNull()) {
      System.out.println("\n--- 最近一次执行 ---");
      System.out.printf("  日志 ID:     %s%n", lastExec.path("logId").asText());
      System.out.printf("  触发类型:    %s%n", lastExec.path("triggerType").asText());
      System.out.printf("  开始时间:    %s%n", lastExec.path("startTime").asText());
      System.out.printf("  耗时:        %d ms%n", lastExec.path("durationMs").asLong());
      String errorMsg = lastExec.path("errorMessage").asText();
      if (errorMsg != null && !errorMsg.isEmpty() && !"-".equals(errorMsg)) {
        System.out.printf("  错误信息:    %s%n", errorMsg);
      }
    }

    System.out.printf("%n  锁状态:      %s%n", data.path("lockAcquired").asBoolean() ? "已锁定" : "未锁定");
    System.out.printf("  运行中任务:  %d%n", data.path("clusterRunningTasks").asInt());
  }
}
