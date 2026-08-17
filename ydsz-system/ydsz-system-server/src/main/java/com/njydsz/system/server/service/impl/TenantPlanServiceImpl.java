package com.njydsz.system.server.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.jdbc.support.PageResponses;
import com.njydsz.system.domain.converter.SystemConverter;
import com.njydsz.system.domain.entity.Tenant;
import com.njydsz.system.domain.entity.TenantPlan;
import com.njydsz.system.domain.enums.SystemExceptionCode;
import com.njydsz.system.domain.vo.TenantPlanVO;
import com.njydsz.system.infra.repository.TenantPlanRepository;
import com.njydsz.system.infra.repository.TenantRepository;
import com.njydsz.system.server.service.TenantPlanService;

/**
 * 租户套餐 Service 实现
 *
 * <p>对 {@link TenantPlanService} 接口的完整实现，是「租户套餐管理」的核心业务逻辑层。 提供套餐的 CRUD、分页查询、全量查询等能力。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #save} / {@link #updateById} / {@link #removeById}
 *   <li><b>分页查询</b>：{@link #page} — 管理后台「套餐管理」列表
 *   <li><b>全量查询</b>：{@link #listAll} — 租户注册页「选择套餐」下拉数据源
 *   <li><b>关联校验</b>：删除前校验是否有关联租户，有关联时禁止删除
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see TenantPlanService 套餐 Service 接口
 * @see com.njydsz.system.domain.entity.TenantPlan 套餐实体
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantPlanServiceImpl implements TenantPlanService {

  /** 套餐 Repository */
  private final TenantPlanRepository tenantPlanRepository;

  /** 租户 Repository（用于关联校验） */
  private final TenantRepository tenantRepository;

  /**
   * 按 ID 查询套餐
   *
   * @param id 主键 ID
   * @return 套餐 VO；不存在返回 {@code null}
   */
  @Override
  public TenantPlanVO getById(String id) {
    TenantPlan entity = tenantPlanRepository.getTenantPlanMapper().selectById(id);
    return SystemConverter.INSTANT.entityToVO(entity);
  }

  /**
   * 分页查询套餐列表
   *
   * @param pageNum 当前页码
   * @param pageSize 每页记录数
   * @param planName 套餐名称模糊搜索（可选）
   * @param status 状态过滤（可选）
   * @return 分页结果
   */
  @Override
  public PageResponse<List<TenantPlanVO>> page(
      int pageNum, int pageSize, String planName, String status) {
    LambdaQueryWrapper<TenantPlan> wrapper = new LambdaQueryWrapper<>();
    if (planName != null && !planName.isBlank()) {
      wrapper.like(TenantPlan::getPlanName, planName);
    }
    if (status != null && !status.isBlank()) {
      wrapper.eq(TenantPlan::getStatus, status);
    }
    wrapper.orderByAsc(TenantPlan::getSortOrder);
    IPage<TenantPlan> page =
        tenantPlanRepository
            .getTenantPlanMapper()
            .selectPage(new Page<>(pageNum, pageSize), wrapper);
    return PageResponses.success(page, SystemConverter.INSTANT::entityToVO);
  }

  /**
   * 查询全部启用套餐
   *
   * <p>按 {@code sortOrder} 升序返回，供租户注册页「选择套餐」下拉使用。
   *
   * @return 套餐列表
   */
  @Override
  public List<TenantPlanVO> listAll() {
    LambdaQueryWrapper<TenantPlan> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(TenantPlan::getStatus, "ENABLED");
    wrapper.orderByAsc(TenantPlan::getSortOrder);
    return tenantPlanRepository.getTenantPlanMapper().selectList(wrapper).stream()
        .map(SystemConverter.INSTANT::entityToVO)
        .collect(Collectors.toList());
  }

  /**
   * 创建套餐
   *
   * <p>写入前校验 {@code planCode} 全局唯一性。
   *
   * @param vo 套餐 DTO
   * @return 新建套餐主键 ID
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public String save(TenantPlanVO vo) {
    LambdaQueryWrapper<TenantPlan> checkWrapper = new LambdaQueryWrapper<>();
    checkWrapper.eq(TenantPlan::getPlanCode, vo.getPlanCode());
    if (tenantPlanRepository.getTenantPlanMapper().selectCount(checkWrapper) > 0) {
      throw BusinessException.of(SystemExceptionCode.TENANT_PLAN_CODE_DUPLICATE)
          .data("planCode", vo.getPlanCode());
    }
    TenantPlan entity = toEntity(vo);
    tenantPlanRepository.getTenantPlanMapper().insert(entity);
    log.info("创建套餐成功: planCode={}, planId={}", vo.getPlanCode(), entity.getId());
    return entity.getId();
  }

  /**
   * 更新套餐
   *
   * <p>更新时校验 {@code planCode} 唯一性（排除自身）。
   *
   * @param vo 套餐 DTO（{@code id} 必填）
   * @return 是否成功
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean updateById(TenantPlanVO vo) {
    LambdaQueryWrapper<TenantPlan> checkWrapper = new LambdaQueryWrapper<>();
    checkWrapper.eq(TenantPlan::getPlanCode, vo.getPlanCode());
    checkWrapper.ne(TenantPlan::getId, vo.getId());
    if (tenantPlanRepository.getTenantPlanMapper().selectCount(checkWrapper) > 0) {
      throw BusinessException.of(SystemExceptionCode.TENANT_PLAN_CODE_DUPLICATE)
          .data("planCode", vo.getPlanCode());
    }
    TenantPlan entity = toEntity(vo);
    return tenantPlanRepository.getTenantPlanMapper().updateById(entity) > 0;
  }

  /**
   * 删除套餐
   *
   * <p>删除前校验是否有关联租户。若存在关联租户，禁止删除并抛出 {@link SystemExceptionCode#TENANT_PLAN_LINKED} 异常。
   *
   * @param id 主键 ID
   * @return 是否成功
   * @throws BusinessException 套餐下存在关联租户时抛出 {@link SystemExceptionCode#TENANT_PLAN_LINKED}
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean removeById(String id) {
    // 关联校验：是否存在引用该套餐的租户
    LambdaQueryWrapper<Tenant> tenantWrapper = new LambdaQueryWrapper<>();
    tenantWrapper.eq(Tenant::getPlanId, id);
    if (tenantRepository.getTenantMapper().selectCount(tenantWrapper) > 0) {
      throw BusinessException.of(SystemExceptionCode.TENANT_PLAN_LINKED).data("planId", id);
    }
    return tenantPlanRepository.getTenantPlanMapper().deleteById(id) > 0;
  }

  /**
   * DTO → DO 转换（私有）
   *
   * @param vo 套餐 DTO
   * @return 套餐 Entity
   */
  private TenantPlan toEntity(TenantPlanVO vo) {
    TenantPlan entity = new TenantPlan();
    entity.setId(vo.getId());
    entity.setPlanCode(vo.getPlanCode());
    entity.setPlanName(vo.getPlanName());
    entity.setDescription(vo.getDescription());
    entity.setSortOrder(vo.getSortOrder());
    entity.setQuotaJson(vo.getQuotaJson());
    entity.setFeatureJson(vo.getFeatureJson());
    return entity;
  }
}
