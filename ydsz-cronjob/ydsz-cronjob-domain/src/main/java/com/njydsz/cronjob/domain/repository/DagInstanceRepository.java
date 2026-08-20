package com.njydsz.cronjob.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.njydsz.cronjob.domain.vo.JobDagInstanceVO;

/**
 * DAG 实例 Repository 接口（domain 层）。
 *
 * <p>封装 JobDagInstance 数据访问细节，server 层通过本接口访问，不直接依赖 Mapper 或 Entity。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface DagInstanceRepository {

  /**
   * 按 ID 查询 DAG 实例。
   *
   * @param id 实例 ID
   * @return DAG 实例 VO
   */
  Optional<JobDagInstanceVO> findById(String id);

  /**
   * 暂停 DAG 实例（RUNNING → PAUSED）。
   *
   * @param instanceId 实例 ID
   * @return 受影响行数
   */
  int markPaused(String instanceId);

  /**
   * 恢复暂停的 DAG 实例（PAUSED → RUNNING）。
   *
   * @param instanceId 实例 ID
 * @return 受影响行数
   */
  int markResumed(String instanceId);

  /**
   * 取消 DAG 实例。
   *
   * @param instanceId 实例 ID
   * @param finishedAt 结束时间
   * @param durationMs 执行耗时
   * @return 受影响行数
   */
  int markCanceled(String instanceId, LocalDateTime finishedAt, long durationMs);

  /**
   * 更新 DAG 实例（供执行器等内部场景使用）。
   *
   * @param vo DAG 实例 VO（必须含 id）
   * @return 受影响行数
   */
  int update(JobDagInstanceVO vo);

  /**
   * 插入 DAG 实例。
   *
   * @param vo DAG 实例 VO
   * @return 插入后的 ID
   */
  String insert(JobDagInstanceVO vo);

  /**
   * 查询待触发的 DAG 实例列表。
   *
   * @param now 当前时间
   * @param limit 最大条数
   * @return DAG 实例 VO 列表
   */
  List<JobDagInstanceVO> findPendingInstances(LocalDateTime now, int limit);

  /**
   * 标记 DAG 实例运行中（PENDING → RUNNING）。
   *
   * @param instanceId 实例 ID
   * @param startedAt 开始时间
   * @return 受影响行数
   */
  int markRunning(String instanceId, LocalDateTime startedAt);

  /**
   * 标记 DAG 实例完成。
   *
   * @param instanceId 实例 ID
   * @param status 终态状态
   * @param finishedAt 结束时间
   * @param durationMs 执行耗时
   * @param errorMessage 错误信息
   * @param totalNodes 总节点数
   * @param successNodes 成功节点数
   * @param failedNodes 失败节点数
   * @param skippedNodes 跳过节点数
   * @return 受影响行数
   */
  int markFinished(
      String instanceId,
      String status,
      LocalDateTime finishedAt,
      long durationMs,
      String errorMessage,
      int totalNodes,
      int successNodes,
      int failedNodes,
      int skippedNodes);

  /**
   * 原子合并 DAG 实例上下文 JSON（PostgreSQL jsonb ||）。
   *
   * @param instanceId 实例 ID
   * @param mergeJson 待合并 JSON
   * @return 受影响行数
   */
  int mergeContextAtomic(String instanceId, String mergeJson);

  /**
   * 更新 DAG 定义的统计计数。
   *
   * @param dagId DAG 定义 ID
   * @param success 是否成功
   * @return 受影响行数
   */
  int updateResultStats(String dagId, boolean success);
}
