package com.njydsz.userinfo.infra.repository.impl;

import java.util.Collection;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.entity.Company;
import com.njydsz.userinfo.infra.mapper.CompanyMapper;
import com.njydsz.userinfo.infra.repository.CompanyRepository;

/**
 * 公司 Repository 实现
 *
 * <p>基于 MyBatis-Plus 的 {@link CompanyMapper} 实现公司的数据访问。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class CompanyRepositoryImpl implements CompanyRepository {

  private final CompanyMapper companyMapper;

  @Override
  public Company findById(String id) {
    return companyMapper.selectById(id);
  }

  @Override
  public Company findByCompanyCode(String companyCode) {
    LambdaQueryWrapper<Company> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Company::getCompanyCode, companyCode);
    return companyMapper.selectOne(wrapper);
  }

  @Override
  public List<Company> list(LambdaQueryWrapper<Company> wrapper) {
    return companyMapper.selectList(wrapper);
  }

  @Override
  public List<Company> listByIds(Collection<String> ids) {
    return companyMapper.selectBatchIds(ids);
  }

  @Override
  public int insert(Company entity) {
    return companyMapper.insert(entity);
  }

  @Override
  public int updateById(Company entity) {
    return companyMapper.updateById(entity);
  }

  @Override
  public int deleteById(String id) {
    return companyMapper.deleteById(id);
  }

  @Override
  public long count(LambdaQueryWrapper<Company> wrapper) {
    return companyMapper.selectCount(wrapper);
  }
}
