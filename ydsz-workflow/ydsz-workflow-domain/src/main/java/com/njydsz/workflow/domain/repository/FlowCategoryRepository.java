package com.njydsz.workflow.domain.repository;

import java.util.List;
import java.util.Optional;

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
   * @param vo 流程分类 VO
   * @return 保存后的流程分类 VO（含生成的 id 与审计字段）
   */
  FlowCategoryVO save(FlowCategoryVO vo);

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
   * @param vo 流程分类 VO（含 id）
   * @return 更新后的流程分类 VO
   */
  FlowCategoryVO update(FlowCategoryVO vo);
}
