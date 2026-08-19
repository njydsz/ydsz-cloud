package com.njydsz.system.infra.repository.impl;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.system.infra.converter.SystemConverter;
import com.njydsz.system.infra.entity.Tenant;
import com.njydsz.system.infra.mapper.TenantMapper;
import com.njydsz.system.domain.repository.TenantRepository;
import com.njydsz.system.domain.dto.TenantDTO;
import com.njydsz.system.domain.query.TenantPageQuery;
import com.njydsz.system.domain.vo.TenantVO;

/**
 * 租户仓储实现（Infra 层）。
 *
 * <p>实现 {@link TenantRepository} 接口，封装 {@link TenantMapper} 数据访问细节。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>所有数据访问通过本类的语义方法，禁止暴露 Mapper
 *   <li>通过 {@link SystemConverter} 将 DO 转换为 VO 后返回
 *   <li>CUD 入参 DTO 通过 {@link SystemConverter} 转换为 DO 后执行数据库操作
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class TenantRepositoryImpl implements TenantRepository {

  private final TenantMapper tenantMapper;

  private final SystemConverter converter;

  @Override
  public Optional<TenantVO> findById(String id) {
    return Optional.ofNullable(tenantMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public PageResponse<List<TenantVO>> findByPage(TenantPageQuery query) {
    Page<Tenant> page = new Page<>(query.getPageNum(), query.getPageSize());
    LambdaQueryWrapper<Tenant> wrapper = new LambdaQueryWrapper<>();
    if (query.getTenantName() != null && !query.getTenantName().isBlank()) {
      wrapper.like(Tenant::getTenantName, query.getTenantName());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq(Tenant::getStatus, query.getStatus());
    }
    wrapper.orderByDesc(Tenant::getCreatedAt);
    com.baomidou.mybatisplus.core.metadata.IPage<Tenant> result = tenantMapper.selectPage(page, wrapper);
    List<TenantVO> vos = converter.tenantListToVO(result.getRecords());
    return PageResponse.success(result.getTotal(), (long)query.getPageNum(), (long)query.getPageSize(), vos);
  }

  @Override
  public long countByCondition(TenantPageQuery query) {
    LambdaQueryWrapper<Tenant> wrapper = new LambdaQueryWrapper<>();
    if (query.getTenantName() != null && !query.getTenantName().isBlank()) {
      wrapper.like(Tenant::getTenantName, query.getTenantName());
    }
    if (query.getTenantCode() != null && !query.getTenantCode().isBlank()) {
      wrapper.eq(Tenant::getTenantCode, query.getTenantCode());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq(Tenant::getStatus, query.getStatus());
    }
    Long count = tenantMapper.selectCount(wrapper);
    return count != null ? count : 0L;
  }

  @Override
  public boolean insert(TenantDTO dto) {
    Tenant entity = converter.dtoToEntity(dto);
    return tenantMapper.insert(entity) > 0;
  }

  @Override
  public boolean updateById(TenantDTO dto) {
    Tenant entity = converter.dtoToEntityWithId(dto);
    return tenantMapper.updateById(entity) > 0;
  }

  @Override
  public boolean deleteById(String id) {
    return tenantMapper.deleteById(id) > 0;
  }

  @Override
  public int disableExpiredTenants() {
    return tenantMapper.disableExpiredTenants();
  }
}
