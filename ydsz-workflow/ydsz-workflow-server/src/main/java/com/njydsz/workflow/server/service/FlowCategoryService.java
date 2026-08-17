package com.njydsz.workflow.server.service;

import java.util.List;

import com.njydsz.workflow.domain.dto.FlowCategoryDTO;
import com.njydsz.workflow.infra.entity.FlowCategoryDO;
import com.njydsz.workflow.domain.vo.FlowCategoryTreeVO;

/**
 * 流程分类服务接口
 *
 * <p>P1-6: 对标钉钉/飞书审批的"流程分类管理"能力， 提供流程分类的 CRUD 与树形结构查询，是设计器左侧导航与发起审批时分类筛选的数据源。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>查询能力</b>：全部分类（{@link #listAll}，按 {@code sortNum} 升序）/ 树形结构（{@link #tree}，使用 {@link
 *       com.njydsz.common.domain.tree.TreeBuilder#buildSimple} 构建）
 *   <li><b>CRUD</b>：新增（{@link #create}）/ 编辑（{@link #update}）/ 删除（{@link #delete}）
 *   <li><b>引用校验</b>：删除前校验是否有子分类或关联的流程定义，有则阻断
 * </ul>
 *
 * <p><b>事务边界：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}， 分类编码唯一性校验在
 * {@code @UniqueCheck} 拦截器中完成。
 *
 * <p><b>性能优化：</b>「查询全部分类」使用 {@code ydsz_flow_category} 索引（{@code idx_parent} + {@code idx_sort}），
 * 全表一次性返回（分类数据量小，无分页必要）。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.workflow.server.service.impl.FlowCategoryServiceImpl 实现类
 * @see com.njydsz.workflow.infra.entity.FlowDefinitionDO 流程定义（{@code category} 字段引用本表分类编码）
 */
public interface FlowCategoryService {

  /**
   * 查询全部分类（扁平结构，按 sortNum 排序）
   *
   * @param tenantId 租户 ID
   * @return 分类列表（扁平结构）
   */
  List<FlowCategoryDO> listAll(String tenantId);

  /**
   * 查询全部分类（树形结构，使用 TreeBuilder 构建）
   *
   * <p>一次性查询全表后在内存中构建树，自动填充 {@code level}/{@code path} 元数据。 分类数据量小（百级别），全量加载可接受。
   *
   * @param tenantId 租户 ID
   * @return 分类树形结构根节点列表，无数据返回空列表
   * @since 1.7.0
   */
  List<FlowCategoryTreeVO> tree(String tenantId);

  /**
   * 新增分类
   *
   * @param dto 分类 DTO
   * @param tenantId 租户 ID
   * @return 分类 ID
   */
  String create(FlowCategoryDTO dto, String tenantId);

  /**
   * 编辑分类
   *
   * @param dto 分类 DTO（id 必传）
   */
  void update(FlowCategoryDTO dto);

  /**
   * 删除分类（校验是否有子分类和关联的流程定义）
   *
   * @param id 分类 ID
   */
  void delete(String id);
}
