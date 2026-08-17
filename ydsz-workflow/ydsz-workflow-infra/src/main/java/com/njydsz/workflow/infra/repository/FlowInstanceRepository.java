package com.njydsz.workflow.infra.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.workflow.domain.entity.FlowInstance;

/**
 * 流程实例仓储接口（Repository Port）
 *
 * <p>领域层定义的持久化抽象，隔离领域模型与具体数据访问技术实现。 应用层 Service 通过此接口操作聚合根，不直接依赖 MyBatis Mapper。
 *
 * <p><b>设计原则：</b>
 *
 * <ul>
 *   <li>以聚合根（{@link FlowInstance}）为操作单位
 *   <li>接口签名使用领域语言（如 {@link #save(FlowInstance)} 而非 insert/update）
 *   <li>实现类位于 {@code ydsz-workflow-infra} 模块
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FlowInstanceRepository {

  /**
   * 保存流程实例（新增 or 更新） <br>
   * 新增：entity.id 由 Snowflake 生成后传入 <br>
   * 更新：根据 entity.id 定位并更新
   *
   * @param instance 流程实例聚合根
   * @return 保存后的实例（含生成的 id）
   */
  FlowInstance save(FlowInstance instance);

  /**
   * 根据 ID 查询流程实例
   *
   * @param id 实例 ID
   * @return Optional 包装的流程实例
   */
  Optional<FlowInstance> findById(String id);

  /**
   * 根据业务类型 + 业务单据 ID 查询流程实例
   *
   * @param businessType 业务类型
   * @param businessId 业务单据 ID
   * @return Optional 包装的流程实例
   */
  Optional<FlowInstance> findByBusiness(String businessType, String businessId);

  /**
   * 查询发起人的流程实例列表
   *
   * @param initiatorId 发起人 ID
   * @return 流程实例列表
   */
  List<FlowInstance> findByInitiatorId(String initiatorId);

  /**
   * 查询父流程下的子流程实例
   *
   * @param parentInstanceId 父流程实例 ID
   * @return 子流程实例列表
   */
  List<FlowInstance> findChildren(String parentInstanceId);

  /**
   * 统计某状态下的实例数量
   *
   * @param flowStatus 流程状态（{@link com.njydsz.workflow.domain.enums.FlowInstanceStatus#name()}）
   * @return 实例数量
   */
  long countByStatus(String flowStatus);

  /**
   * 查询挂起于指定时间之前的实例（用于管理员清理）
   *
   * @param before 时间阈值
   * @param limit 最大返回数量
   * @return 实例列表
   */
  List<FlowInstance> findSuspendedBefore(java.time.LocalDateTime before, int limit);

  /**
   * 根据 ID 删除流程实例（逻辑删除）
   *
   * @param id 实例 ID
   */
  void deleteById(String id);
}
