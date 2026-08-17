package com.njydsz.userinfo.infra.repository;

import java.util.List;

import com.njydsz.userinfo.domain.entity.CompanyDept;

/**
 * 公司-部门关联 Repository 接口
 *
 * <p>封装公司-部门关联表（{@code ydsz_company_dept}）的数据访问操作。
 *
 * <p>禁止暴露底层 Mapper，所有数据库操作通过本接口进行。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface CompanyDeptRepository {

  /**
   * 根据 ID 查询公司-部门关联。
   *
   * @param id 关联 ID
   * @return 公司-部门关联实体，不存在时返回 null
   */
  CompanyDept findById(String id);

  /**
   * 根据公司 ID 查询公司-部门关联列表。
   *
   * @param companyId 公司 ID
   * @return 公司-部门关联列表
   */
  List<CompanyDept> findByCompanyId(String companyId);

  /**
   * 根据部门 ID 查询公司-部门关联。
   *
   * @param deptId 部门 ID
   * @return 公司-部门关联，不存在时返回 null
   */
  CompanyDept findByDeptId(String deptId);

  /**
   * 条件查询公司-部门关联列表。
   *
   * @param wrapper 查询条件
   * @return 关联列表
   */
  List<CompanyDept> list(
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CompanyDept> wrapper);

  /**
   * 保存公司-部门关联（插入）。
   *
   * @param entity 公司-部门关联实体
   * @return 插入影响的行数
   */
  int insert(CompanyDept entity);

  /**
   * 更新公司-部门关联。
   *
   * @param entity 公司-部门关联实体
   * @return 更新影响的行数
   */
  int updateById(CompanyDept entity);

  /**
   * 根据公司 ID 删除关联。
   *
   * @param companyId 公司 ID
   * @return 删除影响的行数
   */
  int deleteByCompanyId(String companyId);

  /**
   * 根据 ID 删除关联（逻辑删除）。
   *
   * @param id 关联 ID
   * @return 删除影响的行数
   */
  int deleteById(String id);

  /**
   * 条件删除公司-部门关联。
   *
   * @param wrapper 删除条件
   * @return 删除影响的行数
   */
  int delete(
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CompanyDept> wrapper);
}
