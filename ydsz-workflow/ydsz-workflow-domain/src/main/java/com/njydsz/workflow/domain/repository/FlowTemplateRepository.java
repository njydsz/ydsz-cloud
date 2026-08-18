package com.njydsz.workflow.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.workflow.domain.vo.FlowTemplateVO;

/**
 * 流程模板仓储接口（domain 层契约）。
 *
 * <p>定义流程模板（ydsz_flow_template）的持久化抽象，隔离领域模型与具体数据访问技术实现。
 * 应用层 Service 通过此接口操作模板聚合，不直接依赖 MyBatis Mapper。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link FlowTemplateVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段（code / categoryId / tenantId 等）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FlowTemplateRepository {

  /**
   * 保存流程模板（新增）。
   *
   * @param vo 流程模板 VO
   * @return 保存后的流程模板 VO（含生成的 id 与审计字段）
   */
  FlowTemplateVO save(FlowTemplateVO vo);

  /**
   * 根据 ID 查询流程模板。
   *
   * @param id 模板 ID
   * @return 流程模板 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowTemplateVO> findById(String id);

  /**
   * 根据模板编码查询流程模板。
   *
   * @param code 模板编码
   * @return 流程模板 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowTemplateVO> findByCode(String code);

  /**
   * 查询所有流程模板列表。
   *
   * @param tenantId 租户 ID（可选）
   * @return 流程模板 VO 列表
   */
  List<FlowTemplateVO> findAll(String tenantId);

  /**
   * 根据分类 ID 查询模板列表。
   *
   * @param categoryId 分类 ID
   * @return 流程模板 VO 列表
   */
  List<FlowTemplateVO> findByCategoryId(String categoryId);

  /**
   * 根据 ID 删除流程模板。
   *
   * @param id 模板 ID
   */
  void deleteById(String id);

  /**
   * 更新流程模板。
   *
   * @param vo 流程模板 VO（含 id）
   * @return 更新后的流程模板 VO
   */
  FlowTemplateVO update(FlowTemplateVO vo);
}
