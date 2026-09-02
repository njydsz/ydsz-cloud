package com.njydsz.workflow.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.workflow.domain.vo.FlowUserVO;

/**
 * 流程用户仓储接口（domain 层契约）。
 *
 * <p>定义流程用户（ydsz_flow_user）的持久化抽象，隔离领域模型与具体数据访问技术实现。
 * 应用层 Service 通过此接口操作用户聚合，不直接依赖 MyBatis Mapper。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link FlowUserVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段（ instanceId / userId / userType 等）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface FlowUserRepository {

  /**
   * 保存流程用户（新增）。
   *
   * @param vo 流程用户 VO
   * @return 保存后的流程用户 VO（含生成的 id 与审计字段）
   */
  FlowUserVO save(FlowUserVO vo);

  /**
   * 批量保存流程用户。
   *
   * @param users 流程用户 VO 列表
   * @return 保存后的流程用户 VO 列表
   */
  List<FlowUserVO> saveBatch(List<FlowUserVO> users);

  /**
   * 根据 ID 查询流程用户。
   *
   * @param id 用户 ID
   * @return 流程用户 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowUserVO> findById(String id);

  /**
   * 根据实例 ID 查询流程用户列表。
   *
   * @param instanceId 实例 ID
   * @return 流程用户 VO 列表
   */
  List<FlowUserVO> findByInstanceId(String instanceId);

  /**
   * 根据实例 ID + 用户类型查询流程用户列表。
   *
   * @param instanceId 实例 ID
   * @param userType 用户类型
   * @return 流程用户 VO 列表
   */
  List<FlowUserVO> findByInstanceAndType(String instanceId, String userType);

  /**
   * 根据 ID 删除流程用户。
   *
   * @param id 用户 ID
   */
  void deleteById(String id);

  /**
   * 更新流程用户。
   *
   * @param vo 流程用户 VO（含 id）
   * @return 更新后的流程用户 VO
   */
  FlowUserVO update(FlowUserVO vo);

  /**
   * 根据用户 ID 查询关联的任务 ID 列表（通过 ydsz_flow_user 关联表）。
   *
   * <p>查询条件：user_id = ? AND tenant_id = ? AND processed = 0。
   * 用于 listTodoByUser 场景：获取通过 ydsz_flow_user 关联到该用户的任务。
   *
   * @param userId 用户 ID
   * @param tenantId 租户 ID
   * @return 任务 ID 列表
   */
  List<String> selectTaskIdsByUser(String userId, String tenantId);

  /**
   * 根据 (instanceId, nodeCode, userId) 复合键删除审批人。
   *
   * <p>用于减签场景：从 ydsz_flow_user 中按复合键删除。
   *
   * @param instanceId 实例 ID
   * @param nodeCode 节点编码
   * @param userId 用户 ID
   * @return 删除行数
   */
  int deleteByInstanceAndNodeAndUser(String instanceId, String nodeCode, String userId);
}
