package com.njydsz.userinfo.infra.repository.impl;

import java.util.Collection;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.repository.CompanyRepository;
import com.njydsz.userinfo.infra.entity.CompanyDO;
import com.njydsz.userinfo.infra.mapper.CompanyMapper;

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
  public CompanyDO findById(String id) {
    return companyMapper.selectById(id);
  }

  @Override
  public CompanyDO findByCompanyCode(String companyCode) {
    LambdaQueryWrapper<CompanyDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(CompanyDO::getCompanyCode, companyCode);
    return companyMapper.selectOne(wrapper);
  }

  @Override
  public List<CompanyDO> list(LambdaQueryWrapper<CompanyDO> wrapper) {
    return companyMapper.selectList(wrapper);
  }

  @Override
  public List<CompanyDO> listByIds(Collection<String> ids) {
    return companyMapper.selectBatchIds(ids);
  }

  @Override
  public int insert(CompanyDO entity) {
    return companyMapper.insert(entity);
  }

  @Override
  public int updateById(CompanyDO entity) {
    return companyMapper.updateById(entity);
  }

  @Override
  public int deleteById(String id) {
    return companyMapper.deleteById(id);
  }

  @Override
  public long count(LambdaQueryWrapper<CompanyDO> wrapper) {
    return companyMapper.selectCount(wrapper);
  }
}
