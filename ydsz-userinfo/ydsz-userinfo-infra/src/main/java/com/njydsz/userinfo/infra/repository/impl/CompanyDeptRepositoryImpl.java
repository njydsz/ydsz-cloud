package com.njydsz.userinfo.infra.repository.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.entity.CompanyDept;
import com.njydsz.userinfo.infra.mapper.CompanyDeptMapper;
import com.njydsz.userinfo.infra.repository.CompanyDeptRepository;

/**
 * 公司-部门关联 Repository 实现
 *
 * <p>基于 MyBatis-Plus 的 {@link CompanyDeptMapper} 实现公司-部门关联的数据访问。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class CompanyDeptRepositoryImpl implements CompanyDeptRepository {

  private final CompanyDeptMapper companyDeptMapper;

  @Override
  public CompanyDept findById(String id) {
    return companyDeptMapper.selectById(id);
  }

  @Override
  public List<CompanyDept> findByCompanyId(String companyId) {
    LambdaQueryWrapper<CompanyDept> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(CompanyDept::getCompanyId, companyId);
    return companyDeptMapper.selectList(wrapper);
  }

  @Override
  public CompanyDept findByDeptId(String deptId) {
    LambdaQueryWrapper<CompanyDept> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(CompanyDept::getDeptId, deptId);
    return companyDeptMapper.selectOne(wrapper);
  }

  @Override
  public List<CompanyDept> list(LambdaQueryWrapper<CompanyDept> wrapper) {
    return companyDeptMapper.selectList(wrapper);
  }

  @Override
  public int insert(CompanyDept entity) {
    return companyDeptMapper.insert(entity);
  }

  @Override
  public int updateById(CompanyDept entity) {
    return companyDeptMapper.updateById(entity);
  }

  @Override
  public int deleteByCompanyId(String companyId) {
    LambdaQueryWrapper<CompanyDept> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(CompanyDept::getCompanyId, companyId);
    return companyDeptMapper.delete(wrapper);
  }

  @Override
  public int delete(LambdaQueryWrapper<CompanyDept> wrapper) {
    return companyDeptMapper.delete(wrapper);
  }
}
