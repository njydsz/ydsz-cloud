package com.njydsz.workflow.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.workflow.domain.dto.FlowCategoryDTO;
import com.njydsz.workflow.domain.vo.FlowCategoryVO;

/**
 * 流程分类仓储接口（domain 层契约）。
 *
 * <p>定义流程分类（ydsz_flow_category）的持久化抽象，隔离领域模型与具体数据访问技术实现。
 * 应用层 Service 通过此接口操作分类聚合，不直接依赖 MyBatis Mapper。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link FlowCategoryVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段（code / parentId / tenantId 等）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FlowCategoryRepository {

  /**
   * 保存流程分类（新增）。
   *
   * <p><b>合规说明（1.0.0 DDD 分层规范）：</b>CUD 入参使用 {@link FlowCategoryDTO}（dto/ 包），
   * 符合 §34.2.1（dto/ 命令请求参数 以 DTO 结尾）。
   *
   * @param dto 流程分类命令 DTO
   * @return 保存后的流程分类 VO（含生成的 id 与审计字段）
   */
  FlowCategoryVO save(FlowCategoryDTO dto);

  /**
   * 根据 ID 查询流程分类。
   *
   * @param id 分类 ID
   * @return 流程分类 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowCategoryVO> findById(String id);

  /**
   * 根据分类编码查询流程分类。
   *
   * @param code 分类编码
   * @return 流程分类 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowCategoryVO> findByCode(String code);

  /**
   * 查询所有流程分类列表。
   *
   * @param tenantId 租户 ID（可选）
   * @return 流程分类 VO 列表
   */
  List<FlowCategoryVO> findAll(String tenantId);

  /**
   * 根据父级 ID 查询子分类列表。
   *
   * @param parentId 父级 ID
   * @return 流程分类 VO 列表
   */
  List<FlowCategoryVO> findByParentId(String parentId);

  /**
   * 根据 ID 删除流程分类。
   *
   * @param id 分类 ID
   */
  void deleteById(String id);

  /**
   * 更新流程分类。
   *
   * <p><b>合规说明（1.0.0 DDD 分层规范）：</b>CUD 入参使用 {@link FlowCategoryDTO}（dto/ 包）。
   *
   * @param dto 流程分类命令 DTO（含 id）
   * @return 更新后的流程分类 VO
   */
  FlowCategoryVO update(FlowCategoryDTO dto);

  /**
   * 统计指定租户下某编码的分类数量（用于编码唯一性校验）。
   *
   * @param code 分类编码
   * @param tenantId 租户 ID
   * @return 匹配的分类数量
   */
  long countByCodeAndTenantId(String code, String tenantId);

  /**
   * 统计指定父级 ID 下的子分类数量。
   *
   * @param parentId 父级 ID
   * @return 子分类数量
   */
  long countByParentId(String parentId);

  /**
   * 统计指定分类下已关联的流程定义数量（用于删除前引用校验）。
   *
   * @param categoryId 分类 ID
   * @return 关联的流程定义数量
   */
  long countDefinitionsByCategory(String categoryId);
}
