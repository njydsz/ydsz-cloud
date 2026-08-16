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
 * @since 1.0.0
 */
@Data
public class MapContext {

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
  private List<MapTask> subTasks = new ArrayList<>(64);

  /** 结果存储（初始容量 64，减少大量分片结果下的 rehash 开销） */
  private Map<String, Object> results = new HashMap<>(64);

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
