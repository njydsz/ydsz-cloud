package com.njydsz.system.server.service.impl;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.system.domain.dto.TenantPlanMenuDTO;
import com.njydsz.system.domain.vo.TenantPlanMenuVO;
import com.njydsz.system.domain.repository.TenantPlanMenuRepository;
import com.njydsz.system.server.service.TenantPlanMenuService;

/**
 * 租户套餐-菜单关联 Service 实现
 *
 * <p>对 {@link TenantPlanMenuService} 接口的完整实现。 提供套餐与菜单关联关系的配置能力。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>查询</b>：{@link #listByPlanId} — 查询指定套餐关联的菜单列表
 *   <li><b>批量更新</b>：{@link #updatePlanMenus} — 为套餐批量配置菜单权限（先删后插，事务保证）
 * </ul>
 *
 * <p><b>P1-2 优化：</b>批量插入由 N 次单条 INSERT 改为 1 次批量 INSERT（{@code TenantPlanMenuMapper#insertBatch}），
 * 消除逐条写入的 N 次 DB 往返。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see TenantPlanMenuService 套餐-菜单关联 Service 接口
 * @see com.njydsz.system.infra.entity.TenantPlanMenu 套餐-菜单关联实体
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantPlanMenuServiceImpl implements TenantPlanMenuService {

  /** 套餐-菜单关联 Repository */
  private final TenantPlanMenuRepository tenantPlanMenuRepository;

  /**
   * 查询指定套餐关联的菜单列表
   *
   * @param planId 套餐 ID
   * @return 套餐-菜单关联列表
   */
  @Override
  public List<TenantPlanMenuVO> listByPlanId(String planId) {
    return tenantPlanMenuRepository.findByPlanId(planId);
  }

  /**
   * 为套餐批量配置菜单权限
   *
   * <p>执行逻辑：先删除该套餐的所有旧关联，再批量插入新的关联记录。 整个操作在事务内完成。
   *
   * @param dto 套餐-菜单关联 DTO
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public void updatePlanMenus(TenantPlanMenuDTO dto) {
    // 1. 删除旧关联
    tenantPlanMenuRepository.deleteByPlanId(dto.getPlanId());

    // 2. 批量插入新关联（1 次 SQL 替代 N 次单条 INSERT）
    if (dto.getMenuIds() == null || dto.getMenuIds().isEmpty()) {
      log.info("套餐[{}]菜单配置已清空", dto.getPlanId());
      return;
    }
    tenantPlanMenuRepository.insertBatch(dto);
    log.info("套餐[{}]菜单配置已更新, 菜单数量={}", dto.getPlanId(), dto.getMenuIds().size());
  }
}
