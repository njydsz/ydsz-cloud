package com.njydsz.cronjob.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.njydsz.cronjob.domain.vo.JobDagInstanceVO;

/**
 * DAG 实例 Repository（domain 层契约）。
 *
 * <p>定义 DAG 工作流实例的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link JobDagInstanceVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobDagInstanceRepository {

  /**
   * 根据 DAG ID 查询实例列表。
   *
   * @param dagId DAG ID
   * @param limit 最多返回条数
   * @return 实例 VO 列表
   */
  List<JobDagInstanceVO> findByDagId(String dagId, int limit);

  /**
   * 根据状态查询实例列表。
   *
   * @param status 实例状态
   * @return 实例 VO 列表
   */
  List<JobDagInstanceVO> findByStatus(String status);

  /**
   * 根据 ID 查询实例。
   *
   * @param instanceId 实例 ID
   * @return 实例 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<JobDagInstanceVO> findById(String instanceId);

  /**
   * CAS 更新实例状态。
   *
   * @param instanceId 实例 ID
   * @param fromStatus 原状态
   * @param toStatus 目标状态
   * @return 受影响行数
   */
  int casUpdateStatus(String instanceId, String fromStatus, String toStatus);

  /**
   * 标记实例为 RUNNING。
   *
   * @param instanceId 实例 ID
   * @param startedAt 开始时间
   * @return 受影响行数
   */
  int markRunning(String instanceId, LocalDateTime startedAt);

  /**
   * 标记实例为终态。
   *
   * @param instanceId 实例 ID
   * @param status 终态状态
   * @param finishedAt 完成时间
   * @param durationMs 耗时
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
   * 更新实例上下文（contextJson）。
   *
   * @param instanceId 实例 ID
   * @param contextJson 上下文 JSON
   * @return 受影响行数
   */
  int updateContext(String instanceId, String contextJson);

  /**
   * 原子合并实例上下文（PostgreSQL jsonb || 操作符）。
   *
   * @param instanceId 实例 ID
   * @param mergeJson 待合并的 JSON 片段
   * @return 受影响行数
   */
  int mergeContextAtomic(String instanceId, String mergeJson);

  /**
   * 统计指定 DAG 的活跃（RUNNING/PAUSED）实例数量。
   *
   * @param dagId DAG ID
   * @return 实例数量
   */
  int countActiveInstances(String dagId);

  /**
   * 标记实例为 PAUSED。
   *
   * @param instanceId 实例 ID
   * @return 受影响行数
   */
  int markPaused(String instanceId);

  /**
   * 标记实例为 RESUMED。
   *
   * @param instanceId 实例 ID
   * @return 受影响行数
   */
  int markResumed(String instanceId);

  /**
   * 标记实例为 CANCELED。
   *
   * @param instanceId 实例 ID
   * @param canceledAt 取消时间
   * @param durationMs 耗时
   * @return 受影响行数
   */
  int markCanceled(String instanceId, LocalDateTime canceledAt, long durationMs);
}
