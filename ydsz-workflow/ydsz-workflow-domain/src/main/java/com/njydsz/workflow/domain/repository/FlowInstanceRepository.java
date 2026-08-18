package com.njydsz.workflow.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.workflow.domain.dto.FlowInstanceDTO;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;

/**
 * 流程实例仓储接口（domain 层契约）。
 *
 * <p>定义流程实例的持久化抽象，隔离领域模型与具体数据访问技术实现。应用层 Service 通过此接口操作聚合根，不直接依赖 MyBatis Mapper。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link FlowInstanceVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段（id / businessType / businessId / initiatorId 等）
 *   <li>CUD 入参使用领域 DTO（{@link FlowInstanceDTO}），禁止接受 infra 实体
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FlowInstanceRepository {

  /**
   * 保存流程实例（新增 or 更新）。
   *
   * <p>新增：dto.id 由 Snowflake 生成后传入，内部通过 {@code dtoToEntity} 转换为实体执行 insert。<br>
   * 更新：根据 dto.id 定位并更新，内部通过 {@code dtoToEntityWithId} 转换为实体执行 update。
   *
   * @param dto 流程实例 DTO
   * @return 保存后的流程实例 VO（含生成的 id 与审计字段）
   */
  FlowInstanceVO save(FlowInstanceDTO dto);

  /**
   * 根据 ID 查询流程实例。
   *
   * @param id 实例 ID
   * @return 流程实例 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowInstanceVO> findById(String id);

  /**
   * 根据业务类型 + 业务单据 ID 查询流程实例。
   *
   * @param businessType 业务类型
   * @param businessId 业务单据 ID
   * @return 流程实例 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowInstanceVO> findByBusiness(String businessType, String businessId);

  /**
   * 查询发起人的流程实例列表。
   *
   * @param initiatorId 发起人 ID
   * @return 流程实例 VO 列表
   */
  List<FlowInstanceVO> findByInitiatorId(String initiatorId);

  /**
   * 查询父流程下的子流程实例。
   *
   * @param parentInstanceId 父流程实例 ID
   * @return 子流程实例 VO 列表
   */
  List<FlowInstanceVO> findChildren(String parentInstanceId);

  /**
   * 统计某状态下的实例数量。
   *
   * @param flowStatus 流程状态（{@link com.njydsz.workflow.domain.enums.FlowInstanceStatus#name()}）
   * @return 实例数量
   */
  long countByStatus(String flowStatus);

  /**
   * 查询挂起于指定时间之前的实例（用于管理员清理）。
   *
   * @param before 时间阈值
   * @param limit 最大返回数量
   * @return 流程实例 VO 列表
   */
  List<FlowInstanceVO> findSuspendedBefore(java.time.LocalDateTime before, int limit);

  /**
   * 根据 ID 删除流程实例（逻辑删除）。
   *
   * @param id 实例 ID
   */
  void deleteById(String id);
}
