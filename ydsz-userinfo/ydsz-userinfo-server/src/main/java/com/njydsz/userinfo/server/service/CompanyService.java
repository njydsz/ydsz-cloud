package com.njydsz.userinfo.server.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.njydsz.userinfo.domain.dto.CompanyDTO;
import com.njydsz.userinfo.infra.entity.CompanyDO;
import com.njydsz.userinfo.domain.vo.CompanyTreeVO;
import com.njydsz.userinfo.domain.vo.CompanyVO;

/**
 * 公司 Service 接口
 *
 * <p>封装公司的完整业务逻辑：CRUD、跨服务名称富化。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li>公司全量列表查询（{@code list}，按创建时间降序）/ 树形结构查询（{@code tree}，使用 {@link
 *       com.njydsz.common.domain.tree.TreeBuilder#buildSimple} 构建）
 *   <li>公司 CRUD（含 {@code companyCode} 唯一性校验）
 *   <li>跨服务名称富化（{@code batchNamesByIds}，供 NameAssembler 调用）
 * </ul>
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see CompanyDO 公司实体
 */
public interface CompanyService {

  /**
   * 根据 ID 查询公司详情。
   *
   * @param id 公司 ID
   * @return 公司 VO；不存在或已删除时抛出异常
   */
  CompanyVO getById(String id);

  /**
   * 查询全部未删除公司列表（按创建时间降序）。
   *
   * @return 公司 VO 列表
   */
  List<CompanyVO> list();

  /**
   * 查询全部未删除公司树形结构（使用 TreeBuilder 构建，自动填充 level/path 元数据）。
   *
   * <p>一次性查询全表后在内存中构建树，集团-子公司层级关系清晰。 公司数据量小（百级别），全量加载可接受。
   *
   * @return 公司树形结构根节点列表，无数据返回空列表
   * @since 1.7.0
   */
  List<CompanyTreeVO> tree();

  /**
   * 创建公司。
   *
   * <p>校验：{@code companyCode} 唯一性。
   *
   * @param dto 公司 DTO
   * @return 新公司 ID
   */
  String create(CompanyDTO dto);

  /**
   * 更新公司。
   *
   * @param dto 公司 DTO（含 ID）
   * @return true=成功
   */
  boolean update(CompanyDTO dto);

  /**
   * 删除公司（逻辑删除）。
   *
   * @param id 公司 ID
   * @return true=成功
   */
  boolean removeById(String id);

  /**
   * 批量查询公司 ID → 公司名映射（供 NameAssembler 跨服务富化 companyName 字段）。
   *
   * @param companyIds 公司 ID 集合（允许 null / 空，返回空 Map）
   * @return companyId → companyName 映射
   */
  Map<String, String> batchNamesByIds(Collection<String> companyIds);
}
