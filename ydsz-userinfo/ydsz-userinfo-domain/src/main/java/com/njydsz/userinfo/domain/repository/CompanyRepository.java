package com.njydsz.userinfo.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.userinfo.domain.dto.CompanyDTO;
import com.njydsz.userinfo.domain.query.CompanyPageQuery;
import com.njydsz.userinfo.domain.vo.CompanyVO;

/**
 * 公司 Repository 接口
 *
 * <p>封装公司表（{@code ydsz_org_company}）的数据访问操作，为 Service 层提供业务语义化的数据访问方法。
 *
 * <p>入参为 DTO / Query / 具体字段，返回值为 VO 类型，禁止暴露 MyBatis-Plus 类。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface CompanyRepository {

  /**
   * 根据 ID 查询公司。
   *
   * @param id 公司 ID
   * @return 公司 VO
   */
  Optional<CompanyVO> findById(String id);

  /**
   * 根据公司编码查询公司。
   *
   * @param companyCode 公司编码
   * @return 公司 VO
   */
  Optional<CompanyVO> findByCompanyCode(String companyCode);

  /**
   * 分页查询公司列表。
   *
   * @param query 分页查询参数
   * @return 分页结果
   */
  PageResponse<List<CompanyVO>> page(CompanyPageQuery query);

  /**
   * 条件查询公司列表。
   *
   * @param query 查询参数
   * @return 公司列表
   */
  List<CompanyVO> list(CompanyPageQuery query);

  /**
   * 批量根据 ID 查询公司。
   *
   * @param ids 公司 ID 集合
   * @return 公司列表
   */
  List<CompanyVO> listByIds(Collection<String> ids);

  /**
   * 保存公司（创建或更新）。
   *
   * <p>统一 DTO：创建时 {@code id} 可不传，更新时 {@code id} 必填。
   *
   * @param dto 公司 DTO
   * @return 保存后的公司 VO
   */
  CompanyVO save(CompanyDTO dto);

  /**
   * 根据 ID 删除公司（逻辑删除）。
   *
   * @param id 公司 ID
   * @return 是否删除成功
   */
  boolean deleteById(String id);

  /**
   * 统计符合条件的公司数量。
   *
   * @param query 查询参数
   * @return 公司数量
   */
  long countByQuery(CompanyPageQuery query);
}
