package com.njydsz.cronjob.domain.job;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MapReduce 子任务定义
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
public class MapTask {

  /** 子任务名称 */
  private String taskName;

  /** 子任务参数 JSON */
  private String taskParams;

  // 显式构造器（避免 Lombok 处理差异）
  public MapTask(String taskName, String taskParams) {
    this.taskName = taskName;
    this.taskParams = taskParams;
  }

  // 显式 getter/setter
  public String getTaskName() {
    return taskName;
  }

  public void setTaskName(String taskName) {
    this.taskName = taskName;
  }

  public String getTaskParams() {
    return taskParams;
  }

  public void setTaskParams(String taskParams) {
    this.taskParams = taskParams;
  }
}
