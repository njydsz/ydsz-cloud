package com.njydsz.cronjob.domain.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * MapReduce 任务上下文
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class MapContext {

  /** 集合初始容量 */
  private static final int INITIAL_CAPACITY = 64;


  /** 任务 ID */
  private String jobId;

  /** 日志 ID */
  private String logId;

  /** 任务 key */
  private String jobKey;

  /** 任务名称 */
  private String taskName;

  /** 任务参数 */
  private String taskParams;

  /** 是否为 Root 任务 */
  private boolean root;

  /** 子任务列表（初始容量 64，减少 MapReduce 大量子任务场景下的扩容开销） */
  private List<MapTask> subTasks = new ArrayList<>(INITIAL_CAPACITY);

  /** 结果存储（初始容量 64，减少大量分片结果下的 rehash 开销） */
  private Map<String, Object> results = new HashMap<>(INITIAL_CAPACITY);

  // 显式 getter/setter（避免 Lombok 处理差异）
  public String getJobId() {
    return jobId;
  }

  public void setJobId(String jobId) {
    this.jobId = jobId;
  }

  public String getLogId() {
    return logId;
  }

  public void setLogId(String logId) {
    this.logId = logId;
  }

  public String getJobKey() {
    return jobKey;
  }

  public void setJobKey(String jobKey) {
    this.jobKey = jobKey;
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

  public boolean isRoot() {
    return root;
  }

  public void setRoot(boolean root) {
    this.root = root;
  }

  public List<MapTask> getSubTasks() {
    return subTasks;
  }

  public void setSubTasks(List<MapTask> subTasks) {
    this.subTasks = subTasks;
  }

  public Map<String, Object> getResults() {
    return results;
  }

  public void setResults(Map<String, Object> results) {
    this.results = results;
  }

  /**
   * 添加子任务
   *
   * @param taskName 子任务名称
   * @param taskParams 子任务参数 JSON
   */
  public void addSubTask(String taskName, String taskParams) {
    subTasks.add(new MapTask(taskName, taskParams));
  }
}
