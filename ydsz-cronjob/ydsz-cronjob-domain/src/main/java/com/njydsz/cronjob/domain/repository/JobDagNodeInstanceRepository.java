package com.njydsz.cronjob.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.njydsz.cronjob.domain.vo.JobDagNodeInstanceVO;

/**
 * DAG 节点实例 Repository（domain 层契约）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface JobDagNodeInstanceRepository {

  /**
   * 根据 DAG 实例 ID 查询节点实例列表。
   *
   * @param dagInstanceId DAG 实例 ID
   * @return 节点实例 VO 列表，按创建时间升序
   */
  List<JobDagNodeInstanceVO> findByDagInstanceId(String dagInstanceId);

  /**
   * 根据 DAG 实例 ID 和任务 ID 查询节点实例。
   *
   * @param dagInstanceId DAG 实例 ID
   * @param jobId 任务 ID
   * @return 匹配的节点实例；不存在时返回 {@code Optional.empty()}
   */
  Optional<JobDagNodeInstanceVO> findByDagInstanceAndJob(String dagInstanceId, String jobId);

  /**
   * 根据 DAG 实例 ID 和任务 ID 查询所有匹配的节点实例（含 LOOP iter 实例）。
   *
   * @param dagInstanceId DAG 实例 ID
   * @param jobId 任务 ID
   * @return 全部匹配的节点实例列表（含 LOOP iter 实例），按创建时间升序
   */
  List<JobDagNodeInstanceVO> findAllByDagInstanceAndJob(String dagInstanceId, String jobId);

  /**
   * 标记节点实例为 RUNNING。
   *
   * @param nodeInstanceId 节点实例 ID
   * @param startedAt 节点开始执行时间
   * @return 受影响行数（0 表示非 PENDING 状态）
   */
  int markRunning(String nodeInstanceId, LocalDateTime startedAt);

  /**
   * 标记节点实例为终态。
   *
   * @param nodeInstanceId 节点实例 ID
   * @param status 终态状态（SUCCESS / FAILED / SKIPPED）
   * @param finishedAt 节点结束时间
   * @param durationMs 节点执行耗时（毫秒）
   * @param resultJson 节点执行结果 JSON
   * @param errorMessage 错误信息（失败时填写）
   * @param logId 关联的执行日志 ID
   * @return 受影响行数（0 表示非 RUNNING 状态）
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
   * @return 受影响行数（0 表示非 PENDING 状态）
   */
  int markSkipped(String nodeInstanceId);

  /**
   * 标记节点实例为 RETRY（递增 retryCount）。
   *
   * @param nodeInstanceId 节点实例 ID
   * @return 受影响行数（0 表示非 FAILED 状态或已达最大重试次数）
   */
  int markRetry(String nodeInstanceId);

  /**
   * 批量插入节点实例。
   *
   * @param vos 待批量插入的节点实例 VO 列表
   */
  void insertBatch(List<JobDagNodeInstanceVO> vos);
}
