package com.njydsz.workflow.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.workflow.domain.vo.FlowDefinitionVO;

/**
 * 流程定义仓储接口（domain 层契约）。
 *
 * <p>定义流程定义（ydsz_flow_definition）的持久化抽象，隔离领域模型与具体数据访问技术实现。
 * 应用层 Service 通过此接口操作流程定义聚合，不直接依赖 MyBatis Mapper。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link FlowDefinitionVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段（flowCode / version / tenantId 等）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FlowDefinitionRepository {

  /**
   * 保存流程定义（新增 or 更新）。
   *
   * @param vo 流程定义 VO
   * @return 保存后的流程定义 VO（含生成的 id 与审计字段）
   */
  FlowDefinitionVO save(FlowDefinitionVO vo);

  /**
   * 根据 ID 查询流程定义。
   *
   * @param id 流程定义 ID
   * @return 流程定义 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowDefinitionVO> findById(String id);

  /**
   * 根据流程编码查询最新已发布的流程定义。
   *
   * @param flowCode 流程编码
   * @param version 版本号（可为 null，表示查询最新版本）
   * @param tenantId 租户 ID
   * @return 流程定义 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowDefinitionVO> findPublished(String flowCode, String version, String tenantId);

  /**
   * 根据流程编码查询所有版本的流程定义。
   *
   * @param flowCode 流程编码
   * @return 流程定义 VO 列表
   */
  List<FlowDefinitionVO> findByFlowCode(String flowCode);

  /**
   * 根据流程编码 + 版本号查询流程定义。
   *
   * @param flowCode 流程编码
   * @param version 版本号
   * @return 流程定义 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowDefinitionVO> findByFlowCodeAndVersion(String flowCode, String version);

  /**
   * 根据 ID 删除流程定义（逻辑删除）。
   *
   * @param id 流程定义 ID
   */
  void deleteById(String id);

  /**
   * 更新流程定义。
   *
   * @param vo 流程定义 VO（含 id）
   * @return 更新后的流程定义 VO
   */
  FlowDefinitionVO update(FlowDefinitionVO vo);

  /**
   * 查询流程定义列表（分页）。
   *
   * @param flowCode 流程编码（可选）
   * @param flowName 流程名称（可选）
   * @param tenantId 租户 ID（可选）
   * @param offset 偏移量
   * @param limit 每页大小
   * @return 流程定义 VO 列表
   */
  List<FlowDefinitionVO> findPage(String flowCode, String flowName, String tenantId, int offset, int limit);

  /**
   * 统计流程定义数量。
   *
   * @param flowCode 流程编码（可选）
   * @param flowName 流程名称（可选）
   * @param tenantId 租户 ID（可选）
   * @return 流程定义数量
   */
  long countPage(String flowCode, String flowName, String tenantId);

  /**
   * 按分类查询已启用的流程定义列表。
   *
   * <p>返回 {@code category = ? AND activityStatus = 1 AND isPublish = 1 AND deleted = 0} 的定义列表，
   * 按创建时间倒序排列。用于流程发起页按分类展示可发起的流程。
   *
   * @param categoryCode 流程分类编码
   * @param tenantId 租户 ID
   * @return 流程定义 VO 列表
   */
  List<FlowDefinitionVO> findEnabledByCategory(String categoryCode, String tenantId);
}
