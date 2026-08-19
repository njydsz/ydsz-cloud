package com.njydsz.cronjob.infra.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.cronjob.infra.entity.dag.JobDagNodeInstance;

/**
 * DAG 节点实例 Repository。
 *
 * <p>封装 {@code ydsz_job_dag_node_instance} 表的数据访问，提供业务语义化的查询方法。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobDagNodeInstanceRepository {

  /**
   * 根据 DAG 实例 ID 查询节点实例列表。
   *
   * @param dagInstanceId DAG 实例 ID
   * @return 节点实例列表
   */
  List<JobDagNodeInstance> selectByDagInstanceId(String dagInstanceId);

  /**
   * 根据 DAG 实例 ID 和任务 ID 查询节点实例。
   *
   * @param dagInstanceId DAG 实例 ID
   * @param jobId 任务 ID
   * @return 节点实例
   */
  JobDagNodeInstance selectByDagInstanceAndJob(String dagInstanceId, String jobId);

  /**
   * 根据 DAG 实例 ID 和任务 ID 查询所有匹配的节点实例（含 LOOP iter 实例）。
   *
   * @param dagInstanceId DAG 实例 ID
   * @param jobId 任务 ID
   * @return 节点实例列表
   */
  List<JobDagNodeInstance> selectAllByDagInstanceAndJob(String dagInstanceId, String jobId);

  /**
   * 标记节点实例为 RUNNING。
   *
   * @param nodeInstanceId 节点实例 ID
   * @param startedAt 开始时间
   * @return 受影响行数
   */
  int markRunning(String nodeInstanceId, LocalDateTime startedAt);

  /**
   * 标记节点实例为终态。
   *
   * @param nodeInstanceId 节点实例 ID
   * @param status 终态状态
   * @param finishedAt 完成时间
   * @param durationMs 耗时
   * @param resultJson 结果 JSON
   * @param errorMessage 错误信息
   * @param logId 日志 ID
   * @return 受影响行数
   */
  int markFinished(
      String nodeInstanceId,
      String status,
      LocalDateTime finishedAt,
      long durationMs,
      String resultJson,
      String errorMessage,
      String logId);

  /**
   * 标记节点实例为 SKIPPED。
   *
   * @param nodeInstanceId 节点实例 ID
   * @return 受影响行数
   */
  int markSkipped(String nodeInstanceId);

  /**
   * 标记节点实例为 RETRY（递增 retryCount）。
   *
   * @param nodeInstanceId 节点实例 ID
   * @return 受影响行数
   */
  int markRetry(String nodeInstanceId);

  /**
   * 批量插入节点实例。
   *
   * @param instances 节点实例列表
   */
  void insertBatch(List<JobDagNodeInstance> instances);
}
