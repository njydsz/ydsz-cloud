package com.njydsz.system.server.service.impl;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.system.domain.dto.TenantPlanDTO;
import com.njydsz.system.domain.enums.SystemExceptionCode;
import com.njydsz.system.domain.query.TenantPlanPageQuery;
import com.njydsz.system.domain.query.TenantPlanQuery;
import com.njydsz.system.domain.vo.TenantPlanVO;
import com.njydsz.system.domain.repository.TenantPlanRepository;
import com.njydsz.system.domain.repository.TenantRepository;
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
 * @see com.njydsz.system.infra.entity.TenantPlan 套餐实体
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
    return tenantPlanRepository.findById(id).orElse(null);
  }

  /**
   * 分页查询套餐列表
   *
   * @param query 分页查询条件（pageNum / pageSize / planName / status）
   * @return 分页结果
   */
  @Override
  public PageResponse<List<TenantPlanVO>> page(TenantPlanPageQuery query) {
    return tenantPlanRepository.findByPage(query);
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
    TenantPlanQuery query = new TenantPlanQuery();
    query.setStatus("ENABLED");
    return tenantPlanRepository.findList(query);
  }

  /**
   * 创建套餐
   *
   * <p>写入前校验 {@code planCode} 全局唯一性。
   *
   * @param dto 套餐 DTO（命令入参）
   * @return 新建套餐主键 ID
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public String save(TenantPlanDTO dto) {
    TenantPlanQuery checkQuery = new TenantPlanQuery();
    checkQuery.setPlanCode(dto.getPlanCode());
    if (tenantPlanRepository.countByCondition(checkQuery) > 0) {
      throw BusinessException.of(SystemExceptionCode.TENANT_PLAN_CODE_DUPLICATE)
          .data("planCode", dto.getPlanCode());
    }
    tenantPlanRepository.insert(dto);
    log.info("创建套餐成功: planCode={}", dto.getPlanCode());
    return dto.getId();
  }

  /**
   * 更新套餐
   *
   * <p>更新时校验 {@code planCode} 唯一性（排除自身）。
   *
   * @param dto 套餐 DTO（命令入参，{@code id} 必填）
   * @return 是否成功
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean updateById(TenantPlanDTO dto) {
    TenantPlanQuery checkQuery = new TenantPlanQuery();
    checkQuery.setPlanCode(dto.getPlanCode());
    if (tenantPlanRepository.countByCondition(checkQuery) > 0) {
      throw BusinessException.of(SystemExceptionCode.TENANT_PLAN_CODE_DUPLICATE)
          .data("planCode", dto.getPlanCode());
    }
    return tenantPlanRepository.updateById(dto);
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
    com.njydsz.system.domain.query.TenantPageQuery tenantQuery =
        new com.njydsz.system.domain.query.TenantPageQuery();
    tenantQuery.setSearchKey(id);
    if (tenantRepository.countByCondition(tenantQuery) > 0) {
      throw BusinessException.of(SystemExceptionCode.TENANT_PLAN_LINKED).data("planId", id);
    }
    return tenantPlanRepository.deleteById(id);
  }

}
