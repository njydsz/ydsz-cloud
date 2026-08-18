package com.njydsz.workflow.domain.repository;

import java.util.List;
import java.util.Optional;

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
}
