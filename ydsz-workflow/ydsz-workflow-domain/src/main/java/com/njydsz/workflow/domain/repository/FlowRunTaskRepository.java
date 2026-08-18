package com.njydsz.workflow.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.workflow.domain.dto.FlowTaskQueryDTO;
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
   * @param vo 运行时任务 VO
   * @return 保存后的运行时任务 VO（含生成的 id 与审计字段）
   */
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
   * @param condition 查询条件 DTO
   * @return 运行时任务 VO 列表
   */
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
}
