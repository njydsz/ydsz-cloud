package com.njydsz.system.infra.repository;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.system.domain.dto.TenantPlanDTO;
import com.njydsz.system.domain.query.TenantPlanPageQuery;
import com.njydsz.system.domain.query.TenantPlanQuery;
import com.njydsz.system.domain.repository.TenantPlanRepository;
import com.njydsz.system.domain.vo.TenantPlanVO;
import com.njydsz.system.domain.converter.SystemConverter;
import com.njydsz.system.domain.entity.TenantPlan;
import com.njydsz.system.infra.mapper.TenantPlanMapper;




/**
 * 租户方案仓储实现（Infra 层）。
 *
 * <p>实现 {@link TenantPlanRepository} 接口，封装 {@link TenantPlanMapper} 数据访问细节。
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
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class TenantPlanRepositoryImpl implements TenantPlanRepository {

  private final TenantPlanMapper tenantPlanMapper;

  private final SystemConverter converter;

  @Override
  public Optional<TenantPlanVO> findById(String id) {
    return Optional.ofNullable(tenantPlanMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public PageResponse<List<TenantPlanVO>> findByPage(TenantPlanPageQuery query) {
    Page<TenantPlan> page = new Page<>(query.getPageNum(), query.getPageSize());
    LambdaQueryWrapper<TenantPlan> wrapper = new LambdaQueryWrapper<>();
    if (query.getPlanName() != null && !query.getPlanName().isBlank()) {
      wrapper.like(TenantPlan::getPlanName, query.getPlanName());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq(TenantPlan::getStatus, query.getStatus());
    }
    wrapper.orderByAsc(TenantPlan::getSortOrder);
    IPage<TenantPlan> result = tenantPlanMapper.selectPage(page, wrapper);
    List<TenantPlanVO> vos = converter.planListToVO(result.getRecords());
    return PageResponse.success(result.getTotal(), (long) query.getPageNum(), (long) query.getPageSize(), vos);
  }

  @Override
  public List<TenantPlanVO> findList(TenantPlanQuery query) {
    LambdaQueryWrapper<TenantPlan> wrapper = new LambdaQueryWrapper<>();
    if (query.getPlanName() != null && !query.getPlanName().isBlank()) {
      wrapper.like(TenantPlan::getPlanName, query.getPlanName());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq(TenantPlan::getStatus, query.getStatus());
    }
    wrapper.orderByAsc(TenantPlan::getSortOrder);
    return converter.planListToVO(tenantPlanMapper.selectList(wrapper));
  }

  @Override
  public long countByCondition(TenantPlanQuery query) {
    LambdaQueryWrapper<TenantPlan> wrapper = new LambdaQueryWrapper<>();
    if (query.getPlanName() != null && !query.getPlanName().isBlank()) {
      wrapper.like(TenantPlan::getPlanName, query.getPlanName());
    }
    if (query.getPlanCode() != null && !query.getPlanCode().isBlank()) {
      wrapper.eq(TenantPlan::getPlanCode, query.getPlanCode());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq(TenantPlan::getStatus, query.getStatus());
    }
    Long count = tenantPlanMapper.selectCount(wrapper);
    return count != null ? count : 0L;
  }

  @Override
  public boolean insert(TenantPlanDTO dto) {
    TenantPlan entity = converter.dtoToEntity(dto);
    boolean success = tenantPlanMapper.insert(entity) > 0;
    // MyBatis-Plus 回填 snowflake ID 到 entity，需同步回 DTO
    if (success && entity.getId() != null) {
      dto.setId(entity.getId());
    }
    return success;
  }

  @Override
  public boolean updateById(TenantPlanDTO dto) {
    TenantPlan entity = converter.dtoToEntityWithId(dto);
    return tenantPlanMapper.updateById(entity) > 0;
  }

  @Override
  public boolean deleteById(String id) {
    return tenantPlanMapper.deleteById(id) > 0;
  }
}
