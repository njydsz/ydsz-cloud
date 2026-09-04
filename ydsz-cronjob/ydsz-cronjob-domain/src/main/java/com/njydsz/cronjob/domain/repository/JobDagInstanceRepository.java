package com.njydsz.cronjob.domain.repository;

import java.time.LocalDate;
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
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link JobDagInstanceVO}），非 DTO / infra 实体
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
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
   * @param instanceId DAG 实例 ID
   * @param status 终态状态（SUCCESS/FAILED/PARTIAL_SUCCESS）
   * @param finishedAt 结束时间
   * @param durationMs 执行耗时（毫秒）
   * @param errorMessage 错误信息（成功时传 null）
   * @param totalNodes 节点总数
   * @param successNodes 成功节点数
   * @param failedNodes 失败节点数
   * @param skippedNodes 跳过节点数
   * @return 受影响行数（0 表示非 RUNNING 状态）
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
   * @param instanceId DAG 实例 ID
   * @param contextJson 新的上下文 JSON 字符串
   * @return 受影响行数
   */
  int updateContext(String instanceId, String contextJson);

  /**
   * 原子合并实例上下文（PostgreSQL jsonb || 操作符）。
   *
   * @param instanceId DAG 实例 ID
   * @param mergeJson 待合并的 JSON 片段（PostgreSQL jsonb || 操作）
   * @return 受影响行数（0 表示实例不存在或已删除）
   */
  int mergeContextAtomic(String instanceId, String mergeJson);

  /**
   * 统计指定 DAG 的活跃（RUNNING/PAUSED）实例数量。
   *
   * @param dagId DAG 定义 ID
   * @return 活跃（RUNNING/PAUSED）实例数量
   */
  int countActiveInstances(String dagId);

  /**
   * 标记实例为 PAUSED。
   *
   * @param instanceId DAG 实例 ID
   * @return 受影响行数（0 表示非 RUNNING 状态）
   */
  int markPaused(String instanceId);

  /**
   * 标记实例为 RESUMED。
   *
   * @param instanceId DAG 实例 ID
   * @return 受影响行数（0 表示非 PAUSED 状态）
   */
  int markResumed(String instanceId);

  /**
   * 标记实例为 CANCELED。
   *
   * @param instanceId DAG 实例 ID
   * @param canceledAt 取消时间
   * @param durationMs 已执行耗时（毫秒）
   * @return 受影响行数（0 表示非 RUNNING/PAUSED 状态）
   */
  int markCanceled(String instanceId, LocalDateTime canceledAt, long durationMs);

  /**
   * 新增实例。
   *
   * @param vo 实例 VO
   * @return 新实例 ID
   */
  String insert(JobDagInstanceVO vo);

  /**
   * 统计指定状态的实例数量。
   *
   * @param status 实例状态（RUNNING/PAUSED/SUCCESS/FAILED/CANCELLED）
   * @return 实例数量
   */
  long countByStatus(String status);

  /**
   * 统计指定日期触发的实例数量。
   *
   * @param date 日期
   * @return 实例数量
   */
  long countByDate(LocalDate date);

  /**
   * 统计指定日期、指定状态的实例数量。
   *
   * @param status 实例状态
   * @param date 日期
   * @return 实例数量
   */
  long countByStatusAndDate(String status, LocalDate date);
}
