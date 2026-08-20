package com.njydsz.cronjob.domain.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.cronjob.domain.vo.JobDagNodeInstanceVO;

/**
 * DAG 节点实例 Repository 接口（domain 层）。
 *
 * <p>封装 JobDagNodeInstance 数据访问细节，server 层通过本接口访问，不直接依赖 Mapper 或 Entity。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface DagNodeInstanceRepository {

  /**
   * 按 ID 查询节点实例。
   *
   * @param id 节点实例 ID
   * @return 节点实例 VO
   */
  JobDagNodeInstanceVO findById(String id);

  /**
   * 按 DAG 实例 ID 查询所有节点实例。
   *
   * @param dagInstanceId DAG 实例 ID
   * @return 节点实例 VO 列表
   */
  List<JobDagNodeInstanceVO> findByDagInstanceId(String dagInstanceId);

  /**
   * 标记节点跳过。
   *
   * @param nodeId 节点实例 ID
   * @return 受影响行数
   */
  int markSkipped(String nodeId);

  /**
   * 标记节点完成。
   *
   * @param nodeId 节点实例 ID
   * @param status 终态状态（SUCCESS/FAILED/SKIPPED/APPROVAL_REJECTED）
   * @param finishedAt 完成时间
   * @param durationMs 执行耗时
   * @param resultJson 结果 JSON
   * @param errorMessage 错误信息
   * @param logId 关联日志 ID
   * @return 受影响行数
   */
  int markFinished(
      String nodeId,
      String status,
      LocalDateTime finishedAt,
      long durationMs,
      String resultJson,
      String errorMessage,
      String logId);

  /**
   * 标记节点运行中。
   *
   * @param nodeId 节点实例 ID
   * @param startedAt 开始时间
   * @return 受影响行数
   */
  int markRunning(String nodeId, LocalDateTime startedAt);

  /**
   * 插入节点实例。
   *
   * @param vo 节点实例 VO
   * @return 受影响行数
   */
  int insert(JobDagNodeInstanceVO vo);

  /**
   * 按 DAG 实例 ID 和状态查询节点实例。
   *
   * @param dagInstanceId DAG 实例 ID
   * @param status 节点状态
   * @return 节点实例 VO 列表
   */
  List<JobDagNodeInstanceVO> findByDagInstanceIdAndStatus(String dagInstanceId, String status);

  /**
   * 按 DAG 实例 ID 和 jobId 查询节点实例（用于查找当前 DAG 中的特定 Job 节点）。
   *
   * @param dagInstanceId DAG 实例 ID
   * @param jobId 任务 ID
   * @return 节点实例 VO；无匹配返回 null
   */
  JobDagNodeInstanceVO findByDagInstanceAndJob(String dagInstanceId, String jobId);

  /**
   * 按 DAG 实例 ID 和 jobKey 查询节点实例（用于按 KEY 精确查找）。
   *
   * @param dagInstanceId DAG 实例 ID
   * @param jobKey 任务 KEY
   * @return 节点实例 VO；无匹配返回 null
   */
  JobDagNodeInstanceVO findByDagInstanceAndJobKey(String dagInstanceId, String jobKey);

  /**
   * 标记节点重试（FAILED → PENDING，递增 retryCount）。
   *
   * @param nodeId 节点实例 ID
   * @return 受影响行数（0 表示重试次数用尽或状态非 FAILED）
   */
  int markRetry(String nodeId);

  /**
   * 按 jobId 查询所有 PENDING/RUNNING 状态的活跃节点实例（跨 DAG 实例）。
   *
   * @param jobId 任务 ID
   * @return 节点实例 VO 列表
   */
  List<JobDagNodeInstanceVO> selectActiveByJobId(String jobId);
}
