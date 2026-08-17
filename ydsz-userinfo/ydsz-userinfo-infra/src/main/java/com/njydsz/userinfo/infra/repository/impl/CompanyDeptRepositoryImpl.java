package com.njydsz.userinfo.infra.repository.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.infra.repository.CompanyDeptRepository;
import com.njydsz.userinfo.infra.entity.CompanyDeptDO;
import com.njydsz.userinfo.infra.mapper.CompanyDeptMapper;

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
  public CompanyDeptDO findById(String id) {
    return companyDeptMapper.selectById(id);
  }

  @Override
  public List<CompanyDeptDO> findByCompanyId(String companyId) {
    LambdaQueryWrapper<CompanyDeptDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(CompanyDeptDO::getCompanyId, companyId);
    return companyDeptMapper.selectList(wrapper);
  }

  @Override
  public CompanyDeptDO findByDeptId(String deptId) {
    LambdaQueryWrapper<CompanyDeptDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(CompanyDeptDO::getDeptId, deptId);
    return companyDeptMapper.selectOne(wrapper);
  }

  @Override
  public List<CompanyDeptDO> list(LambdaQueryWrapper<CompanyDeptDO> wrapper) {
    return companyDeptMapper.selectList(wrapper);
  }

  @Override
  public int insert(CompanyDeptDO entity) {
    return companyDeptMapper.insert(entity);
  }

  @Override
  public int updateById(CompanyDeptDO entity) {
    return companyDeptMapper.updateById(entity);
  }

  @Override
  public int deleteById(String id) {
    return companyDeptMapper.deleteById(id);
  }

  @Override
  public int deleteByCompanyId(String companyId) {
    LambdaQueryWrapper<CompanyDeptDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(CompanyDeptDO::getCompanyId, companyId);
    return companyDeptMapper.delete(wrapper);
  }

  @Override
  public int delete(LambdaQueryWrapper<CompanyDeptDO> wrapper) {
    return companyDeptMapper.delete(wrapper);
  }
}
