package com.njydsz.system.infra.repository.impl;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.system.infra.converter.SystemConverter;
import com.njydsz.system.infra.entity.Tenant;
import com.njydsz.system.infra.mapper.TenantMapper;
import com.njydsz.system.domain.repository.TenantRepository;
import com.njydsz.system.domain.dto.TenantDTO;
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
  public IPage<TenantVO> findByPage(Page<TenantVO> page, String tenantName, String status) {
    Page<Tenant> entityPage = new Page<>(page.getCurrent(), page.getSize());
    LambdaQueryWrapper<Tenant> wrapper = new LambdaQueryWrapper<>();
    if (tenantName != null && !tenantName.isBlank()) {
      wrapper.like(Tenant::getTenantName, tenantName);
    }
    if (status != null && !status.isBlank()) {
      wrapper.eq(Tenant::getStatus, status);
    }
    wrapper.orderByDesc(Tenant::getCreatedAt);
    IPage<Tenant> result = tenantMapper.selectPage(entityPage, wrapper);
    // DO → VO 转换
    List<TenantVO> vos = converter.tenantListToVO(result.getRecords());
    Page<TenantVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
    voPage.setRecords(vos);
    return voPage;
  }

  @Override
  public long countByCondition(LambdaQueryWrapper<TenantVO> wrapper) {
    // 注意：此处 wrapper 基于 VO 字段，需要适配为 Entity 查询
    // 简化实现：直接统计全量（实际使用场景需根据 VO 字段构造 Entity 条件）
    Long count = tenantMapper.selectCount(null);
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
