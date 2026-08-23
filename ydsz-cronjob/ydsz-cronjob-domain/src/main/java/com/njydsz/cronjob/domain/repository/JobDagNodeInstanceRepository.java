package com.njydsz.cronjob.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.njydsz.cronjob.domain.vo.JobDagNodeInstanceVO;

/**
 * DAG 节点实例 Repository（domain 层契约）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobDagNodeInstanceRepository {

  /**
   * 根据 DAG 实例 ID 查询节点实例列表。
   *
   * @param dagInstanceId 参数说明
   * @return 返回值说明
   */
  List<JobDagNodeInstanceVO> findByDagInstanceId(String dagInstanceId);

  /**
   * 根据 DAG 实例 ID 和任务 ID 查询节点实例。
   *
   * @param dagInstanceId 参数说明
   * @param jobId 参数说明
   * @return 返回值说明
   */
  Optional<JobDagNodeInstanceVO> findByDagInstanceAndJob(String dagInstanceId, String jobId);

  /**
   * 根据 DAG 实例 ID 和任务 ID 查询所有匹配的节点实例（含 LOOP iter 实例）。
   *
   * @param dagInstanceId 参数说明
   * @param jobId 参数说明
   * @return 返回值说明
   */
  List<JobDagNodeInstanceVO> findAllByDagInstanceAndJob(String dagInstanceId, String jobId);

  /**
   * 标记节点实例为 RUNNING。
   *
   * @param nodeInstanceId 参数说明
   * @param startedAt 参数说明
   * @return 返回值说明
   */
  int markRunning(String nodeInstanceId, LocalDateTime startedAt);

  /**
   * 标记节点实例为终态。
   *
   * @param nodeInstanceId 参数说明
   * @param status 参数说明
   * @param finishedAt 参数说明
   * @param durationMs 参数说明
   * @param resultJson 参数说明
   * @param errorMessage 参数说明
   * @param logId 参数说明
   * @return 返回值说明
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
   * @param nodeInstanceId 参数说明
   * @return 返回值说明
   */
  int markSkipped(String nodeInstanceId);

  /**
   * 标记节点实例为 RETRY（递增 retryCount）。
   *
   * @param nodeInstanceId 参数说明
   * @return 返回值说明
   */
  int markRetry(String nodeInstanceId);

  /**
   * 批量插入节点实例。
   *
   * @param vos 参数说明
   */
  void insertBatch(List<JobDagNodeInstanceVO> vos);
}
