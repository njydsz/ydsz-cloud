package com.njydsz.userinfo.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.userinfo.domain.dto.CompanyDeptDTO;
import com.njydsz.userinfo.domain.vo.CompanyDeptVO;

/**
 * 公司-部门关联 Repository 接口
 *
 * <p>封装公司-部门关联表（{@code ydsz_company_dept}）的数据访问操作。
 *
 * <p>入参为 DTO / 具体字段，返回值为 VO 类型，禁止暴露 MyBatis-Plus 类。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface CompanyDeptRepository {

  /**
   * 根据 ID 查询公司-部门关联。
   *
   * @param id 关联 ID
   * @return 公司-部门关联 VO
   */
  Optional<CompanyDeptVO> findById(String id);

  /**
   * 根据公司 ID 查询公司-部门关联列表。
   *
   * @param companyId 公司 ID
   * @return 公司-部门关联列表
   */
  List<CompanyDeptVO> findByCompanyId(String companyId);

  /**
   * 根据部门 ID 查询公司-部门关联。
   *
   * @param deptId 部门 ID
   * @return 公司-部门关联 VO
   */
  Optional<CompanyDeptVO> findByDeptId(String deptId);

  /**
   * 根据公司 ID 和部门 ID 查询关联。
   *
   * @param companyId 公司 ID
   * @param deptId 部门 ID
   * @return 公司-部门关联 VO
   */
  Optional<CompanyDeptVO> findByCompanyIdAndDeptId(String companyId, String deptId);

  /**
   * 保存公司-部门关联（插入）。
   *
   * @param dto 公司-部门关联 DTO
   * @return 保存后的关联 VO
   */
  CompanyDeptVO create(CompanyDeptDTO dto);

  /**
   * 根据公司 ID 删除关联。
   *
   * @param companyId 公司 ID
   * @return 删除影响的行数
   */
  int deleteByCompanyId(String companyId);

  /**
   * 根据部门 ID 删除关联。
   *
   * @param deptId 部门 ID
   * @return 删除影响的行数
   */
  int deleteByDeptId(String deptId);

  /**
   * 根据 ID 删除关联（逻辑删除）。
   *
   * @param id 关联 ID
   * @return 是否删除成功
   */
  boolean deleteById(String id);
}
