package com.njydsz.cronjob.domain.job;

import java.util.Objects;

/**
 * MapReduce 子任务定义
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class MapTask {

  /** 子任务名称 */
  private String taskName;

  /** 子任务参数 JSON */
  private String taskParams;

  /** 无参构造器 */
  public MapTask() {}

  /**
   * 全参构造器。
   *
   * @param taskName 子任务名称
   * @param taskParams 子任务参数 JSON
   */
  public MapTask(String taskName, String taskParams) {
    this.taskName = taskName;
    this.taskParams = taskParams;
  }

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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    MapTask that = (MapTask) o;
    return Objects.equals(taskName, that.taskName)
        && Objects.equals(taskParams, that.taskParams);
  }

  @Override
  public int hashCode() {
    return Objects.hash(taskName, taskParams);
  }

  @Override
  public String toString() {
    return "MapTask{taskName='" + taskName + "', taskParams='" + taskParams + "'}";
  }
}
