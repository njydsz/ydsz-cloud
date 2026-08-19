package com.njydsz.cronjob.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.njydsz.cronjob.domain.entity.dag.JobDagNodeInstance;
import com.njydsz.cronjob.domain.vo.JobDagNodeInstanceVO;

/**
 * DAG 节点实例 Repository（domain 层契约）。
 *
 * <p>定义 DAG 节点实例的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link JobDagNodeInstanceVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobDagNodeInstanceRepository {

  /**
   * 根据 DAG 实例 ID 查询节点实例列表。
   *
   * @param dagInstanceId DAG 实例 ID
   * @return 节点实例 VO 列表
   */
  List<JobDagNodeInstanceVO> findByDagInstanceId(String dagInstanceId);

  /**
   * 根据 DAG 实例 ID 和任务 ID 查询节点实例。
   *
   * @param dagInstanceId DAG 实例 ID
   * @param jobId 任务 ID
   * @return 节点实例 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<JobDagNodeInstanceVO> findByDagInstanceAndJob(String dagInstanceId, String jobId);

  /**
   * 根据 DAG 实例 ID 和任务 ID 查询所有匹配的节点实例（含 LOOP iter 实例）。
   *
   * @param dagInstanceId DAG 实例 ID
   * @param jobId 任务 ID
   * @return 节点实例 VO 列表
   */
  List<JobDagNodeInstanceVO> findAllByDagInstanceAndJob(String dagInstanceId, String jobId);

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
   * @param vos 节点实例 VO 列表
   */
  void insertBatch(List<JobDagNodeInstanceVO> vos);

  /** 按 DAG 实例 ID 查询节点实例实体列表（Service 层可视化/状态查询使用）。 */
  List<JobDagNodeInstance> selectByDagInstanceId(String dagInstanceId);
}
