package com.njydsz.cronjob.cli.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.njydsz.cronjob.cli.CronjobCliApplication;

/**
 * 触发任务命令（P2-7）。
 *
 * <p>立即触发指定任务执行一次。
 *
 * <p>用法：{@code trigger <jobId>}
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class TriggerCommand extends AbstractHttpCommand {

  /**
   * 构造触发命令。
   *
   * @param serverUrl 服务端地址
   */
  public TriggerCommand(String serverUrl) {
    super(serverUrl);
  }

  @Override
  public String name() {
    return "trigger";
  }

  @Override
  public String description() {
    return "立即触发指定任务";
  }

  @Override
  public String usage() {
    return "trigger <jobId>";
  }

  @Override
  public void execute(String[] args) throws Exception {
    if (args.length < 1) {
      System.out.println("用法: " + usage());
      return;
    }
    String jobId = args[0];
    String body = post("/api/v1/cronjob/job/" + jobId + "/trigger?holdLock=true");
    JsonNode data = CronjobCliApplication.parseData(body);
    System.out.println("触发成功，执行日志 ID: " + data.asText());
  }
}
