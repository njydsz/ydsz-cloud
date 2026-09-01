package com.njydsz.system.domain.repository;

import java.util.List;

import com.njydsz.system.domain.dto.TenantPlanMenuDTO;
import com.njydsz.system.domain.vo.TenantPlanMenuVO;

/**
 * 租户套餐-菜单关联仓储接口（domain 层契约）。
 *
 * <p>定义套餐-菜单关联数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link TenantPlanMenuVO}），非 DTO / infra 实体
 *   <li>CUD 入参使用领域 DTO（{@link TenantPlanMenuDTO}），禁止接受 infra 实体
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface TenantPlanMenuRepository {

  /**
   * 按套餐 ID 查询套餐-菜单关联列表。
   *
   * @param planId 套餐 ID
   * @return 套餐-菜单关联 VO 列表
   */
  List<TenantPlanMenuVO> findByPlanId(String planId);

  /**
   * 按套餐 ID 删除套餐-菜单关联。
   *
   * @param planId 套餐 ID
   * @return 删除成功返回 {@code true}
   */
  boolean deleteByPlanId(String planId);

  /**
   * 批量插入套餐-菜单关联（一次 SQL 批量写入）。
   *
   * @param dto 套餐-菜单关联 DTO（含套餐 ID + 菜单 ID 列表）
   * @return 插入成功返回 {@code true}
   */
  boolean insertBatch(TenantPlanMenuDTO dto);
}
