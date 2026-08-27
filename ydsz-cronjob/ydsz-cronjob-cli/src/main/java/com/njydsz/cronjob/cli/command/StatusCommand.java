package com.njydsz.cronjob.cli.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.njydsz.cronjob.cli.CronjobCliApplication;

/**
 * 集群状态命令（P2-7）。
 *
 * <p>查询 Dashboard 概览数据，展示集群运行状态。
 *
 * <p>用法：{@code status}
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class StatusCommand extends AbstractHttpCommand {

  /**
   * 构造状态命令。
   *
   * @param serverUrl 服务端地址
   */
  public StatusCommand(String serverUrl) {
    super(serverUrl);
  }

  @Override
  public String name() {
    return "status";
  }

  @Override
  public String description() {
    return "查看集群运行状态";
  }

  @Override
  public String usage() {
    return "status";
  }

  @Override
  public void execute(String[] args) throws Exception {
    String body = get("/api/v1/cronjob/dashboard/overview");
    JsonNode data = CronjobCliApplication.parseData(body);

    JsonNode summary = data.path("summary");
    System.out.println("=== 集群运行状态 ===");
    System.out.printf("  任务总数:    %d%n", summary.path("total").asInt());
    System.out.printf("  正常运行:    %d%n", summary.path("normalCount").asInt());
    System.out.printf("  已暂停:      %d%n", summary.path("pausedCount").asInt());
    System.out.printf("  异常状态:    %d%n", summary.path("errorCount").asInt());

    JsonNode statusDist = data.path("statusDistribution");
    if (!statusDist.isMissingNode()) {
      System.out.println("\n--- 状态分布 ---");
      statusDist.fields().forEachRemaining(entry -> System.out.printf("  %-12s %d%n", entry.getKey(), entry.getValue().asInt()));
    }
  }
}
