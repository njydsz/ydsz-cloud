package com.njydsz.workflow.domain.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.njydsz.workflow.domain.vo.FlowHisTaskVO;

/**
 * 历史任务仓储接口（domain 层契约）。
 *
 * <p>定义历史任务（ydsz_flow_his_task）的持久化抽象，隔离领域模型与具体数据访问技术实现。
 * 应用层 Service 通过此接口操作历史任务聚合，不直接依赖 MyBatis Mapper。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link FlowHisTaskVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段（instanceId / nodeCode / assigneeId 等）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FlowHisTaskRepository {

  /**
   * 保存历史任务（新增）。
   *
   * @param vo 历史任务 VO
   * @return 保存后的历史任务 VO（含生成的 id 与审计字段）
   */
  FlowHisTaskVO save(FlowHisTaskVO vo);

  /**
   * 根据 ID 查询历史任务。
   *
   * @param id 任务 ID
   * @return 历史任务 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowHisTaskVO> findById(String id);

  /**
   * 根据实例 ID 查询历史任务列表。
   *
   * @param instanceId 实例 ID
   * @return 历史任务 VO 列表
   */
  List<FlowHisTaskVO> findByInstanceId(String instanceId);

  /**
   * 根据实例 ID 查询已通过的节点列表（用于撤回场景）。
   *
   * @param instanceId 实例 ID
   * @return 节点列表，每个 Map 包含 nodeCode / nodeName / firstFinishAt / visitCount
   */
  List<Map<String, Object>> listPassedNodes(String instanceId);

  /**
   * 根据实例 ID + 节点编码查询历史任务。
   *
   * @param instanceId 实例 ID
   * @param nodeCode 节点编码
   * @return 历史任务 VO 列表
   */
  List<FlowHisTaskVO> findByInstanceAndNode(String instanceId, String nodeCode);

  /**
   * 根据 ID 删除历史任务。
   *
   * @param id 任务 ID
   */
  void deleteById(String id);

  /**
   * 根据办理人查询历史任务列表。
   *
   * <p>查询 {@code assigneeId = ?} 的历史任务，
   * 按完成时间倒序排列，限制返回数量。用于「我审批过的」查询。
   *
   * @param userId 办理人 ID
   * @param limit 返回数量上限
   * @return 历史任务 VO 列表
   */
  List<FlowHisTaskVO> findByAssignee(String userId, int limit);
}
