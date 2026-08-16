package com.njydsz.userinfo.server.service;

import com.njydsz.userinfo.domain.dto.post.CompanyPostDTO;
import com.njydsz.userinfo.domain.dto.put.CompanyPutDTO;
import com.njydsz.userinfo.domain.entity.Company;
import com.njydsz.userinfo.domain.vo.CompanyVO;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 公司 Service 接口
 *
 * <p>封装公司的完整业务逻辑：CRUD、跨服务名称富化。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li>公司全量列表查询（{@code list}，按创建时间降序）
 *   <li>公司 CRUD（含 {@code companyCode} 唯一性校验）
 *   <li>跨服务名称富化（{@code batchNamesByIds}，供 NameAssembler 调用）
 * </ul>
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see Company 公司实体
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
   * 创建公司。
   *
   * <p>校验：{@code companyCode} 唯一性。
   *
   * @param dto 公司创建 DTO
   * @return 新公司 ID
   */
  String create(CompanyPostDTO dto);

  /**
   * 更新公司。
   *
   * @param dto 公司更新 DTO（含 ID）
   * @return true=成功
   */
  boolean update(CompanyPutDTO dto);

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
