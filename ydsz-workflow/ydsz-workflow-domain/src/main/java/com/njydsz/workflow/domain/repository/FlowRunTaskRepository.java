package com.njydsz.workflow.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.njydsz.workflow.domain.query.FlowTaskQuery;
import com.njydsz.workflow.domain.dto.FlowTaskQueryDTO;
import com.njydsz.workflow.domain.dto.FlowRunTaskDTO;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;

/**
 * 运行时任务仓储接口（domain 层契约）。
 *
 * <p>定义运行时任务（ydsz_flow_run_task）的持久化抽象，隔离领域模型与具体数据访问技术实现。
 * 应用层 Service 通过此接口操作运行时任务聚合，不直接依赖 MyBatis Mapper。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link FlowRunTaskVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段（id / instanceId / nodeCode / assigneeId 等）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FlowRunTaskRepository {

  /**
   * 保存运行时任务（新增）。
   *
   * <p><b>合规说明（v2.23 DDD 分层规范）：</b>CUD 入参使用 {@link FlowRunTaskDTO}（dto/ 包），
   * 符合 §34.2.1（dto/ 命令请求参数 以 DTO 结尾）。
   *
   * @param dto 运行时任务命令 DTO
   * @return 保存后的运行时任务 VO（含生成的 id 与审计字段）
   */
  FlowRunTaskVO save(FlowRunTaskDTO dto);

  /**
   * 保存运行时任务（新增，已废弃）。
   *
   * @deprecated 使用 {@link #save(FlowRunTaskDTO)} 替代，CUD 入参应使用 DTO
   */
  @Deprecated
  FlowRunTaskVO save(FlowRunTaskVO vo);

  /**
   * 根据 ID 查询运行时任务。
   *
   * @param id 任务 ID
   * @return 运行时任务 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowRunTaskVO> findById(String id);

  /**
   * 根据 ID 删除运行时任务。
   *
   * @param id 任务 ID
   */
  void deleteById(String id);

  /**
   * 根据实例 ID 查询待办任务列表。
   *
   * @param instanceId 实例 ID
   * @return 运行时任务 VO 列表
   */
  List<FlowRunTaskVO> findPendingByInstance(String instanceId);

  /**
   * 根据实例 ID + 节点编码查询待办任务列表。
   *
   * @param instanceId 实例 ID
   * @param nodeCode 节点编码
   * @return 运行时任务 VO 列表
   */
  List<FlowRunTaskVO> findPendingByNode(String instanceId, String nodeCode);

  /**
   * 根据办理人 ID 查询待办任务列表。
   *
   * @param assigneeId 办理人 ID
   * @param offset 偏移量
   * @param limit 每页大小
   * @return 运行时任务 VO 列表
   */
  List<FlowRunTaskVO> findPendingByAssignee(String assigneeId, int offset, int limit);

  /**
   * 统计办理人的待办任务数量。
   *
   * @param assigneeId 办理人 ID
   * @return 待办任务数量
   */
  long countPendingByAssignee(String assigneeId);

  /**
   * 根据实例 ID 冻结任务（挂起实例时调用）。
   *
   * @param instanceId 实例 ID
   * @return 更新行数
   */
  int freezeByInstance(String instanceId);

  /**
   * 根据实例 ID 解冻任务（激活实例时调用）。
   *
   * @param instanceId 实例 ID
   * @return 更新行数
   */
  int unfreezeByInstance(String instanceId);

  /**
   * 根据实例 ID 更新任务状态。
   *
   * @param instanceId 实例 ID
   * @param taskStatus 任务状态
   * @return 更新行数
   */
  int updateStatusByInstance(String instanceId, String taskStatus);

  /**
   * 更新运行时任务。
   *
   * @param vo 运行时任务 VO（含 id）
   * @return 更新后的运行时任务 VO
   */
  FlowRunTaskVO update(FlowRunTaskVO vo);

  /**
   * 根据实例 ID 查询所有任务列表。
   *
   * @param instanceId 实例 ID
   * @return 运行时任务 VO 列表
   */
  List<FlowRunTaskVO> findByInstanceId(String instanceId);

  /**
   * 查询办理人的待办任务列表（带租户隔离）。
   *
   * <p>与 {@link #findPendingByAssignee(String, int, int)} 类似，但额外增加租户隔离条件，
   * 用于多租户场景下「我的待办」查询。按创建时间倒序排列。
   *
   * @param userId 办理人 ID
   * @param tenantId 租户 ID
   * @param limit 返回数量上限
   * @return 运行时任务 VO 列表
   */
  List<FlowRunTaskVO> findTodoByAssignee(String userId, String tenantId, int limit);

  /**
   * 根据复杂条件查询运行时任务列表。
   *
   * <p>支持多条件组合过滤：流程编码、实例 ID、节点编码、办理人、任务状态、业务类型、
   * 优先级、创建时间范围、截止时间范围等。所有条件均为可选，为空时忽略。
   *
   * <p><b>命名合规说明（v2.23 DDD 分层规范）：</b>查询参数使用 {@link FlowTaskQuery}（query/ 包），
   * 符合 §34.2.1 表格规定（query/ 查询请求参数 以 Query 结尾）。
   *
   * @param query 查询条件
   * @return 运行时任务 VO 列表
   */
  List<FlowRunTaskVO> findByCondition(FlowTaskQuery query);

  /**
   * 根据复杂条件查询运行时任务列表（已废弃）。
   *
   * @deprecated 使用 {@link #findByCondition(FlowTaskQuery)} 替代，查询参数应置于 query/ 包并以 Query 结尾
   */
  @Deprecated
  List<FlowRunTaskVO> findByCondition(FlowTaskQueryDTO condition);

  /**
   * 根据条件批量更新任务状态。
   *
   * <p>满足 instanceId + nodeCode + fromStatus 条件的所有任务，统一更新为 toStatus。
   * 用于流程推进时批量刷新同实例同节点的任务状态。
   *
   * @param instanceId 实例 ID
   * @param nodeCode 节点编码（可为 null，表示不限制节点）
   * @param fromStatus 原始任务状态
   * @param toStatus 目标任务状态
   * @return 更新行数
   */
  int updateStatusByCondition(String instanceId, String nodeCode, String fromStatus, String toStatus);

  /**
   * 统计指定状态列表下的任务数量。
   *
   * @param statuses 任务状态列表
   * @return 任务数量
   */
  long countByStatusIn(List<String> statuses);

  /**
   * 统计超期任务数量（dueAt < now 且状态为 PENDING/CLAIMED）。
   *
   * @return 超期任务数量
   */
  long countOverdue();

  /**
   * 统计所有待办任务数量（状态为 PENDING 且未删除）。
   *
   * <p>用于健康检查探针。
   *
   * @return 待办任务数量
   */
  long countPending();

  /**
   * 查询超时未处理的待办任务列表（用于自动催办）。
   *
   * <p>返回创建于指定时间之前、状态为 PENDING 或 CLAIMED 的任务，按实例去重后可用于催办通知。
   *
   * @param thresholdTime 时间阈值（查询创建时间早于此值的任务）
   * @param limit 返回数量上限
   * @return 超时任务 VO 列表
   */
  List<FlowRunTaskVO> findOverdueTasks(LocalDateTime thresholdTime, int limit);

  /**
   * 查询 SLA 候选任务（超期待办扫描专用）。
   *
   * <p>返回满足 SLA 扫描条件的待办任务：状态为 PENDING/CLAIMED、未删除、已超期。
   *
   * @param limit 返回数量上限
   * @return SLA 候选任务 VO 列表
   */
  List<FlowRunTaskVO> selectSlaCandidates(int limit);

  /**
   * 递增任务的催办计数。
   *
   * @param taskId 任务 ID
   * @param newUrgeCount 新的催办次数
   * @param urgeAt 催办时间
   */
  void incrementUrgeCount(String taskId, int newUrgeCount, LocalDateTime urgeAt);

  /**
   * 标记任务的 SLA 动作。
   *
   * @param taskId 任务 ID
   * @param slaAction SLA 动作（AUTO_PASS/AUTO_REJECT/ESCALATE/NOTIFY）
   * @param slaEscalated 是否已升级（0/1）
   */
  void markSlaAction(String taskId, String slaAction, int slaEscalated);

  /**
   * 完成任务（更新状态为 COMPLETED / TIMEOUT 等终态）。
   *
   * <p>更新 {@code taskStatus, finishAt, durationMs}。
   *
   * @param taskId 任务 ID
   * @param taskStatus 目标状态
   * @param finishAt 完成时间
   * @param durationMs 耗时（毫秒）
   */
  void completeTask(String taskId, String taskStatus, LocalDateTime finishAt, Long durationMs);

  /**
   * 取消任务（更新状态为 CANCELLED）。
   *
   * <p>更新 {@code taskStatus = 'CANCELLED', comment, finishAt = now()}。
   *
   * @param taskId 任务 ID
   * @param taskStatus 目标状态（通常为 CANCELLED）
   * @param comment 取消原因
   */
  void cancelTask(String taskId, String taskStatus, String comment);

  /**
   * 查询 WAITING 事件订阅的任务列表（按实例ID + 节点编码）。
   *
   * @param instanceId 实例 ID
   * @param nodeCode 节点编码
   * @return 运行时任务 VO 列表
   */
  List<FlowRunTaskVO> findByInstanceAndNode(String instanceId, String nodeCode);
}
