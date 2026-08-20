package com.njydsz.userinfo.infra.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.userinfo.domain.dto.CompanyDTO;
import com.njydsz.userinfo.domain.query.CompanyPageQuery;
import com.njydsz.userinfo.domain.repository.CompanyRepository;
import com.njydsz.userinfo.domain.vo.CompanyVO;
import com.njydsz.userinfo.infra.converter.UserInfoOrgConverter;
import com.njydsz.userinfo.infra.entity.CompanyDO;
import com.njydsz.userinfo.infra.mapper.CompanyMapper;

/**
 * 公司 Repository 实现
 *
 * <p>基于 MyBatis-Plus 的 {@link CompanyMapper} 实现公司的数据访问。
 * 所有返回值通过 {@link UserInfoOrgConverter} 从 DO 转换为 VO，对调用方屏蔽持久化细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class CompanyRepositoryImpl implements CompanyRepository {

  private final CompanyMapper companyMapper;
  private final UserInfoOrgConverter converter;

  @Override
  public Optional<CompanyVO> findById(String id) {
    CompanyDO entity = companyMapper.selectById(id);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public Optional<CompanyVO> findByCompanyCode(String companyCode) {
    LambdaQueryWrapper<CompanyDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(CompanyDO::getCompanyCode, companyCode);
    CompanyDO entity = companyMapper.selectOne(wrapper);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public PageResponse<List<CompanyVO>> page(CompanyPageQuery query) {
    Page<CompanyDO> page = new Page<>(query.getPageNum(), query.getPageSize());
    LambdaQueryWrapper<CompanyDO> wrapper = buildWrapper(query);
    Page<CompanyDO> result = companyMapper.selectPage(page, wrapper);
    List<CompanyVO> vos = converter.companyListToVO(result.getRecords());
    return PageResponse.success(
        result.getTotal(),
        (long) query.getPageNum(),
        (long) query.getPageSize(),
        vos);
  }

  @Override
  public List<CompanyVO> list(CompanyPageQuery query) {
    LambdaQueryWrapper<CompanyDO> wrapper = buildWrapper(query);
    List<CompanyDO> entities = companyMapper.selectList(wrapper);
    return converter.companyListToVO(entities);
  }

  @Override
  public List<CompanyVO> listByIds(Collection<String> ids) {
    List<CompanyDO> entities = companyMapper.selectBatchIds(ids);
    return converter.companyListToVO(entities);
  }

  @Override
  public CompanyVO save(CompanyDTO dto) {
    if (dto.getId() == null || dto.getId().isBlank()) {
      CompanyDO entity = converter.dtoToEntity(dto);
      companyMapper.insert(entity);
      return converter.entityToVO(entity);
    } else {
      CompanyDO entity = converter.dtoToEntityWithId(dto);
      companyMapper.updateById(entity);
      return converter.entityToVO(entity);
    }
  }

  @Override
  public boolean deleteById(String id) {
    return companyMapper.deleteById(id) > 0;
  }

  @Override
  public long countByQuery(CompanyPageQuery query) {
    LambdaQueryWrapper<CompanyDO> wrapper = buildWrapper(query);
    return companyMapper.selectCount(wrapper);
  }

  private LambdaQueryWrapper<CompanyDO> buildWrapper(CompanyPageQuery query) {
    LambdaQueryWrapper<CompanyDO> wrapper = new LambdaQueryWrapper<>();
    if (query.getCompanyCode() != null && !query.getCompanyCode().isBlank()) {
      wrapper.like(CompanyDO::getCompanyCode, query.getCompanyCode());
    }
    if (query.getCompanyName() != null && !query.getCompanyName().isBlank()) {
      wrapper.like(CompanyDO::getCompanyName, query.getCompanyName());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq(CompanyDO::getStatus, query.getStatus());
    }
    return wrapper;
  }
}
