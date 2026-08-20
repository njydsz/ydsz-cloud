package com.njydsz.workflow.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.workflow.domain.vo.FlowAutoTriggerVO;

/**
 * 自动触发仓储接口（domain 层契约）。
 *
 * <p>定义自动触发（ydsz_flow_auto_trigger）的持久化抽象，隔离领域模型与具体数据访问技术实现。
 * 应用层 Service 通过此接口操作自动触发聚合，不直接依赖 MyBatis Mapper。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link FlowAutoTriggerVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段（flowCode / triggerType / tenantId 等）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FlowAutoTriggerRepository {

  /**
   * 保存自动触发（新增）。
   *
   * @param vo 自动触发 VO
   * @return 保存后的自动触发 VO（含生成的 id 与审计字段）
   */
  FlowAutoTriggerVO save(FlowAutoTriggerVO vo);

  /**
   * 根据 ID 查询自动触发。
   *
   * @param id 自动触发 ID
   * @return 自动触发 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowAutoTriggerVO> findById(String id);

  /**
   * 根据流程编码查询自动触发列表。
   *
   * @param flowCode 流程编码
   * @return 自动触发 VO 列表
   */
  List<FlowAutoTriggerVO> findByFlowCode(String flowCode);

  /**
   * 根据触发类型查询自动触发列表。
   *
   * @param triggerType 触发类型
   * @return 自动触发 VO 列表
   */
  List<FlowAutoTriggerVO> findByTriggerType(String triggerType);

  /**
   * 根据 ID 删除自动触发。
   *
   * @param id 自动触发 ID
   */
  void deleteById(String id);

  /**
   * 更新自动触发。
   *
   * @param vo 自动触发 VO（含 id）
   * @return 更新后的自动触发 VO
   */
  FlowAutoTriggerVO update(FlowAutoTriggerVO vo);

  /**
   * 按源流程编码查询所有启用的触发规则。
   *
   * @param sourceFlowCode 源流程编码
   * @return 启用的触发规则 VO 列表
   */
  List<FlowAutoTriggerVO> findEnabledBySourceFlowCode(String sourceFlowCode);

  /**
   * 按源流程编码删除触发规则。
   *
   * @param sourceFlowCode 源流程编码
   */
  void deleteBySourceFlowCode(String sourceFlowCode);

  /**
   * 查询所有触发规则（按 sortOrder 升序、id 升序）。
   *
   * @return 全部触发规则 VO 列表
   */
  List<FlowAutoTriggerVO> findAllOrderBySort();
}
