package com.njydsz.workflow.domain.repository;

import java.util.List;
import java.util.Optional;


/**
 * 流程节点仓储接口（domain 层契约）。
 *
 * <p>定义流程节点（ydsz_flow_node）的持久化抽象，隔离领域模型与具体数据访问技术实现。
 * 应用层 Service 通过此接口操作节点聚合，不直接依赖 MyBatis Mapper。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link FlowNodeVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段（definitionId / nodeCode 等）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface FlowNodeRepository {

  /**
   * 保存流程节点（新增）。
   *
   * @param vo 流程节点 VO
   * @return 保存后的流程节点 VO（含生成的 id 与审计字段）
   */
  FlowNodeVO save(FlowNodeVO vo);

  /**
   * 批量保存流程节点。
   *
   * @param nodes 流程节点 VO 列表
   * @return 保存后的流程节点 VO 列表
   */
  List<FlowNodeVO> saveBatch(List<FlowNodeVO> nodes);

  /**
   * 根据 ID 查询流程节点。
   *
   * @param id 节点 ID
   * @return 流程节点 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowNodeVO> findById(String id);

  /**
   * 根据流程定义 ID + 节点编码查询节点。
   *
   * @param definitionId 流程定义 ID
   * @param nodeCode 节点编码
   * @return 流程节点 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowNodeVO> findByCode(String definitionId, String nodeCode);

  /**
   * 根据流程定义 ID 查询所有节点。
   *
   * @param definitionId 流程定义 ID
   * @return 流程节点 VO 列表
   */
  List<FlowNodeVO> findByDefinitionId(String definitionId);

  /**
   * 根据流程定义 ID 删除所有节点（逻辑删除）。
   *
   * @param definitionId 流程定义 ID
   */
  void deleteByDefinitionId(String definitionId);

  /**
   * 根据 ID 删除流程节点。
   *
   * @param id 节点 ID
   */
  void deleteById(String id);
}
