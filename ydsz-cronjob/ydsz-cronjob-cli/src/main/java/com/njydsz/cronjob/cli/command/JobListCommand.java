package com.njydsz.cronjob.cli.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.njydsz.cronjob.cli.CronjobCliApplication;

/**
 * 任务列表命令（P2-7）。
 *
 * <p>分页查询任务列表，支持关键字/状态/分组过滤。
 *
 * <p>用法：{@code list [--page N] [--size N] [--status STATUS] [--group GROUP]}
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class JobListCommand extends AbstractHttpCommand {

  /**
   * 构造任务列表命令。
   *
   * @param serverUrl 服务端地址
   */
  public JobListCommand(String serverUrl) {
    super(serverUrl);
  }

  @Override
  public String name() {
    return "list";
  }

  @Override
  public String description() {
    return "分页查询任务列表";
  }

  @Override
  public String usage() {
    return "list [--page N] [--size N] [--status STATUS] [--group GROUP]";
  }

  @Override
  public void execute(String[] args) throws Exception {
    int page = 1;
    int size = 20;
    String status = null;
    String group = null;

    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--page" -> page = Integer.parseInt(args[++i]);
        case "--size" -> size = Integer.parseInt(args[++i]);
        case "--status" -> status = args[++i];
        case "--group" -> group = args[++i];
        default -> {
          /* ignore */
        }
      }
    }

    StringBuilder path = new StringBuilder("/api/v1/cronjob/job/page?page=" + page + "&size=" + size);
    if (status != null) {
      path.append("&status=").append(status);
    }
    if (group != null) {
      path.append("&jobGroup=").append(group);
    }

    String body = get(path.toString());
    JsonNode data = CronjobCliApplication.parseData(body);

    JsonNode records = data.path("records");
    System.out.printf("=== 任务列表 (第 %d 页, 共 %d 条) ===%n", page, data.path("total").asInt());
    System.out.printf("%-20s %-20s %-10s %-12s %-10s%n", "ID", "KEY", "名称", "状态", "分组");
    System.out.println("-".repeat(75));

    if (records.isArray()) {
      for (JsonNode record : records) {
        System.out.printf(
            "%-20s %-20s %-10s %-12s %-10s%n",
            abbreviate(record.path("id").asText(), 20),
            abbreviate(record.path("jobKey").asText(), 20),
            abbreviate(record.path("jobName").asText(), 10),
            record.path("status").asText(),
            record.path("jobGroup").asText());
      }
    }
  }

  /**
   * 缩写字符串到指定长度。
   *
   * @param str 原字符串
   * @param maxLen 最大长度
   * @return 缩写后的字符串
   */
  private String abbreviate(String str, int maxLen) {
    if (str == null || str.isEmpty()) {
      return "-";
    }
    return str.length() > maxLen ? str.substring(0, maxLen - 2) + ".." : str;
  }
}
